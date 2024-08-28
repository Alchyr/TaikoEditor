package alchyr.networking.standard;

import java.io.DataOutputStream;
import java.io.IOException;

public abstract class MessageSender {
    private final Object[] params;

    public MessageSender(Object[] params) {
        this.params = params;
    }

    public final void send(DataOutputStream out) throws IOException {
        send(out, params);
    }

    public abstract void send(DataOutputStream out, Object[] params) throws IOException;
}
