package alchyr.taikoedit.core.input;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.input.entityinput.EntityInput;
import com.badlogic.gdx.Input;

import java.util.ArrayList;

public class EntityInputProcessor extends AdjustedInputProcessor {
    private final ArrayList<EntityInput> keyDownInputs;
    private final ArrayList<EntityInput> keyUpInputs;
    private final ArrayList<EntityInput> keyTypedInputs;
    private final ArrayList<EntityInput> touchDownInputs;
    private final ArrayList<EntityInput> touchUpInputs;
    private final ArrayList<EntityInput> touchDraggedInputs;
    private final ArrayList<EntityInput> mouseMovedInputs;
    private final ArrayList<EntityInput> scrolledInputs;

    public EntityInputProcessor() {
        keyDownInputs = new ArrayList<>();
        keyUpInputs = new ArrayList<>();
        keyTypedInputs = new ArrayList<>();
        touchDownInputs = new ArrayList<>();
        touchUpInputs = new ArrayList<>();
        touchDraggedInputs = new ArrayList<>();
        mouseMovedInputs = new ArrayList<>();
        scrolledInputs = new ArrayList<>();
    }

    public void addInput(EntityInput input)
    {
        if (input != null)
        {
            int flags = input.getInputType();

            if ((flags & EntityInput.KEYDOWN) != 0)
                keyDownInputs.add(input);

            if ((flags & EntityInput.KEYUP) != 0)
                keyUpInputs.add(input);

            if ((flags & EntityInput.KEYTYPED) != 0)
                keyTypedInputs.add(input);

            if ((flags & EntityInput.TOUCHDOWN) != 0)
                touchDownInputs.add(input);

            if ((flags & EntityInput.TOUCHUP) != 0)
                touchUpInputs.add(input);

            if ((flags & EntityInput.TOUCHDRAGGED) != 0)
                touchDraggedInputs.add(input);

            if ((flags & EntityInput.MOUSEMOVED) != 0)
                mouseMovedInputs.add(input);

            if ((flags & EntityInput.SCROLLED) != 0)
                scrolledInputs.add(input);
        }
    }


    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE)
        {
            TaikoEditor.end();
            return false;
        }
        for (EntityInput i : keyDownInputs)
        {
            if (i.keyDown(keycode))
                return true;
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        for (EntityInput i : keyUpInputs)
        {
            if (i.keyUp(keycode))
                return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        for (EntityInput i : keyTypedInputs)
        {
            if (i.keyTyped(character))
                return true;
        }
        return false;
    }

    @Override
    public boolean onTouchDown(int gameX, int gameY, int pointer, int button) {
        for (EntityInput i : touchDownInputs)
        {
            if (i.onTouchDown(gameX, gameY, pointer, button))
                return true;
        }
        return false;
    }

    @Override
    public boolean onTouchUp(int gameX, int gameY, int pointer, int button) {
        for (EntityInput i : touchUpInputs)
        {
            if (i.onTouchUp(gameX, gameY, pointer, button))
                return true;
        }
        return false;
    }

    @Override
    public boolean onTouchDragged(int gameX, int gameY, int pointer) {
        for (EntityInput i : touchDraggedInputs)
        {
            if (i.touchDragged(gameX, gameY, pointer))
                return true;
        }
        return false;
    }

    @Override
    public boolean onMouseMoved(int gameX, int gameY) {
        for (EntityInput i : mouseMovedInputs)
        {
            if (i.mouseMoved(gameX, gameY))
                return true;
        }
        return false;
    }

    @Override
    public boolean scrolled(int amount) {
        for (EntityInput i : scrolledInputs)
        {
            if (i.scrolled(amount))
                return true;
        }
        return false;
    }
}
