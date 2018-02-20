import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provide the PollServer, which is pretty much same as EPollServer. But due to
 * unfortunately the lower layer issue at PollSelectProvider, which caused for
 * java.util.ConcurrentModificationException by Poll SelectProvider
 * 
 * @author john @2018-02-11
 * 
 * 
 *         How-to: 1) Epoll java
 *         -Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.EPollSelectorProvider
 *         2) Common selector java
 *         -Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.PollSelectorProvider
 *         SelectServer
 * 
 */
public class PollServer implements Runnable {

	static String ADDRESS = "localhost";
	static int PORT = 8511;

	static int BUFFER_SIZE = 1024 * 51;
	static final int ULIMIT_SIZE = 80000; // Default
	private static final long SELECT_TIMEOUT = 10000; // second of the select timeout just in case;

	static int CLIENT_COUNTER;

	private ServerSocketChannel serverChannel;
	private Selector selector;

	private ConcurrentHashMap<SocketChannel, byte[]> mesgCache = new ConcurrentHashMap<SocketChannel, byte[]>();

	public PollServer() {

	}

	public static void main(String[] args) {
		
		execute(ADDRESS, PORT,BUFFER_SIZE,1);  // only single thread

	}

	public static void execute(String ip, int port, int buffer, int threadNum) {
		PollServer ps = new PollServer();
		BUFFER_SIZE = buffer;
		ps.init(ip, port);
		ps.run();

	}

	private void init(String ip, int port) {
		Util.loger("initializing server");

		if (selector != null || serverChannel != null)
			return;

		try {

			selector = Selector.open();
			serverChannel = ServerSocketChannel.open();

			serverChannel.configureBlocking(false);

			serverChannel.register(selector, SelectionKey.OP_ACCEPT);
			// bind to the address
			serverChannel.socket().bind(new InetSocketAddress(ip, port));

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {

		// Scale for multi-thread Servers:
		Util.loger("Now accepting connections by..." + Thread.currentThread().getName());

		try {

			while (!Thread.currentThread().isInterrupted()) {
				long startPoint = System.currentTimeMillis();
				selector.select(SELECT_TIMEOUT); // block
				Util.loger(startPoint, "seletor.select");

				Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

				while (keys.hasNext()) {
					SelectionKey key = keys.next();
					// remove the key so as not to process this OPERATION again.
					keys.remove();

					// for instance the client closed the connection.
					if (!key.isValid()) {
						continue;
					}

					if (key.isAcceptable()) {
						Util.loger("Accepting connection");
						accept(key);
						CLIENT_COUNTER++;
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

	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
		SocketChannel socketChannel = serverSocketChannel.accept();
		socketChannel.configureBlocking(false);

		socketChannel.register(selector, SelectionKey.OP_WRITE);
		byte[] hello = new String("Hello from server").getBytes();
		mesgCache.put(socketChannel, hello);
	}

	private void write(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();

		byte[] data = mesgCache.get(channel);
		mesgCache.remove(channel);

		channel.write(ByteBuffer.wrap(data));
		Util.loger("Data write out is:" + new String(data));

		key.interestOps(SelectionKey.OP_READ); // IMPORTANT, alike state-machine trigger.

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
		// IMPORTANT - don't forget the flip() the buffer. It is like a reset without
		// clearing it.
		readBuffer.flip();

		byte[] data = new byte[BUFFER_SIZE];
		// read only buffer[length] can be accepted, otherwise, out-of-bound exception
		// for data..
		read = read < BUFFER_SIZE ? read : (BUFFER_SIZE - 1);

		readBuffer.get(data, 0, read);

		// echo back..
		echo(key, data);
	}

	/**
	 * Channel is a two way communication linked with Buffer .
	 */

	private void echo(SelectionKey key, byte[] data) {

		if (null == data)
			return; // gor the null data , ignore ...
		SocketChannel socketChannel = (SocketChannel) key.channel();

		mesgCache.put(socketChannel, data);
		key.interestOps(SelectionKey.OP_WRITE); // really need for demo purpose
	}

	private void closeConnection() {
		Util.loger("Closing Connection, server is down");
		showstats();
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

	private void showstats() {

		Util.loger("The Client Number is\t" + CLIENT_COUNTER);
	}

}