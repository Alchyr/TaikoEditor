package alchyr.taikoedit.entities;

import alchyr.taikoedit.entities.components.ControllerComponent;
import alchyr.taikoedit.entities.controllers.Controller;
import alchyr.taikoedit.core.scenes.Hitbox;
import alchyr.taikoedit.entities.components.ComponentMaps;
import alchyr.taikoedit.entities.components.PositionComponent;
import alchyr.taikoedit.entities.components.VelocityComponent;
import com.badlogic.gdx.math.Vector2;

public abstract class ControllableEntity extends com.badlogic.ashley.core.Entity {
    public float getX()
    {
        return ComponentMaps.position.get(this).x();
    }
    public float getY()
    {
        return ComponentMaps.position.get(this).y();
    }
    public Vector2 getPosition()
    {
        return ComponentMaps.getPosition(this);
    }
    public Vector2 getVelocity()
    {
        return ComponentMaps.getVelocity(this);
    }

    public Hitbox hb;
    protected Controller c;

    public ControllableEntity()
    {
        this(null);
    }

    public ControllableEntity(Controller c)
    {
        this.c = c;
        if (c != null)
        {
            c.setEntity(this);
            add(new ControllerComponent(c));
        }

        add(new PositionComponent());
        add(new VelocityComponent());
    }

    public Controller getController() { return c; }
}
