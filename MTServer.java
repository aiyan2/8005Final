
import java.io.BufferedReader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;

public class MTServer implements Runnable {

	private static int SERVER_PORT = 8899;

	Socket csocket;
	int tid;

	static boolean poolMode = false;
	 static final int      MIN_THREAD_POOL_SIZE=2;
	 static final int      MAX_THREAD_POOL_SIZE=1024; // for processing requests
	
	MTServer(Socket csocket, int threadId) {
		this.csocket = csocket;
		tid = threadId;
	}

	public static void main(String args[]) throws Exception {
		
		if(args.length ==2 && (args[1].indexOf("pool")!=-1))
			poolMode = true;
		
		execute(SERVER_PORT);
	}

	


	
	 static void execute (int port) {
		 
		ServerSocket ssock;
		
	    // Create a thread pool (Executor)
	  Executor  executor=new ThreadPoolExecutor(MIN_THREAD_POOL_SIZE, MAX_THREAD_POOL_SIZE, 30000, TimeUnit.MILLISECONDS,
	                                    new LinkedBlockingQueue(1000));
		
	  int counter =0;
	  long startPoint =0;
		try {
			ssock = new ServerSocket(port);
		
			Util.loger("Listening on port:" + port);

			for (int i = 1;; i++) {
				Socket sock = ssock.accept();

				startPoint = System.currentTimeMillis();
				Util.loger("Connected");

				Thread mts = new Thread(new MTServer(sock, i));
				mts.setName("MTServer-" + i);
				
				
				Runtime.getRuntime().addShutdownHook(new Thread() {
			         @Override
			         public void run() {
			           Util.loger("W: interrupt received, killing server…");
			       //     this.showStats(startPoint,counter);
			            try {
			               mts.interrupt();
			               mts.join();
			            } catch (InterruptedException e) {
			            	// e.printStackTrace();
			            }
			         }
			      });
				
				
				if (!poolMode)
					mts.start();
				else
					executor.execute(mts);

				counter++;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			
		} finally	{
			
//			showStats(startPoint,counter);
		
		}
		
	 }
	private  void showStats(long startPoint,int counter) {
		// TODO Auto-generated method stub
		Util.loger(startPoint, "Here is the connections handled:\t"+ counter);
		
	}

	public void run() {
		Util.loger(Thread.currentThread().getName()+" running...");
		try {
			PrintStream pstream = new PrintStream(csocket.getOutputStream());

			Util.logd("I got the data:" + pstream);
			for (int i = 10; i >= 0; i--) {
				pstream.println(i + " piles of GOLD  in the CAVE");				
			}
			Util.logd(" I've sent back GOLD to you  PLUS those you sent me");
			
			BufferedReader in = new BufferedReader(new InputStreamReader(csocket.getInputStream()));
			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				pstream.println(inputLine);				
				Util.logd(inputLine);
			}
			pstream.close();
			csocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}