// $Id: Proxy.java,v 1.3.4.1 2008/01/22 10:01:16 belaban Exp $



import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * Redirects incoming TCP connections to other hosts/ports. All redirections are defined in a file as for example
 * <pre>
 * 127.0.0.1:8888=www.ibm.com:80
 * localhost:80=pop.mail.yahoo.com:110
 * </pre>
 * The first line forwards all requests to port 8888 on to www.ibm.com at port 80 (it also forwards the HTTP
 * response back to the sender. The second line essentially provides a POP-3 service on port 8110, using
 * Yahoo's POP service. This is neat when you're behind a firewall and one of the few services in the outside
 * world that are not blocked is port 80 (HHTP).<br/>
 * Note that JDK 1.4 is required for this class. Also, concurrent.jar has to be on the classpath. Note that
 * you also need to include jsse.jar/jce.jar (same location as rt.jar) if you want SSL sockets.<br>
 * To create SSLServerSockets you'll need to do the following:
 * Generate a certificate as follows:
 * <pre>
 * keytool -genkey -keystore /home/bela/.keystore -keyalg rsa -alias bela -storepass <passwd> -keypass <passwd>
 * </pre>
 *
 * Start the Proxy as follows:
 * <pre>
 * java -Djavax.net.ssl.keyStore=/home/bela/.keystore -Djavax.net.ssl.keyStorePassword=<passwd>
 *      -Djavax.net.ssl.trustStore=/home/bela/.keystore -Djavax.net.ssl.trustStorePassword=<passwd>
 *      org.jgroups.util.Proxy -file /home/bela/map.properties
 * </pre>
 * Start client as follows:
 * <pre>
 * java -Djavax.net.ssl.trustStore=/home/bela/.keystore -Djavax.net.ssl.trustStorePassword=<passwd> sslclient
 * </pre>
 * <br/>
 * To import a certificate into the keystore, use the following steps:
 * <pre>
 * openssl x509 -in server.crt -out server.crt.der -outform DER
 * keytool -import -trustcacerts -alias <your alias name> -file server.crt.der
 * </pre>
 * This will store the server's certificate in the ${user.home}/.keystore key store.
 * <br/>
 * Note that an SSL client or server can be debugged by starting it as follows:
 * <pre>-Djava.protocol.handler.pkgs=com.sun.net.ssl.internal.www.protocol -Djavax.net.debug=ssl</pre>
 * <br/>
 * If you run a web browser, simply enter https://<host>:<port> as URL to connect to an SSLServerSocket
 * <br/>Note that we cannot use JDK 1.4's selectors for SSL sockets, as
 * getChannel() on an SSL socket doesn't seem to work.
 * @todo Check whether SSLSocket.getChannel() or SSLServerSocket.getChannel() works.
 * @author Bela Ban
 */
public class Proxy {
    InetAddress           local=null, remote=null;
    int                   local_port=0, remote_port=0;
    static boolean        verbose=false;
    static boolean        debug=false;
    String                mapping_file=null; // contains a list of src and dest host:port pairs
    final HashMap<MyInetSocketAddress, MyInetSocketAddress>     mappings=new HashMap(); // keys=MyInetSocketAddr (src), values=MyInetSocketAddr (dest)
    Executor              executor; // maintains a thread pool
    static final int      MIN_THREAD_POOL_SIZE=2;
    static final int      MAX_THREAD_POOL_SIZE=64; // for processing requests
    static final int      BUFSIZE=1024; // size of data transfer buffer



    public Proxy(InetAddress local, int local_port, InetAddress remote, int remote_port, boolean verbose, boolean debug) {
        this.local=local;
        this.local_port=local_port;
        this.remote=remote;
        this.remote_port=remote_port;
        Proxy.verbose=verbose;
        Proxy.debug=debug;
    }

    public Proxy(InetAddress local, int local_port, InetAddress remote, int remote_port,
                    boolean verbose, boolean debug, String mapping_file) {
        this(local, local_port, remote, remote_port, verbose, debug);
        this.mapping_file=mapping_file;
    }

    public void start() throws Exception {
        Map.Entry           entry;
        Selector            selector;
        ServerSocketChannel sock_channel;
        MyInetSocketAddress key, value;

        if (remote !=null && local !=null)
            mappings.put(new MyInetSocketAddress(local, local_port), new MyInetSocketAddress(remote, remote_port));
        
        if (mapping_file !=null) {
            try {
                populateMappings(mapping_file);
            }
            catch (Exception ex) {
                log("Failed reading " + mapping_file);
                throw ex;
            }
        }

        log("\nProxy started at " + new java.util.Date());

        if (verbose) {
            log("\nMappings:\n---------");
            for (Iterator<Entry<MyInetSocketAddress, MyInetSocketAddress>> it=mappings.entrySet().iterator(); it.hasNext();) {
                entry=(Map.Entry) it.next();
                log(toString((InetSocketAddress) entry.getKey()) + " <--> "
                    + toString((InetSocketAddress) entry.getValue()));
            }
            log("\n");
        }

        // 1. Create a Selector
        selector=Selector.open();

        // Create a thread pool (Executor)
        executor=new ThreadPoolExecutor(MIN_THREAD_POOL_SIZE, MAX_THREAD_POOL_SIZE, 30000, TimeUnit.MILLISECONDS,
                                        new LinkedBlockingQueue<Runnable>(1000));

        for (Iterator<MyInetSocketAddress> it=mappings.keySet().iterator(); it.hasNext();) {
            key=(MyInetSocketAddress) it.next();
            value=(MyInetSocketAddress) mappings.get(key);

//            key=it.next();
//            value= mappings.get(key);

            // if either source or destination are SSL, we cannot use JDK 1.4
            // NIO selectors, but have to fall back on separate threads per connection

            if (key.ssl() || value.ssl()) { // mmmm: update, assuming ssl handled by nio
                // if(2 == 2) {
         //       SocketAcceptor acceptor=new SocketAcceptor(key, value);
           //     executor.execute(acceptor);
             //   continue;
            }

            // 2. Create a ServerSocketChannel
            sock_channel=ServerSocketChannel.open();
            sock_channel.configureBlocking(false);
            
            
//            sock_channel.socket().bind(key);  /// Here related with ssl 
            
           sock_channel.socket().bind(new InetSocketAddress("localhost", 8899));

            // 3. Register the selector with all server sockets. 'Key' is attachment, so we get it again on
            //    select(). That way we can associate it with the mappings hashmap to find the corresponding
            //    value
            sock_channel.register(selector, SelectionKey.OP_ACCEPT, key);  
            //mmm selector=Selector.open(); then  ready_keys=selector.selectedKeys();
        }

        // 4. Start main loop. won't return until CTRL-C'ed        
        loop(selector);
    }



    /** We handle only non-SSL connections */
    void loop(Selector selector) {
        Set<SelectionKey>                 ready_keys;  //mmm  Set --> Set(Selectionkey)
        SelectionKey        key;
        ServerSocketChannel srv_sock;
        SocketChannel       in_sock, out_sock;
        InetSocketAddress   src, dest;

        while (true) {
            if (verbose)
                log("[Proxy] ready to accept connection");

            // 4. Call Selector.select()
            try {
                selector.select();

                // get set of ready objects
                ready_keys=selector.selectedKeys();
                for (Iterator<SelectionKey> it=ready_keys.iterator(); it.hasNext();) {
                    key=(SelectionKey) it.next();
                    it.remove();

                    if (key.isAcceptable()) {
                        srv_sock=(ServerSocketChannel) key.channel();   // svr_sock is a channel 
                        // get server socket and attachment
                       src=(InetSocketAddress) key.attachment();
                        in_sock=srv_sock.accept(); // accept request
                        if (verbose)
                            log("Proxy.loop()", "accepted connection from " + toString(in_sock));
                        dest=(InetSocketAddress) mappings.get(src);
                        // find corresponding dest
                        if (dest == null) {
                            in_sock.close();
                            log("Proxy.loop()", "did not find a destination host for " + src);
                            continue;
                        }
                        else {
                            if (verbose)
                                log("Proxy.loop()", "relaying traffic from " + toString(src) + " to " + toString(dest));
                        }

                        // establish connection to destination host
                        try {
                            out_sock=SocketChannel.open(dest);
                            // uses thread pool (Executor) to handle request, closes socks at end
                            handleConnection(in_sock, out_sock);  //// mmmmmm importtant 
                        }
                        catch (Exception ex) {
                            in_sock.close();
                            throw ex;
                        }
                    }
                }
            }
            catch (Exception ex) {
                log("Proxy.loop()", "exception: " + ex);
            }
        }
    }



    void handleConnection(SocketChannel in, SocketChannel out) {
        try {
            _handleConnection(in, out);
        }
        catch (Exception ex) {
            log("Proxy.handleConnection()", "exception: " + ex);
        }
    }
    
    void _handleConnection(final SocketChannel in_channel, final SocketChannel out_channel) throws Exception {
        executor.execute(new Runnable() {
                public void run() {
                    Selector sel=null;
                    SocketChannel tmp;
                    Set ready_keys;
                    SelectionKey key;
                    ByteBuffer transfer_buf=ByteBuffer.allocate(BUFSIZE);

                    try {
                        sel=Selector.open();
                        in_channel.configureBlocking(false);
                        out_channel.configureBlocking(false);
                        in_channel.register(sel, SelectionKey.OP_READ);
                        out_channel.register(sel, SelectionKey.OP_READ);
                        
                        while (sel.select() > 0) {
                            ready_keys=sel.selectedKeys();
                            for (Iterator it=ready_keys.iterator(); it.hasNext();) {
                                key=(SelectionKey) it.next();
                                it.remove(); // remove current entry (why ?)
                                tmp=(SocketChannel) key.channel();
                                if (tmp == null) {
                                    log(
                                        "Proxy._handleConnection()",
                                        "attachment is null, continuing");
                                    continue;
                                }
                                if (key.isReadable()) { // data is available to be read from tmp
                                    if (tmp == in_channel) {
                                        // read all data from in_channel and forward it to out_channel (request)
                                        if (relay(tmp, out_channel, transfer_buf) == false)
                                            return;
                                    }
                                    if (tmp == out_channel) {
                                        // read all data from out_channel and forward it 
                                        // to in_channel (response)
                                        if (relay(tmp, in_channel, transfer_buf) == false)
                                            return;
                                    }
                                }
                            }
                        }
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    finally {
                        close(sel, in_channel, out_channel);
                    }
                }
            });
    }
    
    void close(Selector sel, SocketChannel in_channel, SocketChannel out_channel) {
        try {
            if (sel !=null)
                sel.close();
        }
        catch (Exception ex) {
        }
        try {
            if (in_channel !=null)
                in_channel.close();
        }
        catch (Exception ex) {
        }
        try {
            if (out_channel !=null)
                out_channel.close();
        }
        catch (Exception ex) {
        }
    }


    /**
     * Read all data from <code>from</code> and write it to <code>to</code>.
     * Returns false if channel was closed
     */
    boolean relay(SocketChannel from, SocketChannel to, ByteBuffer buf) throws Exception {
        int num;
        StringBuilder sb;

        buf.clear();
        while (true) {
            num=from.read(buf);
            if (num < 0)
                return false;
            else
                if (num == 0)
                    return true;
            buf.flip();
            if (verbose) {
                log(printRelayedData(toString(from), toString(to), buf.remaining()));
            }
            if (debug) {
                sb=new StringBuilder();
                sb.append(new String(buf.array()).trim());
                sb.append('\n');
                log(sb.toString());
            }
            to.write(buf);
            buf.flip();
        }
    }

    String toString(SocketChannel ch) {
        StringBuilder sb=new StringBuilder();
        Socket sock;

        if (ch == null)
            return null;
        if ((sock=ch.socket()) == null)
            return null;
        sb.append(sock.getInetAddress().getHostName()).append(':').append(sock.getPort());
        return sb.toString();
    }

    String toString(InetSocketAddress addr) {
        StringBuilder sb;
        sb=new StringBuilder();

        if (addr == null)
            return null;
        sb.append(addr.getAddress().getHostName()).append(':').append(addr.getPort());
        if (addr instanceof MyInetSocketAddress)
            sb.append(" [ssl=").append(((MyInetSocketAddress) addr).ssl()).append(']');
        return sb.toString();
    }

    
    static String printRelayedData(String from, String to, int num_bytes) {
        StringBuilder sb;
        sb=new StringBuilder();
        sb.append("\n[PROXY] ").append(from);
        sb.append(" to ").append(to);
        sb.append(" (").append(num_bytes).append(" bytes)");
        // log("Proxy.relay()", sb.toString());
        return sb.toString();
    }
    

    /**
     * Populates <code>mappings</code> hashmap. An example of a definition file is:
     * <pre>
     * http://localhost:8888=http://www.yahoo.com:80
     * https://localhost:2200=https://cvs.sourceforge.net:22
     * http://localhost:8000=https://www.ibm.com:443
     * </pre>
     * Mappings can be http-https, https-http, http-http or https-https
     */
    void populateMappings(String filename) throws Exception {
        FileInputStream   in=new FileInputStream(filename);
        BufferedReader    reader;
        String            line;
        URI               key, value;
        int               index;
        boolean           ssl_key, ssl_value;
        final String      HTTPS="https";

        reader=new BufferedReader(new InputStreamReader(in));
        while ((line=reader.readLine()) !=null) {
            line=line.trim();
            if (line.startsWith("//") || line.startsWith("#") || line.length() == 0)
                continue;
            index=line.indexOf('=');
            if (index == -1)
                throw new Exception("Proxy.populateMappings(): detected no '=' character in " + line);
            key=new URI(line.substring(0, index));
            ssl_key=key.getScheme().trim().equals(HTTPS);

            value=new URI(line.substring(index + 1));
            ssl_value=value.getScheme().trim().equals(HTTPS);

            check(key);
            check(value);

            log("key: " + key + ", value: " + value);

            mappings.put(new MyInetSocketAddress(key.getHost(), key.getPort(), ssl_key),
                         new MyInetSocketAddress(value.getHost(), value.getPort(), ssl_value));
        }
        in.close();
    }

    /** Checks whether a URI is http(s)://<host>:<port> */
    void check(URI u) throws Exception {
        if (u.getScheme() == null)
            throw new Exception(
                "scheme is null in " + u + ", (valid URI is \"http(s)://<host>:<port>\")");

        if (u.getHost() == null)
            throw new Exception(
                "host is null in " + u + ", (valid URI is \"http(s)://<host>:<port>\")");

        if (u.getPort() <=0)
            throw new Exception(
                "port is <=0 in " + u + ", (valid URI is \"http(s)://<host>:<port>\")");

    }

    /** Input is "host:port" */
    SocketAddress strToAddr(String input) throws Exception {
        StringTokenizer tok=new StringTokenizer(input, ":");
        String host, port;

        host=tok.nextToken();
        port=tok.nextToken();
        return new InetSocketAddress(host, Integer.parseInt(port));
    }

    String printSelectionOps(SelectionKey key) {
        StringBuilder sb=new StringBuilder();
        if ((key.readyOps() & SelectionKey.OP_ACCEPT) !=0)
            sb.append("OP_ACCEPT ");
        if ((key.readyOps() & SelectionKey.OP_CONNECT) !=0)
            sb.append("OP_CONNECT ");
        if ((key.readyOps() & SelectionKey.OP_READ) !=0)
            sb.append("OP_READ ");
        if ((key.readyOps() & SelectionKey.OP_WRITE) !=0)
            sb.append("OP_WRITE ");
        return sb.toString();
    }

    public static void main(String[] args) {
        Proxy    p;
        InetAddress local=null, remote=null;
        int         local_port=0, remote_port=0;
        String      tmp, tmp_addr, tmp_port;
        boolean     verbose=false, debug=false;
        int         index;
        String      mapping_file=null;

        try {
            for (int i=0; i < args.length; i++) {
                tmp=args[i];
                if ("-help".equals(tmp)) {
                    help();
                    return;
                }
                if ("-verbose".equals(tmp)) {
                    verbose=true;
                    continue;
                }
                if ("-local".equals(tmp)) {
                    tmp_addr=args[++i];
                    index=tmp_addr.indexOf(':');
                    if (index > -1) { // it is in the format address:port
                        tmp_port=tmp_addr.substring(index + 1);
                        local_port=Integer.parseInt(tmp_port);
                        tmp_addr=tmp_addr.substring(0, index);
                        local=InetAddress.getByName(tmp_addr);
                    }
                    else
                        local=InetAddress.getByName(args[++i]);
                    continue;
                }
                if ("-local_port".equals(tmp)) {
                    local_port=Integer.parseInt(args[++i]);
                    continue;
                }
                if ("-remote".equals(tmp)) {
                    tmp_addr=args[++i];
                    index=tmp_addr.indexOf(':');
                    if (index > -1) { // it is in the format address:port
                        tmp_port=tmp_addr.substring(index + 1);
                        remote_port=Integer.parseInt(tmp_port);
                        tmp_addr=tmp_addr.substring(0, index);
                        remote=InetAddress.getByName(tmp_addr);
                    }
                    else
                        remote=InetAddress.getByName(args[++i]);
                    continue;
                }
                if ("-remote_port".equals(tmp)) {
                    remote_port=Integer.parseInt(args[++i]);
                    continue;
                }
                if ("-file".equals(tmp)) {
                    mapping_file=args[++i];
                    continue;
                }
                if ("-debug".equals(tmp)) {
                    debug=true;
                    continue;
                }
                help();
                return;
            }

            if (local == null)
                local=InetAddress.getLocalHost();

//            p=new Proxy(local, local_port, remote, remote_port, verbose, debug, mapping_file);
            
            p = new Proxy(InetAddress.getLocalHost(), 8899, InetAddress.getLocalHost(), 8511, true, true, null);
            p.start();
            
        }
        catch (Throwable ex) {
            ex.printStackTrace();
        }
    }

    static void help() {
        System.out.println("Proxy [-help] [-local <local address>] [-local_port <port>] "
                           + "[-remote <remote address>] [-remote_port <port>] [-verbose] "
                           + "[-file <mapping file>] [-debug]");
    }

    static void log(String method_name, String msg) {
        System.out.println('[' + method_name + "]: " + msg);
    }

    static void log(String msg) {
        System.out.println(msg);
    }

    static void close(Socket in, Socket out) {
        if (in !=null) {
            try {
                in.close();
            }
            catch (Exception ex) {
            }
        }
        if (out !=null) {
            try {
                out.close();
            }
            catch (Exception ex) {
            }
        }
    }

    static void close(Socket sock) {
        if (sock !=null) {
            try {
                sock.close();
            }
            catch (Exception ex) {
            }
        }
    }

   

    static class MyInetSocketAddress extends InetSocketAddress {
        boolean is_ssl=false;

        public MyInetSocketAddress(InetAddress addr, int port) {
            super(addr, port);
        }

        public MyInetSocketAddress(InetAddress addr, int port, boolean is_ssl) {
            super(addr, port);
            this.is_ssl=is_ssl;
        }
  
        public MyInetSocketAddress(int port) {
            super(port);
        }

        public MyInetSocketAddress(int port, boolean is_ssl) {
            super(port);
            this.is_ssl=is_ssl;
        }

        public MyInetSocketAddress(String hostname, int port) {
            super(hostname, port);
        }

        public MyInetSocketAddress(String hostname, int port, boolean is_ssl) {
            super(hostname, port);
            this.is_ssl=is_ssl;
        }

        public boolean ssl() {
            return is_ssl;
        }

        public String toString() {
            return super.toString() + " [ssl: " + ssl() + ']';
        }
    }

   
}