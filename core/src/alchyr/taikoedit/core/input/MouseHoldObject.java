package alchyr.taikoedit.core.input;

import java.util.function.BiConsumer;

public class MouseHoldObject {
    public static final MouseHoldObject nothing = new MouseHoldObject(null, null);

    private final BiConsumer<Float, Float> onRelease;
    private final BiConsumer<Float, Float> onDrag;

    //onRelease returns whether or not the touch release event should be consumed.
    public MouseHoldObject(BiConsumer<Float, Float> onDrag, BiConsumer<Float, Float> onRelease)
    {
        this.onRelease = onRelease;
        this.onDrag = onDrag;
    }

    public void onRelease(float x, float y)
    {
        if (onRelease != null)
            onRelease.accept(x, y);
    }

    public void onDrag(float x, float y)
    {
        if (onDrag != null)
            onDrag.accept(x, y);
    }

    public void update(float elapsed)
    {

    }
}
