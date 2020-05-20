package alchyr.taikoedit.util;

import alchyr.taikoedit.entities.components.RenderComponent;
import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.HashMap;

public class RenderableEntityList implements EntityListener {
    private final ArrayList<Entity> renderableEntities = new ArrayList<>();
    private final HashMap<Entity, ComponentMapper<? extends RenderComponent>> renderComponentMap = new HashMap<>();

    public void render(SpriteBatch sb, ShapeRenderer sr)
    {
        for (Entity e : renderableEntities)
            renderComponentMap.get(e).get(e).render(sb, sr);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void entityAdded(Entity entity) {
        for (Component c : entity.getComponents())
        {
            if (c instanceof RenderComponent)
            {
                renderableEntities.add(entity);
                ComponentMapper<?> mapper = ComponentMapper.getFor(c.getClass());
                renderComponentMap.put(entity, (ComponentMapper<? extends RenderComponent>) mapper);
            }
        }
    }

    @Override
    public void entityRemoved(Entity entity) {
        renderableEntities.remove(entity);
        renderComponentMap.remove(entity);
    }
}
