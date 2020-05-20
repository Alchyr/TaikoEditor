package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.entities.components.ComponentMaps;
import alchyr.taikoedit.entities.components.InputComponent;
import alchyr.taikoedit.entities.components.RenderComponent;
import alchyr.taikoedit.core.GameLayer;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.input.EntityInputProcessor;
import alchyr.taikoedit.entities.entitysystems.ControllerUpdateSystem;
import alchyr.taikoedit.entities.entitysystems.UpdateSystem;
import alchyr.taikoedit.util.RenderableEntityList;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class GameplayLayer extends GameLayer implements InputLayer {
    private static Family renderFamily = Family.all(RenderComponent.class).get();

    private Engine engine;

    private EntityInputProcessor processor;
    private RenderableEntityList renderList;

    public GameplayLayer() {
        processor = new EntityInputProcessor();
        renderList = new RenderableEntityList();
        engine = new Engine();

        engine.addSystem(new ControllerUpdateSystem());
        engine.addSystem(new UpdateSystem());

        engine.addEntityListener(renderList);
    }

    public GameplayLayer addEntity(Entity e) {
        engine.addEntity(e);
        return this;
    }

    @Override
    public void initialize() {
        Family inputFamily = Family.all(InputComponent.class).get();

        for (Entity e : engine.getEntitiesFor(inputFamily))
        {
            processor.addInput(ComponentMaps.input.get(e).getInput());
        }
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    @Override
    public void update(float elapsed) {
        engine.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        renderList.render(sb, sr);
    }
}