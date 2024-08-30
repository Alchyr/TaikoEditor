package alchyr.networking.standard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

public abstract class ClientMessageHandler {
    private static final Logger logger = LogManager.getLogger();
    public boolean alive = true;

    public final ConnectionClient client;
    public float timeout;
    public Function<ClientMessageHandler, Boolean> onTimeout;


    public ClientMessageHandler(ConnectionClient client, float timeout, Function<ClientMessageHandler, Boolean> onTimeout) {
        this.client = client;
        this.timeout = timeout;
        this.onTimeout = onTimeout;
    }
    public ClientMessageHandler(ConnectionClient client) {
        this(client, -1, null);
    }


    public ClientMessageHandler setTimeout(float time, Function<ClientMessageHandler, Boolean> onTimeout) {
        this.timeout = time;
        this.onTimeout = onTimeout;
        return this;
    }
    public void update(float elapsed) {
        if (!alive) return;

        if (timeout > 0) {
            timeout -= elapsed;
            if (timeout <= 0) {
                alive = onTimeout != null ? onTimeout.apply(this) : false;
                return;
            }
        }
        while (!client.receivedMessages.isEmpty()) {
            Message msg = client.receivedMessages.poll();
            try {
                handleMessage(msg);
            }
            catch (Exception e) {
                logger.error("Exception occurred while receiving message:", e);
                client.fail("Disconnected: an error occurred while processing a message.");
            }

            if (!alive) return;
        }
    }

    public abstract void handleMessage(Message msg);
}
