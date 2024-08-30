package alchyr.networking.standard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class ServerMessageHandler {
    private static final Logger logger = LogManager.getLogger();

    public abstract boolean handleMessage(ConnectionClient client, Message msg);
}
