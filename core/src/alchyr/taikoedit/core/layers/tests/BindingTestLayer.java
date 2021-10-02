package alchyr.taikoedit.core.layers.tests;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.input.BoundInputProcessor;
import alchyr.taikoedit.core.layers.LoadedLayer;
import alchyr.taikoedit.core.layers.LoadingLayer;
import alchyr.taikoedit.core.layers.MenuLayer;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class BindingTestLayer extends LoadedLayer implements InputLayer {
    private BindingTestProcessor processor;

    private Texture pixel;
    private Color background = new Color(0.0f, 0.0f, 0.0f, 0.7f);

    public BindingTestLayer()
    {
        processor = new BindingTestProcessor(this);
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

    private boolean exit()
    {
        TaikoEditor.removeLayer(this);
        TaikoEditor.addLayer(MenuLayer.getReturnLoader());
        return true;
    }

    @Override
    public LoadingLayer getLoader() {
        return new LoadingLayer(new String[] {
                "ui"
        }, this, true);
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
    }


    private static class BindingTestProcessor extends BoundInputProcessor
    {
        private final BindingTestLayer source;

        public BindingTestProcessor(BindingTestLayer source)
        {
            super(BindingMaster.getBindingGroup("test"));

            this.source = source;
        }

        @Override
        public void bind() {
            bindings.bind("Exit", source::exit);
        }

        @Override
        public boolean keyTyped(char character) {
            return false;
        }

        @Override
        public boolean scrolled(int amount) {
            return false;
        }
    }
}
