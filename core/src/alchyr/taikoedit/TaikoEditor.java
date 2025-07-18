package alchyr.taikoedit;

import alchyr.taikoedit.audio.MusicWrapper;
import alchyr.taikoedit.audio.mp3.PreloadedMp3;
import alchyr.taikoedit.audio.ogg.PreloadOgg;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.layers.EditorLoadingLayer;
import alchyr.taikoedit.core.layers.LoadingLayer;
import alchyr.taikoedit.core.layers.MenuLayer;
import alchyr.taikoedit.core.layers.sub.SvFunctionLayer;
import alchyr.taikoedit.core.ui.CursorHoverText;
import alchyr.taikoedit.editor.changes.MapChange;
import alchyr.taikoedit.editor.maps.MapInfo;
import alchyr.taikoedit.editor.maps.Mapset;
import alchyr.taikoedit.management.*;
import alchyr.taikoedit.management.assets.skins.Skins;
import alchyr.taikoedit.util.EventWindowListener;
import alchyr.taikoedit.util.RunningAverage;
import alchyr.taikoedit.util.Sync;
import alchyr.taikoedit.util.TextRenderer;
import alchyr.taikoedit.util.interfaces.functional.VoidMethod;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.backends.lwjgl3.audio.DeviceSwapping;
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALLwjgl3Audio;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.StreamUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

import static alchyr.taikoedit.management.assets.skins.Skins.currentSkin;
import static org.lwjgl.glfw.GLFW.glfwGetTime;

public class TaikoEditor extends ApplicationAdapter implements EventWindowListener.WindowEventReceiver {
    public static final int VERSION = 406; //x.x.x -> xxx

    public static final boolean DIFFCALC = false; //ctrl+alt+d

    public static final Logger editorLogger = LogManager.getLogger("TaikoEditor");

    public static DecimalFormatSymbols osuDecimalFormat;

    static {
        osuDecimalFormat = new DecimalFormatSymbols(Locale.US);
        osuDecimalFormat.setDecimalSeparator('.');
    }

    private SpriteBatch sb;
    private ShapeRenderer sr;

    public static AssetMaster assetMaster;
    public static AudioMaster audioMaster;
    public static TextRenderer textRenderer;

    public static MusicWrapper music;

    public static CursorHoverText hoverText;

    private static Cursor hiddenCursor;
    private static Cursor defaultCursor;

    public final static List<ProgramLayer> layers = new ArrayList<>();

    private static final List<ProgramLayer> addTopLayers = new ArrayList<>();
    private static final List<ProgramLayer> addBottomLayers = new ArrayList<>();
        private static final List<ProgramLayer> removeLayers = new ArrayList<>();
        private static final List<ProgramLayer> disposeLayers = new ArrayList<>();

    private static InputMultiplexer input = new InputMultiplexer();

    private static final Queue<VoidMethod> actionQueue = new ConcurrentLinkedQueue<>();

    private static volatile boolean end = false;

    //launch info
    private final int launchWidth, launchHeight;
    private final boolean borderless;
    private final String directOpen;

    //update/framerate control
    private Thread updateThread;
    private boolean paused;
    private final RunningAverage fpsLimitTracker = new RunningAverage(20);
    private float frameTimer, targetFrameDuration = 0; //setForegroundFps was added to lwjgl3 config
    private int updateCount = 0;

    private static final ReentrantLock layerLock = new ReentrantLock();

    int renderLayer = 0;

    private final boolean useFastMenu;

    private static final Sync sync = new Sync();

    public TaikoEditor(int width, int height, int targetFps, boolean borderless, boolean fastMenu, String directOpen) {
        if (targetFps > 0) {
            this.frameTimer = this.targetFrameDuration = 1.0f / targetFps; //1 second / number of frames per second
        }
        fpsLimitTracker.init(1.0f / 60.0f);

        launchWidth = width;
        launchHeight = height;
        this.borderless = borderless;

        this.useFastMenu = fastMenu;
        this.directOpen = directOpen;
    }

    @Override
    public void create() {
        createCursors();
        hideCursor();

        end = false;

        if (assetMaster != null) //shouldn't happen...
        {
            assetMaster.dispose();
        }
        assetMaster = new AssetMaster();
        audioMaster = new AudioMaster();

        if (sb != null)
            sb.dispose();
        if (sr != null)
            sr.dispose();

        layers.clear();
        addTopLayers.clear();
        addBottomLayers.clear();
        removeLayers.clear();

        actionQueue.clear();

        textRenderer = new TextRenderer();

        //set up input
        input = new InputMultiplexer();
        Gdx.input.setInputProcessor(input);

        //A U D I O
        ((OpenALLwjgl3Audio) Gdx.audio).registerMusic("mp3", PreloadedMp3.class);
        ((OpenALLwjgl3Audio) Gdx.audio).registerMusic("ogg", PreloadOgg.class);
        music = new MusicWrapper();


        hoverText = new CursorHoverText();
    }

    @Override
    public void subscribe(EventWindowListener eventWindowListener) {
        eventWindowListener.listen(EventWindowListener.FOCUS_GAIN, ()->{
            paused = false;
        });
        eventWindowListener.listen(EventWindowListener.FOCUS_LOST, ()->{
            paused = true;
        });
    }

    private void postCreate() {
        //Gdx.graphics.setVSync(true);
        Gdx.graphics.setVSync(false);
        if (launchWidth != -1 && launchHeight != -1) {
            if (borderless) {
                //borderless
                Gdx.graphics.setUndecorated(true); //updating size directly in the create method results in issues
                Graphics.DisplayMode displayMode = Gdx.graphics.getDisplayMode();
                //OnionExtension.setBorderlessFullscreen((Lwjgl3Graphics) Gdx.graphics, displayMode.width, displayMode.height + 1); //little +1 to avoid certain fullscreen mode
                Gdx.graphics.setWindowedMode(displayMode.width, displayMode.height + 1);
                SettingsMaster.updateDimensions(displayMode, displayMode.width, displayMode.height + 1);
            }
            else {
                //windowed
                Gdx.graphics.setWindowedMode(launchWidth, launchHeight);
                SettingsMaster.updateDimensions(null, launchWidth, launchHeight);
                /*((Lwjgl3Graphics)Gdx.graphics).getWindow().setPosition( //auto-repositioning was added
                        ((Lwjgl3Graphics)Gdx.graphics).getWindow().getPositionX() + 200 - (launchWidth / 2),
                        ((Lwjgl3Graphics)Gdx.graphics).getWindow().getPositionY() + 200 - (launchHeight / 2));*/
            }
        }
        else {
            SettingsMaster.updateDimensions();
        }


        sb = new SpriteBatch();
        sr = new ShapeRenderer();

        assetMaster.loadAssetLists();
        assetMaster.addSpecialLoaders();
        SettingsMaster.loadGeneralSettings();
        LocalizationMaster.loadDefaultFolder();

        BindingMaster.initialize();

        MapChange.registerMapChanges();

        if (directOpen != null) {
            //Check if valid. If it isn't, error message and end.
            if (!open(directOpen)) {
                editorLogger.error("Map not found: " + directOpen);
                fileError("Map not found: " + directOpen, "failure");
                end();
                return;
            }
        }
        else {
            addLayer(new MenuLayer(useFastMenu).getLoader());
        }

        //Setup and start update thread
        updateThread = new Thread(() -> {
            try {
                if (lastTime == 0)
                    lastTime = getTime();

                double time;
                float elapsed;
                while (!end) {
                    sync.sync(paused ? 24 : 480, paused ? 25 : 2);

                    layerLock.lock();

                    time = getTime();
                    elapsed = (float)(time - lastTime);
                    music.update(time - lastTime);
                    updateLayers();
                    renderLayer = gameUpdate(elapsed);
                    layerLock.unlock();

                    DeviceSwapping.updateActiveDevice(elapsed);
                    ++updateCount;

                    lastTime = time;
                }
            } catch (Exception e) {
                e.printStackTrace();

                while (layerLock.getHoldCount() > 0)
                    layerLock.unlock();

                try {
                    PrintWriter pWriter = null;

                    try {
                        StringBuilder crashlog = new StringBuilder("Version: ").append(TaikoEditor.VERSION);
                        crashlog.append("\nError occurred during update:\n");
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        crashlog.append(sw);
                        sw.close();

                        if (EditorLayer.activeEditor != null) {
                            crashlog.append("\n\nActive editor detected.\n");
                            try {
                                if (EditorLayer.activeEditor.saveAll(false)) {
                                    crashlog.append("Saved map successfully.\n");
                                }
                                else {
                                    crashlog.append("Failed to save map.");
                                }
                            }
                            catch (Exception ignored) {
                                crashlog.append("Failed to save map.");
                            }
                            EditorLayer.activeEditor = null;
                        }

                        File f = new File("error.txt");

                        pWriter = new PrintWriter(f);
                        pWriter.println(crashlog);

                        JOptionPane.showMessageDialog(null, crashlog, "Error", JOptionPane.ERROR_MESSAGE);
                    }
                    catch (Exception ex) {
                        Thread.sleep(1500);
                    }
                    finally {
                        StreamUtils.closeQuietly(pWriter);
                    }
                }
                catch (Exception ignored) {

                }
            }

            try {
                editorLogger.info("Update thread done.");
                end();
                //Gdx.graphics.setContinuousRendering(true);
            }
            catch (Exception e) {
                //wtf another one?
                e.printStackTrace();
                Gdx.app.exit();
            }
        });

        updateThread.setName("TaikoEditor Update");
        updateThread.setDaemon(true);
        updateThread.start();
    }

    private double lastTime = 0;
    public static double getTime() {
        return glfwGetTime();
    }

    private boolean first = true;

    @Override
    public void render() {
        if (end)
        {
            if (updateThread != null && updateThread.isAlive())
                return;
            Gdx.app.exit(); //causes disposal
            return;
        }

        if (first) {
            first = false;
            postCreate();
            return;
        }

        //editorLogger.info(Gdx.graphics.getFramesPerSecond());

        float elapsed = Gdx.graphics.getDeltaTime();
        fpsLimitTracker.add(elapsed);
        /*frameTimer += elapsed;

        if (frameTimer < targetFrameDuration && targetFrameDuration > 0) {
            return;
        }
        else {
            frameTimer %= targetFrameDuration; //no "catchup"
        }*/

        layerLock.lock();
        updateCount = 0;

        VoidMethod m;
        while (!actionQueue.isEmpty()) {
            m = actionQueue.poll();
            if (m != null)
                m.run();
        }

        TextRenderer.swapLayouts();
        //assetMaster must be updated on main thread.
        //Give it more time to load if none/only loadinglayers are rendered.
        if (gameRender(renderLayer)) {
            assetMaster.longUpdate(paused ? 168 : 84); //has to be updated on the main thread
        }
        else {
            assetMaster.fastUpdate(); //has to be updated on the main thread
        }
        disposeLayers();
        TextRenderer.swapLayouts();

        layerLock.unlock();
    }


    private int gameUpdate(float elapsed)
    {
        //Update misc
        audioMaster.update(elapsed);
        hoverText.update(elapsed);

        //Update layers
        boolean update = true;
        int renderIndex = -1;
        int checkIndex = layers.size() - 1;

        while (checkIndex > -1 && (update || renderIndex == -1))
        {
            if (update)
                layers.get(checkIndex).update(elapsed);

            switch (layers.get(checkIndex).type)
            {
                case FULL_STOP:
                    update = false;
                case RENDER_STOP:
                    if (renderIndex == -1)
                    {
                        renderIndex = checkIndex;
                    }
                    break;
                case UPDATE_STOP:
                    update = false;
                    break;
            }

            --checkIndex;
        }

        return renderIndex;
    }
    private boolean gameRender(int renderIndex)
    {
        boolean onlyLoading = true;
        if (renderIndex < 0) //the lowest layer does not cancel rendering, clear with black color
        {
            Gdx.gl.glClearColor(0, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            renderIndex = 0;
        }

        if (!layers.isEmpty()) //Render layers
        {
            sb.begin();

            for (; renderIndex < layers.size(); ++renderIndex)
            {
                if (!(layers.get(renderIndex) instanceof LoadingLayer))
                    onlyLoading = false;
                layers.get(renderIndex).render(sb, sr); //, elapsed);
            }

            hoverText.render(sb, sr);

            /*if (textRenderer.hasFont())
                textRenderer.renderText(sb, String.valueOf(1 / fpsLimitTracker.avg()), 30, 30, Color.WHITE);*/

            sb.end();
        }

        return onlyLoading;
    }
    private void updateLayers()
    {
        // Add and remove layers (and input)
        for (ProgramLayer l : addBottomLayers)
        {
            l.initialize();
            layers.add(0, l);
            editorLogger.info("Added " + l.getClass().getSimpleName());
            if (l instanceof InputLayer)
                input.addProcessor(((InputLayer) l).getProcessor());
        }
        addBottomLayers.clear();

        for (ProgramLayer l : addTopLayers)
        {
            l.initialize();
            layers.add(l);
            editorLogger.info("Added " + l.getClass().getSimpleName());
            if (l instanceof InputLayer)
                input.addProcessor(0, ((InputLayer) l).getProcessor());
        }
        addTopLayers.clear();

        for (ProgramLayer l : removeLayers)
        {
            disposeLayers.add(l);
            layers.remove(l);
            editorLogger.info("Removed " + l.getClass().getSimpleName());
            if (l instanceof InputLayer)
                input.removeProcessor(((InputLayer) l).getProcessor());
        }
        removeLayers.clear();
    }
    private void disposeLayers() {
        for (ProgramLayer l : disposeLayers) {
            l.dispose();
        }
        disposeLayers.clear();
    }


    @Override
    public void dispose() {
        layerLock.lock();

        //dispose of layers
        for (ProgramLayer layer : layers)
            layer.dispose();

        disposeLayers();

        layers.clear();
        addBottomLayers.clear();
        addTopLayers.clear();
        removeLayers.clear();

        music.dispose();

        SvFunctionLayer.disposeFunctions();
        assetMaster.dispose();
        sb.dispose();

        layerLock.unlock();
    }

    public static void end()
    {
        end = true;
    }

    public static void onMain(VoidMethod later) {
        actionQueue.add(later);
    }

    //Should only be called from contexts that have already acquired the layer lock
    public static void addLayer(ProgramLayer layer)
    {
        layerLock.lock();
        addTopLayers.add(layer);
        layerLock.unlock();
    }
    public static void addLayerToBottom(ProgramLayer layer)
    {
        layerLock.lock();
        addBottomLayers.add(layer);
        layerLock.unlock();
    }
    public static void removeLayer(ProgramLayer layer)
    {
        layerLock.lock();
        removeLayers.add(layer);
        layerLock.unlock();
    }

    private static void createCursors()
    {
        Pixmap blank = new Pixmap(1, 1, Pixmap.Format.RGBA8888);

        blank.setColor(0.0f, 0.0f, 0.0f, 0.0f);
        blank.fill();

        //Pixmap test = new Pixmap(1, 1, Pixmap.Format.RGBA8888);

        //test.setColor(1.0f, 0.0f, 0.0f, 1.0f);
        //test.fill();

        hiddenCursor = Gdx.graphics.newCursor(blank, 0, 0);
        //defaultCursor = Gdx.graphics.newCursor(test, 0, 0);

        defaultCursor = Gdx.graphics.newCursor(new Pixmap(Gdx.files.internal("taikoedit/images/ui/cursor.png")), 16, 16);
    }

    public static void hideCursor()
    {
        Gdx.graphics.setCursor(hiddenCursor);
    }
    public static void showCursor()
    {
        Gdx.graphics.setCursor(defaultCursor);
    }

    private boolean open(String file) {
        File map = new File(file);
        if (map.exists()) {
            File mapFolder = map;
            if (map.isFile() && map.getName().endsWith(".osu")) {
                mapFolder = map.getParentFile();
            }
            else if (map.isDirectory()) {
                map = null;
            }
            else {
                return false;
            }

            if (mapFolder == null)
                return false;

            //mapFolder is the folder, map is the specific difficulty to open (if not null)
            Mapset set = null;
            try { //Subfolders are ignored
                boolean hasMap = map != null;

                File[] all = mapFolder.listFiles();
                if (all != null && !hasMap) {
                    for (File f : all) {
                        if (f.isFile() && f.getPath().endsWith(".osu")) {
                            hasMap = true;
                            break;
                        }
                    }
                }

                if (hasMap)
                {
                    editorLogger.info("Mapset: " + mapFolder.getName());
                    set = new Mapset(mapFolder);

                    if (!set.isEmpty())
                    {
                        editorLogger.info("Mapset " + mapFolder.getName() + " has " + set.getMaps().size() + " taiko difficulties.");
                    }
                    else
                    {
                        set = null;
                        editorLogger.info("Mapset " + mapFolder.getName() + " has no taiko maps.");
                    }
                }
            }
            catch (Exception e) {
                editorLogger.info("Error occurred reading folder " + mapFolder.getPath());
                editorLogger.error(e.getStackTrace());
                e.printStackTrace();
            }

            if (set != null) {
                MapInfo initial = null;
                if (map != null) {
                    for (MapInfo info : set.getMaps()) {
                        if (info.getMapFile().equals(map)) {
                            initial = info;
                            break;
                        }
                    }

                    if (initial == null) {
                        editorLogger.info("Selected file isn't a taiko map or isn't in set?");
                    }
                }
                EditorLayer edit = new EditorLayer(null, set, Collections.singleton(initial)); //no source, will close on exit
                ProgramLayer loader = edit.getLoader();
                addLayer(new LoadingLayer()
                        .loadLists("base")
                        .addLayers(true,
                                new EditorLoadingLayer()
                                        .loadLists("ui", "font", "background", "menu", "editor", "hitsound")
                                        .addTask(Skins::load)
                                        .addCallback(TaikoEditor::initialize)
                                        .addLayers(true,
                                                ()->{
                                                    if (currentSkin == null) {
                                                        return new ProgramLayer[] { loader };
                                                    }
                                                    else {
                                                        LoadingLayer skinLoader = currentSkin.getLoader(loader);
                                                        if (skinLoader != null) {
                                                            return new ProgramLayer[] { skinLoader };
                                                        }
                                                        else {
                                                            return new ProgramLayer[] { loader };
                                                        }
                                                    }
                                                }
                                        )
                        )
                );
                music.loadAsync(set.getSongFile(), (thread)->{
                    if (thread.success()) {
                        music.play();
                        music.pause();
                    }
                    else {
                        end();
                        fileError("Failed to load music for set.", "error");
                    }
                });
                return true;
            }
        }
        return false;
    }

    //Callback of MenuLayer.
    public static void initialize()
    {
        try
        {
            textRenderer.setFont(assetMaster.getFont("default"));
            hoverText.initialize(assetMaster.getFont("base:aller small"), 16);
            showCursor();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void pause() {
        super.pause();
        paused = true;
    }

    @Override
    public void resume() {
        super.resume();
        paused = false;
    }

    private static void fileError(String msg, String filename) {
        try {
            File f = new File(filename + ".txt");
            PrintWriter pWriter = null;

            try {
                pWriter = new PrintWriter(f);
                pWriter.println("Error occurred:");
                pWriter.println(msg);
            }
            catch (Exception ignored) {

            }
            finally {
                StreamUtils.closeQuietly(pWriter);
            }
        }
        catch (Exception ignored) {

        }
    }
}
