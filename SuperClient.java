
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * 1) Test scalability by mutil-connections 2) Test Performance
 * 
 * @author john
 *
 */
public class SuperClient implements Runnable {

	private static String IP = "localhost";
	private static int PORT = 8511;
	
	static int BUFFER_SIZE=1024*51;
	static int INTERVAL=100; // in  the span of the connection creation, control the speed (Milli-Sec)
	static int TTL=1000*30;  // The duration of one connection (Milli-Sec) 

	SocketChannel socketChannel;

	private static int tid = 0; // thread_id

	private static final String IPADDRESS_PATTERN = "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
			+ "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
			+ "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";

	SuperClient(int threadId) {

		tid = threadId;

	}

	public static void main(String[] args) throws UnknownHostException, IOException {

		if (args.length == 4) {
			IP = args[0];
			
			// on purpose to demo different ways to transfer Str to Int
			PORT = new Integer(args[1]); 
			INTERVAL = Integer.parseInt(args[2]);
			TTL=Integer.parseInt(args[3]);
			
		} else {
			Util.loger(" Usage: {IP} {PORT} {INTERVAL} {TTL}");
			Util.loger("Current ip and port :" + IP + " " + PORT);
			
			return;
		}

		Pattern pattern = Pattern.compile(IPADDRESS_PATTERN); // seems complex pattern should compile first;
		boolean validatedIP = pattern.matcher(IP).matches();
		// or another way
		validatedIP = Pattern.matches(IPADDRESS_PATTERN, IP);

		if (!validatedIP && !IP.equals("localhost")) {
			Util.loger("Invalidate IP format...");
			System.exit(-1);
		}

		for (int i = 0;; i++) {
			SuperClient tc = new SuperClient(i);
			Thread t = new Thread(tc);
			t.setName("SuperClient-" + i);
			t.start();
			
			// Give interval for next threads in milli-seconds
			Util.setTimer(INTERVAL);
		}

	}

	void connect() throws UnknownHostException, IOException {

		if (null == IP || 0 == PORT) {
			Util.loger(" Need ip and/or port to connect");
			System.exit(-1);
			;
		}

		socketChannel = SocketChannel.open();
		socketChannel.connect(new InetSocketAddress(IP, PORT));

		Util.loger("Connected to: \t" + IP);

	}

	@Override
	public void run() {

		String tName = Thread.currentThread().getName();
		Util.loger(tName + " started...");
		try {
			this.connect();

			String mesgToSend = " Send from " + tName + Util.bigMsg(BUFFER_SIZE);

			this.send("Length:" + mesgToSend.length() + mesgToSend);
			
			Util.setTimer(10);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	void send(String msg) {

		try {
			BUFFER_SIZE = msg.length();
			ByteBuffer writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);
			ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);  
    
			writeBuffer.put(msg.getBytes());
			writeBuffer.flip();

			// while (true)
			{
				long startPoint = System.currentTimeMillis();
				writeBuffer.rewind();
				socketChannel.write(writeBuffer);
				
				//check return 
				readBuffer.clear();
				socketChannel.read(readBuffer);
				Util.loger(startPoint, "Intervial_of_echo time");
				Util.logd(Util.byteBuffer2String(readBuffer));

		
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
