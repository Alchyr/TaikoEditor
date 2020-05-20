package alchyr.taikoedit.entities.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;

public abstract class RenderComponent implements Component {
    public Vector2 position = new Vector2(0, 0);

    public float x()
    {
        return position.x;
    }

    public abstract void render(SpriteBatch sb, ShapeRenderer sr);
}
