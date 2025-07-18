package alchyr.taikoedit.core.layers;

import alchyr.networking.standard.ConnectionClient;
import alchyr.networking.standard.ConnectionServer;
import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.core.input.KeyHoldObject;
import alchyr.taikoedit.core.input.TextInputProcessor;
import alchyr.taikoedit.core.layers.sub.*;
import alchyr.taikoedit.core.ui.Dropdown;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.core.ui.TextOverlay;
import alchyr.taikoedit.editor.*;
import alchyr.taikoedit.editor.changes.FinisherChange;
import alchyr.taikoedit.editor.changes.MapChange;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.MapInfo;
import alchyr.taikoedit.editor.maps.Mapset;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.editor.views.*;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.LocalizationMaster;
import alchyr.taikoedit.management.MapMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.management.assets.FileHelper;
import alchyr.taikoedit.management.assets.loaders.OsuBackgroundLoader;
import alchyr.taikoedit.management.localization.LocalizedText;
import alchyr.taikoedit.util.FileDropHandler;
import alchyr.taikoedit.util.GeneralUtils;
import alchyr.taikoedit.util.interfaces.functional.VoidMethod;
import alchyr.taikoedit.util.structures.BooleanWrapper;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.List;
import java.util.*;

import static alchyr.taikoedit.TaikoEditor.*;

public class EditorLayer extends LoadedLayer implements InputLayer, FileDropHandler.Handler {
    public static EditorLayer activeEditor = null;

    private static final Set<String> IMAGE_EXTENSIONS;
    static {
        IMAGE_EXTENSIONS = new HashSet<>();
        IMAGE_EXTENSIONS.add("png");
        IMAGE_EXTENSIONS.add("jpg");
        IMAGE_EXTENSIONS.add("jpeg");
    }

    //Return to menu
    private final ProgramLayer src;

    //Input
    public static boolean finisherLock = false;
    public static EditorProcessor processor;
    private float exitDelay = 0.25f;

    /* * * * * * UI ELEMENTS * * * * * */
    private Texture pixel; //General rendering
    private Texture connected; //General rendering
    private BitmapFont aller;

    //Overlays
    public TextOverlay textOverlay;

    //Background
    private static final Color bgColor = new Color(0.3f, 0.3f, 0.25f, 1.0f);
    private String backgroundImg;
    private int bgWidth, bgHeight;
    private Texture background;

    private static final Color CENTER_LINE_COLOR = new Color(0.6f, 0.6f, 0.6f, 0.5f);

    //Top bar
    private int topBarHeight;
    private int topBarY;
    private int timelineY;

    private Dropdown<String> topBarDropdown;

    private final List<Dropdown.DropdownElement<String>> fileOptionsList = new ArrayList<>();
    private final List<Dropdown.DropdownElement<String>> baseSelectionList = new ArrayList<>();
    private final List<Dropdown.DropdownElement<String>> svDropdownList = new ArrayList<>();
    //TODO: More lists based on selection (primary view type)?

    private final List<ImageButton> buttons = new ArrayList<>();
    private ImageButton networkingButton;

    private Timeline timeline; //Timeline

    //Map views
    public MapView primaryView;
    private final HashMap<EditorBeatmap, ViewSet> mapViews;

    //View information
    public static float viewScale = 1.0f;
    public static int viewTime = 1500; //number of milliseconds before and after current position

    //Tools
    public Tools tools;

    //Networking
    private ConnectionServer server = null;
    private ConnectionClient client = null;
    private ClientEditorMessageHandler clientEditorMessageHandler = null;

    //Data
    private LocalizedText keyNames;

    /* * * * * * Beatmap Stuff * * * * * */
    private final ArrayList<EditorBeatmap> activeMaps;

    private final Mapset set;
    private final List<MapInfo> initial = new ArrayList<>();
    private final ArrayList<MapView> addLater;

    private final DivisorOptions divisorOptions; //Always shared
    private BeatDivisors universalDivisor; //Sometimes shared
    private double currentPos; //current second position in song.


    //Copy and Paste
    private static MapObjectTreeMap<MapObject> copyObjects = null;
    private static MapView.ViewType copyType;


    //Vertical scroll, when too many views exist
    private int minimumVisibleY = 0;
    private boolean verticalScrollEnabled = false;
    private int scrollPos = 0, maxScrollPosition = 0;

    public EditorLayer(ProgramLayer src, Mapset set, Collection<MapInfo> initial)
    {
        this.src = src;
        this.set = set;
        this.initial.addAll(initial);

        processor = new EditorProcessor(this);
        backgroundImg = set.getBackground();

        mapViews = new HashMap<>();
        activeMaps = new ArrayList<>();
        addLater = new ArrayList<>();

        divisorOptions = new DivisorOptions();
        divisorOptions.reset();

        this.type = backgroundImg == null || backgroundImg.isEmpty() ? LAYER_TYPE.UPDATE_STOP : LAYER_TYPE.FULL_STOP;
    }

    public void setClient(ConnectionClient client) {
        this.client = client;
    }

    @Override
    public void initialize() {
        activeEditor = this;
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
                FileHandle h = Gdx.files.absolute(backgroundImg);
                if (h.exists()) {
                    try {
                        background = new Texture(Gdx.files.absolute(backgroundImg), true); //these song folders have quite high odds of containing characters libgdx doesn't like, which messes up assetMaster loading.
                        background.setFilter(Texture.TextureFilter.MipMapLinearNearest, Texture.TextureFilter.MipMapLinearNearest);

                        float bgScale = Math.max((float) SettingsMaster.getWidth() / background.getWidth(), (float) SettingsMaster.getHeight() / background.getHeight());
                        bgWidth = (int) Math.ceil(background.getWidth() * bgScale);
                        bgHeight = (int) Math.ceil(background.getHeight() * bgScale);
                    }
                    catch (Exception e) {
                        editorLogger.error("Failed to load background image.", e);
                        if (!OsuBackgroundLoader.loadedBackgrounds.isEmpty())
                        {
                            background = assetMaster.get(OsuBackgroundLoader.loadedBackgrounds.get(MathUtils.random(OsuBackgroundLoader.loadedBackgrounds.size() - 1)));

                            float bgScale = Math.max((float) SettingsMaster.getWidth() / background.getWidth(), (float) SettingsMaster.getHeight() / background.getHeight());
                            bgWidth = (int) Math.ceil(background.getWidth() * bgScale);
                            bgHeight = (int) Math.ceil(background.getHeight() * bgScale);
                        }
                    }
                }
                else if (!OsuBackgroundLoader.loadedBackgrounds.isEmpty()) {
                    background = assetMaster.get(OsuBackgroundLoader.loadedBackgrounds.get(MathUtils.random(OsuBackgroundLoader.loadedBackgrounds.size() - 1)));

                    float bgScale = Math.max((float) SettingsMaster.getWidth() / background.getWidth(), (float) SettingsMaster.getHeight() / background.getHeight());
                    bgWidth = (int) Math.ceil(background.getWidth() * bgScale);
                    bgHeight = (int) Math.ceil(background.getHeight() * bgScale);
                }
            });
        }
        else if (!OsuBackgroundLoader.loadedBackgrounds.isEmpty())
        {
            background = assetMaster.get(OsuBackgroundLoader.loadedBackgrounds.get(MathUtils.random(OsuBackgroundLoader.loadedBackgrounds.size() - 1)));

            float bgScale = Math.max((float) SettingsMaster.getWidth() / background.getWidth(), (float) SettingsMaster.getHeight() / background.getHeight());
            bgWidth = (int) Math.ceil(background.getWidth() * bgScale);
            bgHeight = (int) Math.ceil(background.getHeight() * bgScale);
        }

        aller = assetMaster.getFont("aller medium");

        pixel = assetMaster.get("ui:pixel");
        connected = assetMaster.get("ui:connected");
        topBarHeight = 40;
        timelineY = SettingsMaster.getHeight() - (topBarHeight + Timeline.HEIGHT);
        topBarY = SettingsMaster.getHeight() - topBarHeight;
        topBarHeight += 10;
        //Title/top bar
        //float titleOffsetX = 10;
        //float titleOffsetY = 35;
        minimumVisibleY = Tools.HEIGHT;
        textOverlay = new TextOverlay(aller, SettingsMaster.getHeight() / 2, 100);

        //Top bar
        //NEEDS LOCALIZATION
        buttons.add(new ImageButton(0, topBarY, assetMaster.get("editor:dropdown"), (Texture) assetMaster.get("editor:dropdownh"))
                .setClick(this::moreOptions).setHovered(()->{ hoverText.setText("Files"); }));
        buttons.add(new ImageButton(40, topBarY, assetMaster.get("editor:edit"), (Texture) assetMaster.get("editor:edith"))
                .setClick(this::selectionOptions).setHovered(()->{ hoverText.setText("Edit"); }));

        int buttonX = SettingsMaster.getWidth();
        buttons.add(new ImageButton(buttonX -= 40, topBarY, assetMaster.get("ui:exit"), (Texture) assetMaster.get("ui:exith"))
                .setClick(this::returnToMenu).setHovered(()->{ hoverText.setText("Exit"); }));
        buttons.add(new ImageButton(buttonX -= 40, topBarY, assetMaster.get("ui:settings"), (Texture) assetMaster.get("ui:settingsh"))
                .setClick(this::settings).setHovered(()->{ hoverText.setText("Settings"); }));
        buttons.add(new ImageButton(buttonX -= 40, topBarY, assetMaster.get("editor:open"), (Texture) assetMaster.get("editor:openh"))
                .setClick(this::openDifficultyMenu)
                .setHovered(()->{
                    String input = processor.getBindingInput(keyNames.get(""), "OpenView");
                    hoverText.setText(input == null ? "Open New View" : "Open New View (" + input + ")");
                }));

        if (client == null) {
            networkingButton = new ImageButton(buttonX -= 40, topBarY, assetMaster.get("ui:connect"), (Texture) assetMaster.get("ui:connecth"))
                    .setHovered(()->{ hoverText.setText("Start Hosting"); }).setClick(this::openConnect);
            buttons.add(networkingButton);
        }
        else {
            networkingButton = new ImageButton(buttonX -= 40, topBarY, assetMaster.get("ui:connecth"), (Texture) assetMaster.get("ui:connecth"))
                    .setHovered(()->{ hoverText.setText("Connected as Client"); });
            buttons.add(networkingButton);
        }



        topBarDropdown = new Dropdown<>(Math.min(SettingsMaster.getWidth() / 2, 240));

        //Dropdown contents
        {
            BindingGroup editorBindings = BindingMaster.getBindingGroupCopy("Editor");
            LocalizedText keyNames = LocalizationMaster.getLocalizedText("keys", "names");
            String[] keyNameArray = keyNames == null ? new String[] { } : keyNames.get("");

            fileOptionsList.clear();
            fileOptionsList.add(new Dropdown.ItemElement<>("Save", aller)
                    .setCondition((e) -> {
                        e.setHoverText(editorBindings.getBindingInputString("Save", keyNameArray));
                        return primaryView != null;
                    }).setOnClick((e) -> {
                        savePrimary();
                        return true;
                    })
            );
            fileOptionsList.add(new Dropdown.ItemElement<>("Save All", aller)
                    .setCondition((e) -> {
                        e.setHoverText(editorBindings.getBindingInputString("SaveAll", keyNameArray));
                        return !activeMaps.isEmpty();
                    }).setOnClick((e) -> {
                        saveAll();
                        return true;
                    })
            );
            fileOptionsList.add(new Dropdown.SeparatorElement<>());
            fileOptionsList.add(new Dropdown.ItemElement<>("Open All", aller)
                    .setCondition((e) -> mapViews.size() < set.getMaps().size())
                    .setOnClick((e) -> {
                        openAll(true);
                        return true;
                    })
            );


            baseSelectionList.clear();
            baseSelectionList.add(new Dropdown.ItemElement<>("Undo", aller)
                    .setCondition((e) -> {
                        e.setHoverText(editorBindings.getBindingInputString("Undo", keyNameArray));
                        return primaryView != null && primaryView.map.canUndo();
                    })
                    .setOnClick((e) -> {
                        if (primaryView != null) {
                            primaryView.map.undo();
                        }
                        return true;
                    })
            );
            baseSelectionList.add(new Dropdown.ItemElement<>("Redo", aller)
                    .setCondition((e) -> {
                        e.setHoverText(editorBindings.getBindingInputString("Redo", keyNameArray));
                        return primaryView != null && primaryView.map.canRedo();
                    })
                    .setOnClick((e) -> {
                        if (primaryView != null)
                            primaryView.map.redo();
                        return true;
                    })
            );
            baseSelectionList.add(new Dropdown.SeparatorElement<>());
            baseSelectionList.add(new Dropdown.ItemElement<>("Select All", aller)
                    .setCondition((e) -> {
                        e.setHoverText(editorBindings.getBindingInputString("SelectAll", keyNameArray));
                        return primaryView != null;
                    })
                    .setOnClick((e) -> {
                        if (primaryView != null)
                            primaryView.selectAll();
                        return true;
                    })
            );
            baseSelectionList.add(new Dropdown.ItemElement<>("Clear Selection", aller)
                    .setCondition((e) -> {
                        e.setHoverText(editorBindings.getBindingInputString("ClearSelect", keyNameArray));
                        return primaryView != null && primaryView.hasSelection();
                    })
                    .setOnClick((e) -> {
                        if (primaryView != null)
                            primaryView.clearSelection();
                        return true;
                    })
            );
            baseSelectionList.add(new Dropdown.SeparatorElement<>());
            baseSelectionList.add(new Dropdown.ItemElement<>("Cut", aller)
                    .setCondition((e) -> {
                        e.setHoverText(editorBindings.getBindingInputString("Cut", keyNameArray));
                        return primaryView != null && primaryView.hasSelection();
                    })
                    .setOnClick((e) -> {
                        cutPrimary();
                        return true;
                    })
            );
            baseSelectionList.add(new Dropdown.ItemElement<>("Copy", aller)
                    .setCondition((e) -> {
                        e.setHoverText(editorBindings.getBindingInputString("Copy", keyNameArray));
                        return primaryView != null && primaryView.hasSelection();
                    })
                    .setOnClick((e) -> {
                        copyPrimary();
                        return true;
                    })
            );
            baseSelectionList.add(new Dropdown.ItemElement<>("Paste", aller)
                    .setCondition((e) -> {
                        e.setHoverText(editorBindings.getBindingInputString("Paste", keyNameArray));
                        return copyObjects != null && primaryView != null;
                    })
                    .setOnClick((e) -> {
                        if (copyObjects != null && primaryView != null) {
                            primaryView.pasteObjects(copyType, copyObjects);
                        }
                        return true;
                    })
            );
            baseSelectionList.add(new Dropdown.ItemElement<>("Delete Selection", aller)
                    .setCondition((e) -> {
                        e.setHoverText(editorBindings.getBindingInputString("Delete", keyNameArray));
                        return primaryView != null && primaryView.hasSelection();
                    })
                    .setOnClick((e) -> {
                        if (primaryView != null && primaryView.hasSelection())
                            primaryView.deleteSelection();
                        return true;
                    })
            );

            svDropdownList.clear();
            svDropdownList.add(new Dropdown.SeparatorElement<>());
            svDropdownList.add(new Dropdown.ItemElement<>("Scale SV", aller)
                    .setCondition((e) -> primaryView instanceof EffectView && primaryView.hasSelection())
                    .setOnClick((e) -> {
                        if (primaryView instanceof EffectView && primaryView.hasSelection()) {
                            TaikoEditor.addLayer(new SvScalingLayer(0, 100, (properties)->{
                                if (primaryView != null && primaryView.hasSelection()) {
                                    MapObjectTreeMap<MapObject> greenLines = new MapObjectTreeMap<>();
                                    Map<MapObject, Double> newValueMap = new HashMap<>();

                                    primaryView.getSelection().forEachObject(
                                        (o)->{
                                            if (o instanceof TimingPoint && !((TimingPoint) o).uninherited) {
                                                greenLines.add(o);
                                                newValueMap.put(o,
                                                        ((o.getValue() - properties.scalingPoint) * properties.scalingMult / 100) + properties.scalingPoint
                                                    );
                                            }
                                        }
                                    );

                                    primaryView.map.registerAndPerformValueChange(greenLines, newValueMap);
                                }
                            }));
                            EditorLayer.processor.releaseInput(true);
                        }
                        return true;
                    })
            );
        }

        setViewScale(1.0f);

        //Initialize this
        SvFunctionLayer.init();

        //Editor stuff
        timeline = new Timeline(timelineY, music.getSecondLength());
        tools = new Tools(this);

        if (client != null) {
            openAll(false);
        }

        if (mapViews.isEmpty())
        {
            openDifficultyMenu();
        }
        else //started with a diff open
        {
            organizeViews(); //Positions views and sets primary view, determines scroll
            if (activeMaps.stream().anyMatch(map->!map.autoBreaks)) {
                textOverlay.setText("Map contains invalid breaks; automatic break control disabled.", 2.5f);
            }
        }

        FileDropHandler.set(this);
        MapChange.registerEditorForChanges(this);
        processor.bind();

        if (client != null) {
            clientEditorMessageHandler = new ClientEditorMessageHandler(this, client);
        }
    }

    @Override
    public void update(float elapsed) {
        processor.update(elapsed);

        if (exitDelay > 0)
            exitDelay -= elapsed;

        if (!addLater.isEmpty())
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

        boolean canHover = true;

        timeline.update(currentPos);
        if (topBarDropdown.update())
            canHover = false;

        for (ViewSet views : mapViews.values())
        {
            views.update(currentPos, msTime, elapsed, music.isPlaying(), canHover);
        }

        textOverlay.update(elapsed);
        tools.update(timelineY, minimumVisibleY, activeMaps, mapViews, elapsed);

        for (ImageButton button : buttons) {
            button.update(elapsed);
        }

        if (server != null) {
            server.update();

            if (!server.isAlive()) {
                try {
                    server.close();
                }
                catch (Exception ignored) { }
                server = null;
                for (EditorBeatmap map : activeMaps) {
                    map.setServer(null);
                }
                networkingButton.setTextures(assetMaster.get("ui:connect"), assetMaster.get("ui:connecth"));
                networkingButton.setHovered(()->hoverText.setText("Start Hosting"));
            }
        }
        if (client != null) {
            if (!client.isAlive()) {
                try {
                    client.close();
                } catch (Exception ignored) { }
                String cause = client.failure;
                client = null;
                for (EditorBeatmap map : activeMaps) {
                    map.setClient(null);
                }
                if (cause == null) {
                    textOverlay.setText("Lost connection.", 2.0f);
                }
                else {
                    textOverlay.setText(cause, 2.0f);
                }

                networkingButton.setTextures(assetMaster.get("ui:connect"), assetMaster.get("ui:connecth"));
                networkingButton.setHovered(()->hoverText.setText("Start Hosting")).setClick(this::openConnect);
            }
            else if (clientEditorMessageHandler != null) {
                clientEditorMessageHandler.update(elapsed);
            }
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
        Iterator<EditorBeatmap> mapIterator = activeMaps.iterator();
        EditorBeatmap map;
        while (mapIterator.hasNext()) {
            map = mapIterator.next();
            ViewSet set = mapViews.get(map);
            if (set != null)
                set.render(sb, sr);
            /*else {
                editorLogger.error("MAP IN ACTIVE MAPS WITH NO VIEWS: " + map.getName());
                editorLogger.error("Dirty: " + map.dirty);
                editorLogger.info("Removing from active maps.");
                mapIterator.remove();
            }*/
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

        for (ImageButton button : buttons) {
            button.render(sb, sr);
        }

        timeline.render(sb, sr);

        //Tools
        tools.render(sb, sr);

        //Overlays
        if (server != null) {
            server.renderConnectedNames(textRenderer, sb, connected, aller, SettingsMaster.getWidth() - 16, 0.6f);
        }
        else if (client != null) {
            client.renderConnectedNames(textRenderer, sb, connected, aller, SettingsMaster.getWidth() - 16, 0.6f);
        }

        topBarDropdown.render(sb, sr);
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

    private void moreOptions() {
        if (!topBarDropdown.isOpen() || !topBarDropdown.id.equals("Opt")) {
            topBarDropdown.id = "Opt";
            topBarDropdown.setPos(0, topBarY);
            topBarDropdown.setElements(fileOptionsList).open();
        }
        else {
            topBarDropdown.close();
        }
    }
    private void selectionOptions() {
        if (!topBarDropdown.isOpen() || !topBarDropdown.id.equals("Sel")) {
            topBarDropdown.id = "Sel";
            topBarDropdown.setPos(40, topBarY);
            topBarDropdown.setElements(baseSelectionList);

            if (primaryView != null) {
                switch (primaryView.type) {
                    case EFFECT_VIEW:
                        if (primaryView instanceof EffectView && ((EffectView) primaryView).mode) {
                            topBarDropdown.addElements(svDropdownList);
                        }
                        break;
                }
            }

            topBarDropdown.open();
        }
        else {
            topBarDropdown.close();
        }
    }

    private void returnToMenu()
    {
        clean();

        List<EditorBeatmap> dirtyMaps = new ArrayList<>();
        for (EditorBeatmap map : activeMaps) {
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
                            String err = m.save();
                            if (err != null) {
                                success = false;
                                textOverlay.setText(err, 3.0f);
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
            SettingsMaster.saveMapSettings(this, set);
            closed = true;
            activeEditor = null;
            music.setOffset(0);
            TaikoEditor.removeLayer(this);
            FileDropHandler.remove(this);
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

        if (server != null) {
            try {
                server.close();
            } catch (Exception e) {
                editorLogger.error("Exception occurred while closing server", e);
            }
        }
        if (client != null) {
            try {
                client.close();
            }
            catch (Exception e) {
                editorLogger.error("Exception occurred while closing client", e);
            }
        }
    }

    @Override
    public LoadingLayer getLoader() {
        if (!initial.isEmpty() && !initial.get(0).getSongFile().equals(set.getSongFile())) {
            //Different song
            editorLogger.info("Difficulty has different song, loading audio");
            return new EditorLoadingLayer()
                    .addLayers(true, this)
                    .addTask(this::stopMusic)
                    .newSet().addTask(()->music.loadAsync(initial.get(0).getSongFile(), null))
                    .newSet().addTracker(music::getProgress, ()->music.noTrack() || music.hasMusic(), true) //music loading starts in song select
                    .addFailure(music::noTrack)
                    .addTask(true, ()->{ music.play(); music.pause(); })
                    .addTask(true, ()->music.seekSecond(0))
                    .addTask(true, ()->SettingsMaster.loadMapSettings(EditorLayer.this, set))
                    .addTask(true, this::loadBeatmap);
        }
        else {
            return new EditorLoadingLayer()
                    .addLayers(true, this)
                    .addTask(this::stopMusic)
                    .newSet().addTracker(music::getProgress, ()->music.noTrack() || music.hasMusic(), true) //music loading starts in song select
                    .addFailure(music::noTrack)
                    .addTask(true, ()->{ music.play(); music.pause(); })
                    .addTask(true, ()->music.seekSecond(0))
                    .addTask(true, ()->SettingsMaster.loadMapSettings(EditorLayer.this, set))
                    .addTask(true, this::loadBeatmap);
        }
    }

    public void loadViewsetInfo(String val) {
        if (client != null) return; //Do not open from settings if client exist

        String[] maps = val.split("-");
        for (String map : maps) {
            String[] mapInfo = map.split("\\+");
            if (mapInfo.length < 2)
                continue;
            MapInfo toOpen = set.getMaps().stream().filter((info)->mapInfo[0].equals(safeify(info.getDifficultyName()))).findFirst().orElse(null);
            if (toOpen == null) {
                editorLogger.info("Failed to find previous open difficulty \"" + mapInfo[0] + "\"");
                continue;
            }

            EditorBeatmap newMap = new EditorBeatmap(set, toOpen);
            addMap(newMap);

            for (int i = 1; i < mapInfo.length; ++i) {
                MapView newView = MapView.fromTypeString(mapInfo[i], this, newMap);
                if (newView != null)
                    addView(newView, false);
            }
        }
    }
    public String getViewsetInfo() {
        StringBuilder data = new StringBuilder();
        for (EditorBeatmap map : activeMaps) {
            ViewSet viewset = mapViews.get(map);
            if (viewset != null && !viewset.isEmpty()) {
                if (data.length() != 0)
                    data.append('-');
                data.append(safeify(map.getName()));
                for (MapView view : viewset.getViews())
                    data.append('+').append(view.typeString());
            }
        }
        return data.toString();
    }
    private static String safeify(String s) {
        s = s.replaceAll("[-+]", "");
        return s;
    }

    private void stopMusic()
    {
        music.cancelAsyncFollowup();
        music.pause();
    }

    private void loadBeatmap()
    {
        editorLogger.info("Loading mapset. " + initial.size() + " difficulties initially open.");
        if (!initial.isEmpty()) {
            for (MapInfo toOpen : initial) {
                prepSingleDiff(toOpen, initial.size() == 1);
            }
        }
        else if (activeMaps.isEmpty() && set.getMaps().size() == 1) {
            //No saved editor info, didn't choose an initial map, only one difficulty in set
            prepSingleDiff(set.getMaps().get(0), true);
        }

        //Test code: Load all diffs automatically
        /*for (MapInfo info : set.getMaps())
        {
            EditorBeatmap beatmap = getEditorBeatmap(info);
            addObjectView(beatmap, false);
        }*/

        if (activeMaps.isEmpty()) //no map loaded.
        {
            divisorOptions.set(4);
        }
        else
        {
            backgroundImg = activeMaps.get(0).getFullMapInfo().getBackground(); //no need to refresh bg if no map was loaded.
            divisorOptions.set(activeMaps.get(0).getDefaultDivisor());
        }
        editorLogger.info("Loaded beatmap successfully.");
    }

    private void prepSingleDiff(MapInfo info, boolean toTop) {
        EditorBeatmap mapToPrep = null;
        for (EditorBeatmap map : activeMaps) {
            if (map.is(info)) {
                mapToPrep = map;
                break;
            }
        }
        if (mapToPrep == null) {
            mapToPrep = new EditorBeatmap(set, info);

            if (toTop) {
                addMap(mapToPrep, 0);
            }
            else {
                addMap(mapToPrep);
            }
        }

        ViewSet view = getViewSet(mapToPrep);

        if (view == null) {
            addObjectView(mapToPrep, false);
            addEffectView(mapToPrep, false);
            addView(new GameplayView(this, mapToPrep), false);
        }
    }

    public List<EditorBeatmap> getActiveMaps() {
        return activeMaps;
    }
    public EditorBeatmap getEditorBeatmap(MapInfo info)
    {
        for (EditorBeatmap map : activeMaps)
        {
            if (map.is(info))
                return map;
        }

        try {
            EditorBeatmap newMap = new EditorBeatmap(set, info);
            addMap(newMap);
            return newMap;
        }
        catch (Exception e) {
            editorLogger.error("Failed to load map.", e);
            e.printStackTrace();
            return null;
        }
    }
    public void addMap(EditorBeatmap newMap) {
        addMap(newMap, -1);
    }
    public void addMap(EditorBeatmap newMap, int index) {
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

        if (index < 0 || index > activeMaps.size())
            activeMaps.add(newMap);
        else
            activeMaps.add(index, newMap);

        if (!newMap.autoBreaks && textOverlay != null) {
            textOverlay.setText("Map contains invalid breaks; automatic break control disabled.", 2.5f);
        }

        if (server != null && server.isAlive()) {
            newMap.setServer(server);
        }
        else if (client != null && client.isAlive()) {
            newMap.setClient(client);
        }
    }

    //View control
    private void addObjectView(EditorBeatmap beatmap, boolean delayed)
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
            //if (!mapViews.get(newView.map).contains((o)->o.type == newView.type))
            addLater.add(newView);
            //else
                //newView.dispose();
        }
        else
        {
            if (!mapViews.containsKey(newView.map))
                mapViews.put(newView.map, new ViewSet(this, newView.map));

            //if (!mapViews.get(newView.map).contains((o)->o.type == newView.type)) {
            newView.update(currentPos, Math.round(currentPos * 1000), 0, false);
            mapViews.get(newView.map).addView(newView);
            setPrimaryView(newView);
            //}
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
                            String err = toRemove.map.save();
                            if (err == null) {
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
                                textOverlay.setText(err, 3.0f);
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
        //if initialized
        if (timeline != null) {
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
    }

    //Menu option methods
    public boolean savePrimary() {
        if (primaryView != null) {
            String err = primaryView.map.save();
            if (err == null) {
                textOverlay.setText("Difficulty \"" + primaryView.map.getName() + "\" saved!", 0.5f);
                return true;
            }
            else {
                textOverlay.setText(err, 3.0f);
            }
        }
        return false;
    }
    public boolean saveAll() {
        return saveAll(true);
    }
    public boolean saveAll(boolean withOverlay) {
        int failures = 0;
        StringBuilder failed = new StringBuilder();
        for (EditorBeatmap m : activeMaps) {
            String err = m.save();
            if (err != null) {
                failed.append(" [").append(m.getName()).append("]");
                ++failures;
            }
        }
        if (withOverlay) {
            if (failures == 0) {
                textOverlay.setText("Saved all.", 2.0f);
            }
            else if (failures == 1) {
                textOverlay.setText("Failed to save difficulty" + failed + ".", 2.0f);
            }
            else {
                textOverlay.setText("Failed to save difficulties" + failed + ".", 2.0f);
            }
        }
        return failures == 0;
    }
    private void openAll(boolean withView) {
        List<MapInfo> toOpen = new ArrayList<>(set.getMaps());
        toOpen.removeIf(info -> {
            for (EditorBeatmap map : activeMaps) {
                if (map.is(info))
                    return true;
            }
            return false;
        });

        for (MapInfo info : toOpen) {
            EditorBeatmap newMap = new EditorBeatmap(set, info);
            addMap(newMap);

            if (withView)
                addObjectView(newMap, true);
        }
    }
    private void cutPrimary() {
        if (primaryView != null && primaryView.hasSelection()) {
            copyObjects = primaryView.getSelection().copy();
            copyType = primaryView.type; //Can only paste into same type of view.
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new StringSelection(primaryView.getSelectionString()), null);

            primaryView.deleteSelection();
        }
    }
    private void copyPrimary() {
        if (primaryView != null && primaryView.hasSelection()) {
            copyObjects = primaryView.getSelection().copy();
            copyType = primaryView.type; //Can only paste into same type of view.
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new StringSelection(primaryView.getSelectionString()), null);
        }
    }

    private void disposeMap(EditorBeatmap map) {
        mapViews.remove(map);
        activeMaps.remove(map);
        if (universalDivisor != null && universalDivisor.usesMap(map)) {
            if (activeMaps.isEmpty()) {
                universalDivisor.dispose();
                universalDivisor = null;
            }
            else {
                universalDivisor.setTimingMap(activeMaps.get(0));
            }
        }
        timeline.closeMap(map);
        map.dispose();
    }

    private static void setViewScale(float newScale)
    {
        if (newScale > 0.93f && newScale < 1.07f)
            newScale = 1;
        viewScale = Math.min(Math.max(0.001f, newScale), 500.0f);
        viewTime = (int) ((SettingsMaster.getMiddleX() + 500) / viewScale);
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
                Snap s = primaryView.getPreviousSnap((long) (currentPos * 1000));
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
    private void jumpLeft()
    {
        if (primaryView == null || primaryView.noSnaps())
        {
            music.seekSecond(currentPos - 1);
            return;
        }
        Snap s = primaryView.getClosestSnap((currentPos - 1) * 1000, 900);
        if (s == null)
            s = primaryView.getPreviousSnap((long) (currentPos * 1000));

        if (s == null)
        {
            music.seekSecond(0);
        }
        else
        {
            music.seekMs(s.pos);
        }
    }
    private void seekRight()
    {
        //TaikoEditor.onMain(()->{
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
                    Snap s = primaryView.getNextSnap((long) (currentPos * 1000));
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
        //});
    }
    private void jumpRight()
    {
        if (primaryView == null || primaryView.noSnaps())
        {
            music.seekSecond(currentPos + 1);
            return;
        }
        Snap s = primaryView.getClosestSnap((currentPos + 1) * 1000, 900);
        if (s == null)
            s = primaryView.getNextSnap((long) (currentPos * 1000));

        if (s == null)
        {
            music.seekSecond(music.getSecondLength());
        }
        else
        {
            music.seekMs(s.pos);
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

    private void increaseWaveformOffset()
    {
        if (BindingGroup.ctrl())
        {
            SettingsMaster.waveformOffset += 5;
        }
        else
        {
            SettingsMaster.waveformOffset += 1;
        }
        SettingsMaster.saveGeneralSettings();
        textOverlay.setText("Waveform Offset: " + SettingsMaster.waveformOffset, 1.5f);
    }
    private void decreaseWaveformOffset()
    {
        if (BindingGroup.ctrl())
        {
            SettingsMaster.waveformOffset -= 5;
        }
        else
        {
            SettingsMaster.waveformOffset -= 1;
        }
        SettingsMaster.saveGeneralSettings();
        textOverlay.setText("Waveform Offset: " + SettingsMaster.waveformOffset, 1.5f);
    }

    private void nudgeSelection(int ms) {
        if (primaryView != null && primaryView.hasSelection()) {
            MapObjectTreeMap<MapObject> movementCopy = new MapObjectTreeMap<>();
            movementCopy.addAll(primaryView.getSelection());
            primaryView.map.registerAndPerformObjectMovement(movementCopy, ms);
        }
    }

    private void toggleFinisher() {
        if (primaryView != null && primaryView.type == MapView.ViewType.OBJECT_VIEW && primaryView.hasSelection()) {
            boolean toFinisher = false;
            List<HitObject> finisher = new ArrayList<>();
            List<HitObject> nonFinisher = new ArrayList<>();
            for (ArrayList<MapObject> stack : primaryView.getSelection().values()) {
                for (MapObject o : stack) {
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

            primaryView.map.registerChange(new FinisherChange(primaryView.map, toFinisher ? nonFinisher : finisher, toFinisher).preDo());
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
        TaikoEditor.addLayer(new DifficultyMenuLayer(this, set, (client == null && server == null)));
    }
    private void settings()
    {
        clean();
        TaikoEditor.addLayer(new SettingsLayer(background));
    }

    public void clean() {
        if (music.isPlaying())
            music.pause();

        if (topBarDropdown != null)
            topBarDropdown.close();

        processor.releaseInput(false);
    }

    //Utility method(s)
    private boolean objectExists(int x, int y) {
        for (EditorBeatmap m : activeMaps)
        {
            ViewSet set = mapViews.get(m);
            if (set != null && set.containsY(y))
            {
                if (set.objectExists(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void receiveFiles(String[] files) {
        //Receive a dropped image file and set it as the background.
        for (String path : files) {
            File f = new File(path);
            if (!f.exists()) continue;
            if (!f.isFile()) continue;
            if (!f.canRead()) continue;

            String extension = FileHelper.getFileExtension(f.getName());

            if (!IMAGE_EXTENSIONS.contains(extension)) {
                showText("File is not a valid image file.");
                continue;
            }

            File destination = new File(set.getDirectory(), f.getName());
            try {
                Files.copy(f.toPath(), destination.toPath());
            } catch (IOException e) {
                showText("Failed to copy image to map folder.");
                GeneralUtils.logStackTrace(editorLogger, e);
                return;
            }

            if (!destination.exists()) {
                showText("Failed to copy image to map folder.");
                return;
            }

            clean();
            TaikoEditor.addLayer(new ConfirmationLayer("Use background for all difficulties?", "Use For All", "Use For Open", true)
                    .onConfirm(()->{
                        backgroundImg = destination.getAbsolutePath();
                        List<MapInfo> allMaps = new ArrayList<>(set.getMaps());
                        for (EditorBeatmap map : activeMaps) {
                            map.getFullMapInfo().setBackground(backgroundImg, destination.getName());
                            allMaps.remove(map.getFullMapInfo().getInfo());
                        }

                        for (MapInfo info : allMaps) {
                            EditorBeatmap temp = new EditorBeatmap(set, info);
                            temp.getFullMapInfo().setBackground(backgroundImg, destination.getName());
                            temp.save();
                        }


                        set.setBackground(backgroundImg);
                        MapMaster.mapDatabase.save();

                        updateEditorBg();
                    })
                    .onDeny(()->{
                        backgroundImg = destination.getAbsolutePath();
                        for (EditorBeatmap map : activeMaps) {
                            map.getFullMapInfo().setBackground(backgroundImg, destination.getName());
                        }
                        if (set.getBackground() == null) {
                            set.setBackground(backgroundImg);
                            MapMaster.mapDatabase.save();
                        }
                        updateEditorBg();
                    }));
            return;
        }
    }

    private void updateEditorBg() {
        TaikoEditor.onMain(()->{
            FileHandle h = Gdx.files.absolute(backgroundImg);
            if (h.exists()) {
                try {
                    background = new Texture(Gdx.files.absolute(backgroundImg), true); //these song folders have quite high odds of containing characters libgdx doesn't like, which messes up assetMaster loading.
                    background.setFilter(Texture.TextureFilter.MipMapLinearNearest, Texture.TextureFilter.MipMapLinearNearest);

                    float bgScale = Math.max((float) SettingsMaster.getWidth() / background.getWidth(), (float) SettingsMaster.getHeight() / background.getHeight());
                    bgWidth = (int) Math.ceil(background.getWidth() * bgScale);
                    bgHeight = (int) Math.ceil(background.getHeight() * bgScale);
                }
                catch (Exception e) {
                    editorLogger.error("Failed to load background image.", e);
                    if (!OsuBackgroundLoader.loadedBackgrounds.isEmpty())
                    {
                        background = assetMaster.get(OsuBackgroundLoader.loadedBackgrounds.get(MathUtils.random(OsuBackgroundLoader.loadedBackgrounds.size() - 1)));

                        float bgScale = Math.max((float) SettingsMaster.getWidth() / background.getWidth(), (float) SettingsMaster.getHeight() / background.getHeight());
                        bgWidth = (int) Math.ceil(background.getWidth() * bgScale);
                        bgHeight = (int) Math.ceil(background.getHeight() * bgScale);
                    }
                }
            }
        });
    }


    //Networking Stuff
    private void openConnect() {
        if (server != null && server.isAlive()) {
            return;
        }

        clean();

        //Open text prompt for port, then open server on that port
        TaikoEditor.addLayer(new ServerSetupLayer(30000, (port, clientLimit)->{
            try {
                server = new ConnectionServer(SettingsMaster.NAME, port, clientLimit);

                networkingButton.setTextures(assetMaster.get("ui:connecth"), assetMaster.get("ui:connecth"));
                networkingButton.setHovered(()->{ hoverText.setText("Server Open"); });

                Toolkit.getDefaultToolkit()
                        .getSystemClipboard()
                        .setContents(new StringSelection(server.getConnectionText()), null);
                textOverlay.setText("Copied pass to clipboard.", 2.0f);


                openAll(false);


                for (EditorBeatmap map : activeMaps) {
                    map.setServer(server);
                }


                server.registerEventTrigger(ConnectionServer.EVENT_NEW_CLIENT, (params)->{
                    ConnectionClient client = (ConnectionClient) params[0];

                    client.send("MPR" + set.getCreator());
                    client.send("ART" + set.getArtist());
                    client.send("TTL" + set.getTitle());

                    BooleanWrapper clientConfirmed = new BooleanWrapper(false);

                    server.registerEventTrigger("CLIENT_READY" + client.ID, (ignored)->{
                        clientConfirmed.value = true;
                        return true; //remove after triggered
                    });

                    List<ConnectionClient> otherClients = new ArrayList<>(server.getClients());
                    otherClients.remove(client);

                    for (ConnectionClient otherClient : otherClients) {
                        otherClient.send(ConnectionServer.EVENT_SENT + "WAITJOIN" + "|" + client.ID);
                    }

                    TaikoEditor.addLayer(new WaitLayer("Sending map to client...", ()->{
                            if (clientConfirmed.value) {
                                for (ConnectionClient otherClient : otherClients) {
                                    otherClient.send(ConnectionServer.EVENT_SENT + "JOIN_SUCCESS" + "|" + client.ID);
                                }
                                return true;
                            }
                            else if (!client.isAlive()) {
                                for (ConnectionClient otherClient : otherClients) {
                                    otherClient.send(ConnectionServer.EVENT_SENT + "JOIN_FAIL" + "|" + client.ID);
                                }
                                return true;
                            }
                            return false;
                        }).onCancel(()->{
                            try {
                                client.close();
                            } catch (Exception e) {
                                editorLogger.error("Exception occurred while closing connection to client", e);
                            }
                            for (ConnectionClient otherClient : otherClients) {
                                otherClient.send(ConnectionServer.EVENT_SENT + "READY");
                            }
                        })
                    );
                    EditorLayer.processor.releaseInput(true);
                    return false;
                });

                server.registerEventTrigger(ConnectionServer.EVENT_FILE_REQ, (params)->{
                    ConnectionClient client = (ConnectionClient) params[0];
                    String pass = params[1].toString();
                    String req = params[2].toString();
                    switch (req) {
                        case "AUDIO":
                            String songFilePath = set.getSongFile();
                            if (songFilePath.isEmpty()) {
                                //>:(
                                client.send("FAIL");
                                break;
                            }
                            FileHandle handle = Gdx.files.absolute(songFilePath);
                            if (!handle.exists()) {
                                client.send("FAIL");
                                break;
                            }

                            client.sendFile(pass, handle);
                            break;
                        case "MAPS":
                            if (!saveAll()) {
                                editorLogger.warn("Didn't save successfully, might result in desync");
                            }
                            for (ConnectionClient otherClient : server.getClients()) {
                                if (!otherClient.equals(client)) {
                                    otherClient.send(ConnectionServer.EVENT_SENT + "RESET_STATE");
                                }
                            }
                            for (EditorBeatmap map : activeMaps) {
                                map.clearState();
                                map.keyMapObjects();
                            }
                            List<MapInfo> maps = set.getMaps();
                            for (MapInfo map : maps) {
                                client.sendFile(pass, new FileHandle(map.getMapFile()));
                            }
                            client.send("DONE");
                            break;
                    }
                    return false;
                });

                server.registerEventTrigger("EDITORSTATE", (params)->{
                    ConnectionClient client = (ConnectionClient) params[0];

                    client.send(String.format(Locale.US,"POSN%.3f", currentPos));

                    for (EditorBeatmap map : activeMaps) {
                        if (mapViews.get(map) != null) {
                            String mapKey = GeneralUtils.generateCode(4);
                            client.send("DIFF" + mapKey + map.getName());
                        }

                        //ok you know what frick syncing undo/redo queue that's too much of a pain
                    }
                    client.send("DONE");
                    return false;
                });

                server.setMessageHandler(new ServerEditorMessageHandler());
            }
            catch (Exception e) {
                editorLogger.error("Failed to start ConnectionServer", e);
            }
        }));
        EditorLayer.processor.releaseInput(true);
    }

    public static class EditorProcessor extends TextInputProcessor {
        private final EditorLayer sourceLayer;

        private static final DecimalFormat oneDecimal = new DecimalFormat("##0.#", osuDecimalFormat);
        private static final DecimalFormat twoDecimal = new DecimalFormat("##0.##", osuDecimalFormat);

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

                bindings.bind("NudgeRight", () -> sourceLayer.nudgeSelection(1));
                bindings.bind("NudgeLeft", () -> sourceLayer.nudgeSelection(-1));

                bindings.bind("RateUp", () -> sourceLayer.textOverlay.setText("Playback rate " + twoDecimal.format(music.changeTempo(0.05f)), 1.0f));
                bindings.bind("RateDown", () -> sourceLayer.textOverlay.setText("Playback rate " + twoDecimal.format(music.changeTempo(-0.05f)), 1.0f));

                bindings.bind("ZoomIn", () -> zoom(-1));
                bindings.bind("ZoomOut", () -> zoom(1));

                bindings.bind("SnapUpNew", () -> changeSnapping(-1));
                bindings.bind("SnapDownNew", () -> changeSnapping(1));

                bindings.bind("MoveViewUp", this::moveViewUp);
                bindings.bind("MoveViewDown", this::moveViewDown);
            }

            bindings.bind("Bookmark", () -> {
                for (EditorBeatmap map : sourceLayer.activeMaps) {
                    map.addBookmark((int) music.getMsTime());
                }
                sourceLayer.timeline.recalculateBookmarks();
            });
            bindings.bind("RemoveBookmark", () -> {
                for (EditorBeatmap map : sourceLayer.activeMaps) {
                    map.removeBookmark((int) music.getMsTime());
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
                            sourceLayer.copyPrimary();
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

                            sourceLayer.cutPrimary();
                        }
                    }
                });

                bindings.bind("Paste", () -> {
                    if (copyObjects != null && sourceLayer.primaryView != null) {
                        releaseMouse(true);

                        sourceLayer.primaryView.pasteObjects(copyType, copyObjects);
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

                bindings.bind("Invert", () -> {
                    if (sourceLayer.primaryView != null && sourceLayer.primaryView.hasSelection()) {
                        releaseMouse(true);

                        sourceLayer.primaryView.invert();
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

            bindings.bind("Save", sourceLayer::savePrimary);
            bindings.bind("SaveAll", (VoidMethod) sourceLayer::saveAll);

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
                    sourceLayer.primaryView.map.redo();
                }
            });
            bindings.bind("Undo", ()->{
                if (sourceLayer.primaryView != null)
                {
                    releaseMouse(true);
                    sourceLayer.primaryView.map.undo();
                }
            });
            bindings.bind("Delete", ()->{
                if (sourceLayer.primaryView != null)
                {
                    releaseMouse(true);
                    sourceLayer.primaryView.delete(SettingsMaster.getMiddleX(), (sourceLayer.primaryView.bottom + sourceLayer.primaryView.top) / 2);
                }
            });

            bindings.bind("IncreaseOffset", sourceLayer::increaseOffset);
            bindings.bind("DecreaseOffset", sourceLayer::decreaseOffset);

            bindings.bind("IncreaseWaveformOffset", sourceLayer::increaseWaveformOffset);
            bindings.bind("DecreaseWaveformOffset", sourceLayer::decreaseWaveformOffset);

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
                    //Wavetapper.generate(sourceLayer.primaryView.map);
                    //Current function: Doubles sv in a certain section
                    long startTime = 453214, endTime = 490691;
                    SortedMap<Long, ArrayList<TimingPoint>> section = sourceLayer.primaryView.map.effectPoints.subMap(startTime, endTime);

                    for (ArrayList<TimingPoint> point : section.values())
                    {
                        for (TimingPoint p : point)
                        {
                            p.setValue(p.value * 2);
                        }
                    }
                }
            });*/

            if (DIFFCALC)
                bindings.bind("DIFFCALC", ()->{
                   if (sourceLayer.primaryView != null)
                       sourceLayer.getViewSet(sourceLayer.primaryView.map).calculateDifficulty();
                });

            bindings.bind("PositionLines", ()->{
                if (sourceLayer.primaryView != null) {
                    sourceLayer.clean();
                    TaikoEditor.addLayer(new LinePositioningLayer(this.sourceLayer, sourceLayer.primaryView.map));
                }
            });

            //------------ Mouse Bindings ------------
            //Dropdown
            bindings.addMouseBind((x, y, b) -> sourceLayer.topBarDropdown.isOpen() && sourceLayer.topBarDropdown.contains(x, y),
                    (p, button) -> {
                        sourceLayer.topBarDropdown.click(p.x, p.y);
                        return null;
                    });
            //Top area
            bindings.addMouseBind((x, y, b) -> y > sourceLayer.timelineY,
                    (p, button) -> {
                        if (p.y < sourceLayer.topBarY)
                        {
                            //timeline area
                            sourceLayer.topBarDropdown.close();
                            return sourceLayer.timeline.click(p.x, p.y);
                        }
                        else //Top bar
                        {
                            sourceLayer.topBarDropdown.close();
                            for (ImageButton btn : sourceLayer.buttons) {
                                if (btn.click(p.x, p.y, button)) {
                                    return null;
                                }
                            }
                        }
                        return null;
                    });
            //MapView area
            bindings.addMouseBind((x, y, b) -> y > sourceLayer.minimumVisibleY,
                    (p, button) -> {
                        sourceLayer.topBarDropdown.close();
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
            bindings.addDoubleClick((x, y, b) -> (b == Input.Buttons.LEFT)
                            && (y > sourceLayer.minimumVisibleY && y <= sourceLayer.timelineY)
                            && (sourceLayer.objectExists(x, y)),
                    (p, button) -> {
                        sourceLayer.topBarDropdown.close();
                        for (EditorBeatmap m : sourceLayer.activeMaps)
                        {
                            ViewSet set = sourceLayer.mapViews.get(m);
                            if (set == null || !set.containsY(p.y))
                                continue;

                            MapView clicked = set.getView(p.y);
                            if (clicked == null)
                                continue;

                            MapObject o = clicked.getObjectAt(p.x, p.y);
                            if (o == null)
                                continue;

                            music.seekMs(o.getPos());
                        }
                    });
            //tools
            bindings.addMouseBind((x, y, b)->true,
                    (p, b) -> {
                        sourceLayer.topBarDropdown.close();
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
                        sourceLayer.scrollPos += amountY * 50;

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

                if (BindingGroup.alt()) {
                    if (amountY > 0)
                    {
                        sourceLayer.jumpRight();
                    }
                    else
                    {
                        sourceLayer.jumpLeft();
                    }
                }
                else {
                    if (amountY > 0)
                    {
                        sourceLayer.seekRight();
                    }
                    else
                    {
                        sourceLayer.seekLeft();
                    }
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
            sourceLayer.divisorOptions.adjust(direction, BindingGroup.shift());
            sourceLayer.textOverlay.setText("Snapping: " + sourceLayer.divisorOptions, 1.0f);
        }

        private void moveViewUp() {
            EditorLayer editor = sourceLayer;
            onMain(()->{
                if (editor.primaryView != null) {
                    //If top view in set, move entire set up
                    //Otherwise, move up within set
                    ViewSet set = editor.mapViews.get(editor.primaryView.map);
                    if (set == null) return;

                    int setIndex = editor.activeMaps.indexOf(editor.primaryView.map);
                    if (setIndex < 0) return;

                    int viewIndex = set.getViews().indexOf(editor.primaryView);
                    if (viewIndex < 0) return;

                    if (viewIndex > 0) {
                        set.getViews().remove(viewIndex);
                        set.insertView(editor.primaryView, viewIndex - 1);
                    }
                    else if (setIndex > 0) {
                        editor.activeMaps.remove(editor.primaryView.map);
                        editor.activeMaps.add(setIndex - 1, editor.primaryView.map);
                    }
                    sourceLayer.organizeViews();
                }
            });
        }
        private void moveViewDown() {
            EditorLayer editor = sourceLayer;
            onMain(()->{
                if (editor.primaryView != null) {
                    //If top view in set, move entire set up
                    //Otherwise, move up within set
                    ViewSet set = editor.mapViews.get(editor.primaryView.map);
                    if (set == null) return;

                    int setIndex = editor.activeMaps.indexOf(editor.primaryView.map);
                    if (setIndex < 0) return;

                    int viewIndex = set.getViews().indexOf(editor.primaryView);
                    if (viewIndex < 0) return;

                    if (viewIndex + 1 < set.getViews().size()) {
                        set.getViews().remove(viewIndex);
                        set.insertView(editor.primaryView, viewIndex + 1);
                    }
                    else if (setIndex + 1 < editor.activeMaps.size()) {
                        editor.activeMaps.remove(editor.primaryView.map);
                        editor.activeMaps.add(setIndex + 1, editor.primaryView.map);
                    }
                    sourceLayer.organizeViews();
                }
            });
        }
    }
}
