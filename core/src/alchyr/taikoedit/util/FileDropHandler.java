package alchyr.taikoedit.util;

public class FileDropHandler {
    private static Handler activeHandler = null;
    public static void receive(String[] files) {
        if (activeHandler != null)
            activeHandler.receiveFiles(files);
    }

    public static void remove(Handler h) {
        if (activeHandler == h)
            activeHandler = null;
    }
    public static void set(Handler h) {
        activeHandler = h;
    }

    public interface Handler {
        void receiveFiles(String[] files);
    }
}
