package alchyr.taikoedit.core.input;

import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.InputProcessor;

public abstract class AdjustedInputProcessor implements InputProcessor {
    public static final float NORMAL_FIRST_DELAY = 0.4f;
    public static final float NORMAL_REPEAT_DELAY = 0.08f;

    @Deprecated
    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return onTouchUp(screenX, SettingsMaster.getHeight() - screenY, pointer, button);
    }

    public boolean onTouchUp(int gameX, int gameY, int pointer, int button)
    {
        return false;
    }

    @Deprecated
    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return onTouchDown(screenX, SettingsMaster.getHeight() - screenY, pointer, button);
    }

    public boolean onTouchDown(int gameX, int gameY, int pointer, int button)
    {
        return false;
    }

    @Deprecated
    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return onTouchDragged(screenX, SettingsMaster.getHeight() - screenY, pointer);
    }

    public boolean onTouchDragged(int gameX, int gameY, int pointer)
    {
        return false;
    }

    @Deprecated
    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return onMouseMoved(screenX, SettingsMaster.getHeight() - screenY);
    }

    public boolean onMouseMoved(int gameX, int gameY)
    {
        return false;
    }
}
