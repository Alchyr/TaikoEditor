package alchyr.taikoedit.util.input;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class MouseHoldObject {
    private final BiFunction<Integer, Integer, Boolean> onRelease;
    private final BiConsumer<Integer, Integer> onDrag;

    //onRelease returns whether or not the touch release event should be consumed.
    public MouseHoldObject(BiConsumer<Integer, Integer> onDrag, BiFunction<Integer, Integer, Boolean> onRelease)
    {
        this.onRelease = onRelease;
        this.onDrag = onDrag;
    }

    public boolean onRelease(int x, int y)
    {
        if (onRelease != null)
            return onRelease.apply(x, y);
        return false;
    }

    public void onDrag(int x, int y)
    {
        if (onDrag != null)
            onDrag.accept(x, y);
    }
}
