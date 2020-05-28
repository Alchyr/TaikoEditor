package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.audio.MusicWrapper;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.input.AdjustedInputProcessor;
import alchyr.taikoedit.core.ui.TextOverlay;
import alchyr.taikoedit.editor.*;
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

    //Input
    public static boolean finisherLock = false;
    public static EditorProcessor processor;
    private float exitDelay = 0.25f;

    /* * * * * * UI ELEMENTS * * * * * */
    private Texture pixel; //General rendering

    //Overlays
    private TextOverlay textOverlay;

    //Background
    private static final Color bgColor = new Color(0.3f, 0.3f, 0.25f, 1.0f);
    private String backgroundImg;
    private int bgWidth, bgHeight;
    private Texture background;

    //Title/top bar
    private float titleOffsetX, titleOffsetY;
    private int topBarHeight;
    private int topBarY;
    private int timelineY;

    private Timeline timeline; //Timeline

    //Map views
    public MapView primaryView;
    private final HashMap<EditorBeatmap, ViewSet> mapViews;

    //View information
    public static float viewScale = 1.0f;
    public static int viewTime = 1500; //number of milliseconds before and after current position

    //Tools
    public Tools tools;


    /* * * * * * Beatmap Stuff * * * * * */
    private final ArrayList<EditorBeatmap> activeMaps;

    private final Mapset set;

    private DivisorOptions divisorOptions; //Always shared
    private BeatDivisors universalDivisor; //Sometimes shared
    private float currentPos; //current second position in song.


    //Vertical scroll, when too many views exist
    private int minimumVisibleY = 0;
    private boolean verticalScrollEnabled = false;
    private int scrollPos = 0, maxScrollPosition = 0;

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
        finisherLock = false;

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
        topBarHeight = 60;
        timelineY = SettingsMaster.getHeight() - (topBarHeight  + Timeline.HEIGHT);
        topBarY = SettingsMaster.getHeight() - topBarHeight;
        titleOffsetX = 10;
        titleOffsetY = 35;
        minimumVisibleY = Tools.HEIGHT;
        textOverlay = new TextOverlay(assetMaster.getFont("aller medium"), SettingsMaster.getHeight() / 2, 100);

        setViewScale(1.0f);

        //Editor stuff
        timeline = new Timeline(timelineY, music.getSecondLength());
        tools = new Tools();
        organizeViews(); //Positions views and sets primary view, determines scroll
    }

    @Override
    public void update(float elapsed) {
        if (exitDelay > 0)
            exitDelay -= elapsed;

        processor.keyHoldManager.update(elapsed);
        if (processor.mouseHold != null)
            processor.mouseHold.update(elapsed);

        currentPos = getSecondPosition(Gdx.graphics.getRawDeltaTime());
        int msTime = (int) (currentPos * 1000);
        //editorLogger.info(pos);

        timeline.update(currentPos);

        for (ViewSet views : mapViews.values())
        {
            views.update(currentPos, msTime, music.isPlaying());
        }

        textOverlay.update(elapsed);
        tools.update(timelineY, minimumVisibleY, activeMaps, mapViews, elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        //Background
        if (background != null)
        {
            sb.setColor(bgColor);
            sb.draw(background, 0, 0, bgWidth, bgHeight);
        }

        //Map views
        for (EditorBeatmap map : activeMaps)
        {
            mapViews.get(map).render(sb, sr);
        }
        if (primaryView != null)
            primaryView.primaryRender(sb, sr);

        tools.renderCurrentTool(sb, sr);

        //For each mapview, render overlay (difficulty name)
        for (ViewSet views : mapViews.values())
        {
            views.renderOverlays(sb, sr);
        }

        //Top bar
        sb.setColor(Color.BLACK);
        sb.draw(pixel, 0, topBarY, SettingsMaster.getWidth(), topBarHeight);

        timeline.render(sb, sr);

        //Tools
        tools.render(sb, sr);

        //Overlays
        textOverlay.render(sb, sr);

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

        for (EditorBeatmap m : activeMaps)
        {
            mapViews.get(m).dispose();
            m.dispose();
        }

        music.dispose();
    }

    @Override
    public LoadingLayer getLoader() {
        return new LoadingLayer(new String[] {
                "editor",
                "background"
        }, this, true)
                .addTask(this::setMusic)
                .addCallback(this::prepMusic)
                .addCallback(true, HitObject::loadTextures).addCallback(this::loadBeatmap)
                .addCallback(true, this::initMusic);
    }

    private void setMusic()
    {
        music.setMusic(Gdx.files.absolute(set.getSongFile()));
    }
    private void prepMusic() //preloads the music.
    {
        music.prep();
    }
    private void initMusic() //gets audio source and makes sure music is ready to play
    {
        try {
            editorLogger.info("Attempting to initialize music.");
            if (music.initialize())
            {
                editorLogger.info("Initialized music successfully.");
            }
            else
            {
                //Failure.
                editorLogger.info("Failed to initialize music.");
                music.dispose();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
            addEditView(beatmap, false);
        }

        if (activeMaps.isEmpty())
        {
            divisorOptions.set(4);
        }
        else
        {
            divisorOptions.set(activeMaps.get(0).getDefaultDivisor());
        }
        editorLogger.info("Loaded beatmap successfully.");
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
        return newMap;
    }

    private void addEditView(EditorBeatmap beatmap, boolean reorganize)
    {
        addView(new ObjectView(this, beatmap), reorganize);
    }
    public void addView(MapView newView, boolean reorganize)
    {
        if (!mapViews.containsKey(newView.map))
            mapViews.put(newView.map, new ViewSet(newView.map));

        mapViews.get(newView.map).addView(newView);

        if (reorganize)
            organizeViews();
    }
    public void removeView(MapView toRemove)
    {
        ViewSet container = mapViews.get(toRemove.map);

        if (container != null)
        {
            container.removeView(toRemove);

            if (toRemove.equals(primaryView))
            {
                primaryView = null;
            }

            if (container.isEmpty())
            {
                container.dispose();
                mapViews.remove(toRemove.map);
                activeMaps.remove(toRemove.map);
                toRemove.map.dispose();
            }
        }
        organizeViews();
    }

    private void organizeViews()
    {
        int y = timelineY;

        for (EditorBeatmap b : activeMaps)
        {
            y = mapViews.get(b).reposition(y);

            if (primaryView == null)
            {
                primaryView = mapViews.get(b).first();
                if (primaryView != null)
                {
                    tools.changeToolset(primaryView);
                    primaryView.isPrimary = true;
                }
            }
        }

        if (y < minimumVisibleY)
        {
            verticalScrollEnabled = true;
            maxScrollPosition = minimumVisibleY - y;
            if (scrollPos > maxScrollPosition)
                scrollPos = maxScrollPosition;


        }
        else
        {
            verticalScrollEnabled = false;
            scrollPos = 0;
        }

        updateScrollOffset();
    }

    private static void setViewScale(float newScale)
    {
        viewScale = Math.max(0.2f, newScale);
        viewTime = (int) ((SettingsMaster.getMiddle() + 500) / viewScale);
    }

    private void updateScrollOffset()
    {
        for (EditorBeatmap b : activeMaps)
            mapViews.get(b).setOffset(scrollPos);
    }


    private void seekLeft()
    {
        if (primaryView == null || primaryView.noSnaps())
            music.seekSecond(currentPos - 1);
        else
        {
            Snap s = primaryView.getPreviousSnap();
            if (s == null)
            {
                music.seekSecond(0);
            }
            else
            {
                music.seekMs(s.pos);
            }
        }
    }
    private void seekRight()
    {
        if (primaryView == null || primaryView.noSnaps())
            music.seekSecond(currentPos + 1);
        else
        {
            Snap s = primaryView.getNextSnap();
            if (s == null)
            {
                music.seekSecond(music.getSecondLength());
            }
            else
            {
                music.seekMs(s.pos);
            }
        }
    }


    public static class EditorProcessor extends AdjustedInputProcessor {
        private final EditorLayer sourceLayer;

        private final KeyHoldManager keyHoldManager;
        private MouseHoldObject mouseHold;

        private KeyHoldObject left;
        private KeyHoldObject right;
        private KeyHoldObject lctrl;
        private KeyHoldObject rctrl;

        public EditorProcessor(EditorLayer source)
        {
            this.sourceLayer = source;
            this.keyHoldManager = new KeyHoldManager();
        }

        private void initializeInput()
        {
            left = new KeyHoldObject(Input.Keys.LEFT, NORMAL_FIRST_DELAY, NORMAL_REPEAT_DELAY, (i)->sourceLayer.seekLeft(), null).addConflictingKey(Input.Keys.RIGHT);
            right = new KeyHoldObject(Input.Keys.RIGHT, NORMAL_FIRST_DELAY, NORMAL_REPEAT_DELAY, (i)->sourceLayer.seekRight(), null).addConflictingKey(Input.Keys.LEFT);
            lctrl = new KeyHoldObject(Input.Keys.CONTROL_LEFT, Float.MAX_VALUE, Float.MAX_VALUE, null, null);
            rctrl = new KeyHoldObject(Input.Keys.CONTROL_RIGHT, Float.MAX_VALUE, Float.MAX_VALUE, null, null);
        }

        public void cancelMouseHold(MouseHoldObject obj)
        {
            if (obj.equals(mouseHold))
            {
                mouseHold = null;
            }
        }

        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) //Process input using the current primary view? TODO: Add keybindings? SettingsMaster has some keybind code.
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
                case Input.Keys.A:
                    if (keyHoldManager.isHeld(Input.Keys.CONTROL_LEFT))
                    {
                        sourceLayer.primaryView.selectAll();
                        return true;
                    }
                    break;
                case Input.Keys.S:
                    if (keyHoldManager.isHeld(Input.Keys.CONTROL_LEFT))
                    {
                        if (sourceLayer.primaryView.map.save())
                        {
                            sourceLayer.textOverlay.setText("Difficulty \"" + sourceLayer.primaryView.map.getName() + "\" saved!", 0.5f);
                        }
                        else
                        {
                            sourceLayer.textOverlay.setText("Failed to save!", 2.0f);
                        }
                        return true;
                    }
                    break;
                case Input.Keys.Q:
                    finisherLock = !finisherLock;
                    break;
                case Input.Keys.Y:
                    if (keyHoldManager.isHeld(Input.Keys.CONTROL_LEFT))
                    {
                        sourceLayer.primaryView.map.redo();
                        return true;
                    }
                    break;
                case Input.Keys.Z:
                    if (keyHoldManager.isHeld(Input.Keys.CONTROL_LEFT))
                    {
                        sourceLayer.primaryView.map.undo();
                        return true;
                    }
                    break;
                case Input.Keys.BACKSPACE:
                case Input.Keys.FORWARD_DEL:
                    sourceLayer.primaryView.deleteSelection();
                    return true;
                case Input.Keys.EQUALS:
                    if (keyHoldManager.isHeld(Input.Keys.CONTROL_LEFT))
                        music.modifyOffset(0.005f);
                    else
                        music.modifyOffset(0.001f);
                    sourceLayer.textOverlay.setText("Offset: " + music.getDisplayOffset(), 1.5f);
                    return true;
                case Input.Keys.MINUS:
                    if (keyHoldManager.isHeld(Input.Keys.CONTROL_LEFT))
                        music.modifyOffset(-0.005f);
                    else
                        music.modifyOffset(-0.001f);
                    sourceLayer.textOverlay.setText("Offset: " + music.getDisplayOffset(), 1.5f);
                    return true;
                case Input.Keys.SPACE:
                    music.toggle();
                    return true;
                case Input.Keys.ESCAPE:
                    if (sourceLayer.exitDelay <= 0)
                        sourceLayer.returnToMenu();
                    return true;
                case Input.Keys.CONTROL_LEFT:
                case Input.Keys.CONTROL_RIGHT:
                    keyHoldManager.add(lctrl);
                    keyHoldManager.add(rctrl);
                    return true;
                case Input.Keys.NUM_1:
                    return sourceLayer.tools.selectToolIndex(0);
                case Input.Keys.NUM_2:
                    return sourceLayer.tools.selectToolIndex(1);
                case Input.Keys.NUM_3:
                    return sourceLayer.tools.selectToolIndex(2);
                case Input.Keys.NUM_4:
                    return sourceLayer.tools.selectToolIndex(3);
                case Input.Keys.NUM_5:
                    return sourceLayer.tools.selectToolIndex(4);
                case Input.Keys.NUM_6:
                    return sourceLayer.tools.selectToolIndex(5);
                case Input.Keys.NUM_7:
                    return sourceLayer.tools.selectToolIndex(6);
                case Input.Keys.NUM_8:
                    return sourceLayer.tools.selectToolIndex(7);
                case Input.Keys.NUM_9:
                    return sourceLayer.tools.selectToolIndex(8);
                case Input.Keys.NUM_0:
                    return sourceLayer.tools.selectToolIndex(9);
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
            if (button != 0 && button != 1)
                return false; //i only care about left and right click.

            if (mouseHold != null)
            {
                if (mouseHold.onRelease(gameX, gameY))
                {
                    mouseHold = null;
                    return true;
                }
                mouseHold = null;
            }

            if (gameY > sourceLayer.timelineY)
            {
                if (gameY < sourceLayer.topBarY)
                {
                    //timeline area
                    mouseHold = sourceLayer.timeline.click(gameX, gameY, pointer, button);
                    return mouseHold != null;
                }
                return false;
            }
            else if (gameY > sourceLayer.minimumVisibleY) //ViewSet area
            {
                for (EditorBeatmap m : sourceLayer.activeMaps)
                {
                    ViewSet set = sourceLayer.mapViews.get(m);
                    if (set.containsY(gameY))
                    {
                        mouseHold = set.click(sourceLayer, gameX, gameY, pointer, button, keyHoldManager);
                        return true;
                    }
                }
            }
            else
            {
                //Tools area

                //Tool selection logic. in Tools.java?
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
        public boolean onTouchDragged(int gameX, int gameY, int pointer) {
            if (mouseHold != null)
                mouseHold.onDrag(gameX, gameY);
            return false;
        }

        @Override
        public boolean onMouseMoved(int gameX, int gameY) {
            return false;
        }

        @Override
        public boolean scrolled(int amount) {
            if (keyHoldManager.isHeld(Input.Keys.CONTROL_LEFT))
            {
                sourceLayer.divisorOptions.adjust(amount);
            }
            else
            {
                if (sourceLayer.verticalScrollEnabled)
                {
                    int gameY = SettingsMaster.getHeight() - Gdx.input.getY();

                    if (gameY < sourceLayer.timelineY && gameY > sourceLayer.minimumVisibleY)
                    {
                        sourceLayer.scrollPos += amount * 16;

                        if (sourceLayer.scrollPos < 0)
                            sourceLayer.scrollPos = 0;

                        if (sourceLayer.scrollPos > sourceLayer.maxScrollPosition)
                        {
                            sourceLayer.scrollPos = sourceLayer.maxScrollPosition;
                        }

                        sourceLayer.updateScrollOffset();
                        return true;
                    }
                }

                if (amount > 0)
                {
                    sourceLayer.seekRight();
                }
                else
                {
                    sourceLayer.seekLeft();
                }
            }
            return true;
        }
    }
}
