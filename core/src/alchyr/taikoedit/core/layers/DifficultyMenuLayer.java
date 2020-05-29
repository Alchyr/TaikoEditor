package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.GameLayer;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.input.AdjustedInputProcessor;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class DifficultyMenuLayer extends GameLayer implements InputLayer {
    private static DifficultyMenuProcessor processor;

    //Rendering
    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.75f);

    //Textures
    private Texture pix;

    public DifficultyMenuLayer(EditorLayer editor)
    {
        this.type = LAYER_TYPE.UPDATE_STOP;

        processor = new DifficultyMenuProcessor(this);
        pix = assetMaster.get("ui:pixel");
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight());
    }

    private void close()
    {
        TaikoEditor.removeLayer(this);
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    private static class DifficultyMenuProcessor extends AdjustedInputProcessor {
        private final DifficultyMenuLayer sourceLayer;

        public DifficultyMenuProcessor(DifficultyMenuLayer source)
        {
            this.sourceLayer = source;
        }

        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) //Process input using the current primary view? TODO: Add keybindings? SettingsMaster has some keybind code.
            {
                case Input.Keys.ESCAPE:
                    sourceLayer.close();
                    return true;
                case Input.Keys.RIGHT:
                    break;
            }
            return true;
        }

        @Override
        public boolean keyUp(int keycode) {
            return true;
        }

        @Override
        public boolean keyTyped(char character) {
            /*if (sourceLayer.searchInput.keyTyped(character)) {
                sourceLayer.mapOptions.clear();

                if (sourceLayer.searchInput.text.isEmpty()) {
                    for (String key : MapMaster.mapDatabase.keys) {
                        sourceLayer.mapOptions.add(sourceLayer.hashedMapOptions.get(key));
                    }
                }
                else {
                    MapMaster.search(sourceLayer.searchInput.text).forEach((m)->{
                        sourceLayer.mapOptions.add(sourceLayer.hashedMapOptions.get(m.key));
                    });
                }
                return true;
            }*/

            return true;
        }

        @Override
        public boolean onTouchDown(int gameX, int gameY, int pointer, int button) {
            if (button != 0 && button != 1)
                return true; //i only care about left and right click.

            return true;
        }

        @Override
        public boolean onTouchUp(int gameX, int gameY, int pointer, int button) {
            return true;
        }

        @Override
        public boolean onTouchDragged(int gameX, int gameY, int pointer) {
            return true;
        }

        @Override
        public boolean onMouseMoved(int gameX, int gameY) {
            return true;
        }

        @Override
        public boolean scrolled(int amount) {
            return true;
        }
    }
}
