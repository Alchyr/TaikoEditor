package alchyr.networking.standard;

public class Message {
    public static final int UTF = 0x01;
    public static final int FILE = 0x02;

    public static Message EMPTY = new Message(UTF, "");

    public final int identifier;
    public final Object[] contents;

    public Message(int identifier, Object... contents) {
        this.identifier = identifier;
        this.contents = contents;
    }
}
