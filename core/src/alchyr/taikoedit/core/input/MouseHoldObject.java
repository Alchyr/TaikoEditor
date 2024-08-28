package alchyr.taikoedit.core.input;

import java.util.function.BiConsumer;

/**
 * Instances of this class should NOT be reused unless the release event is always null.
 */
public class MouseHoldObject {
    public static final MouseHoldObject nothing = new MouseHoldObject(null, null);

    protected BiConsumer<Float, Float> onRelease;
    protected BiConsumer<Float, Float> onDrag;

    //onRelease returns whether or not the touch release event should be consumed.
    public MouseHoldObject(BiConsumer<Float, Float> onDrag, BiConsumer<Float, Float> onRelease)
    {
        this.onRelease = onRelease;
        this.onDrag = onDrag;
    }

    public final void onRelease(float x, float y)
    {
        if (onRelease != null) {
            onRelease.accept(x, y);
            onRelease = null; //Guarantee duplicate release events will not occur
        }
    }

    public final void onDrag(float x, float y)
    {
        if (onDrag != null)
            onDrag.accept(x, y);
    }

    public void update(float elapsed)
    {

    }
}
