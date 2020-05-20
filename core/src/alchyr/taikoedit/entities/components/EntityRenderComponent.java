package alchyr.taikoedit.entities.components;

import alchyr.taikoedit.entities.RenderableEntity;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class EntityRenderComponent extends RenderComponent {
    private RenderableEntity entity;

    public EntityRenderComponent(RenderableEntity e)
    {
        this.entity = e;
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        entity.render(sb, sr);
    }
}
