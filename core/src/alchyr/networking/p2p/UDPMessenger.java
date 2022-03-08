package alchyr.networking.p2p;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class UDPMessenger {
    private static final int MSG_LENGTH = 512;
    private byte[] buf = new byte[MSG_LENGTH];
    private DatagramPacket packet;
    private DatagramSocket sender;
    private ScheduledExecutorService keepAlive = null;
    private boolean active;

    public UDPMessenger() {
        try {
            sender = new DatagramSocket();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start(String keepAlive, String destinationIP, int destinationPort) throws UnknownHostException {
        start(keepAlive, InetAddress.getByName(destinationIP), destinationPort);
    }
    public void start(String keepAlive, InetAddress destinationIP, int destinationPort) {
        try {
            packet = new DatagramPacket(buf, buf.length, destinationIP, destinationPort);
            if (!active) {
                active = true;

                editorLogger.info("Pass: " + keepAlive);
                byte[] keepAliveBuffer = keepAlive.getBytes(StandardCharsets.UTF_16);
                DatagramPacket keepAlivePacket = new DatagramPacket(keepAliveBuffer, keepAliveBuffer.length, packet.getAddress(), packet.getPort());
                Runnable keepAliveAction = () -> {
                    try {
                        editorLogger.info("Screaming into the void...  (specifically, to " + destinationIP.getHostAddress() + ":" + destinationPort + ")");
                        sender.send(keepAlivePacket);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                };

                this.keepAlive = Executors.newScheduledThreadPool(2);
                this.keepAlive.scheduleAtFixedRate(keepAliveAction, 0, 5, TimeUnit.SECONDS);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getLocalPort() {
        return sender.getLocalPort();
    }

    public void dispose() {
        if (keepAlive != null) {
            keepAlive.shutdown();
            keepAlive = null;
            active = false;
        }
    }
}
