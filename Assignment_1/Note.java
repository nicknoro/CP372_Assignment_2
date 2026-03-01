import java.util.*;

public class Note {
  private final int noteX;
  private int noteY;
  private String color;
  private String message;
  private Set<String> pins=new HashSet<>();

  public Note(int x, int y,String color,String message){
    this.noteX=x;
    this.noteY=y;
    this.message=message;
    this.color=color;
  }

  public int getX(){
    return this.noteX;
  }

  public int getY(){
    return this.noteY;
  }
  public String getColor(){
    return this.color;
  }
  public String getMessage(){
    return this.message;
  }
  public Set<String> getPins(){
    return this.pins;
  }
  public synchronized void addPin(int px, int py) {
    // only add if coordinate is inside the note
    pins.add(px + ":" + py);
  }

    // Remove a pin at coordinate px, py
    public synchronized void removePin(int px, int py) {
        pins.remove(px + ":" + py); // removes pin if exists
    }

    // Check if the note is pinned (has at least one pin)
    public synchronized boolean isPinned() {
        return !pins.isEmpty();
    }

    // Check if a coordinate is inside this note
    public boolean contains(int px, int py, int noteWidth, int noteHeight) {
    return px >= this.noteX && px < this.noteX + noteWidth &&
           py >= this.noteY && py < this.noteY + noteHeight;
}
}
