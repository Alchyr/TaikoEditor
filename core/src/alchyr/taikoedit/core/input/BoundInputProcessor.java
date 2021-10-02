package alchyr.taikoedit.core.input;

import alchyr.taikoedit.management.bindings.BindingGroup;

public abstract class BoundInputProcessor extends AdjustedInputProcessor {
    protected final BindingGroup bindings;

    public BoundInputProcessor(BindingGroup bindings)
    {
        this.bindings = bindings;
    }

    public void update(float elapsed)
    {
        bindings.update(elapsed);
    }

    @Override
    public boolean keyDown(int keycode) {
        return bindings.receiveKeyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        return bindings.receiveKeyUp(keycode);
    }

    public abstract void bind();

    public void clearInput()
    {
        bindings.clearInput();
    }
}
