# DS-FTP (Datagram Socket File Transfer Protocol)

This project implements a reliable file transfer protocol over UDP using Java. It supports both **Stop-and-Wait** and **Go-Back-N (GBN)** modes, handling packet loss, out-of-order delivery, and retransmissions.

---

## Features

- Stop-and-Wait and Go-Back-N modes
- Reliable data transfer over UDP
- Handles packet loss and duplicates
- Supports SOT (Start of Transmission) and EOT (End of Transmission) packets
- Configurable window size and timeout

---

## Project Structure
├── Sender.java # Sender program
├── Receiver.java # Receiver program
├── DSPacket.java # Packet wrapper class
├── ChaosEngine.java # Simulates packet loss and reordering
├── input.txt # Sample input file
└── output.txt # Example output file


---

## How to Run

### Compile

```bash
javac *.java

Run Receiver
java Receiver <sender_ip> <sender_ack_port> <rcv_data_port> <output_file> <RN>

sender_ip – IP of the sender (e.g., 127.0.0.1)

sender_ack_port – Port on sender for ACKs

rcv_data_port – Port to receive data

output_file – File to save received data

RN – Reliability number for simulating ACK drops (0 = no drops)

Run Sender
java Sender <rcv_ip> <rcv_data_port> <sender_ack_port> <input_file> <timeout_ms> [window_size]

rcv_ip – Receiver IP

rcv_data_port – Receiver data port

sender_ack_port – Sender port for receiving ACKs

input_file – File to send

timeout_ms – Timeout in milliseconds

[window_size] – Optional, >1 for GBN mode

Notes

The protocol ensures in-order delivery even with packet loss and out-of-order packets.

ChaosEngine can simulate packet loss, duplication, and shuffling for testing.

Stop-and-Wait mode uses a window size of 1, while GBN uses a configurable window.

Author

Nikin Noronha, Jason Noronha
CS/Networking Project - CP372
