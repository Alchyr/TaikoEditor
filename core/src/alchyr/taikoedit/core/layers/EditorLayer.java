package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.audio.MusicWrapper;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.input.AdjustedInputProcessor;
import alchyr.taikoedit.editor.BeatDivisors;
import alchyr.taikoedit.editor.DivisorOptions;
import alchyr.taikoedit.editor.Timeline;
import alchyr.taikoedit.editor.views.ObjectView;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.maps.MapInfo;
import alchyr.taikoedit.maps.Mapset;
import alchyr.taikoedit.maps.components.HitObject;
import alchyr.taikoedit.util.assets.loaders.OsuBackgroundLoader;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.util.input.KeyHoldManager;
import alchyr.taikoedit.util.input.KeyHoldObject;
import alchyr.taikoedit.util.input.MouseHoldObject;
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

    //Title/top bar
    private float titleOffsetX, titleOffsetY;
    private int topBarHeight;
    private int titleY;
    private int topBarY;

    private Timeline timeline;

    private static final Color bgColor = new Color(0.3f, 0.3f, 0.25f, 1.0f);

    private final Mapset set;

    private DivisorOptions divisorOptions; //Always shared
    private BeatDivisors universalDivisor; //Sometimes shared

    //Only usable if sameSong is true
    //private HashMap<Snap, int[]> snappings;

    private float currentPos; //current second position in song.

    //Map views
    private final ArrayList<EditorBeatmap> activeMaps;
    private final HashMap<EditorBeatmap, ViewSet> mapViews;

    public MapView primaryView;

    //View information
    public float viewScale = 1.0f;
    public int viewTime = 1500; //number of milliseconds before and after current position


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

        //input
        processor.initializeInput();

        //graphics positions/initialization
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
        topBarHeight = 85 + Timeline.HEIGHT;
        titleY = SettingsMaster.getHeight() - 85;
        topBarY = SettingsMaster.getHeight() - topBarHeight;
        titleOffsetX = 10;
        titleOffsetY = 40;

        //Editor stuff
        timeline = new Timeline(SettingsMaster.getHeight() - topBarHeight, music.getSecondLength());
        organizeViews(); //Positions views and sets primary view
    }

    @Override
    public void update(float elapsed) {
        if (exitDelay > 0)
            exitDelay -= elapsed;

        processor.keyHoldManager.update(elapsed);

        currentPos = getSecondPosition(Gdx.graphics.getRawDeltaTime());
        int msTime = (int) (currentPos * 1000);
        //editorLogger.info(pos);

        timeline.update(currentPos);

        for (ViewSet views : mapViews.values())
        {
            views.update(currentPos, msTime, music.isPlaying());
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        //Background
        if (background != null)
        {
            sb.setColor(bgColor);
            sb.draw(background, 0, 0, bgWidth, bgHeight);
        }

        //Top bar
        sb.setColor(Color.BLACK);
        sb.draw(pixel, 0, topBarY, SettingsMaster.getWidth(), topBarHeight);

        timeline.render(sb, sr);

        //Map views
        for (EditorBeatmap map : activeMaps)
        {
            mapViews.get(map).render(sb, sr);
        }

        if (primaryView != null)
            primaryView.primaryRender(sb, sr);

        //For each mapview, render overlay (difficulty name)

        /*sb.setColor(Color.WHITE);
        searchInput.render(sb, searchTextOffsetX, SettingsMaster.getHeight() - searchTextOffsetY);*/
    }

    private static int getMillisecondPosition(float elapsed)
    {
        return music.getMsTime(elapsed);
    }
    private static float getSecondPosition(float elapsed)
    {
        return music.getSecondTime(elapsed);
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
        }, this, true).addTask(this::setMusic).addCallback(this::prepMusic).addCallback(true, HitObject::loadTextures).addCallback(this::loadBeatmap);
    }

    private void setMusic()
    {
        music.setMusic(Gdx.files.absolute(set.getSongFile()));
    }
    private void prepMusic() //preloads the music.
    {
        music.prep();
    }

    private void loadBeatmap()
    {
        divisorOptions = new DivisorOptions();
        divisorOptions.reset();

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
            EditorBeatmap beatmap = getEditorBeatmap(info);
            addEditView(beatmap);
        }

        if (activeMaps.isEmpty())
        {
            divisorOptions.activate(4);
        }
        else
        {
            divisorOptions.activate(activeMaps.get(0).getDefaultDivisor());
        }
        editorLogger.info("Loaded beatmap successfully.");
    }

    private void addEditView(EditorBeatmap beatmap)
    {
        mapViews.get(beatmap).addView(new ObjectView(this, beatmap));
    }

    private EditorBeatmap getEditorBeatmap(MapInfo info)
    {
        for (EditorBeatmap map : activeMaps)
        {
            if (map.is(info))
                return map;
        }
        EditorBeatmap newMap = new EditorBeatmap(set, info);

        if (!set.sameSong)
        {
            newMap.generateDivisor(divisorOptions);
        }
        else
        {
            if (universalDivisor == null)
            {
                universalDivisor = newMap.generateDivisor(divisorOptions);
            }
            else
            {
                newMap.setDivisorObject(universalDivisor);
            }
        }

        activeMaps.add(newMap);
        mapViews.put(newMap, new ViewSet(newMap));
        return newMap;
    }

    private void organizeViews()
    {
        int y = topBarY;

        for (EditorBeatmap b : activeMaps)
        {
            y = mapViews.get(b).reposition(y);

            if (primaryView == null)
            {
                primaryView = mapViews.get(b).first();
                if (primaryView != null)
                    primaryView.isPrimary = true;
            }
        }
    }


    private void seekLeft()
    {
        music.seekSecond(currentPos - 1);
    }
    private void seekRight()
    {
        music.seekSecond(currentPos + 1);
    }


    private static class EditorProcessor extends AdjustedInputProcessor {
        private final EditorLayer sourceLayer;

        private final KeyHoldManager keyHoldManager;
        private MouseHoldObject mouseHold;

        private KeyHoldObject left;
        private KeyHoldObject right;

        public EditorProcessor(EditorLayer source)
        {
            this.sourceLayer = source;
            this.keyHoldManager = new KeyHoldManager();
        }

        private void initializeInput()
        {
            left = new KeyHoldObject(Input.Keys.LEFT, NORMAL_FIRST_DELAY, NORMAL_REPEAT_DELAY, (i)->sourceLayer.seekLeft(), null).addConflictingKey(Input.Keys.RIGHT);
            right = new KeyHoldObject(Input.Keys.RIGHT, NORMAL_FIRST_DELAY, NORMAL_REPEAT_DELAY, (i)->sourceLayer.seekRight(), null).addConflictingKey(Input.Keys.LEFT);
        }

        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) //Process input using the current primary view?
            {
                case Input.Keys.RIGHT:
                    sourceLayer.seekRight();
                    keyHoldManager.add(right);
                    return true;
                case Input.Keys.LEFT:
                    sourceLayer.seekLeft();
                    keyHoldManager.add(left);
                    return true;
                case Input.Keys.UP:
                    music.changeTempo(0.1f);
                    return true;
                case Input.Keys.DOWN:
                    music.changeTempo(-0.1f);
                    return true;
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
            return keyHoldManager.release(keycode);
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
            editorLogger.trace("Game coordinates: " + gameX + ", " + gameY);
            editorLogger.trace("Pointer: " + pointer);
            editorLogger.trace("Button: " + button);
            /*for (Button b : sourceLayer.buttons)
            {
                if (b.click(gameX, gameY, button))
                    return true;
            }*/

            if (mouseHold != null) //shouldn't be possible to have mouseHold non-null here, but just in case of some cases like alt-tabbing and missing release or something.
            {
                mouseHold.onRelease(gameX, gameY);
                mouseHold = null;
            }

            if (gameY > sourceLayer.topBarY)
            {
                if (gameY < sourceLayer.titleY)
                {
                    //timeline area
                    mouseHold = sourceLayer.timeline.click(gameX, gameY, pointer, button);
                    if (mouseHold != null)
                        return true;
                }
                return false;
            }
            for (EditorBeatmap m : sourceLayer.activeMaps)
            {
                ViewSet set = sourceLayer.mapViews.get(m);
                if (set.click(sourceLayer, gameX, gameY, pointer, button))
                {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean onTouchUp(int gameX, int gameY, int pointer, int button) {
            if (mouseHold != null) //shouldn't be possible to have mouseHold non-null here, but just in case of some cases like alt-tabbing and missing release or something.
            {
                boolean consumed = mouseHold.onRelease(gameX, gameY);
                mouseHold = null;
                return consumed;
            }
            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (mouseHold != null)
                mouseHold.onDrag(screenX, screenY);
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
