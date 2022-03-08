package alchyr.taikoedit.core.input;

import java.util.HashSet;
import java.util.function.Consumer;

public class KeyHoldObject {
    public HashSet<Integer> conflictingKeys = new HashSet<>();

    public final int keycode;

    private final Consumer<Integer> onRepeat;

    private final float firstRepeatDelay;
    private final float repeatDelay;
    private float delay;

    public KeyHoldObject(int keycode, float firstRepeatDelay, float repeatDelay, Consumer<Integer> onRepeat)
    {
        this.keycode = keycode;

        this.onRepeat = onRepeat;

        this.delay = this.firstRepeatDelay = firstRepeatDelay;
        this.repeatDelay = repeatDelay;
    }

    public KeyHoldObject(int keycode, float repeatDelay, Consumer<Integer> onRepeat)
    {
        this(keycode, repeatDelay, repeatDelay, onRepeat);
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
        if (delay < 0) { //even if update is occurring with a wide gap, only trigger onRepeat once to avoid making it even worse
            delay = repeatDelay;
            onRepeat();
        }
    }

    private void onRepeat()
    {
        if (onRepeat != null)
            onRepeat.accept(keycode);
    }
}
