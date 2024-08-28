package alchyr.taikoedit.util;

import alchyr.taikoedit.util.interfaces.functional.VoidMethod;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;

import java.util.ArrayList;
import java.util.List;

public class EventWindowListener extends Lwjgl3WindowAdapter {
    private List<VoidMethod>[] listeners;

    public static final int FOCUS_GAIN = 0;
    public static final int FOCUS_LOST = 1;
    public static final int ICONIFIED = 2;
    public static final int MAXIMIZED = 3;

    public EventWindowListener() {
        listeners = new List[] {
                new ArrayList<VoidMethod>(),
                new ArrayList<VoidMethod>(),
                new ArrayList<VoidMethod>(),
                new ArrayList<VoidMethod>()
        };
    }

    public void listen(int eventKey, VoidMethod receiver) {
        if (eventKey >= 0 && eventKey < listeners.length) {
            listeners[eventKey].add(receiver);
        }
    }

    private void triggerEvent(int key) {
        for (VoidMethod m : listeners[key]) {
            m.run();
        }
    }

    @Override
    public void created(Lwjgl3Window window) {
        if (window.getListener() instanceof WindowEventReceiver) {
            ((WindowEventReceiver) window.getListener()).subscribe(this);
        }
    }

    @Override
    public void focusGained() {
        triggerEvent(FOCUS_GAIN);
    }

    @Override
    public void focusLost() {
        triggerEvent(FOCUS_LOST);
    }

    @Override
    public void iconified(boolean isIconified) {
        triggerEvent(ICONIFIED);
    }

    @Override
    public void maximized(boolean isMaximized) {
        triggerEvent(MAXIMIZED);
    }

    public interface WindowEventReceiver {
        void subscribe(EventWindowListener eventWindowListener);
    }
}
