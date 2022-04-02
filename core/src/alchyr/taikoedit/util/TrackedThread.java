package alchyr.taikoedit.util;

import java.util.function.Supplier;

public class TrackedThread extends Thread {
    private final Supplier<Float> tracker;

    public TrackedThread(Runnable r, Supplier<Float> tracker) {
        super(r);

        this.tracker = tracker;
    }

    public float getProgress() {
        return tracker.get();
    }
}
