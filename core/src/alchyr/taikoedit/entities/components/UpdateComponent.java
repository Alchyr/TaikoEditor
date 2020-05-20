package alchyr.taikoedit.entities.components;

import alchyr.taikoedit.entities.UpdatingEntity;
import com.badlogic.ashley.core.Component;

public class UpdateComponent implements Component {
    private UpdatingEntity entity;

    public UpdateComponent(UpdatingEntity e)
    {
        this.entity = e;
    }
    //A void method that updates the entity based on elapsed time.
    public void update(float elapsed)
    {
        entity.update(elapsed);
    }
}
