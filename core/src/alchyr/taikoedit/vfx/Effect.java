package alchyr.taikoedit.vfx;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public abstract class Effect {
    public boolean isDone;

    public Effect()
    {
        isDone = false;
    }

    public abstract void update(float elapsed);
    public abstract void render(SpriteBatch sb, ShapeRenderer sr);
}
