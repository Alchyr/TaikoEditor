package alchyr.taikoedit.core;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public abstract class ProgramLayer {
    public LAYER_TYPE type = LAYER_TYPE.NORMAL;

    public void initialize()
    {

    }

    public void update(float elapsed)
    {

    }

    public void render(SpriteBatch sb, ShapeRenderer sr)
    {

    }

    public void dispose()
    {

    }

    public enum LAYER_TYPE {
        NORMAL,
        UPDATE_STOP,
        RENDER_STOP,
        FULL_STOP
    }
}
