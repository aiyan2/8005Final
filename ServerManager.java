import java.net.InetAddress;
import java.nio.channels.spi.SelectorProvider;

public class ServerManager {

	final static String POLL = "sun.nio.ch.PollSelectorProvider";
	final static String EPOLL = "sun.nio.ch.EPollSelectorProvider";

	// Configurable Arguments
	public static int ULIMIT_SIZE = 100 * 1000;

	static String ServerIP = "localhost";
	static int ServerPort = 8088;
	static String Mode = "EPOLL";
	static int BUFFER_SIZE = 1024 * 50;
	static int ThreadNum = 1;

	public static void main(String[] args) {

		prepArgs(args);
		// setupEnv();

		initServer();

	}

	static void prepArgs(String[] args) {

		Util.loger("<ServerIP> <ServerPort> <EPOLL|POLL|MT> <BUFFER_SIZE> <ThreadNumber> ");

		if (args.length >= 4) {

			ServerIP = args[0];
			ServerPort = Integer.parseInt(args[1]);
			Mode = args[2];
			BUFFER_SIZE = Integer.parseInt(args[3]);
			ThreadNum = Integer.parseInt(args[4]);

			if (Mode.equalsIgnoreCase("POLL")) {
				Util.loger(
						" MUST-DO: -f POLL, MUST HAVE!! -Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.PollSelectorProvider");
				// @todo further check...
			}
		}

	}

	static void setupEnv() {

		// put it in a shell
		Util.runCmd("ulimit -n " + ULIMIT_SIZE);

	}

	static void initServer() {

		Util.loger("The High Perform Server is starting on port:" + ServerPort);

		if (Mode.equalsIgnoreCase("EPOLL")) {
			EPollServer.execute(ServerIP, ServerPort, BUFFER_SIZE, ThreadNum);
			Util.loger("The provider is \t" + getProvider());
			return;
		} else if (Mode.equalsIgnoreCase("POLL")) {
			PollServer.execute(ServerIP, ServerPort, BUFFER_SIZE, ThreadNum);
			Util.loger("The provider is \t" + getProvider());
			return;
		} else if (Mode.equalsIgnoreCase("MT")){
			MTServer.execute(ServerPort);
			return;
		}

	}
	
	
	 public static void mainParse(String[] args) {
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

	            p=new Proxy(local, local_port, remote, remote_port, verbose, debug, mapping_file);
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


	static String getProvider() {

		// Util.loger(System.getProperties());
		String rst;
		rst = SelectorProvider.provider().getClass().getName();
		return rst;

	}

	static void setProvider(Boolean isPoll) {

		String providerName = isPoll ? POLL : EPOLL;

		try {
			// hack to work around compiler complaints about sun.nio.ch.PollSelectorProvider
			// being proprietary
			Class.forName(providerName).newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		System.setProperty("java.nio.channels.spi.SelectorProvider", SelectorProvider.class.getName());
	}

}
