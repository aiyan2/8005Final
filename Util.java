import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class Util {

	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		
		String str=bigMsg(1024);
		
		System.out.println(str);
		
		if (isLinux())
			
			runCmd(" uname -a");
		else
		    runCmd (" cmd");
					

	}
	
	
	public static void loger(Object obj) {

		System.out.println(obj);

	}

	public static void loger(long startpoint, String name) {

		long i = System.currentTimeMillis();
		loger(name+" time used:" + (i - startpoint));

	}
	
	 public static void logd(Object o) {
		   loger(o);
	   }

	public static void setTimer(int milsec) {

		loger("Timer set for " + milsec + " milsecs, starting ....");

		try {
			Thread.sleep(milsec);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	public static String bigMsg(int nlimit) {

		String core = "HELLO FROM BCIT:-)";
		StringBuffer rst = new StringBuffer(core);

		while (true) {

			rst = rst.append(core);

			if (rst.length() >= nlimit)
				break;

		}

		return rst.toString();

	}

	public static void runCmd(String cmd) {

		try {
			loger("cmd is: " + cmd);

			Process proc = Runtime.getRuntime().exec(cmd);

			// retrieve and process the output
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

			// read the output from the command
			System.out.println("Here is the standard output of the command:\n");
			String s = null;
			while ((s = stdInput.readLine()) != null) {
				System.out.println(s);
			}

			// read any errors from the attempted command
			System.out.println("Here is the standard error of the command (if any):\n");
			while ((s = stdError.readLine()) != null) {
				System.out.println(s);
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	   
	   static boolean isLinux() {

			// Ignore if not Linux
			if (-1 != (System.getProperty("os.name").toLowerCase().indexOf("nix"))) 
				return true;
			else
				return false;
			
	   }
	   
	/*
	 * Same ByteBuffer can be used to read and write data. If you want to read from
	 * ByteBuffer just call the flip() method and it will convert ByteBuffer into
	 * reading mode
	 * 
	 * Read more:
	 * http://www.java67.com/2015/06/how-to-convert-bytebuffer-to-string-in-java-
	 * example.html#ixzz57XQvUkRj
	 */
	   static String byteBuffer2String(ByteBuffer bf) {
		   
		   try {
			return (new String(bf.array(), "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	   }
	   
	   //  byte[] to String here and then String2 byte[] when doing ..., add load for Tasks..
	   static void commonTask(String raw, String key) {
		   
		   try {
			Util_Task.encrypt(raw, key);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	   }
	
}

	
