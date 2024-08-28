package alchyr.networking.standard;

import java.io.DataInputStream;
import java.io.IOException;

public interface MessageReceiver {
    Message receiveMessage(DataInputStream source) throws IOException;
}
