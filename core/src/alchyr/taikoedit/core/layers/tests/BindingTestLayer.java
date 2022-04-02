package alchyr.taikoedit.core.layers.tests;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.input.BoundInputProcessor;
import alchyr.taikoedit.core.layers.LoadedLayer;
import alchyr.taikoedit.core.layers.LoadingLayer;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.end;

public class BindingTestLayer extends LoadedLayer implements InputLayer {
    private BindingTestProcessor processor;

    private Texture pixel;
    private Color background = new Color(0.0f, 0.0f, 0.0f, 0.7f);

    public BindingTestLayer()
    {
        processor = new BindingTestProcessor(this);
    }

    @Override
    public void update(float elapsed) {
        processor.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(background);
        sb.draw(pixel, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight());
    }

    @Override
    public void initialize() {
        processor.bind();

        pixel = assetMaster.get("ui:pixel");
    }

    private void exit()
    {
        TaikoEditor.removeLayer(this);
        end();
    }

    @Override
    public LoadingLayer getLoader() {
        return new LoadingLayer()
                .loadLists("ui")
                .addLayers(true, this);
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    @Override
    public void dispose() {
        super.dispose();
        processor.dispose();
    }

    private static class BindingTestProcessor extends BoundInputProcessor
    {
        private final BindingTestLayer source;

        public BindingTestProcessor(BindingTestLayer source)
        {
            super(BindingMaster.getBindingGroupCopy("test"), true);

            this.source = source;
        }

        @Override
        public void bind() {
            bindings.bind("Exit", source::exit);
        }
    }
}
