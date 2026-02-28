import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class Receiver {

    private static final int MAX_SEQ = 128;
    private static final int WINDOW_SIZE = MAX_SEQ / 2; // 64 (Safe GBN window)

    private DatagramSocket socket;
    private InetAddress senderAddress;
    private int senderAckPort;
    private FileOutputStream fos;

    private int expectedSeq = 1;
    private int lastDeliveredSeq = 0;

    private Map<Integer, DSPacket> buffer = new HashMap<>();
    private ChaosEngine chaosEngine;

    public Receiver(String senderIP, int senderAckPort, int rcvDataPort,
                    String outputFile, int RN) throws Exception {

        this.socket = new DatagramSocket(rcvDataPort);
        this.senderAddress = InetAddress.getByName(senderIP);
        this.senderAckPort = senderAckPort;
        this.fos = new FileOutputStream(outputFile);
        this.chaosEngine = new ChaosEngine(RN);

        System.out.println("Receiver listening on port " + rcvDataPort);
    }

    private int distance(int seq) {
        return (seq - expectedSeq + MAX_SEQ) % MAX_SEQ;
    }

    private boolean isExact(int seq) {
        return distance(seq) == 0;
    }

    private boolean isFuture(int seq) {
        int d = distance(seq);
        return d > 0 && d < WINDOW_SIZE;
    }

    private void sendACK(int seq) throws IOException {
        DSPacket ackPacket = new DSPacket();
        ackPacket.setType(2); // ACK Type
        ackPacket.setSeqNum(seq);
        ackPacket.setLength((short) 0);

        byte[] ackBytes = ackPacket.toBytes();

        if (chaosEngine.shouldDropAck()) {
            return; // Dropped by ChaosEngine
        }

        DatagramPacket dp = new DatagramPacket(
                ackBytes,
                ackBytes.length,
                senderAddress,
                senderAckPort
        );

        socket.send(dp);
    }

    private void handshake() throws Exception {
        byte[] bufferBytes = new byte[128];
        DatagramPacket dp = new DatagramPacket(bufferBytes, bufferBytes.length);

        while (true) {
            socket.receive(dp);
            DSPacket packet = DSPacket.fromBytes(dp.getData());

            if (packet.getType() == 0 && packet.getSeqNum() == 0) {
                sendACK(0);
                System.out.println("SOT received. Connection established.");
                return;
            }
        }
    }

    private void receiveData() throws Exception {
        byte[] bufferBytes = new byte[128];
        DatagramPacket dp = new DatagramPacket(bufferBytes, bufferBytes.length);

        while (true) {
            socket.receive(dp);
            DSPacket packet = DSPacket.fromBytes(dp.getData());

            int type = packet.getType();
            int seq = packet.getSeqNum();

            // Robustness: Re-ACK SOT if it arrives during data phase
            if (type == 0 && seq == 0) {
                sendACK(0);
                continue;
            }

            // EOT Handling with Linger Logic
            if (type == 3) {
                System.out.println("EOT received. Entering linger mode.");
                sendACK(seq);

                socket.setSoTimeout(2000); 
                try {
                    while (true) {
                        byte[] lingerBuffer = new byte[128];
                        DatagramPacket lingerPacket = new DatagramPacket(lingerBuffer, 128);
                        socket.receive(lingerPacket);
                        DSPacket p = DSPacket.fromBytes(lingerPacket.getData());
                        if (p.getType() == 3) {
                            sendACK(p.getSeqNum());
                        }
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("Linger complete. No more EOTs.");
                }
                break;
            }

            if (type != 1) continue; // Ignore non-DATA

            if (isExact(seq)) {
                deliver(packet);
                // Check buffer for contiguous sequence
                while (buffer.containsKey(expectedSeq)) {
                    deliver(buffer.remove(expectedSeq));
                }
            } else if (isFuture(seq)) {
                if (!buffer.containsKey(seq)) {
                    buffer.put(seq, packet);
                }
            }

            // Cumulative ACK: Always ACK the last in-order delivered packet
            sendACK(lastDeliveredSeq);
        }
    }

    private void deliver(DSPacket packet) throws IOException {
        byte[] payload = packet.getPayload();
        int length = packet.getLength();

        if (length > 0) {
            fos.write(payload, 0, length);
        }

        lastDeliveredSeq = expectedSeq;
        expectedSeq = (expectedSeq + 1) % MAX_SEQ;
    }

    private void close() throws IOException {
        fos.close();
        socket.close();
        System.out.println("Receiver closed. Mission successful.");
    }

    public static void main(String[] args) {
        if (args.length != 5) {
            System.out.println("Usage: java Receiver <sender_ip> <sender_ack_port> <rcv_data_port> <output_file> <RN>");
            return;
        }

        try {
            Receiver receiver = new Receiver(
                    args[0], Integer.parseInt(args[1]), 
                    Integer.parseInt(args[2]), args[3], 
                    Integer.parseInt(args[4])
            );

            receiver.handshake();
            receiver.receiveData();
            receiver.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
