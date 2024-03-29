package alchyr.taikoedit.core.input;

import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.util.interfaces.functional.VoidMethod;

//Requires update to be called and for bind to be called after everything it references is initialized.
public abstract class BoundInputProcessor extends AdjustedInputProcessor {
    protected final BindingGroup bindings;
    protected boolean defaultReturn;

    public BoundInputProcessor(BindingGroup bindings, boolean defaultReturn)
    {
        this.bindings = bindings;
        this.defaultReturn = defaultReturn;
    }

    public void queue(VoidMethod action) {
        bindings.queue(action);
    }

    public void cancelMouseHold(MouseHoldObject obj)
    {
        bindings.cancelMouseHold(obj);
    }
    public void releaseMouse(boolean safe) {
        bindings.releaseMouse(safe);
    }

    public void update(float elapsed)
    {
        bindings.update(elapsed);
    }

    @Override
    public boolean keyDown(int keycode) {
        return bindings.receiveKeyDown(keycode) || defaultReturn;
    }

    @Override
    public boolean keyUp(int keycode) {
        return bindings.receiveKeyUp(keycode) || defaultReturn;
    }

    @Override
    public boolean keyTyped(char character) {
        return defaultReturn;
    }

    @Override
    public boolean onTouchDown(int gameX, int gameY, int pointer, int button) {
        return bindings.receiveTouchDown(gameX, gameY, button) || defaultReturn;
    }
    @Override
    public boolean onDoubleClick(int gameX, int gameY, int pointer, int button) {
        return bindings.receiveDoubleClick(gameX, gameY, button); //no default return as it will move onto onTouchDown if false
    }

    @Override
    public boolean onTouchUp(int gameX, int gameY, int pointer, int button) {
        return bindings.receiveTouchUp(gameX, gameY, button) || defaultReturn;
    }

    @Override
    public boolean onTouchDragged(int gameX, int gameY, int pointer) {
        return bindings.receiveTouchDragged(gameX, gameY) || defaultReturn;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return defaultReturn;
    }

    public abstract void bind();

    public void releaseInput(boolean safe)
    {
        bindings.releaseInput(safe);
    }

    public void dispose() {
        BindingMaster.releaseCopy(bindings);
    }

    public String getBindingInput(String[] keyNameArray, String key) {
        if (bindings == null)
            return null;
        InputBinding input = bindings.getBindings().get(key);
        if (input == null || input.getInputs().isEmpty())
            return null;
        return input.getInputs().get(0).toString(keyNameArray);
    }
}
