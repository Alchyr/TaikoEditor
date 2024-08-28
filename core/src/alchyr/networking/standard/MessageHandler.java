package alchyr.networking.standard;

import java.util.function.Function;

public abstract class MessageHandler {
    public boolean alive = true;

    public final ConnectionClient client;
    public float timeout;
    public Function<MessageHandler, Boolean> onTimeout;


    public MessageHandler(ConnectionClient client, float timeout, Function<MessageHandler, Boolean> onTimeout) {
        this.client = client;
        this.timeout = timeout;
        this.onTimeout = onTimeout;
    }
    public MessageHandler(ConnectionClient client) {
        this(client, -1, null);
    }


    public MessageHandler setTimeout(float time, Function<MessageHandler, Boolean> onTimeout) {
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
            handleMessage(msg);

            if (!alive) return;
        }
    }

    public abstract void handleMessage(Message msg);
}
