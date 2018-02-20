
import java.net.*;
import java.io.*;

public class STServer {
    public static void main(String[] args) throws IOException {
        
      
        int portNumber = 8899;
        
     try {
            ServerSocket serverSocket =
                new ServerSocket(portNumber);
         Util.logd("Server starting on port:"+portNumber);	
            Socket clientSocket = serverSocket.accept();   
        		
        	Util.logd("accept");
        		
            PrintWriter out =
                new PrintWriter(clientSocket.getOutputStream(), true);                   
            BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
    
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                out.println(inputLine);
                Util.logd(inputLine);
            }
        } catch (IOException e) {
            System.out.println("Exception caught when trying to listen on port "
                + portNumber + " or listening for a connection");
            System.out.println(e.getMessage());
        }
    }
}