//Import statement
import java.util.*;

public class Board {
  private final int boardWidth;
  private final int boardHeight;
  private final int noteHeight;
  private final int noteWidth;
  private final ArrayList<String> colors;
  private final ArrayList<Note> notes;

  public Board(int boardWidth,int boardHeight,int noteHeight,int noteWidth, ArrayList<String> colors){
    this.boardWidth=boardWidth;
    this.boardHeight=boardHeight;
    this.noteWidth=noteWidth;
    this.noteHeight=noteHeight;
    this.colors=colors;
    this.notes = new ArrayList<>();
  }

  //Get Board Width
  public int getBoardWidth(){
    return this.boardWidth;
  }
  //Get Board Height
  public int getBoardHeight(){
    return this.boardHeight;
  }
  //Get Note Width
  public int getNoteWidth(){
    return this.noteWidth;
  }
  //Get Note Height
  public int getNoteHeight(){
    return this.noteHeight;
  }
  //Get List of Colors
  public ArrayList<String> getColors(){
    return this.colors;
  }

  // Adding/Posting Note: Answering postHandle()
  public synchronized int addNote(int x,int y, String color, String message){
    if (x>this.boardWidth || y>this.boardHeight){
      return 1;
    }
    if (!this.getColors().contains(color.toLowerCase())) {
      return 2;
    }
    Note newNote=new Note(x,y,color,message);
    for (Note n : notes) {
      // check complete overlap
      if (newNote.getX() == n.getX() && newNote.getY() == n.getY()) {
        return 3;
      }
    }
    this.notes.add(newNote);
    return 0;
  }

  // Get notes: Answering getHandle()
  public synchronized Note getNote(String color,int x, int y, String referWord){
    for (Note note : notes) {
      if (color != null && !note.getColor().equalsIgnoreCase(color))
          continue;

      if (x != -1 && note.getX() != x)
          continue;

      if (y != -1 && note.getY() != y)
          continue;

      if (referWord != null && !note.getMessage().contains(referWord))
          continue;

      return note;   // first matching note
  }
  return null;
}

  // Unpin: Answering unpinHandle()
  public synchronized boolean unpin(int x, int y){
    for (Note note:notes){
      Set<String> pins=note.getPins();
      for (String pin:pins){
        String[] coordinates=pin.split(":");
        int Xcor=Integer.parseInt(coordinates[0]);
        int Ycor=Integer.parseInt(coordinates[1]);
        if (Xcor==x && Ycor==y){
          note.removePin(x, y);
          return true;
        }

      }
    }
    return false;
  }

  //Pin: Answering pinHandle()
  public synchronized int pin(int x,int y){
    int result=0;
    for (Note note:notes){
      if(note.contains(x, y, getNoteWidth(), getNoteHeight())){
        note.addPin(x, y);
        result=1;
      }
    }
    return result;
  }

  //Clear notes and pins: Answering clearHandle()
  public synchronized void clear(){
    for (Note note:notes){
      note.getPins().clear();
      }
      this.notes.clear();
  }

  //Answers shakeHandle()
  public synchronized void shake(){
    notes.removeIf(note -> !note.isPinned());
  }
  
  

}

