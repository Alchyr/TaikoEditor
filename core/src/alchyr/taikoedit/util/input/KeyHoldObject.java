package alchyr.taikoedit.util.input;

import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Function;

public class KeyHoldObject {
    public HashSet<Integer> conflictingKeys = new HashSet<>();

    public final int keycode;

    private final Function<Integer, Boolean> onRelease;
    private final Consumer<Integer> onRepeat;

    private final float firstRepeatDelay;
    private final float repeatDelay;
    private float delay;

    public KeyHoldObject(int keycode, float firstRepeatDelay, float repeatDelay, Consumer<Integer> onRepeat, Function<Integer, Boolean> onRelease)
    {
        this.keycode = keycode;

        this.onRelease = onRelease;
        this.onRepeat = onRepeat;

        this.delay = this.firstRepeatDelay = firstRepeatDelay;
        this.repeatDelay = repeatDelay;
    }

    public KeyHoldObject(int keycode, float repeatDelay, Consumer<Integer> onRepeat, Function<Integer, Boolean> onRelease)
    {
        this(keycode, repeatDelay, repeatDelay, onRepeat, onRelease);
    }

    public void reset()
    {
        this.delay = firstRepeatDelay;
    }

    public KeyHoldObject addConflictingKey(int keycode)
    {
        conflictingKeys.add(keycode);
        return this;
    }

    //Should be called each frame.
    public void update(float elapsed)
    {
        this.delay -= elapsed;
        while (delay < 0) {
            delay += repeatDelay;
            onRepeat();
        }
    }

    public boolean onRelease() //called upon keyUp
    {
        if (onRelease != null)
            return onRelease.apply(keycode);
        return false;
    }

    private void onRepeat()
    {
        if (onRepeat != null)
            onRepeat.accept(keycode);
    }
}
