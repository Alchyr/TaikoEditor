package alchyr.taikoedit.core;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public interface UIElement {
    void update();
    void render(SpriteBatch sb, ShapeRenderer sr);
}
