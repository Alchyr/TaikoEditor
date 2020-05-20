package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.input.AdjustedInputProcessor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.input.sub.TextInput;
import alchyr.taikoedit.core.ui.Button;
import alchyr.taikoedit.management.MapMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.maps.Mapset;
import alchyr.taikoedit.util.assets.loaders.OsuBackgroundLoader;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;
import java.util.HashMap;

import static alchyr.taikoedit.TaikoEditor.*;

public class MenuLayer extends LoadedLayer implements InputLayer {
    private static MenuLayer menu;

    private static final int PER_ROW = 5;
    private static final int PER_COLUMN = 4;
    private static final int PER_SCREEN = PER_ROW * (PER_COLUMN + 1); //add an extra row for those that poke off the edges of the screen

    private static int OPTION_WIDTH, OPTION_HEIGHT;

    private boolean initialized;

    private MenuProcessor processor;

    //public ArrayList<Button> buttons;

    private ArrayList<Button> mapOptions;
    private HashMap<String, Button> hashedMapOptions;

    private Texture background;
    private int bgWidth, bgHeight;

    private Texture pixel;
    private int searchHeight;
    private float searchTextOffsetX, searchTextOffsetY;

    private TextInput searchInput;

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
            OPTION_WIDTH = SettingsMaster.getWidth() / PER_ROW;
            OPTION_HEIGHT = SettingsMaster.getHeight() / PER_COLUMN;

            if (!OsuBackgroundLoader.loadedBackgrounds.isEmpty())
            {
                background = assetMaster.get(OsuBackgroundLoader.loadedBackgrounds.get(MathUtils.random(OsuBackgroundLoader.loadedBackgrounds.size() - 1)));

                float bgScale = Math.max((float) SettingsMaster.getWidth()/ background.getWidth(), (float) SettingsMaster.getHeight() / background.getHeight());
                bgWidth = (int) Math.ceil(background.getWidth() * bgScale);
                bgHeight = (int) Math.ceil(background.getHeight() * bgScale);
            }

            pixel = assetMaster.get("ui:pixel");
            searchHeight = (int) (70 * SettingsMaster.SCALE);
            searchTextOffsetX = 10 * SettingsMaster.SCALE;
            searchTextOffsetY = 30 * SettingsMaster.SCALE;

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
                mapOptionY[i] = OPTION_HEIGHT * (PER_COLUMN - 1 - i);
            }

            searchInput = new TextInput(128, assetMaster.getFont("default"));

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
        sb.draw(pixel, 0, SettingsMaster.getHeight() - searchHeight, SettingsMaster.getWidth(), searchHeight);

        sb.setColor(Color.WHITE);
        searchInput.render(sb, searchTextOffsetX, SettingsMaster.getHeight() - searchTextOffsetY);
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

    private void play(int button)
    {
        TaikoEditor.removeLayer(this);

        /*AlchemistGame.addLayer(new LoadingLayer("game", new GameLayer[] {
                new AttackLayer(),
                new GameplayLayer().addEntity(new Player(new PlayerController(), SettingsMaster.getWidth() / 2.0f, SettingsMaster.getHeight() / 2.0f))
        }, true));*/

        //AlchemistGame.addLayer(new MapTestLayer().getLoader());
    }
    private void settings(int button)
    {
        editorLogger.trace("Settings!");
    }
    private void quit(int button)
    {
        editorLogger.trace("Goodbye!");
        TaikoEditor.end();
    }

    @Override
    public LoadingLayer getLoader() {
        menu = this;
        return new LoadingLayer(new String[] {
                "ui",
                "font",
                "menu",
                "background"
        }, this, true).addTask(MapMaster::load).addCallback(TaikoEditor::initialize);
    }

    public static LoadingLayer getReturnLoader() {
        return new LoadingLayer(new String[] {
                "menu"
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
                    TaikoEditor.end();
                    return true;
            }
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
            //gameLogger.trace("Game coordinates: " + gameX + ", " + gameY);
            /*for (Button b : sourceLayer.buttons)
            {
                if (b.click(gameX, gameY, button))
                    return true;
            }*/

            for (Button b : sourceLayer.mapOptions)
            {
                if (b.click(gameX, gameY, button))
                {
                    sourceLayer.chooseMap(b.action);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            return false;
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            return false;
        }

        @Override
        public boolean scrolled(int amount) {
            sourceLayer.scrollPos += amount * 10;

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
