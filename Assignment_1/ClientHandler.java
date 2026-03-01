//Import Statements
import java.util.*;
import java.io.*;
import java.net.*;

//Class for handling client requests
public class ClientHandler implements Runnable {
  
  private volatile boolean runningClient = true;
  private Socket socket;
  private String clientIP;
  private Board board;
  private PrintWriter out; //To send output to client
  BufferedReader userInput; //To read input from client

  public ClientHandler(Socket socket, String clientIP, Board board){
    this.socket=socket;
    this.clientIP=clientIP;
    this.board=board;
  }

  /**
   * Implement the run() method of the Runnable interface.
   * This method is executed when the thread is started.
   * Main thread -> accepts new connections
   * Worker threads -> process requests independently
   */
  public void run() {
    try {
      processRequest();
    } catch (Exception e) {
      System.err.println("[" + clientIP + "] Error processing request: " + e.getMessage());
    }
  }

  //Central method to process various client requests
  private void processRequest(){
  //Exception handling for reading user input
  try{
    userInput=new BufferedReader(new InputStreamReader(socket.getInputStream()));
    out = new PrintWriter(socket.getOutputStream(), true);
    String command;

    //Read user input and trim command receeived
    while ((command= userInput.readLine()) != null) {
      command = command.trim();   // remove extra spaces

      if (command.isEmpty()) {
          out.println("ERROR EMPTY_COMMAND");
          continue;
      }
      //Method for switching requests according to the command
      handleRequests(command);
    }
  }
  catch(IOException e){
    System.err.println("IO error"+e.getMessage());
  }
}


  public void handleRequests(String command){
    String[] tokens=command.split(" "); //Split arguments
    String methodReq=tokens[0].toUpperCase();

    //Switch method for various requests
    switch(methodReq){
      case "POST":
        postHandle(tokens);
        break;

      case "GET":
        getHandle(tokens);
        break;
      
      case "UNPIN":
        unpinHandle(tokens);
        break;

      case "PIN":
        pinHandle(tokens);
        break;
      
      case "SHAKE":
        shakeHandle();
        break;

      case "CLEAR":
        clearHandle();
        break;

      case "DISCONNECT":
        disconnectHandle();
        break;
      
    }
  }

  /*
    POST <note>
  • Posts a new note to the board.

  • Server rejects the request if:
    o The note is out of bounds
    o The color is invalid
    o The note completely overlaps an existing note

  • Server responds with either:
    o Success confirmation
    o An explicit ERROR message
  */
  private void postHandle(String[] tokens){
    if (tokens.length < 5) {
      out.println("ERROR INVALID FORMAT_POST requires coordinates, color, and message");
      return;
    }
    //Exception handling for parsing Post request
    try {
      int x = Integer.parseInt(tokens[1]);
      int y = Integer.parseInt(tokens[2]);
      String color = tokens[3];
      String message = String.join(" ", Arrays.copyOfRange(tokens, 4, tokens.length));

      //Board method for posting note
      int res = board.addNote(x, y, color, message);

      if (res==0) {
        out.println("OK NOTE_POSTED");
      }
      else if (res==1){
        out.println("ERROR OUT_OF_BOUNDS Note exceeds board boundaries");
      }
      else if (res==2){
        out.println("ERROR COLOR_NOT_SUPPORTED not a valid color");
      }
      else {
        out.println("ERROR COMPLETE_OVERLAP Note overlaps an existing note entirely");
      }

    } catch (NumberFormatException e) {
        out.println("ERROR INVALID_COORDINATES");
    }
  }

  /*
  GET <request>
  GET PINS
  Returns coordinates of all pins.

  General GET form:
  GET color=<color> contains=<x> <y> refersTo=<substring>
  • Missing criteria imply ALL
  • Server returns all notes satisfying all provided criteria
  */
  private void getHandle(String[] tokens){
  try{
        String color = null;
        int x = -1, y = -1;
        String referWord = null;

        for(int i = 1; i < tokens.length; i++){
            String[] pair = tokens[i].split("=",2); // split into 2 parts only
            if(pair.length < 2) continue;

            switch(pair[0].toLowerCase()){
                case "color": color = pair[1]; break;
                case "contains":
                    if(pair[1].contains(" ")) {
                        String[] coords = pair[1].split(" ");
                        x = Integer.parseInt(coords[0]);
                        y = Integer.parseInt(coords[1]);
                    }
                    break;
                case "refersto": referWord = pair[1]; break;
            }
        }

        Note n = board.getNote(color, x, y, referWord);

        if(n == null) out.println("ERROR NOTE_NOT_FOUND");
        else out.println(
            "NOTE " + n.getX() + " " + n.getY() + " " + n.getColor() + " " +
            n.getMessage() + " PINNED=" + n.isPinned()
        );

    } catch(Exception e){
        out.println("ERROR INVALID_REQUEST");
        System.out.println("DEBUG: getHandle error: " + e.getMessage());
    }
}


  /*
  UNPIN removes one pin at the coordinate.
  If no pin exists at that coordinate → ERROR
  */
  private void unpinHandle(String[] tokens){
    //Exception handling for parsing coordinates
    try{
      int x=Integer.parseInt(tokens[1]);
      int y=Integer.parseInt(tokens[2]);

      boolean success=board.unpin(x,y); //Board method for unpinning 

      if (!success){
        out.println("ERROR PIN_NOT_FOUND No pin exists at the given coordinates");
      }
      else{
        out.println("UNPIN_SUCCESSFUL");
      }
    }
    catch(NumberFormatException e){
      out.println("ERROR INVALID_REQUEST");
    }
    
  }

  /*
  •  PIN places a pin at the given coordinate.
  • All notes containing the coordinate are affected.
  • If a PIN is placed at a coordinate that lies inside more than one note (due to partial overlap), the PIN
    command applies to all such notes. Each affected note becomes pinned as a result of this command.
  • If PIN misses all notes → ERROR
  */
  private void pinHandle(String[] tokens){
    //Exception handling for parsing coordinates
    try{
      int x=Integer.parseInt(tokens[1]);
      int y=Integer.parseInt(tokens[2]);

      int success=board.pin(x,y); //Board method for pinnning

      if (success==0){
        out.println("ERROR NO_NOTE_AT_COORDINATE No note contains the given poin");
      }
      else{
        out.println("OK PIN_ADDED");
      }
    }
    catch(NumberFormatException e){
      out.println("ERROR INVALID_REQUEST");
    }
  }

  /*
  Removes all notes and all pins, regardless of status.
  */
  private void clearHandle(){
    board.clear();
    out.println("CLEAR_COMPLETE");
  }

  /*
  • Removes all unpinned notes.
  • Operation is atomic: clients observe either the board before SHAKE or the fully updated board after
    SHAKE
  */
  private void shakeHandle(){
    board.shake();
    out.println("OK SHAKE_COMPLETE");
  }

  /*
  • Terminates the client connection gracefully.
  */
  private void disconnectHandle(){
    try {
      out.println("OK DISCONNECT");  // optional confirmation to client
      System.out.println("[DISCONNECT] Client " + clientIP + " disconnected.");

      // Close I/O streams and socket
      if (out != null) out.close();
      if (userInput != null) userInput.close();
      if (socket != null && !socket.isClosed()) socket.close();

      // Stop the client handler thread
      runningClient = false;  // if you have a running flag in the thread loop

    } catch (IOException e) {
        System.err.println("Error disconnecting client: " + e.getMessage());
    }
  }
}
