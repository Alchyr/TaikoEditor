package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.input.AdjustedInputProcessor;
import alchyr.taikoedit.core.ui.Button;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.HashMap;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class SettingsLayer extends ProgramLayer implements InputLayer {
    private SettingsProcessor processor;

    //public ArrayList<Button> buttons;

    private ArrayList<Button> mapOptions;
    private HashMap<String, Button> hashedMapOptions;

    private int bgWidth, bgHeight;

    private Texture pixel;
    private int searchHeight, searchY;
    private float searchTextOffsetX, searchTextOffsetY;

    private int displayIndex = 0;
    private int scrollPos = 0;

    private int[] mapOptionX, mapOptionY;

    private static final Color bgColor = new Color(0.3f, 0.3f, 0.25f, 1.0f);


    public SettingsLayer()
    {
        processor = new SettingsProcessor(this);
        this.type = LAYER_TYPE.UPDATE_STOP;

        //buttons = new ArrayList<>();
        mapOptions = new ArrayList<>();
        hashedMapOptions = new HashMap<>();

        initialize();
    }

    @Override
    public void initialize() {
        /*PER_ROW = SettingsMaster.getWidth() / 300; //number of 300 pixel wide tiles that fit.
        PER_COLUMN = SettingsMaster.getHeight() / 200;
        PER_SCREEN = PER_ROW * (PER_COLUMN + 1); //add an extra row for those that poke off the edges of the screen

        OPTION_WIDTH = SettingsMaster.getWidth() / PER_ROW;
        OPTION_HEIGHT = SettingsMaster.getHeight() / PER_COLUMN;*/

        pixel = assetMaster.get("ui:pixel");
    }

    @Override
    public void update(float elapsed) {
        super.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(Color.BLACK);
        sb.draw(pixel, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight());
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    private static class SettingsProcessor extends AdjustedInputProcessor {
        private final SettingsLayer sourceLayer;

        public SettingsProcessor(SettingsLayer source)
        {
            this.sourceLayer = source;
        }

        @Override
        public boolean keyDown(int keycode) {
            switch (keycode)
            {
                case Input.Keys.ESCAPE:
                    TaikoEditor.removeLayer(sourceLayer);
                    return true;
            }
            //sourceLayer.test();
            return true;
        }

        @Override
        public boolean keyUp(int keycode) {
            return true;
        }

        @Override
        public boolean keyTyped(char character) {
            return true;
        }

        @Override
        public boolean onTouchDown(int gameX, int gameY, int pointer, int button) {

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
            /*sourceLayer.scrollPos += amount * 30;

            if (sourceLayer.displayIndex == 0 && sourceLayer.scrollPos < 0)
                sourceLayer.scrollPos = 0;

            while (sourceLayer.scrollPos > OPTION_HEIGHT)
            {
                sourceLayer.scrollPos -= OPTION_HEIGHT;
                sourceLayer.displayIndex += PER_ROW;
            }
            while (sourceLayer.scrollPos < 0)
            {
                sourceLayer.scrollPos += OPTION_HEIGHT;
                sourceLayer.displayIndex -= PER_ROW;
            }*/

            return true;
        }
    }
}