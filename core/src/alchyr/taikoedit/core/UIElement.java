package alchyr.taikoedit.core;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public interface UIElement {
    void update(float elapsed);
    void render(SpriteBatch sb, ShapeRenderer sr);
    void render(SpriteBatch sb, ShapeRenderer sr, float dx, float dy);

    void move(float dx, float dy);
}
