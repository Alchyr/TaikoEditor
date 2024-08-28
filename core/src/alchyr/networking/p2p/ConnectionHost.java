package alchyr.networking.p2p;

import alchyr.taikoedit.util.GeneralUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class ConnectionHost {
    private static final Logger logger = LogManager.getLogger("connection host");

    private UDPListener listener;
    private int id = 0;
    private Map<String, UDPMessenger> connections = new HashMap<>();

    private String pass;

    /*Usage:
        Construct host. It preps the listener.
        Call prepConnection. This creates a messenger which will determine the port it is sending from.
        Give information to someone else, which will construct a ConnectionSub with that info.
        That will expose itself to the host's ip from that specific port, and generate some info.
        That info has to be passed back here, which will expose the listener to the sub's messenger.
        Also, it will set up the messenger to send to the sub's listener.
     */

    /*
        CONCLUSION:
        UDP Hole punching is too unreliable and overall, still not very convenient unless you have a server to communicate with.
        Forcing the host to perform port forwarding will likely end up being the easiest way.
     */

    /*
        pwnat - no straightforward "raw" socket support in java

     */
    public ConnectionHost() {
        try {
            listener = new UDPListener();
            pass = GeneralUtils.generateCode(64);
            logger.info("Server input port: " + listener.getLocalPort());
            listener.listen();
        } catch (IOException e) {
            listener = null;
            logger.info("Failed to start server.");
            e.printStackTrace();
        }
    }

    public void finishConnection(String connectionInfo) {
        logger.info("Attempting to complete connection: " + connectionInfo);
        try {
            String[] params = connectionInfo.split("\\|");

            if (params.length != 4) {
                logger.info("Connection data is invalid: " + connectionInfo);
            }

            UDPMessenger messenger = connections.get(params[3]);
            if (messenger == null) {
                logger.info("Invalid connection ID.");
                return;
            }

            InetAddress subAddress = InetAddress.getByName(params[0]);
            if (subAddress == null) {
                logger.info("Invalid address.");
                return;
            }

            int subListenerPort = Integer.parseInt(params[1]);
            int subOutputPort = Integer.parseInt(params[2]);

            if (listener == null) {
                logger.info("No listener, cannot expose.");
            }
            else {
                listener.expose(subAddress, subOutputPort, pass);
                messenger.start(pass, subAddress, subListenerPort);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NumberFormatException e) {
            logger.info("Failed to parse port.");
            e.printStackTrace();
        }
    }

    public String prepConnection() {
        UDPMessenger messenger = new UDPMessenger();
        String connectionInfo = null;
        try {
            connectionInfo = InetAddress.getLocalHost().getHostAddress() + "|" + listener.getLocalPort() + "|" + messenger.getLocalPort() + "|" + pass + "|" + id;
            /*
                LOCAL PORT: Only (maybe) works with local addresses.
                Have to get public port that is routed to the local port?
             */
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }//NetworkUtil
        connections.put(String.valueOf(id), messenger);
        ++id;

        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(connectionInfo), null);
        logger.info("Stored connection info in clipboard.");
        logger.info(connectionInfo);
        return connectionInfo;
    }

    public int getPort() {
        if (listener != null)
            return listener.getLocalPort();
        return -1;
    }

    public void dispose() {
        if (listener != null) {
            listener.dispose();
            listener = null;
        }

        for (UDPMessenger messenger : connections.values()) {
            messenger.dispose();
        }
        connections.clear();
    }
}
