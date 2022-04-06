package alchyr.taikoedit.core.input;

import alchyr.taikoedit.util.interfaces.functional.VoidMethod;

import java.util.HashSet;

public class KeyHoldObject {
    private VoidMethod onRepeat;

    private final float firstRepeatDelay;
    private final float repeatDelay;
    private float delay;

    public KeyHoldObject(float firstRepeatDelay, float repeatDelay, VoidMethod onRepeat)
    {
        this.onRepeat = onRepeat;

        this.delay = this.firstRepeatDelay = firstRepeatDelay;
        this.repeatDelay = repeatDelay;
    }

    public KeyHoldObject(float repeatDelay, VoidMethod onRepeat)
    {
        this(repeatDelay, repeatDelay, onRepeat);
    }

    public void setOnRepeat(VoidMethod onRepeat) {
        this.onRepeat = onRepeat;
    }

    public void reset()
    {
        this.delay = firstRepeatDelay;
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
            onRepeat.run();
    }
}
