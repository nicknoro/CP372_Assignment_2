import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeoutException;

// Sender Class
public class Sender {
  public static void main(String[] args) {
      if (args.length < 5) { // Parse the arguments
          System.out.println("Usage: java Sender <rcv_ip> <rcv_data_port> " +
                  "<sender_ack_port> <input_file> <timeout_ms> [window_size]");
          System.exit(1);
      }

      // Initialize the variables
      String recIP = null;
      int recPort = 0;
      int sendPort = 0;
      String inputFile = null;
      int timeout = 0;
      int windowSize = 1;
      InetAddress receiverAddress = null;

      try {
          recIP = args[0]; // Receiver IP
          try {
              receiverAddress = InetAddress.getByName(recIP); // Receiver Address
          } catch (UnknownHostException e) {
              System.out.println("Invalid Receiver IP");
              System.exit(1);
          }

          recPort = Integer.parseInt(args[1]); // Receiver Port
          sendPort = Integer.parseInt(args[2]); // Sender Port
          inputFile = args[3]; // Input
          timeout = Integer.parseInt(args[4]); // Timeout value

          if (args.length == 6) {
              windowSize = Integer.parseInt(args[5]); // Window size (optional)
              if (windowSize % 4 != 0 || windowSize > 128) {
                  System.out.println("Invalid window Size");
                  System.exit(1);
              }
          }

          // Print Sender Side Info
          System.out.println("=== DS-FTP Sender ===");
          System.out.println("Receiver IP  : " + recIP);
          System.out.println("Receiver Port: " + recPort);
          System.out.println("Sender Port  : " + sendPort);
          System.out.println("Input File   : " + inputFile);
          System.out.println("Timeout      : " + timeout + " ms");
          System.out.println("Mode         : " + (windowSize > 1 ? "Go-Back-N (W=" + windowSize + ")" : "Stop-and-Wait"));
      } catch (NumberFormatException e) {
          System.out.println("Invalid arguments: " + e.getMessage());
      }

      // Socket initialization
      DatagramSocket socket = null;
      try {
          socket = new DatagramSocket(sendPort);
          socket.setSoTimeout(timeout); // Set timeout for socket receiving ACKS

          // Start timer
          long startTime = System.nanoTime();

          // Call handshake
          boolean HandShake = handshake(socket, receiverAddress, recPort);
          if (!HandShake) {
              socket.close();
              System.exit(1);
          }

          if (windowSize == 1) {
              stopAndSend(socket, inputFile, receiverAddress, recPort);
          } else {
              GBN(socket, inputFile, receiverAddress, recPort, windowSize);
          }

          long endTime = System.nanoTime();
          System.out.printf("Total Transmission Time: %.2f seconds%n", (endTime - startTime) / 1e9);

      } catch (SocketException e) {
          System.out.println("Socket error: " + e.getMessage());
          System.exit(1);
      }

      socket.close();
  }

  //<------------------------------------------------------------------->

  private static boolean handshake(DatagramSocket socket, InetAddress receiverAddress, int recPort) {
      DSPacket packet = new DSPacket(DSPacket.TYPE_SOT, 0, null);
      boolean isACKrecd = false;
      int retryCount = 0;

      while (!isACKrecd) {
          try {
              sendPacket(socket, packet, receiverAddress, recPort);
              DSPacket ACKpkt = ackRecd(socket);
              if (ACKpkt.getType() == DSPacket.TYPE_ACK && ACKpkt.getSeqNum() == 0) {
                  System.out.println("Connection Established");
                  isACKrecd = true;
              }
          } catch (SocketTimeoutException e) {
              retryCount++;
              System.out.println("Timeout sending SOT, retry " + retryCount);
              if (retryCount >= 3) {
                  System.out.println("3 consecutive timeouts, terminating transfer.");
                  return isACKrecd;
              }
          } catch (IOException e) {
              System.out.println("Send failed, retrying...");
          }
      }
      return isACKrecd;
  }

  //<------------------------------------------------------------------->

  private static void stopAndSend(DatagramSocket socket, String inputFile, InetAddress receiverAddress, int recPort) {
      File file = new File(inputFile);
      if (file.length() == 0) {
          System.out.println("File is empty. Sending EOT immediately.");
          sendEOT(socket, receiverAddress, recPort, 1);
          return;
      }

      int seqNum = 1;
      try {
          FileInputStream fis = new FileInputStream(file);
          BufferedInputStream bis = new BufferedInputStream(fis);
          byte[] buffer = new byte[DSPacket.MAX_PAYLOAD_SIZE];
          int bytesRead = 0;

          while ((bytesRead = bis.read(buffer)) != -1) {
              byte[] data = Arrays.copyOf(buffer, bytesRead);
              DSPacket pkt = new DSPacket(DSPacket.TYPE_DATA, seqNum % 128, data);

              boolean isACKrecd = false;
              int retryCount = 0;

              while (!isACKrecd) {
                  try {
                      sendPacket(socket, pkt, receiverAddress, recPort);
                      DSPacket ackPkt = ackRecd(socket);
                      if (ackPkt.getType() == DSPacket.TYPE_ACK && ackPkt.getSeqNum() == seqNum % 128) {
                          isACKrecd = true;
                          seqNum++;
                      }
                  } catch (SocketTimeoutException e) {
                      retryCount++;
                      System.out.println("Timeout seq " + (seqNum % 128) + " retry " + retryCount);
                      if (retryCount >= 3) {
                          bis.close();
                          System.out.println("3 consecutive timeouts, terminating transfer.");
                          return;
                      }
                  } catch (IOException e) {
                      System.out.println("Network error: " + e.getMessage());
                      bis.close();
                      return;
                  }
              }
          }

          bis.close();
      } catch (IOException e) {
          System.out.println("File error: " + e.getMessage());
      }

      sendEOT(socket, receiverAddress, recPort, seqNum);
  }

  //<------------------------------------------------------------------->

  private static void sendPacket(DatagramSocket socket, DSPacket pkt, InetAddress addr, int port) throws IOException {
      byte[] data = pkt.toBytes();
      DatagramPacket dp = new DatagramPacket(data, data.length, addr, port);
      socket.send(dp);
  }

  //<------------------------------------------------------------------->

  private static DSPacket ackRecd(DatagramSocket socket) throws IOException {
      byte[] buffer = new byte[DSPacket.MAX_PACKET_SIZE];
      DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
      socket.receive(dp);
      return new DSPacket(dp.getData());
  }

  //<------------------------------------------------------------------->

  private static void GBN(DatagramSocket socket, String inputFile, InetAddress receiverAddress, int recPort, int windowSize) {
      File file = new File(inputFile);
      if (file.length() == 0) {
          System.out.println("File is empty. Sending EOT immediately.");
          sendEOT(socket, receiverAddress, recPort, 1);
          return;
      }

      try {
          BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inputFile));
          int base = 1;
          int nextSeqNum = 1;
          boolean eof = false;
          int retryCount = 0;

          DSPacket[] senderBuffer = new DSPacket[128];
          List<DSPacket> chaosGroup = new ArrayList<>();

          byte[] buffer = new byte[DSPacket.MAX_PAYLOAD_SIZE];
          int bytesRead;

          while (!eof || ((nextSeqNum - base + 128) % 128) > 0) {
              while (!eof && ((nextSeqNum - base + 128) % 128) < windowSize) {
                  bytesRead = bis.read(buffer);
                  if (bytesRead == -1) {
                      eof = true;
                      break;
                  }
                  byte[] data = Arrays.copyOf(buffer, bytesRead);
                  DSPacket pkt = new DSPacket(DSPacket.TYPE_DATA, nextSeqNum % 128, data);
                  senderBuffer[nextSeqNum % 128] = pkt;
                  chaosGroup.add(pkt);
                  nextSeqNum++;
                  if (chaosGroup.size() == 4) {
                      sendChaos(socket, receiverAddress, recPort, chaosGroup);
                      chaosGroup.clear();
                  }
              }

              if (eof && !chaosGroup.isEmpty()) {
                  for (DSPacket pkt : chaosGroup) {
                      sendPacket(socket, pkt, receiverAddress, recPort);
                  }
                  chaosGroup.clear();
              }

              try {
                  DSPacket ackPkt = ackRecd(socket);
                  if (ackPkt.getType() == DSPacket.TYPE_ACK) {
                      int ackNo = ackPkt.getSeqNum();
                      if ((ackNo - base + 128) % 128 < windowSize) {
                          retryCount = 0;
                          base = (ackNo + 1) % 128;
                      }
                  }
              } catch (SocketTimeoutException e) {
                  System.out.println("Timeout → resending window");
                  retryCount++;
                  if (retryCount >= 3) {
                      bis.close();
                      System.out.println("3 consecutive timeouts, terminating transfer.");
                      return;
                  }
                  int seq = base;
                  while (seq != nextSeqNum) {
                      DSPacket pkt = senderBuffer[seq % 128];
                      sendPacket(socket, pkt, receiverAddress, recPort);
                      seq = (seq + 1) % 128;
                  }
              }
          }

          bis.close();
          sendEOT(socket, receiverAddress, recPort, nextSeqNum);

      } catch (FileNotFoundException e) {
          System.out.println("File error");
      } catch (IOException e) {
          System.out.println("File read error: " + e.getMessage());
      }
  }

  //<------------------------------------------------------------------->

  private static void sendChaos(DatagramSocket socket, InetAddress receiverAddress, int recPort, List<DSPacket> windowGroup) {
      List<DSPacket> shuffled = ChaosEngine.permutePackets(windowGroup);
      try {
          for (DSPacket pkt : shuffled) {
              sendPacket(socket, pkt, receiverAddress, recPort);
          }
      } catch (IOException e) {
          System.out.println("Send failed, retrying...");
      }
  }

  //<------------------------------------------------------------------->

  private static void sendEOT(DatagramSocket socket, InetAddress receiverAddress, int recPort, int seqNum) {
      DSPacket eotPacket = new DSPacket(DSPacket.TYPE_EOT, seqNum % 128, null);
      boolean isACKrecd = false;
      int retryCount = 0;

      while (!isACKrecd) {
          try {
              sendPacket(socket, eotPacket, receiverAddress, recPort);
              DSPacket ackpkt = ackRecd(socket);
              if (ackpkt.getType() == DSPacket.TYPE_ACK && ackpkt.getSeqNum() == seqNum % 128) {
                  isACKrecd = true;
              }
          } catch (SocketTimeoutException e) {
              retryCount++;
              System.out.println("Timeout sending EOT, retry " + retryCount);
              if (retryCount >= 3) {
                  System.out.println("3 consecutive timeouts, terminating transfer.");
                  return;
              }
          } catch (IOException e) {
              System.out.println("Network error during EOT: " + e.getMessage());
              return;
          }
      }
  }
}