package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.input.AdjustedInputProcessor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.input.sub.TextInput;
import alchyr.taikoedit.core.layers.sub.ConfirmationLayer;
import alchyr.taikoedit.core.layers.tests.BindingTestLayer;
import alchyr.taikoedit.core.ui.Button;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.management.MapMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.Mapset;
import alchyr.taikoedit.util.assets.loaders.OsuBackgroundLoader;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;

import static alchyr.taikoedit.TaikoEditor.*;

public class MenuLayer extends LoadedLayer implements InputLayer {
    public static LoadedLayer menu;

    private static int OPTION_WIDTH, OPTION_HEIGHT;

    private static int PER_ROW;
    private static int PER_COLUMN;
    private static int PER_SCREEN;


    private boolean initialized;

    private MenuProcessor processor;

    //public ArrayList<Button> buttons;

    private ArrayList<Button> mapOptions;
    private HashMap<String, Button> hashedMapOptions;

    private Texture background;
    private int bgWidth, bgHeight;

    private Texture pixel;
    private int searchHeight, searchY;
    private float searchTextOffsetX, searchTextOffsetY;

    private TextInput searchInput;

    private ImageButton exitButton;
    private ImageButton settingsButton;

    private int displayIndex = 0;
    private int scrollPos = 0;

    private int[] mapOptionX, mapOptionY;

    private static final Color bgColor = new Color(0.3f, 0.3f, 0.25f, 1.0f);


    public MenuLayer()
    {
        processor = new MenuProcessor(this);
        this.type = LAYER_TYPE.FULL_STOP;

        //buttons = new ArrayList<>();
        mapOptions = new ArrayList<>();
        hashedMapOptions = new HashMap<>();

        initialized = false;
    }

    @Override
    public void initialize() {
        if (!initialized)
        {
            PER_ROW = SettingsMaster.getWidth() / 300; //number of 300 pixel wide tiles that fit.
            PER_COLUMN = SettingsMaster.getHeight() / 200;
            PER_SCREEN = PER_ROW * (PER_COLUMN + 1); //add an extra row for those that poke off the edges of the screen

            OPTION_WIDTH = SettingsMaster.getWidth() / PER_ROW;
            OPTION_HEIGHT = SettingsMaster.getHeight() / PER_COLUMN;


            if (!OsuBackgroundLoader.loadedBackgrounds.isEmpty())
            {
                background = assetMaster.get(OsuBackgroundLoader.loadedBackgrounds.get(MathUtils.random(OsuBackgroundLoader.loadedBackgrounds.size() - 1)));
            }
            else
            {
                background = assetMaster.get("menu:background");
            }

            float bgScale = Math.max((float) SettingsMaster.getWidth()/ background.getWidth(), (float) SettingsMaster.getHeight() / background.getHeight());
            bgWidth = (int) Math.ceil(background.getWidth() * bgScale);
            bgHeight = (int) Math.ceil(background.getHeight() * bgScale);

            pixel = assetMaster.get("ui:pixel");
            searchHeight = 40;
            searchY = SettingsMaster.getHeight() - searchHeight;
            searchTextOffsetX = 10;
            searchTextOffsetY = 35;

            for (String setFile : MapMaster.mapDatabase.keys)
            {
                Mapset set = MapMaster.mapDatabase.mapsets.get(setFile);

                Button setButton = new Button(0, 0, OPTION_WIDTH, OPTION_HEIGHT, set.getShortArtist(OPTION_WIDTH * 0.9f, assetMaster.getFont("default")) + '\n' + set.getShortTitle(OPTION_WIDTH * 0.9f, assetMaster.getFont("default")) + '\n' + set.getShortCreator(OPTION_WIDTH * 0.9f, assetMaster.getFont("default")), null);
                setButton.setAction(setFile);

                mapOptions.add(setButton);
                hashedMapOptions.put(setFile, setButton);
            }

            mapOptionX = new int[PER_ROW];
            for (int i = 0; i < PER_ROW; ++i)
            {
                mapOptionX[i] = OPTION_WIDTH * i;
            }

            mapOptionY = new int[PER_COLUMN + 1];
            for (int i = 0; i < PER_COLUMN + 1; ++i)
            {
                mapOptionY[i] = OPTION_HEIGHT * (PER_COLUMN - 1 - i) - searchHeight;
            }

            searchInput = new TextInput(128, assetMaster.getFont("default"));

            exitButton = new ImageButton(SettingsMaster.getWidth() - 40, SettingsMaster.getHeight() - 40, assetMaster.get("ui:exit"), (Texture) assetMaster.get("ui:exith"), this::quit);
            settingsButton = new ImageButton(SettingsMaster.getWidth() - 80, SettingsMaster.getHeight() - 40, assetMaster.get("ui:settings"), (Texture) assetMaster.get("ui:settingsh"), this::settings);

            initialized = true;
        }
    }

    @Override
    public void update(float elapsed) {
        super.update(elapsed);

        for (int i = displayIndex; i < displayIndex + PER_SCREEN && i < mapOptions.size(); ++i)
        {
            mapOptions.get(i).update();
        }

        exitButton.update();
        settingsButton.update();
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(bgColor);
        sb.draw(background, 0, 0, bgWidth, bgHeight);

        //for (Button b : buttons)
            //b.render(sb, sr);

        int x = 0, y = 0;

        for (int i = displayIndex; i < displayIndex + PER_SCREEN && i < mapOptions.size(); ++i)
        {
            mapOptions.get(i).render(sb, sr, mapOptionX[x], mapOptionY[y] + scrollPos);
            ++x;
            if (x >= mapOptionX.length)
            {
                x -= mapOptionX.length;
                ++y;
                if (y >= mapOptionY.length)
                    break;
            }
        }

        sb.setColor(Color.BLACK);
        sb.draw(pixel, 0, searchY, SettingsMaster.getWidth(), searchHeight);

        sb.setColor(Color.WHITE);
        searchInput.render(sb, searchTextOffsetX, searchY + searchTextOffsetY);

        settingsButton.render(sb, sr);
        exitButton.render(sb, sr);
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    private void chooseMap(String key)
    {
        EditorLayer edit = new EditorLayer(MapMaster.mapDatabase.mapsets.get(key));

        TaikoEditor.removeLayer(this);
        TaikoEditor.addLayer(edit.getLoader());
    }

    private void test()
    {
        TaikoEditor.removeLayer(this);
        TaikoEditor.addLayer(new BindingTestLayer().getLoader());
    }

    private void settings(int button)
    {
        editorLogger.trace("Settings!");

        TaikoEditor.addLayer(new SettingsLayer());
    }
    private void quit(int button)
    {
        TaikoEditor.addLayer(new ConfirmationLayer("Exit?", "Yes", "No").onConfirm(TaikoEditor::end).onCancel(TaikoEditor::end));
        //editorLogger.trace("Goodbye!");
        /*int result = JOptionPane.showConfirmDialog(null, "Exit?", "Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (result == 0) {

        }*/
        //TaikoEditor.end();
    }

    @Override
    public LoadingLayer getLoader() {
        menu = this;
        return new LoadingLayer(new String[] {
                "ui",
                "font",
                "background",
                "menu",
                "hitsound"
        }, this, true)
                .addTask(MapMaster::load).addTracker(MapMaster::getProgress)
                .addCallback(TaikoEditor::initialize);
    }

    public static LoadingLayer getReturnLoader() {
        return new LoadingLayer(new String[] {
        }, menu, true);
    }


    private static class MenuProcessor extends AdjustedInputProcessor {
        private final MenuLayer sourceLayer;

        public MenuProcessor(MenuLayer source)
        {
            this.sourceLayer = source;
        }

        @Override
        public boolean keyDown(int keycode) {
            switch (keycode)
            {
                case Input.Keys.ESCAPE:
                    sourceLayer.quit(0);
                    return true;
            }
            //sourceLayer.test();
            return false;
        }

        @Override
        public boolean keyUp(int keycode) {
            return false;
        }

        @Override
        public boolean keyTyped(char character) {
            if (sourceLayer.searchInput.keyTyped(character)) {
                sourceLayer.displayIndex = 0;
                sourceLayer.scrollPos = 0;

                sourceLayer.mapOptions.clear();

                if (sourceLayer.searchInput.text.isEmpty()) {
                    for (String key : MapMaster.mapDatabase.keys) {
                        sourceLayer.mapOptions.add(sourceLayer.hashedMapOptions.get(key));
                    }
                }
                else {
                    MapMaster.search(sourceLayer.searchInput.text).forEach((m)-> sourceLayer.mapOptions.add(sourceLayer.hashedMapOptions.get(m.key)));
                }
                return true;
            }

            return false;
        }

        @Override
        public boolean onTouchDown(int gameX, int gameY, int pointer, int button) {
            editorLogger.trace("Game coordinates: " + gameX + ", " + gameY);
            /*for (Button b : sourceLayer.buttons)
            {
                if (b.click(gameX, gameY, button))
                    return true;
            }*/

            if (sourceLayer.settingsButton.click(gameX, gameY, button))
                return true;

            if (sourceLayer.exitButton.click(gameX, gameY, button))
                return true;

            for (int i = sourceLayer.displayIndex; i < sourceLayer.displayIndex + PER_SCREEN && i < sourceLayer.mapOptions.size(); ++i)
            {
                if (sourceLayer.mapOptions.get(i).click(gameX, gameY, button))
                {
                    sourceLayer.chooseMap(sourceLayer.mapOptions.get(i).action);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean onTouchDragged(int gameX, int gameY, int pointer) {
            return false;
        }

        @Override
        public boolean onMouseMoved(int gameX, int gameY) {
            return false;
        }

        @Override
        public boolean scrolled(int amount) {
            sourceLayer.scrollPos += amount * 30;

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
            }

            return true;
        }
    }
}
