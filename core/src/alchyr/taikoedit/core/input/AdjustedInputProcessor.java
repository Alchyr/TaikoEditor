package alchyr.taikoedit.core.input;

import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.InputProcessor;

public abstract class AdjustedInputProcessor implements InputProcessor {
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
}
