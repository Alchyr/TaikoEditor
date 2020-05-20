package alchyr.taikoedit.entities.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;

public class PositionComponent implements Component {
    public Vector2 position = new Vector2(0, 0);

    public float x()
    {
        return position.x;
    }

    public float y()
    {
        return position.y;
    }
}
