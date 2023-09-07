package alchyr.taikoedit.util;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class TrackedThread extends Thread {
    private final Supplier<Float> tracker;
    private Consumer<TrackedThread> followup;
    final Supplier<Boolean> success;

    public TrackedThread(Runnable r, Supplier<Float> tracker, Supplier<Boolean> success) {
        super(r);

        this.tracker = tracker;
        this.success = success;
    }

    public float getProgress() {
        return tracker.get();
    }

    @Override
    public void run() {
        super.run();

        if (this.followup != null) {
            this.followup.accept(this);
        }
    }

    public void setFollowup(Consumer<TrackedThread> r, boolean started) {
        if (started) {
            if (isAlive()) {
                this.followup = r;
            }
            else if (r != null) {
                r.accept(this);
            }
        }
        else {
            this.followup = r;
        }
    }

    public boolean success() {
        return success.get();
    }
}
