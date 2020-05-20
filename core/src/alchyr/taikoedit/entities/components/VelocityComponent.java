package alchyr.taikoedit.entities.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;

public class VelocityComponent implements Component {
    public Vector2 velocity = new Vector2(0, 0);

    public float x()
    {
        return velocity.x;
    }

    public float y()
    {
        return velocity.y;
    }
}
