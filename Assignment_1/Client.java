import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;

public class Client {

    /* 
       Default connection values
     */
    private static int serverPort = 4554;
    private static String serverHost;

    /* 
       GUI elements
     */
    private static JLabel statusMessage;

    private static JRadioButton rbPost;
    private static JRadioButton rbGet;
    private static JRadioButton rbPin;
    private static JRadioButton rbUnpin;
    private static JRadioButton rbShake;
    private static JRadioButton rbDisconnect;

    private static JTextField tfX;
    private static JTextField tfY;
    private static JTextField tfColor;
    private static JTextField tfHost;
    private static JTextField tfPort;

    private static JTextArea taMessage;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                serverHost = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ignored) {}
            launchGUI();
        });
    }

    /* 
       GUI setup
     */
    private static void launchGUI() {
        JFrame frame = new JFrame("Bulletin Board Client");
        frame.setLayout(null);
        frame.setBounds(100, 100, 500, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JLabel lblOps = new JLabel("Select command:");
        lblOps.setBounds(20, 20, 200, 20);
        frame.add(lblOps);

        rbPost = new JRadioButton("POST");
        rbGet = new JRadioButton("GET");
        rbPin = new JRadioButton("PIN");
        rbUnpin = new JRadioButton("UNPIN");
        rbShake = new JRadioButton("SHAKE");
        rbDisconnect = new JRadioButton("DISCONNECT");

        rbPost.setBounds(20, 45, 80, 20);
        rbGet.setBounds(110, 45, 80, 20);
        rbPin.setBounds(200, 45, 80, 20);
        rbUnpin.setBounds(290, 45, 100, 20);
        rbShake.setBounds(20, 70, 100, 20);
        rbDisconnect.setBounds(130, 70, 120, 20);

        ButtonGroup group = new ButtonGroup();
        group.add(rbPost);
        group.add(rbGet);
        group.add(rbPin);
        group.add(rbUnpin);
        group.add(rbShake);
        group.add(rbDisconnect);

        frame.add(rbPost);
        frame.add(rbGet);
        frame.add(rbPin);
        frame.add(rbUnpin);
        frame.add(rbShake);
        frame.add(rbDisconnect);

        JLabel lblX = new JLabel("X:");
        lblX.setBounds(20, 110, 30, 20);
        frame.add(lblX);

        tfX = new JTextField();
        tfX.setBounds(60, 110, 80, 20);
        frame.add(tfX);

        JLabel lblY = new JLabel("Y:");
        lblY.setBounds(160, 110, 30, 20);
        frame.add(lblY);

        tfY = new JTextField();
        tfY.setBounds(200, 110, 80, 20);
        frame.add(tfY);

        JLabel lblColor = new JLabel("Color:");
        lblColor.setBounds(20, 140, 50, 20);
        frame.add(lblColor);

        tfColor = new JTextField();
        tfColor.setBounds(80, 140, 120, 20);
        frame.add(tfColor);

        JLabel lblMsg = new JLabel("Message:");
        lblMsg.setBounds(20, 170, 80, 20);
        frame.add(lblMsg);

        taMessage = new JTextArea();
        taMessage.setLineWrap(true);
        taMessage.setWrapStyleWord(true);

        JScrollPane msgScroll = new JScrollPane(taMessage);
        msgScroll.setBounds(20, 195, 420, 80);
        frame.add(msgScroll);

        JLabel lblHost = new JLabel("Host:");
        lblHost.setBounds(20, 295, 50, 20);
        frame.add(lblHost);

        tfHost = new JTextField(serverHost);
        tfHost.setBounds(70, 295, 160, 20);
        frame.add(tfHost);

        JLabel lblPort = new JLabel("Port:");
        lblPort.setBounds(250, 295, 40, 20);
        frame.add(lblPort);

        tfPort = new JTextField(String.valueOf(serverPort));
        tfPort.setBounds(290, 295, 70, 20);
        frame.add(tfPort);

        JButton btnSend = new JButton("Send");
        btnSend.setBounds(170, 330, 120, 30);
        btnSend.addActionListener(e -> prepareRequest());
        frame.add(btnSend);

        statusMessage = new JLabel("", SwingConstants.CENTER);
        statusMessage.setBounds(0, 370, 500, 25);
        statusMessage.setForeground(Color.RED);
        frame.add(statusMessage);

        frame.setVisible(true);
    }

    /* 
       Request construction
     */
    private static void prepareRequest() {
        statusMessage.setText("");

        if (!parsePort()) return;
        serverHost = tfHost.getText().trim();

        String request;

        try {
            if (rbPost.isSelected()) {
                int x = Integer.parseInt(tfX.getText().trim());
                int y = Integer.parseInt(tfY.getText().trim());
                String color = tfColor.getText().trim();
                String msg = taMessage.getText().trim();

                if (color.isEmpty() || msg.isEmpty()) {
                    statusMessage.setText("POST requires color and message");
                    return;
                }
                request = "POST " + x + " " + y + " " + color + " " + msg;
            }
            else if (rbPin.isSelected()) {
                request = "PIN " + tfX.getText().trim() + " " + tfY.getText().trim();
            }
            else if (rbUnpin.isSelected()) {
                request = "UNPIN " + tfX.getText().trim() + " " + tfY.getText().trim();
            }
            else if (rbShake.isSelected()) {
                request = "SHAKE";
            }
            else if (rbDisconnect.isSelected()) {
                request = "DISCONNECT";
            }
            else if (rbGet.isSelected()) {
                StringBuilder sb = new StringBuilder("GET");

                String color = tfColor.getText().trim();
                String xText = tfX.getText().trim();
                String yText = tfY.getText().trim();
                String refer = taMessage.getText().trim();

                if (!color.isEmpty()) {
                    sb.append(" color=").append(color);
                }

                if (!xText.isEmpty() && !yText.isEmpty()) {
                    sb.append(" contains=").append(xText).append(" ").append(yText);
                }

                if (!refer.isEmpty()) {
                    sb.append(" refersTo=").append(refer);
                }

                request = sb.toString();
            }
            else {
                statusMessage.setText("Please select a command");
                return;
            }

            sendToServer(request);

        } catch (NumberFormatException e) {
            statusMessage.setText("Invalid numeric input");
        }
    }

    /* 
       Networking
     */
    private static void sendToServer(String request) {
        try (
            Socket socket = new Socket(serverHost, serverPort);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner in = new Scanner(socket.getInputStream())
        ) {
            out.println(request);

            while (in.hasNextLine()) {
                System.out.println(in.nextLine());
            }

        } catch (IOException e) {
            statusMessage.setText("Unable to connect to server");
        }
    }

    /* 
       Utility
     */
    private static boolean parsePort() {
        try {
            serverPort = Integer.parseInt(tfPort.getText().trim());
            return serverPort > 0 && serverPort <= 65535;
        } catch (Exception e) {
            statusMessage.setText("Invalid port number");
            return false;
        }
    }
}