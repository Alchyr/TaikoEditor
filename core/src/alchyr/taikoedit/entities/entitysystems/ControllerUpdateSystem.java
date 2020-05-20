package alchyr.taikoedit.entities.entitysystems;

import alchyr.taikoedit.entities.components.ComponentMaps;
import alchyr.taikoedit.entities.components.ControllerComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;

public class ControllerUpdateSystem extends IteratingSystem {
    public ControllerUpdateSystem() {
        super(Family.all(ControllerComponent.class).get());
    }

    public void processEntity(Entity entity, float deltaTime) {
        ComponentMaps.controller.get(entity).update(deltaTime);
    }
}
