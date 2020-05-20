package alchyr.taikoedit.entities.components;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;

public class ComponentMaps {
    public static final ComponentMapper<PositionComponent> position = ComponentMapper.getFor(PositionComponent.class);
    public static final ComponentMapper<VelocityComponent> velocity = ComponentMapper.getFor(VelocityComponent.class);
    public static final ComponentMapper<ControllerComponent> controller = ComponentMapper.getFor(ControllerComponent.class);
    public static final ComponentMapper<InputComponent> input = ComponentMapper.getFor(InputComponent.class);
    public static final ComponentMapper<UpdateComponent> update = ComponentMapper.getFor(UpdateComponent.class);
    public static final ComponentMapper<RenderComponent> render = ComponentMapper.getFor(RenderComponent.class);

    public static Vector2 getVelocity(Entity e)
    {
        return velocity.get(e).velocity;
    }

    public static Vector2 getPosition(Entity e)
    {
        return position.get(e).position;
    }
}
