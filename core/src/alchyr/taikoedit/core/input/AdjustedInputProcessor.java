package alchyr.taikoedit.core.input;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector2;

import java.util.Arrays;

public abstract class AdjustedInputProcessor implements InputProcessor {
    public static final float NORMAL_FIRST_DELAY = 0.4f;
    public static final float NORMAL_REPEAT_DELAY = 0.08f;
    public static final float DOUBLE_CLICK_TIME = 0.4f;
    public static final float DOUBLE_CLICK_DIST = 7 * 7; //compared to squared dist, so just 7

    private final int max;
    private final int[] lastX, lastY;
    private final double[] lastTime;

    public AdjustedInputProcessor() {
        max = Gdx.input.getMaxPointers();
        lastX = new int[max];
        lastY = new int[max];
        lastTime = new double[max];
        Arrays.fill(lastX, 0);
        Arrays.fill(lastY, 0);
        Arrays.fill(lastTime, Double.MIN_VALUE);
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return touchUp(screenX, screenY, pointer, button);
    }

    @Override
    public final boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return onTouchUp(screenX, (int)SettingsMaster.screenToGameY(screenY), pointer, button);
    }

    public boolean onTouchUp(int gameX, int gameY, int pointer, int button)
    {
        return false;
    }

    @Override
    public final boolean touchDown(int screenX, int screenY, int pointer, int button) {
        int gameY = (int)SettingsMaster.screenToGameY(screenY);
        if (button >= max)
            return onTouchDown(screenX, gameY, pointer, button);

        double time = TaikoEditor.getTime();
        boolean doubleClicked = false;
        if (lastTime[button] > time - DOUBLE_CLICK_TIME) {
            if (Vector2.dst2(lastX[button], lastY[button], screenX, gameY) < DOUBLE_CLICK_DIST) {
                //double click
                doubleClicked = onDoubleClick(screenX, gameY, pointer, button);
            }
            lastTime[button] = Double.MIN_VALUE; //Can't double click twice in a row
        }
        else {
            lastTime[button] = time;
        }
        lastX[button] = screenX;
        lastY[button] = gameY;
        if (!doubleClicked) {
            return onTouchDown(screenX, gameY, pointer, button);
        }
        return true;
    }

    public boolean onTouchDown(int gameX, int gameY, int pointer, int button)
    {
        return false;
    }
    public boolean onDoubleClick(int gameX, int gameY, int pointer, int button)
    {
        return false;
    }

    @Override
    public final boolean touchDragged(int screenX, int screenY, int pointer) {
        return onTouchDragged(screenX, (int)SettingsMaster.screenToGameY(screenY), pointer);
    }

    public boolean onTouchDragged(int gameX, int gameY, int pointer)
    {
        return false;
    }

    @Override
    public final boolean mouseMoved(int screenX, int screenY) {
        return onMouseMoved(screenX, (int)SettingsMaster.screenToGameY(screenY));
    }

    public boolean onMouseMoved(int gameX, int gameY)
    {
        return false;
    }
}
