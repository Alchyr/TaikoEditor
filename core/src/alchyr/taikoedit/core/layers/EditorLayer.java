package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.input.TextInputProcessor;
import alchyr.taikoedit.core.layers.sub.ConfirmationLayer;
import alchyr.taikoedit.core.layers.sub.DifficultyMenuLayer;
import alchyr.taikoedit.core.layers.sub.SvFunctionLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.core.ui.TextOverlay;
import alchyr.taikoedit.editor.*;
import alchyr.taikoedit.editor.changes.FinisherChange;
import alchyr.taikoedit.editor.views.EffectView;
import alchyr.taikoedit.editor.views.ObjectView;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.LocalizationMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.MapInfo;
import alchyr.taikoedit.editor.maps.Mapset;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.management.localization.LocalizedText;
import alchyr.taikoedit.management.assets.loaders.OsuBackgroundLoader;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.core.input.KeyHoldObject;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

import static alchyr.taikoedit.TaikoEditor.*;

public class EditorLayer extends LoadedLayer implements InputLayer {
    //Return to menu
    private ProgramLayer src;

    //Input
    public static boolean finisherLock = false;
    public static EditorProcessor processor;
    private float exitDelay = 0.25f;

    /* * * * * * UI ELEMENTS * * * * * */
    private Texture pixel; //General rendering

    //Overlays
    public TextOverlay textOverlay;

    //Background
    private static final Color bgColor = new Color(0.3f, 0.3f, 0.25f, 1.0f);
    private String backgroundImg;
    private int bgWidth, bgHeight;
    private Texture background;

    private static final Color CENTER_LINE_COLOR = new Color(0.6f, 0.6f, 0.6f, 0.5f);

    private int topBarHeight;
    private int topBarY;
    private int timelineY;
    private ImageButton exitButton;
    private ImageButton settingsButton;
    private ImageButton openButton;

    private Timeline timeline; //Timeline

    //Map views
    public MapView primaryView;
    private final HashMap<EditorBeatmap, ViewSet> mapViews;

    //View information
    public static float viewScale = 1.0f;
    public static int viewTime = 1500; //number of milliseconds before and after current position

    //Tools
    public Tools tools;

    //Data
    private LocalizedText keyNames;

    /* * * * * * Beatmap Stuff * * * * * */
    private final ArrayList<EditorBeatmap> activeMaps;

    private final Mapset set;
    private final MapInfo initial;
    private final ArrayList<MapView> addLater;

    private DivisorOptions divisorOptions; //Always shared
    private BeatDivisors universalDivisor; //Sometimes shared
    private double currentPos; //current second position in song.


    //Copy and Paste
    private static PositionalObjectTreeMap<PositionalObject> copyObjects = null;
    private static MapView.ViewType copyType;


    //Vertical scroll, when too many views exist
    private int minimumVisibleY = 0;
    private boolean verticalScrollEnabled = false;
    private int scrollPos = 0, maxScrollPosition = 0;

    public EditorLayer(ProgramLayer src, Mapset set, MapInfo initial)
    {
        this.src = src;
        this.set = set;
        this.initial = initial;

        processor = new EditorProcessor(this);
        backgroundImg = set.getBackground();

        mapViews = new HashMap<>();
        activeMaps = new ArrayList<>();
        addLater = new ArrayList<>();

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
        finisherLock = false;

        keyNames = LocalizationMaster.getLocalizedText("keys", "names");

        //graphics positions/initialization
        if (backgroundImg != null && !backgroundImg.isEmpty())
        {
            TaikoEditor.onMain(()->{
                //assetMaster.get(backgroundImg.toLowerCase());
                background = new Texture(Gdx.files.absolute(backgroundImg), true); //these song folders have quite high odds of containing characters libgdx doesn't like, which messes up assetMaster loading.
                background.setFilter(Texture.TextureFilter.MipMapLinearNearest, Texture.TextureFilter.MipMapLinearNearest);

                float bgScale = Math.max((float) SettingsMaster.getWidth() / background.getWidth(), (float) SettingsMaster.getHeight() / background.getHeight());
                bgWidth = (int) Math.ceil(background.getWidth() * bgScale);
                bgHeight = (int) Math.ceil(background.getHeight() * bgScale);
            });
        }
        else if (!OsuBackgroundLoader.loadedBackgrounds.isEmpty())
        {
            background = assetMaster.get(OsuBackgroundLoader.loadedBackgrounds.get(MathUtils.random(OsuBackgroundLoader.loadedBackgrounds.size() - 1)));

            float bgScale = Math.max((float) SettingsMaster.getWidth()/ background.getWidth(), (float) SettingsMaster.getHeight() / background.getHeight());
            bgWidth = (int) Math.ceil(background.getWidth() * bgScale);
            bgHeight = (int) Math.ceil(background.getHeight() * bgScale);
        }

        pixel = assetMaster.get("ui:pixel");
        topBarHeight = 40;
        timelineY = SettingsMaster.getHeight() - (topBarHeight + Timeline.HEIGHT);
        topBarY = SettingsMaster.getHeight() - topBarHeight;
        topBarHeight += 10;
        //Title/top bar
        //float titleOffsetX = 10;
        //float titleOffsetY = 35;
        minimumVisibleY = Tools.HEIGHT;
        textOverlay = new TextOverlay(assetMaster.getFont("aller medium"), SettingsMaster.getHeight() / 2, 100);

        //Top bar
        exitButton = new ImageButton(SettingsMaster.getWidth() - 40, SettingsMaster.getHeight() - 40, assetMaster.get("ui:exit"), (Texture) assetMaster.get("ui:exith")).setClick(this::returnToMenu);
        settingsButton = new ImageButton(SettingsMaster.getWidth() - 80, SettingsMaster.getHeight() - 40, assetMaster.get("ui:settings"), (Texture) assetMaster.get("ui:settingsh")).setClick(this::settings);
        openButton = new ImageButton(SettingsMaster.getWidth() - 120, topBarY, assetMaster.get("editor:open"), (Texture) assetMaster.get("editor:openh")).setClick(this::openDifficultyMenu);

        setViewScale(1.0f);

        //Initialize this
        SvFunctionLayer.init();

        //Editor stuff
        timeline = new Timeline(timelineY, music.getSecondLength());
        tools = new Tools(this);
        if (activeMaps.isEmpty())
        {
            openDifficultyMenu();
        }
        else //started with a diff open
        {
            organizeViews(); //Positions views and sets primary view, determines scroll
            if (!activeMaps.get(0).autoBreaks) {
                textOverlay.setText("Map contains invalid breaks; automatic break control disabled.", 2.5f);
            }
        }
        processor.bind();
    }

    @Override
    public void update(float elapsed) {
        processor.update(elapsed);

        if (exitDelay > 0)
            exitDelay -= elapsed;

        if (addLater.size() > 0)
        {
            for (MapView m : addLater)
            {
                addView(m, false);
            }
            addLater.clear();
            organizeViews();
        }

        currentPos = music.getSecondTime();
        long msTime = Math.round(currentPos * 1000);
        //editorLogger.info(pos);

        timeline.update(currentPos);

        for (ViewSet views : mapViews.values())
        {
            views.update(currentPos, msTime, elapsed, music.isPlaying());
        }

        textOverlay.update(elapsed);
        tools.update(timelineY, minimumVisibleY, activeMaps, mapViews, elapsed);

        exitButton.update(elapsed);
        openButton.update(elapsed);
        settingsButton.update(elapsed);

        if (openButton.hovered) {
            String input = processor.getBindingInput(keyNames.get(""), "OpenView");
            hoverText.setText(input == null ? "Open New View" : "Open New View (" + input + ")");
        }
        else if (settingsButton.hovered) {
            hoverText.setText("Settings");
        }
        else if (exitButton.hovered) {
            hoverText.setText("Exit");
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        if (type == LAYER_TYPE.FULL_STOP) {
            Gdx.gl.glClearColor(0, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        }
        //Background
        if (background != null)
        {
            sb.setColor(bgColor);
            sb.draw(background, 0, 0, bgWidth, bgHeight);
        }

        //Map views
        for (EditorBeatmap map : activeMaps)
        {
            ViewSet set = mapViews.get(map);
            if (set != null)
                set.render(sb, sr);
        }

        tools.renderCurrentTool(sb, sr);

        //For each mapview, render overlay (difficulty name), exit button, black separation lines
        for (ViewSet views : mapViews.values())
        {
            views.renderOverlays(sb, sr);

            sb.setColor(CENTER_LINE_COLOR);
            for (MapView view : views.getViews()) {
                //Center Line
                sb.draw(pixel, view.getBasePosition(), view.bottom, 1, view.height);
            }
        }

        if (primaryView != null)
            primaryView.primaryRender(sb, sr);

        //Top bar
        sb.setColor(Color.BLACK);
        sb.draw(pixel, 0, topBarY, SettingsMaster.getWidth(), topBarHeight);
        exitButton.render(sb, sr);
        settingsButton.render(sb, sr);
        openButton.render(sb, sr);

        timeline.render(sb, sr);

        //Tools
        tools.render(sb, sr);

        //Overlays
        textOverlay.render(sb, sr);

        /*sb.setColor(Color.WHITE);
        searchInput.render(sb, searchTextOffsetX, SettingsMaster.getHeight() - searchTextOffsetY);*/
    }

    public ViewSet getViewSet(EditorBeatmap map)
    {
        return mapViews.get(map);
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    private void returnToMenu()
    {
        clean();

        List<EditorBeatmap> dirtyMaps = new ArrayList<>();
        for (EditorBeatmap map : mapViews.keySet()) {
            if (map.dirty) {
                dirtyMaps.add(map);
            }
        }

        if (dirtyMaps.isEmpty()) {
            returnToSrc();
        }
        else {
            StringBuilder unsaved = new StringBuilder("Save changes to difficult");
            if (dirtyMaps.size() == 1) {
                unsaved.append("y ");
            }
            else {
                unsaved.append("ies ");
            }
            for (int i = 0; i < dirtyMaps.size(); ++i) {
                unsaved.append("[").append(dirtyMaps.get(i).getName()).append("]");
                if (i < dirtyMaps.size() - 1)
                    unsaved.append(", ");
            }
            unsaved.append("?");

            TaikoEditor.addLayer(new ConfirmationLayer(unsaved.toString(), "Yes", "No", true)
                    .onConfirm(()->{
                        boolean success = true;
                        for (EditorBeatmap m : dirtyMaps) {
                            if (!m.save()) {
                                success = false;
                                textOverlay.setText("Failed to save!", 2.0f);
                            }
                        }

                        if (success) {
                            returnToSrc();
                        }
                    })
                    .onDeny(this::returnToSrc));
        }
    }

    private boolean closed = false;
    private void returnToSrc() {
        if (!closed) {
            closed = true;
            TaikoEditor.removeLayer(this);
            if (src instanceof LoadedLayer) {
                TaikoEditor.addLayer(((LoadedLayer) src).getReturnLoader());
            }
            else if (src != null) {
                TaikoEditor.addLayer(src);
            }
            else {
                TaikoEditor.end();
            }
        }
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
            ViewSet set = mapViews.get(m);
            if (set != null) {
                set.dispose();
                m.dispose();
            }
        }

        activeMaps.clear();
        mapViews.clear();
        primaryView = null;

        processor.dispose();
    }

    @Override
    public LoadingLayer getLoader() {
        return new EditorLoadingLayer()
                .addLayers(true, this)
                .addTask(this::stopMusic)
                .newSet().addTracker(music::getProgress, music::hasMusic, true)
                .addTask(true, this::loadBeatmap)
                .addTask(true, ()->{ music.play(); music.pause(); })
                .addTask(true, ()->music.seekSecond(0));
    }

    private void stopMusic()
    {
        music.cancelAsyncFollowup();
        music.pause();
    }

    private void loadBeatmap()
    {
        divisorOptions = new DivisorOptions();
        divisorOptions.reset();

        if (initial != null) {
            prepSingleDiff(initial);
        }
        else if (set.getMaps().size() == 1) //If single difficulty, load automatically
        {
            prepSingleDiff(set.getMaps().get(0));
        }

        //Test code: Load all diffs automatically
        /*for (MapInfo info : set.getMaps())
        {
            EditorBeatmap beatmap = getEditorBeatmap(info);
            addEditView(beatmap, false);
        }*/

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

    private void prepSingleDiff(MapInfo info) {
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

        addEditView(newMap, false);
        addEffectView(newMap, false);
        activeMaps.add(newMap);
    }

    public EditorBeatmap getEditorBeatmap(MapInfo info)
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

        if (!newMap.autoBreaks) {
            textOverlay.setText("Map contains invalid breaks; automatic break control disabled.", 2.5f);
        }
        return newMap;
    }
    public void addMap(EditorBeatmap newMap) {

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
    }

    private void addEditView(EditorBeatmap beatmap, boolean delayed)
    {
        addView(new ObjectView(this, beatmap), delayed);
    }
    private void addEffectView(EditorBeatmap beatmap, boolean delayed)
    {
        addView(new EffectView(this, beatmap), delayed);
    }
    public void addView(MapView newView, boolean delayed)
    {
        if (delayed)
        {
            if (!mapViews.containsKey(newView.map) || !mapViews.get(newView.map).contains((o)->o.type == newView.type))
                addLater.add(newView);
            else
                newView.dispose();
        }
        else
        {
            if (!mapViews.containsKey(newView.map))
                mapViews.put(newView.map, new ViewSet(this, newView.map));

            if (!mapViews.get(newView.map).contains((o)->o.type == newView.type)) {
                newView.update(currentPos, Math.round(currentPos * 1000), 0);
                mapViews.get(newView.map).addView(newView);
            }
        }
    }
    public void removeView(MapView toRemove)
    {
        ViewSet container = mapViews.get(toRemove.map);

        if (container != null)
        {
            if (container.getViews().contains(toRemove) && container.getViews().size() == 0 && toRemove.map.dirty) {
                TaikoEditor.addLayer(new ConfirmationLayer("Save changes to difficulty [" + toRemove.map.getName() + "]?", "Yes", "No", true)
                        .onConfirm(()->{
                            if (toRemove.map.save()) {
                                container.removeView(toRemove);

                                if (container.isEmpty()) {
                                    container.dispose();
                                    disposeMap(toRemove.map);
                                }
                                if (toRemove.equals(primaryView))
                                {
                                    primaryView = null;
                                }
                            }
                            else {
                                textOverlay.setText("Failed to save!", 2.0f);
                            }
                        })
                        .onDeny(()->{
                            container.removeView(toRemove);

                            if (container.isEmpty()) {
                                container.dispose();
                                disposeMap(toRemove.map);
                            }
                            if (toRemove.equals(primaryView))
                            {
                                primaryView = null;
                            }
                        }));
            }
            else {
                container.removeView(toRemove);

                if (container.isEmpty()) {
                    container.dispose();
                    disposeMap(toRemove.map);
                }
                if (toRemove.equals(primaryView))
                {
                    primaryView = null;
                }
            }
        }
        organizeViews();
    }
    public void closeViewSet(MapInfo info) {
        ViewSet toClose = null;
        EditorBeatmap map = null;
        for (Map.Entry<EditorBeatmap, ViewSet> view : mapViews.entrySet()) {
            if (view.getKey().is(info)) {
                toClose = view.getValue();
                map = view.getKey();
                break;
            }
        }

        if (toClose != null) {
            if (toClose.contains((v)->v.equals(primaryView)))
            {
                primaryView = null;
            }
            toClose.dispose();
            disposeMap(map);
            organizeViews();
        }
    }

    private void organizeViews()
    {
        int y = timelineY;

        for (EditorBeatmap b : activeMaps)
        {
            ViewSet set = mapViews.get(b);
            if (set != null) {
                y = set.reposition(y);

                if (primaryView == null)
                {
                    setPrimaryView(set.first());
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

    public void setPrimaryView(MapView newPrimary)
    {
        if (primaryView != null) {
            primaryView.notPrimary();
        }

        primaryView = newPrimary;
        if (primaryView != null)
        {
            timeline.setMap(primaryView.map);
            tools.changeToolset(primaryView);
            primaryView.primary();
        }
        else
        {
            timeline.setMap(null);
        }
    }

    private void disposeMap(EditorBeatmap map) {
        mapViews.remove(map);
        activeMaps.remove(map);
        timeline.closeMap(map);
        map.dispose();
    }

    private static void setViewScale(float newScale)
    {
        if (newScale > 0.93f && newScale < 1.07f)
            newScale = 1;
        viewScale = Math.min(Math.max(0.1f, newScale), 500.0f);
        viewTime = (int) ((SettingsMaster.getMiddle() + 500) / viewScale);
    }

    private void updateScrollOffset()
    {
        for (EditorBeatmap b : activeMaps) {
            ViewSet set = mapViews.get(b);
            if (set != null) {
                set.setOffset(scrollPos);
            }
        }
    }


    private void seekLeft()
    {
        if (primaryView == null)
        {
            music.seekSecond(currentPos - 1);
            return;
        }

        if (BindingGroup.ctrl())
        {
            NavigableSet<Integer> bookmarks = primaryView.map.getBookmarks();
            if (!bookmarks.isEmpty())
            {
                Integer previous = bookmarks.lower((int) (currentPos * 1000) - 1);

                if (previous != null)
                {
                    music.seekMs(previous);
                }
            }
        }
        else
        {
            if (primaryView.noSnaps())
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
    }
    private void seekRight()
    {
        if (primaryView == null)
        {
            music.seekSecond(currentPos + 1);
            return;
        }

        if (BindingGroup.ctrl())
        {
            NavigableSet<Integer> bookmarks = primaryView.map.getBookmarks();
            if (!bookmarks.isEmpty())
            {
                Integer next = bookmarks.higher((int) (currentPos * 1000) + 1);

                if (next != null)
                {
                    music.seekMs(next);
                }
            }
        }
        else
        {
            if (primaryView.noSnaps())
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
    }
    private void increaseOffset()
    {
        if (BindingGroup.ctrl())
        {
            music.modifyOffset(0.005f);
        }
        else
        {
            music.modifyOffset(0.001f);
        }
        textOverlay.setText("Offset: " + music.getDisplayOffset(), 1.5f);
    }
    private void decreaseOffset()
    {
        if (BindingGroup.ctrl())
        {
            music.modifyOffset(-0.005f);
        }
        else
        {
            music.modifyOffset(-0.001f);
        }
        textOverlay.setText("Offset: " + music.getDisplayOffset(), 1.5f);
    }

    private void toggleFinisher() {
        if (primaryView != null && primaryView.type == MapView.ViewType.OBJECT_VIEW && primaryView.hasSelection()) {
            boolean toFinisher = false;
            List<HitObject> finisher = new ArrayList<>();
            List<HitObject> nonFinisher = new ArrayList<>();
            for (ArrayList<PositionalObject> stack : primaryView.getSelection().values()) {
                for (PositionalObject o : stack) {
                    if (o instanceof HitObject) {
                        if (((HitObject) o).isFinish()) {
                            finisher.add((HitObject) o);
                        }
                        else {
                            nonFinisher.add((HitObject) o); //If there are any non-finisher objects, this will convert them all to finishers.
                            toFinisher = true;
                        }
                        break;
                    }
                }
            }

            primaryView.map.registerChange(new FinisherChange(primaryView.map, toFinisher ? nonFinisher : finisher, toFinisher).perform());
        }
        else {
            finisherLock = !finisherLock;
        }
    }

    public void showText(String text)
    {
        textOverlay.setText(text, 1.0f);
    }


    /// Overlays?
    private void openDifficultyMenu()
    {
        clean();
        TaikoEditor.addLayer(new DifficultyMenuLayer(this, set));
    }
    private void settings()
    {
        clean();
        TaikoEditor.addLayer(new SettingsLayer());
    }

    public void clean() {
        if (music.isPlaying())
            music.pause();

        processor.releaseInput(false);
    }


    public static class EditorProcessor extends TextInputProcessor {
        private final EditorLayer sourceLayer;

        private static final DecimalFormat oneDecimal = new DecimalFormat("##0.#", osuSafe);
        private static final DecimalFormat twoDecimal = new DecimalFormat("##0.##", osuSafe);

        public EditorProcessor(EditorLayer source)
        {
            super(BindingMaster.getBindingGroupCopy("Editor"), true);
            this.sourceLayer = source;
        }

        @Override
        public void bind() {
            //Arrows
            {
                KeyHoldObject left = new KeyHoldObject(NORMAL_FIRST_DELAY, NORMAL_REPEAT_DELAY, sourceLayer::seekLeft);
                KeyHoldObject right = new KeyHoldObject(NORMAL_FIRST_DELAY, NORMAL_REPEAT_DELAY, sourceLayer::seekRight);

                bindings.bind("SeekRight", sourceLayer::seekRight, right);
                bindings.bind("SeekLeft", sourceLayer::seekLeft, left);

                bindings.bind("RateUp", () -> sourceLayer.textOverlay.setText("Playback rate " + twoDecimal.format(music.changeTempo(0.05f)), 1.0f));
                bindings.bind("RateDown", () -> sourceLayer.textOverlay.setText("Playback rate " + twoDecimal.format(music.changeTempo(-0.05f)), 1.0f));

                bindings.bind("ZoomIn", () -> zoom(-1));
                bindings.bind("ZoomOut", () -> zoom(1));

                bindings.bind("SnapUp", () -> changeSnapping(-1));
                bindings.bind("SnapDown", () -> changeSnapping(1));
            }

            bindings.bind("Bookmark", () -> {
                for (EditorBeatmap map : sourceLayer.activeMaps) {
                    map.addBookmark((int) music.getMsTime());
                }
                sourceLayer.timeline.recalculateBookmarks();
            });

            //Selection controls
            {
                bindings.bind("SelectAll", () -> {
                    if (sourceLayer.primaryView != null)
                        sourceLayer.primaryView.selectAll();
                });

                bindings.bind("Copy", () -> {
                    if (sourceLayer.primaryView != null) {
                        if (sourceLayer.primaryView.hasSelection()) {
                            copyObjects = sourceLayer.primaryView.getSelection().copy();
                            copyType = sourceLayer.primaryView.type; //Can only paste into same type of layer.
                            Toolkit.getDefaultToolkit()
                                    .getSystemClipboard()
                                    .setContents(new StringSelection(sourceLayer.primaryView.getSelectionString()), null);
                        } else {
                            Toolkit.getDefaultToolkit()
                                    .getSystemClipboard()
                                    .setContents(new StringSelection(sourceLayer.timeline.getTimeString() + " - "), null);
                        }
                    }
                });

                bindings.bind("Cut", () -> {
                    if (sourceLayer.primaryView != null) {
                        if (sourceLayer.primaryView.hasSelection()) {
                            releaseMouse(true);

                            copyObjects = sourceLayer.primaryView.getSelection().copy();
                            copyType = sourceLayer.primaryView.type; //Can only paste into same type of layer.
                            Toolkit.getDefaultToolkit()
                                    .getSystemClipboard()
                                    .setContents(new StringSelection(sourceLayer.primaryView.getSelectionString()), null);

                            sourceLayer.primaryView.deleteSelection();
                        }
                    }
                });

                bindings.bind("Paste", () -> {
                    if (copyObjects != null && sourceLayer.primaryView != null && sourceLayer.primaryView.type == copyType) {
                        releaseMouse(true);

                        sourceLayer.primaryView.pasteObjects(copyObjects);
                    }
                });

                bindings.bind("ClearSelect", () -> {
                    if (sourceLayer.primaryView != null && sourceLayer.primaryView.hasSelection())
                        sourceLayer.primaryView.clearSelection();
                });

                bindings.bind("Reverse", () -> {
                    if (sourceLayer.primaryView != null && sourceLayer.primaryView.hasSelection()) {
                        releaseMouse(true);

                        sourceLayer.primaryView.reverse();
                    }
                });

                bindings.bind("Resnap", () -> {
                    if (sourceLayer.primaryView != null) {
                        releaseMouse(true);

                        sourceLayer.primaryView.resnap();
                    }
                });

                bindings.bind("Messy", () -> {
                    if (sourceLayer.primaryView != null && sourceLayer.primaryView.hasSelection() && sourceLayer.primaryView instanceof EffectView) {
                        releaseMouse(true);

                        ((EffectView) sourceLayer.primaryView).fuckSelection();
                    }
                });
            }

            bindings.bind("OpenView", sourceLayer::openDifficultyMenu);

            bindings.bind("Save", ()->{
                if (sourceLayer.primaryView != null) {
                    if (sourceLayer.primaryView.map.save()) {
                        sourceLayer.textOverlay.setText("Difficulty \"" + sourceLayer.primaryView.map.getName() + "\" saved!", 0.5f);
                    }
                    else {
                        sourceLayer.textOverlay.setText("Failed to save!", 2.0f);
                    }
                }
            });
            bindings.bind("SaveAll", ()->{
                int failures = 0;
                StringBuilder failed = new StringBuilder();
                for (EditorBeatmap m : sourceLayer.activeMaps) {
                    if (!m.save()) {
                        failed.append(" [").append(m.getName()).append("]");
                        ++failures;
                    }
                }
                if (failures == 0) {
                    sourceLayer.textOverlay.setText("Saved all.", 2.0f);
                }
                else if (failures == 1) {
                    sourceLayer.textOverlay.setText("Failed to save difficulty" + failed + ".", 2.0f);
                }
                else {
                    sourceLayer.textOverlay.setText("Failed to save difficulties" + failed + ".", 2.0f);
                }
            });

            bindings.bind("TJASave", ()->{
                if (sourceLayer.primaryView != null) {
                    if (sourceLayer.primaryView.map.saveTJA()) {
                        sourceLayer.textOverlay.setText("Difficulty \"" + sourceLayer.primaryView.map.getName() + "\" saved as TJA file!", 0.5f);
                    } else {
                        sourceLayer.textOverlay.setText("TJA Save is not yet supported!", 2.0f); //Failed to save!", 2.0f);
                    }
                }
            });

            bindings.bind("FinishLock", sourceLayer::toggleFinisher);

            bindings.bind("Redo", ()->{
                if (sourceLayer.primaryView != null)
                {
                    releaseMouse(true);
                    if (sourceLayer.primaryView.map.redo())
                        sourceLayer.primaryView.refreshSelection();
                }
            });
            bindings.bind("Undo", ()->{
                if (sourceLayer.primaryView != null)
                {
                    releaseMouse(true);
                    if (sourceLayer.primaryView.map.undo())
                        sourceLayer.primaryView.refreshSelection();
                }
            });
            bindings.bind("Delete", ()->{
                if (sourceLayer.primaryView != null)
                {
                    releaseMouse(true);
                    sourceLayer.primaryView.delete(SettingsMaster.getMiddle(), (sourceLayer.primaryView.bottom + sourceLayer.primaryView.top) / 2);
                }
            });

            bindings.bind("IncreaseOffset", sourceLayer::increaseOffset);
            bindings.bind("DecreaseOffset", sourceLayer::decreaseOffset);

            bindings.bind("TogglePlayback", music::toggle);

            bindings.bind("Exit", ()->{
                if (sourceLayer.exitDelay <= 0)
                    sourceLayer.returnToMenu();
            });

            for (int i = 1; i < 10; ++i)
            {
                final int index = i - 1;
                bindings.bind(Integer.toString(i), ()->sourceLayer.tools.selectToolIndex(index));

                if (i > 1) { //instant use bindings
                    bindings.bind("i" + i, ()->{
                        if (sourceLayer.primaryView != null) {
                            MapView view = sourceLayer.primaryView;
                            return ()->{
                                releaseMouse(true);

                                sourceLayer.tools.instantUse(index, view);
                            };
                        }
                        return null;
                    });
                }
            }
            bindings.bind("0", ()->sourceLayer.tools.selectToolIndex(9));

            //NOTE: DEBUG
            /*bindings.bind("DEBUG", ()->{
                if (sourceLayer.primaryView != null)
                {
                    //Current function: Doubles sv in a certain section
                    long startTime = 1034780, endTime = 1078140;
                    SortedMap<Long, ArrayList<TimingPoint>> section = sourceLayer.primaryView.map.effectPoints.subMap(startTime, endTime);

                    for (ArrayList<TimingPoint> point : section.values())
                    {
                        for (TimingPoint p : point)
                        {
                            p.value *= 2;
                        }
                    }
                }
            });*/

            if (DIFFCALC)
                bindings.bind("DIFFCALC", ()->{
                   if (sourceLayer.primaryView != null)
                       sourceLayer.getViewSet(sourceLayer.primaryView.map).calculateDifficulty();
                });

            //------------ Mouse Bindings ------------
            //Top area
            bindings.addMouseBind((x, y, b) -> y > sourceLayer.timelineY,
                    (p, button) -> {
                        if (p.y < sourceLayer.topBarY)
                        {
                            //timeline area
                            return sourceLayer.timeline.click(p.x, p.y);
                        }
                        else
                        {
                            if (sourceLayer.openButton.click(p.x, p.y, button))
                                return null;
                            else if (sourceLayer.settingsButton.click(p.x, p.y, button))
                                return null;
                            else if (sourceLayer.exitButton.click(p.x, p.y, button))
                                return null;
                        }
                        return null;
                    });
            //MapView area
            bindings.addMouseBind((x, y, b) -> y > sourceLayer.minimumVisibleY,
                    (p, button) -> {
                        for (EditorBeatmap m : sourceLayer.activeMaps)
                        {
                            ViewSet set = sourceLayer.mapViews.get(m);
                            if (set != null && set.containsY(p.y))
                            {
                                return set.click(p.x, p.y, button, BindingGroup.modifierState());
                            }
                        }
                        return null;
                    });
            //tools
            bindings.addMouseBind((x, y, b)->true,
                    (p, b) -> {
                        sourceLayer.tools.click(p.x, p.y, b);
                        return null;
                    });
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            queue(()->{if (BindingGroup.ctrl())
            {
                changeSnapping(amountY);
            }
            else if (BindingGroup.shift())
            {
                zoom(amountY);
            }
            else
            {
                if (sourceLayer.verticalScrollEnabled)
                {
                    float gameY = SettingsMaster.gameY();

                    if (gameY < sourceLayer.timelineY && gameY > sourceLayer.minimumVisibleY)
                    {
                        sourceLayer.scrollPos += amountY * 16;

                        if (sourceLayer.scrollPos < 0)
                            sourceLayer.scrollPos = 0;

                        if (sourceLayer.scrollPos > sourceLayer.maxScrollPosition)
                        {
                            sourceLayer.scrollPos = sourceLayer.maxScrollPosition;
                        }

                        sourceLayer.updateScrollOffset();
                        return;
                    }
                }

                if (amountY > 0)
                {
                    sourceLayer.seekRight();
                }
                else
                {
                    sourceLayer.seekLeft();
                }
            }});

            return true;
        }

        private void zoom(float direction) {
            if (direction > 0)
            {
                EditorLayer.setViewScale(EditorLayer.viewScale * 0.90909090909f);
            }
            else
            {
                EditorLayer.setViewScale(EditorLayer.viewScale * 1.1f);
            }
            sourceLayer.textOverlay.setText("View scale: " + oneDecimal.format(viewScale), 1.0f);
        }
        private void changeSnapping(float direction) {
            sourceLayer.divisorOptions.adjust(direction);
            sourceLayer.textOverlay.setText("Snapping: " + sourceLayer.divisorOptions.toString(), 1.0f);
        }
    }
}
