import java.io.*;
import java.net.*;
import java.util.*;

/**
 * DS-FTP Receiver
 * ---------------
 * Receives a file from a remote Sender over UDP using a custom
 * Reliable Data Transfer protocol.
 *
 * Automatically handles both Stop-and-Wait and GBN based on what
 * the Sender sends (window size is transparent to the receiver;
 * buffered + cumulative ACK logic covers both cases).
 *
 * Usage:
 *   java Receiver <sender_ip> <sender_ack_port> <rcv_data_port> <output_file> <RN>
 */
public class Receiver {

    // ------------------------------------------------------------------ //
    //  Constants
    // ------------------------------------------------------------------ //
    private static final int SEQ_MODULO      = 128;
    private static final int RECEIVE_TIMEOUT = 30000; // 30s overall receive timeout

    // ------------------------------------------------------------------ //
    //  Entry point
    // ------------------------------------------------------------------ //
    public static void main(String[] args) throws Exception {

        if (args.length != 5) {
            System.err.println("Usage: java Receiver <sender_ip> <sender_ack_port> " +
                               "<rcv_data_port> <output_file> <RN>");
            System.exit(1);
        }

        String senderIp      = args[0];
        int    senderAckPort = Integer.parseInt(args[1]);
        int    rcvDataPort   = Integer.parseInt(args[2]);
        String outputFile    = args[3];
        int    rn            = Integer.parseInt(args[4]);

        InetAddress senderAddr = InetAddress.getByName(senderIp);

        // Listen for incoming DATA on rcvDataPort
        DatagramSocket socket = new DatagramSocket(rcvDataPort);
        socket.setSoTimeout(RECEIVE_TIMEOUT);

        System.out.println("[Receiver] Listening on port " + rcvDataPort);
        System.out.println("[Receiver] Will send ACKs to " + senderIp + ":" + senderAckPort);
        System.out.println("[Receiver] RN=" + rn);

        int ackCount = 0;  // 1-indexed total of ACKs the receiver has intended to send

        // ---- Phase 1: Handshake ----------------------------------------
        System.out.println("[Receiver] Waiting for SOT...");
        while (true) {
            DSPacket pkt = receivePacket(socket);
            if (pkt.getType() == DSPacket.TYPE_SOT && pkt.getSeqNum() == 0) {
                System.out.println("[Receiver] SOT received. Sending ACK 0.");
                ackCount++;
                if (!ChaosEngine.shouldDrop(ackCount, rn)) {
                    sendAck(socket, senderAddr, senderAckPort, 0);
                } else {
                    System.out.println("[Receiver] ** ACK 0 (SOT) DROPPED (ChaosEngine) **");
                }
                break;
            }
        }

        // ---- Phase 2 + Phase 3: Data Transfer & Teardown ---------------
        // ackCount is already 1 after the SOT ACK above; pass it in so
        // ChaosEngine counting continues correctly across all ACKs.
        receiveData(socket, senderAddr, senderAckPort, outputFile, rn, ackCount);

        socket.close();
        System.out.println("[Receiver] Done.");
    }

    // ------------------------------------------------------------------ //
    //  Data reception (handles both S&W and GBN via buffered cumulative ACK)
    // ------------------------------------------------------------------ //
    private static void receiveData(DatagramSocket socket,
                                     InetAddress senderAddr, int senderAckPort,
                                     String outputFile, int rn,
                                     int ackCountInit) throws Exception {

        // Buffer: seq -> payload bytes (for GBN out-of-order buffering)
        // We use a circular buffer indexed by sequence number
        byte[][] buffer      = new byte[SEQ_MODULO][];
        boolean[] buffered   = new boolean[SEQ_MODULO];

        int  expectedSeq     = 1;      // First DATA packet seq
        int  lastDelivered   = 0;      // Cumulative ACK value (seq of last in-order delivery)
        int  ackCount        = ackCountInit;
        boolean eotReceived  = false;
        int  eotSeq          = -1;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        while (!eotReceived) {
            DSPacket pkt;
            try {
                pkt = receivePacket(socket);
            } catch (SocketTimeoutException e) {
                System.err.println("[Receiver] Receive timeout. Exiting.");
                break;
            }

            byte type = pkt.getType();
            int  seq  = pkt.getSeqNum();

            // ---- EOT ---------------------------------------------------
            if (type == DSPacket.TYPE_EOT) {
                System.out.println("[Receiver] EOT received (seq=" + seq + ").");
                eotSeq      = seq;
                eotReceived = true;

                // ACK the EOT
                ackCount++;
                if (!ChaosEngine.shouldDrop(ackCount, rn)) {
                    sendAck(socket, senderAddr, senderAckPort, seq);
                    System.out.println("[Receiver] Sent ACK for EOT (seq=" + seq + ").");
                } else {
                    System.out.println("[Receiver] ** ACK for EOT DROPPED (ChaosEngine) **");
                }
                break;
            }

            // ---- DATA --------------------------------------------------
            if (type == DSPacket.TYPE_DATA) {

                System.out.println("[Receiver] DATA seq=" + seq +
                                   " len=" + pkt.getLength() +
                                   " expectedSeq=" + expectedSeq);

                // Determine if seq is within our receive window.
                // For S&W, window=1; for GBN the window is large.
                // We accept anything within [expectedSeq, expectedSeq+127] mod 128
                // i.e. any seq that is "ahead" within the window, and buffer it.

                if (isInWindow(seq, expectedSeq)) {

                    // Buffer if not already received
                    if (!buffered[seq]) {
                        buffered[seq]  = true;
                        buffer[seq]    = Arrays.copyOf(pkt.getPayload(), pkt.getLength());
                        System.out.println("[Receiver] Buffered seq=" + seq);
                    }

                    // Deliver in-order as far as possible
                    while (buffered[expectedSeq]) {
                        baos.write(buffer[expectedSeq], 0, buffer[expectedSeq].length);
                        System.out.println("[Receiver] Delivered seq=" + expectedSeq);
                        buffered[expectedSeq] = false;
                        buffer[expectedSeq]   = null;
                        lastDelivered         = expectedSeq;
                        expectedSeq           = (expectedSeq + 1) % SEQ_MODULO;
                    }

                } else {
                    // Out-of-window: discard, re-send cumulative ACK
                    System.out.println("[Receiver] Out-of-window seq=" + seq +
                                       " expected=" + expectedSeq + ". Discarding.");
                }

                // Send cumulative ACK (= last contiguous in-order seq delivered)
                ackCount++;
                if (!ChaosEngine.shouldDrop(ackCount, rn)) {
                    sendAck(socket, senderAddr, senderAckPort, lastDelivered);
                    System.out.println("[Receiver] Sent cumulative ACK=" + lastDelivered);
                } else {
                    System.out.println("[Receiver] ** ACK " + lastDelivered + " DROPPED (ChaosEngine) **");
                }
            }
        }

        // Write output file
        byte[] fileData = baos.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(fileData);
        }
        System.out.println("[Receiver] File written: " + outputFile +
                           " (" + fileData.length + " bytes)");
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    /**
     * Check if a received sequence number falls within the receive window.
     * The window spans [expectedSeq, expectedSeq + SEQ_MODULO - 1) mod 128.
     * We use a half-window to avoid ambiguity: accept anything within
     * SEQ_MODULO/2 ahead of expectedSeq.
     */
    private static boolean isInWindow(int seq, int expectedSeq) {
        // Distance ahead (mod 128)
        int dist = (seq - expectedSeq + SEQ_MODULO) % SEQ_MODULO;
        // Accept if within [0, SEQ_MODULO/2) i.e. not a wrap-around duplicate
        return dist < SEQ_MODULO / 2;
    }

    /** Send an ACK packet to the Sender's ACK port. */
    private static void sendAck(DatagramSocket socket,
                                  InetAddress addr, int port,
                                  int seqNum) throws IOException {
        DSPacket ack  = new DSPacket(DSPacket.TYPE_ACK, seqNum, null);
        byte[]   bytes = ack.toBytes();
        DatagramPacket dgram = new DatagramPacket(bytes, bytes.length, addr, port);
        socket.send(dgram);
    }

    /** Receive a DSPacket from the socket (blocking, subject to timeout). */
    private static DSPacket receivePacket(DatagramSocket socket)
            throws IOException {
        byte[] buf = new byte[DSPacket.MAX_PACKET_SIZE];
        DatagramPacket dgram = new DatagramPacket(buf, buf.length);
        socket.receive(dgram);
        return new DSPacket(buf);
    }
}
