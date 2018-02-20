
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 *  1) Test scalability by mutil-connections 
 *  2) Test Performance 
 * @author john
 *
 */
public class MTClient implements Runnable {
	
	
	private static String ip="localhost";
	private static int port = 8899;
	
	private Socket socket;
	private Scanner scanner;
	
	private int tid =0; // thread_id
	
		

	MTClient(int threadId){
		
		this.tid=threadId;
		
	}
	public static void main(String[] args) throws UnknownHostException, IOException {
		// TODO Auto-generated method stub
				
	if (args.length==2) {
		ip = args[0];		
		port = new Integer( args[1]);
	}
	
	
	
	for (int i=0;i<5; i++) {
		 MTClient tc = new MTClient(i);
		 Thread t = new Thread(tc);
		 t.setName("testclient-"+i);
		 t.start();

	}
	
	}
	
	

	void connect() throws UnknownHostException,IOException {
		
		if ( null == ip || 0 == port ) {
			
		 log(" Need ip and/or port to connect");
		 System.exit(-1);;
		}
		
		InetAddress serverAddress = InetAddress.getByName(ip);
				
		this.socket = new Socket(serverAddress, port);
        this.scanner = new Scanner(System.in);
        
        log("Connected to: \t" + this.socket.getInetAddress());
		
	}

  void send(String mesg) throws IOException {
  // String mesg="OK, HELLO from BCIT at send of MTClient";
   PrintWriter out = new PrintWriter(this.socket.getOutputStream(), true);
                    out.println(mesg);
 log(" ehco: what I sent is"+mesg);
 out.flush();

}	
		 
		
	
	static void log(Object o) {
		
		System.out.println(o);
	}
	@Override
	public void run() {
		// TODO Auto-generated method stub
		
		try {
			this.connect();
			SuperClient sc = new SuperClient(-1);
                        String msg = Util.bigMsg(1024);
			this.send(msg);
		//	this.getInputThenSend();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		
	}
	
}
