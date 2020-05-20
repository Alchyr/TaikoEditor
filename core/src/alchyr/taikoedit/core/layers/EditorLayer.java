package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.audio.MusicWrapper;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.input.AdjustedInputProcessor;
import alchyr.taikoedit.core.input.sub.TextInput;
import alchyr.taikoedit.editor.views.EditView;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.maps.MapInfo;
import alchyr.taikoedit.maps.Mapset;
import alchyr.taikoedit.maps.components.HitObject;
import alchyr.taikoedit.util.assets.loaders.OsuBackgroundLoader;
import alchyr.taikoedit.util.structures.ViewSet;
import com.badlogic.gdx.Gdx;
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

public class EditorLayer extends LoadedLayer implements InputLayer {
    public static MusicWrapper music; //There shouldn't ever be more than one.

    private EditorProcessor processor;

    private String backgroundImg;
    private Texture background;
    private int bgWidth, bgHeight;

    private Texture pixel;
    private int searchHeight;
    private float searchTextOffsetX, searchTextOffsetY;

    private TextInput searchInput;

    private static final Color bgColor = new Color(0.3f, 0.3f, 0.25f, 1.0f);

    private final Mapset set;

    //Map views
    private final ArrayList<EditorBeatmap> activeMaps;
    private final HashMap<EditorBeatmap, ViewSet> mapViews;

    //View information
    public float viewScale = 1.0f;
    public int viewTime = 1000; //number of milliseconds before and after current position


    private float exitDelay = 0.5f;

    public EditorLayer(Mapset set)
    {
        this.set = set;

        processor = new EditorProcessor(this);
        backgroundImg = set.getBackground();

        mapViews = new HashMap<>();
        activeMaps = new ArrayList<>();

        this.type = backgroundImg == null || backgroundImg.isEmpty() ? LAYER_TYPE.UPDATE_STOP : LAYER_TYPE.FULL_STOP;
    }

    @Override
    public void initialize() {
        if (music.noTrack())
        {
            //No opening a song with no music in this editor.
            returnToMenu();
            return;
        }

        if (backgroundImg != null && !backgroundImg.isEmpty())
        {
            background = new Texture(Gdx.files.absolute(backgroundImg), true); //these song folders have quite high odds of containing characters libgdx doesn't like. assetMaster.get(backgroundImg.toLowerCase());
            background.setFilter(Texture.TextureFilter.MipMapLinearNearest, Texture.TextureFilter.MipMapLinearNearest);

            float bgScale = Math.max((float) SettingsMaster.getWidth() / background.getWidth(), (float) SettingsMaster.getHeight() / background.getHeight());
            bgWidth = (int) Math.ceil(background.getWidth() * bgScale);
            bgHeight = (int) Math.ceil(background.getHeight() * bgScale);
        }
        else if (!OsuBackgroundLoader.loadedBackgrounds.isEmpty())
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

        organizeViews();
    }

    @Override
    public void update(float elapsed) {
        super.update(elapsed);

        if (exitDelay > 0)
            exitDelay -= elapsed;

        int pos = getMillisecondPosition(Gdx.graphics.getRawDeltaTime());
        //editorLogger.info(pos);

        for (ViewSet views : mapViews.values())
        {
            views.update(pos);
            views.prep();
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        if (background != null)
        {
            sb.setColor(bgColor);
            sb.draw(background, 0, 0, bgWidth, bgHeight);
        }

        sb.setColor(Color.BLACK);
        sb.draw(pixel, 0, SettingsMaster.getHeight() - searchHeight, SettingsMaster.getWidth(), searchHeight);

        for (EditorBeatmap map : activeMaps)
        {
            mapViews.get(map).render(sb, sr);
        }

        /*sb.setColor(Color.WHITE);
        searchInput.render(sb, searchTextOffsetX, SettingsMaster.getHeight() - searchTextOffsetY);*/
    }

    private static int getMillisecondPosition(float elapsed)
    {
        return music.getMsTime(elapsed);
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
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
    private void returnToMenu()
    {
        TaikoEditor.removeLayer(this);
        TaikoEditor.addLayer(MenuLayer.getReturnLoader());
    }

    @Override
    public void dispose() {
        super.dispose();

        if (backgroundImg != null && !backgroundImg.isEmpty() && background != null)
        {
            background.dispose();
        }

        music.dispose();
    }

    @Override
    public LoadingLayer getLoader() {
        return new LoadingLayer(new String[] {
                "editor",
                "background"
        }, this, true).addTask(this::loadBeatmap).addCallback(HitObject::loadTextures).addCallback(music::prep);
    }

    private void loadBeatmap()
    {
        music.setMusic(Gdx.files.absolute(set.getSongFile()));

        //If single difficulty, load automatically
        /*if (set.getMaps().size() == 1)
        {
            EditorBeatmap beatmap = new EditorBeatmap(set, set.getMaps().get(0));

            activeMaps.add(beatmap);
            mapViews.put(beatmap, new ViewSet(beatmap));

            mapViews.get(beatmap).addView(new EditView(this, beatmap));
        }*/
        //Test code: Load all diffs automatically
        for (MapInfo info : set.getMaps())
        {
            addEditView(info);
        }
        editorLogger.info("Loaded beatmap successfully.");
    }

    private void addEditView(MapInfo info)
    {
        EditorBeatmap beatmap = getEditorBeatmap(info);
        mapViews.get(beatmap).addView(new EditView(this, beatmap));
    }

    private EditorBeatmap getEditorBeatmap(MapInfo info)
    {
        for (EditorBeatmap map : activeMaps)
        {
            if (map.is(info))
                return map;
        }
        EditorBeatmap newMap = new EditorBeatmap(set, info);
        activeMaps.add(newMap);
        mapViews.put(newMap, new ViewSet(newMap));
        return newMap;
    }

    private void organizeViews()
    {
        int y = SettingsMaster.getHeight() - searchHeight;

        for (EditorBeatmap b : activeMaps)
        {
            y = mapViews.get(b).reposition(y);
        }
    }


    private static class EditorProcessor extends AdjustedInputProcessor {
        private final EditorLayer sourceLayer;

        public EditorProcessor(EditorLayer source)
        {
            this.sourceLayer = source;
        }

        @Override
        public boolean keyDown(int keycode) {
            switch (keycode)
            {
                case Input.Keys.SPACE:
                    music.toggle();
                    return true;
                case Input.Keys.ESCAPE:
                    if (sourceLayer.exitDelay <= 0)
                        sourceLayer.returnToMenu();
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
            /*sourceLayer.scrollPos += amount * 10;

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
