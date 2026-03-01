//Import Statements
import java.util.*;
import java.io.*;
import java.net.*;

public final class BoardServer {
  //flag for checking whether server is running
  private static volatile boolean running = true; 
  
  public static void main(String[] args) throws Exception {
    //Check for number of arguments
    if (args.length <5) {
      System.err.println("Usage: java BoardServer <port> <boardWidth> <boardHeight> <noteWidth> <noteHeight> <colors...>");
      System.exit(1);
    };

    int port=0; //Port initialized
    Board board=null; //Server board initialized

    //Exception handling for parsing Port number
    try{
      port=Integer.parseInt(args[0]);
      if (port<1024||port>65535){
        System.err.println("Port must be between 1024 and 65535");
        System.exit(1);
      }
    }
    catch(NumberFormatException e){
      System.err.println("Invalid numeric board or note dimensions");
      System.exit(1);
    }

    //Exception handling for parsing Board & Note dimensions
    try {
      int boardWidth=Integer.parseInt(args[1]);
      int boardHeight=Integer.parseInt(args[2]);
      int noteWidth=Integer.parseInt(args[3]);
      int noteHeight=Integer.parseInt(args[4]);

      //List of colors allowed
      ArrayList<String> colors = new ArrayList<>();
      for (int i = 5; i < args.length; i++) {
        colors.add(args[i].toLowerCase());
      }
      board=new Board(boardWidth,boardHeight,noteWidth,noteHeight,colors); //Board object

    } catch (NumberFormatException e) {
      System.err.println("Invalid port number: " + args[0]);
      System.exit(1);
    }

    //Server socket object initialized
    ServerSocket serverSocket=null;

    //Exception handling for server socket connection
    try {
      serverSocket = new ServerSocket(port);
      System.out.println("BoardServer started on port " + port);
      System.out.println("Press Ctrl+C to stop the server");
      System.out.println("---------------------------------------------------");

      while(running){
        //Waiting for client..
        try{
        Socket clientConnection=serverSocket.accept();
        String clientIP=clientConnection.getInetAddress().getHostAddress();

        ClientHandler clientHandler=new ClientHandler(clientConnection,clientIP,board); //ClientHandler object 

        Thread thread=new Thread(clientHandler);
        thread.start();
        }

        catch(SocketException e){
          if (running){
            System.err.println("Error while connecting Socket: "+e.getMessage());
          }
        }
      }
    }
    //Catch if port already in use
    catch(BindException e){
      System.err.println("Error: Port: "+port+" is already in use");
      System.exit(1);
    }
    catch (IOException e){
      System.err.println("Server Error: "+ e.getMessage());
      System.exit(1);
    }
    finally{
      if (serverSocket!=null && !(serverSocket.isClosed())){
        //Exception handling for closing server socket
        try{
          serverSocket.close();
          System.out.println("Board Server closed");
          shutdown();
        }
        catch (IOException e){
          System.out.println("Server not closing because: "+e.getMessage());
        }
        
      }
    }
  }

  //To gracefully shutdown the server
  public static void shutdown(){
    running=false;
  }
}
