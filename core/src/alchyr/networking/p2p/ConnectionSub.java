package alchyr.networking.p2p;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class ConnectionSub {
    private static final Logger logger = LogManager.getLogger("connection sub");

    private int id = -1;
    private UDPListener listener;
    private UDPMessenger messenger;

    //The host should already be actively listening.
    //At some point, the connection info from sub should be given to the host, which will cause it to expose its listener to this messenger.
    public ConnectionSub(String connectionInfo) {
        try {
            String[] params = connectionInfo.split("\\|");

            if (params.length != 5) {
                logger.info("Connection data is invalid: " + connectionInfo);
            }

            InetAddress hostAddress = InetAddress.getByName(params[0]);

            id = Integer.parseInt(params[4]);
            int hostListenerPort = Integer.parseInt(params[1]);
            int hostOutputPort = Integer.parseInt(params[2]);

            listener = new UDPListener();
            listener.listen();
            listener.expose(hostAddress, hostOutputPort, params[3]);

            messenger = new UDPMessenger();
            messenger.start(params[3], hostAddress, hostListenerPort);
            getConnectionInfo();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NumberFormatException e) {
            logger.info("Failed to parse port.");
            e.printStackTrace();
        }
    }

    public String getConnectionInfo() {
        String connectionInfo = null;
        try {
            connectionInfo = InetAddress.getLocalHost().getHostAddress() + "|" + listener.getLocalPort() + "|" + messenger.getLocalPort() + "|" + id;
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(connectionInfo), null);
        logger.info("Stored connection info in clipboard.");
        logger.info(connectionInfo);
        return connectionInfo;
    }

    public void dispose() {
        if (listener != null) {
            listener.dispose();
            listener = null;
        }
        if (messenger != null) {
            messenger.dispose();
            messenger = null;
        }
    }
}
