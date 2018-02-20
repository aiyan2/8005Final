
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * PortForward Server based on the EPoll Server: update for the mesgCache only
 * store client mesgs, not the content with right servers.
 * 
 * @author Aiyan @2018-02-13
 * 
 * @@todo -Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.PollSelectorProvider
 * 
 */
public class PortfwdB implements Runnable {

	// Local machine information
	static String ADDRESS = "localhost";
	static int PORT = 8090; // proxy_server port
	public final static long SELECT_TIMEOUT = 10000;

	// forwarding machine information
	static String FWD_IP = "localhost"; // "www.baidu.com";

	static int FWD_PORT = 8511;

	static int BUFFER_SIZE = 1024 * 51;
	static int THREADNUM = 1;

	static int counter;

	private ServerSocketChannel serverChannel;
	private Selector selector;

	private Map<SocketChannel, byte[]> mesgCache = new HashMap<SocketChannel, byte[]>();
	// private Map<SocketChannel, byte[]> mesgFwdCache = new HashMap<SocketChannel,
	// byte[]>();

	public PortfwdB() {

	}

	public static void main(String[] args) {

		execute(ADDRESS, PORT, BUFFER_SIZE, THREADNUM);
	}

	public static void execute(String ip, int port, int buffer, int threadNum) {

		PortfwdB ss = new PortfwdB();
		ss.init(ip, port);
		BUFFER_SIZE = buffer;

		Util.loger("The EPoll server is starting on port:" + port);
		Util.loger("The provier is \t" + getProvider());

		for (int i = 1; i <= threadNum; i++) {
			Thread sstt = new Thread(ss, "Forward-EPoll-ServerThread-" + i);
			sstt.start();
		}

		Util.loger("threadNum is " + threadNum);

	}

	private void init(String ip, int port) {
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
		Util.loger("Now accepting connections by..." + Thread.currentThread().getName());

		counter++;
		Util.loger("Connection Counter is::" + counter);
		try {

			while (!Thread.currentThread().isInterrupted()) {
				long startPoint = System.currentTimeMillis();
				selector.select(SELECT_TIMEOUT); // block
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

					if (key.isWritable()) { // should be NO >>>> handle only in READ
						Util.loger("Forwarding...");
						// write(key);
						// forward(key);
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
			// closeConnection();
			Util.loger("into finally, HSiT");
		}

	}

	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
		SocketChannel socketChannel = serverSocketChannel.accept();
		socketChannel.configureBlocking(false);

		socketChannel.register(selector, SelectionKey.OP_READ);
		// socketChannel.register(selector, SelectionKey.OP_WRITE); //mmmttt added
		// byte[] hello = new String("Hello from server").getBytes();
		// mesgCache.put(socketChannel, hello);
	}


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

		readBuffer.flip();

		byte[] data = new byte[BUFFER_SIZE];
		// read only length can accept, otherwise, out-of-bound exception for data..
		read = read < BUFFER_SIZE ? read : (BUFFER_SIZE - 1);

		readBuffer.get(data, 0, read);

		Util.loger("Received: " + new String(data));

		// cache(key, data); // null..

		forward(channel, data);

	}

	private static void forward(SocketChannel channel, byte[] data) {
		if (null == data) {
			Util.loger("Null from the mesgCache, method: forward()");
			return;
		}

		try {
			SocketChannel socketChannel_fwd = SocketChannel.open();
			socketChannel_fwd.connect(new InetSocketAddress(FWD_IP, FWD_PORT));

			ByteBuffer writeBuffer = ByteBuffer.allocate(data.length);
			ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);

			writeBuffer.put(data); // mmm here is NULL
			writeBuffer.flip();
			writeBuffer.rewind();
			socketChannel_fwd.write(writeBuffer); /// block here also ..
			// writeBuffer.flip(); // here we get IOException

			// get the response from right Server
			readBuffer.clear();
			socketChannel_fwd.read(readBuffer);
			Util.logd(Util.byteBuffer2String(readBuffer));

			// Further write to client:
			readBuffer.rewind();
			channel.write(readBuffer);

		} catch (IOException ie) {
			ie.printStackTrace();
		}

	}

	static String getProvider() {

		String rst;
		rst = java.nio.channels.spi.SelectorProvider.provider().getClass().getName();
		return rst;

	}
	

	/**
	 * SocketChannel receiving back from the key.channel() is the same channel that
	 * was used to register the selector in the accept() method. later, we might
	 * register to write from the read() method (for example).
	 */
	private void write2Client(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();

		byte[] data = mesgCache.get(channel);
		mesgCache.remove(channel);

		channel.write(ByteBuffer.wrap(data));
		Util.loger("Data write out is:" + new String(data));

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

}
