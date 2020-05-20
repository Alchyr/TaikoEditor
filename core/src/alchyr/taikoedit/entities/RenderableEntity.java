package alchyr.taikoedit.entities;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public interface RenderableEntity {
    void render(SpriteBatch sb, ShapeRenderer sr);
}
