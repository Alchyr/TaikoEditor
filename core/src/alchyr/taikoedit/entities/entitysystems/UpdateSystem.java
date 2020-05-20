package alchyr.taikoedit.entities.entitysystems;

import alchyr.taikoedit.entities.components.ComponentMaps;
import alchyr.taikoedit.entities.components.UpdateComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;

public class UpdateSystem extends IteratingSystem {
    public UpdateSystem() {
        super(Family.all(UpdateComponent.class).get(), 1);
    }

    public void processEntity(Entity entity, float deltaTime) {
        ComponentMaps.update.get(entity).update(deltaTime);
    }
}
