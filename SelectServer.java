
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Java NIO uses multiplexing to server multiple clients from the same thread.
 * Before NIO, a server had to open a thread for each client.
 * 
 * @author john @2018-01-31
 * 
 * @@todo: 1) hashmap could be bottleneck, is it thread-safe? 2) JVM tunning 3)
 *         ip stack tuning for linux kernel
 * 
 *
 *Howto:
 *1) Epoll 
 *java -Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.EPollSelectorProvider 
 *2) Common selector 
java -Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.PollSelectorProvider SelectServer

 */
public class SelectServer implements Runnable {

	public final static String ADDRESS = "127.0.0.1";
	public final static int PORT = 8511;
	public final static long TIMEOUT = 10000;

//	private final static int bufferLen = 64 * 2;
	final static int BUFFER_SIZE=1024*51;

static int counter;

	private ServerSocketChannel serverChannel;
	private Selector selector;
	/**
	 * This hashmap is important. It keeps track of the data that will be written to
	 * the clients. This is needed because we read/write asynchronously and we might
	 * be reading while the server wants to write. In other words, we tell the
	 * Selector we are ready to write (SelectionKey.OP_WRITE) and when we get a key
	 * for writting, we then write from the Hashmap. The write() method explains
	 * this further.
	 */
private ConcurrentHashMap <SocketChannel, byte[]> dataTracking = new ConcurrentHashMap <SocketChannel, byte[]>();
	public SelectServer() {
		init();
	}

	public static void main(String[] args) {
    	
    	SelectServer ss = new SelectServer();
    	Util.loger("The select server is starting on port:"+PORT);
    	Util.loger("The provier is \t"+getProvider());
    
    	Thread sst = new Thread (ss,"SelectServerThread-0");
    	sst.start();
    	
   // for multi-thread 
    	int threadNo =1;
    	
    	if (args.length>0) {    		
    	
    		String key="-threadNo=";
    		
    		for (String s : args) {
    			
    			// s = "-thread=30";
    			if (s.startsWith(key))  
    			{    				
//    				Util.loger(s.substring(s.indexOf(key)+key.length(), s.length()));
    				threadNo=new Integer(s.substring(s.indexOf(key)+key.length(), s.length()));
    			}
    			else
    				Util.loger("Illegal arguments, need "+key+"=<int> ");    			
    			
    		}
    			
    		Util.loger("threadNo is "+threadNo);
    		
    		for ( int i=1; i<=threadNo; i++) {
    			Thread sstt = new Thread (ss,"SelectServerThread-"+i);
    	    	sstt.start();
    		} 
    	
    	}    	
	}

	private void init() {
		Util.loger("initializing server");

		if (selector != null)
			return;
		if (serverChannel != null)
			return;

		try {

			selector = Selector.open();

			serverChannel = ServerSocketChannel.open();

			serverChannel.configureBlocking(false);

			serverChannel.register(selector, SelectionKey.OP_ACCEPT);
			// bind to the address
			serverChannel.socket().bind(new InetSocketAddress(ADDRESS, PORT));

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		Util.loger("Now accepting connections by..."+ Thread.currentThread().getName() );

counter++;
Util.loger("Connection Counter is::"+ counter);
		try {

			while (!Thread.currentThread().isInterrupted()) {
				long startPoint = System.currentTimeMillis();
				selector.select(TIMEOUT); // block
				Util.loger(startPoint, "seletor.select");

				Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

				while (keys.hasNext()) {
					SelectionKey key = keys.next();
					// remove the key so that we don't process this OPERATION again.
					keys.remove();

					// key could be invalid if for example, the client closed the connection.
					if (!key.isValid()) {
						continue;
					}

					if (key.isAcceptable()) {
						Util.loger("Accepting connection");
						accept(key);

					}

					if (key.isWritable()) {
						Util.loger("Writing...");
						write(key);
					}

					if (key.isReadable()) {
						Util.loger("Reading connection");
						read(key);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			closeConnection();
		}

	}

	/**
	 * We registered this channel in the Selector. This means that the SocketChannel
	 * we are receiving back from the key.channel() is the same channel that was
	 * used to register the selector in the accept() method. Again, I am just
	 * explaning as if things are synchronous to make things easy to understand.
	 * This means that later, we might register to write from the read() method (for
	 * example).
	 */
	private void write(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();
		/**
		 * The hashmap contains the object SockenChannel along with the information in
		 * it to be written. In this example, we send the "Hello from server" String and
		 * also an echo back to the client. This is what the hashmap is for, to keep
		 * track of the messages to be written and their socketChannels.
		 */
		byte[] data = dataTracking.get(channel);
		dataTracking.remove(channel);

		// Something to notice here is that reads and writes in NIO go directly to the
		// channel and in form of
		// a buffer.
		channel.write(ByteBuffer.wrap(data));
 //Util.loger("Data write out is:"+new String(data));
		// Since we wrote, then we should register to read next, since that is the most
		// logical thing
		// to happen next. YOU DO NOT HAVE TO DO THIS. But I am doing it for the purpose
		// of the example
		// Usually if you register once for a read/write/connect/accept, you never have
		// to register again for that unless you
		// register for none (0). Like it said, I am doing it here for the purpose of
		// the example. The same goes for all others.
		key.interestOps(SelectionKey.OP_READ);

	}

	private void closeConnection() {
		Util.loger("Closing server down");
		if (selector != null) {
			try {
				selector.close();
				serverChannel.socket().close();
				serverChannel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Since we are accepting, we must instantiate a serverSocketChannel by calling
	 * key.channel(). We use this in order to get a socketChannel (which is like a
	 * socket in I/O) by calling serverSocketChannel.accept() and we register that
	 * channel to the selector to listen to a WRITE OPERATION. I do this because my
	 * server sends a hello message to each client that connects to it. This doesn't
	 * mean that I will write right NOW. It means that I told the selector that I am
	 * ready to write and that next time Selector.select() gets called it should
	 * give me a key with isWritable(). More on this in the write() method.
	 */
	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
		SocketChannel socketChannel = serverSocketChannel.accept();
		socketChannel.configureBlocking(false);

		socketChannel.register(selector, SelectionKey.OP_WRITE);
		byte[] hello = new String("Hello from server").getBytes();
		dataTracking.put(socketChannel, hello);
	}

	/**
	 * We read data from the channel. In this case, my server works as an echo, so
	 * it calls the echo() method. The echo() method, sets the server in the WRITE
	 * OPERATION. When the while loop in run() happens again, one of those keys from
	 * Selector.select() will be key.isWritable() and this is where the actual write
	 * will happen by calling the write() method.
	 */
	private void read(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();
		ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
		readBuffer.clear();
		int read;
		try {
			read = channel.read(readBuffer);
		} catch (IOException e) {
			Util.loger("Reading problem, closing connection");
			key.cancel();
			channel.close();
			return;
		}
		if (read == -1) {
			Util.loger("Nothing was there to be read, closing connection");
			channel.close();
			key.cancel();
			return;
		}
		// IMPORTANT - don't forget the flip() the buffer. It is like a reset without
		// clearing it.
		readBuffer.flip();

		byte[] data = new byte[BUFFER_SIZE];
		// read only length can accept, otherwise, out-of-bound exception for data..
		read = read < BUFFER_SIZE ? read : (BUFFER_SIZE - 1);

		readBuffer.get(data, 0, read);

//		Util.loger("Received: " + new String(data));

		echo(key, data);
	}

	/**
	 * Channel is a two way communication linked with Buffer .
	 * 
	 * @param key
	 * @param data
	 */

	private void echo(SelectionKey key, byte[] data) {
		SocketChannel socketChannel = (SocketChannel) key.channel();
		dataTracking.put(socketChannel, data);
		key.interestOps(SelectionKey.OP_WRITE);
	}

	static String getProvider() {

//		Util.loger(System.getProperties());
		String rst;

//		rst = System.getProperties().getProperty("java.nio.channels.spi.SelectorProvider");
		// java.nio.channels.spi.SelectorProvider().provider().getclass();
		rst = java.nio.channels.spi.SelectorProvider.provider().getClass().getName();
		return rst;

		// java.nio.channels.spi.SelectorProvider.provider()getProvider();

	}

}
