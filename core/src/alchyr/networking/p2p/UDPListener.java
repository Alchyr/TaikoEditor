package alchyr.networking.p2p;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class UDPListener {
    private final DatagramSocket listener;
    private Thread listenerThread = null;
    private boolean listening;
    private byte[] buf = new byte[256];

    private ScheduledExecutorService holdOpen = null;
    private String pass = "";

    public UDPListener() throws IOException {
        listener = new DatagramSocket();
    }

    public void expose(InetAddress senderAddress, int senderPort, String pass) throws IOException {
        if (!listener.isClosed()) {
            this.pass = pass;

            if (holdOpen != null) {
                holdOpen.shutdown();
                holdOpen = null;
            }

            byte[] data = pass.getBytes(StandardCharsets.UTF_16);
            DatagramPacket packet = new DatagramPacket(data, data.length, senderAddress, senderPort);
            Runnable exposeAction = () -> {
                try {
                    editorLogger.info("Opening hole to " + packet.getAddress().getHostAddress() + ":" + packet.getPort());
                    listener.send(packet);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            };

            holdOpen = Executors.newScheduledThreadPool(2);
            holdOpen.scheduleAtFixedRate(exposeAction, 0, 5, TimeUnit.SECONDS);
        }
    }

    public void listen() {
        if (!listening) {
            listening = true;
            listenerThread = new Thread (() -> {
                try {
                    editorLogger.info("Waiting for a scream from the void...");
                    InetAddress address;
                    int port;
                    String msg;
                    while (!Thread.interrupted()) {
                        DatagramPacket packet
                                = new DatagramPacket(buf, buf.length);
                        editorLogger.info("Listening to port " + listener.getLocalPort());
                        listener.receive(packet); //blocks until receiving, interrupted, closed (some kind of exception)
                        address = packet.getAddress();
                        port = packet.getPort();
                        msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_16);
                        editorLogger.info("Received message: " + msg);
                        /*packet = new DatagramPacket(buf, buf.length, address, port);
                        listener.send(packet);*/
                        if (holdOpen != null) {
                            holdOpen.shutdown();
                            holdOpen = null;
                        }
                    }
                }
                catch (Exception ignored) {
                }
                editorLogger.info("Done waiting.");
            });
            listenerThread.setName("UDP Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();
        }
    }

    public int getLocalPort() {
        return listener.getLocalPort();
    }

    public void dispose() {
        if (listenerThread != null && listenerThread.isAlive()) {
            listenerThread.interrupt();
            listenerThread = null;
        }
        if (holdOpen != null) {
            holdOpen.shutdown();
            holdOpen = null;
        }
        if (!listener.isClosed())
            listener.close();
    }
}
