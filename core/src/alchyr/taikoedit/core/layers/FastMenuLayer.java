package alchyr.taikoedit.core.layers;


import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class FastMenuLayer extends LoadedLayer {
    public FastMenuLayer() {
        this.type = LAYER_TYPE.NORMAL;
    }

    @Override
    public void update(float elapsed) {
        super.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {

    }

    @Override
    public LoadingLayer getLoader() {
        return new EditorLoadingLayer()
                .loadLists("ui", "font", "hitsound")
                .addLayers(true, this);
    }
}
