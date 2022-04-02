package alchyr.taikoedit;

import alchyr.taikoedit.audio.MusicWrapper;
import alchyr.taikoedit.audio.mp3.PreloadedMp3;
import alchyr.taikoedit.audio.ogg.PreloadOgg;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.layers.FastMenuLayer;
import alchyr.taikoedit.core.layers.MenuLayer;
import alchyr.taikoedit.core.layers.sub.SvFunctionLayer;
import alchyr.taikoedit.core.ui.CursorHoverText;
import alchyr.taikoedit.management.*;
import alchyr.taikoedit.util.Sync;
import alchyr.taikoedit.util.TextRenderer;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALLwjgl3Audio;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;

public class TaikoEditorOriginal extends ApplicationAdapter {
    public static final int VERSION = 301; //x.x.x -> xxx

    public static final boolean DIFFCALC = true; //ctrl+alt+d

    public static final Logger editorLogger = LogManager.getLogger("TaikoEditor");

    public static DecimalFormatSymbols osuSafe;
    static {
        osuSafe = new DecimalFormatSymbols(Locale.US);
        osuSafe.setDecimalSeparator('.');
    }

    private SpriteBatch sb;
    private ShapeRenderer sr;

    public static AssetMaster assetMaster;
    public static AudioMaster audioMaster;
    public static TextRenderer textRenderer;

    public static CursorHoverText hoverText;

    private static Cursor hiddenCursor;
    private static Cursor defaultCursor;

    public final static ArrayList<ProgramLayer> layers = new ArrayList<>();

    private static final ArrayList<ProgramLayer> addTopLayers = new ArrayList<>();
    private static final ArrayList<ProgramLayer> addBottomLayers = new ArrayList<>();
    private static final ArrayList<ProgramLayer> removeLayers = new ArrayList<>();

    private static InputMultiplexer input = new InputMultiplexer();

    private static boolean end = false;

    //update/framerate control
    private boolean paused;
    private final int launchWidth, launchHeight;
    private final boolean borderless;
    private boolean vsync;
    private boolean unlimited;
    private boolean useSync;
    private final int fps;

    private boolean useFastMenu;

    private static final Sync sync = new Sync();

    public TaikoEditorOriginal(int width, int height, boolean borderless, boolean vsyncEnabled, boolean unlimited, int fps, boolean fastMenu) {
        vsync = vsyncEnabled;
        this.unlimited = unlimited;
        useSync = !vsync && !unlimited;
        this.fps = fps;

        launchWidth = width;
        launchHeight = height;
        this.borderless = borderless;

        this.useFastMenu = fastMenu;
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

        //set up input
        input = new InputMultiplexer();
        Gdx.input.setInputProcessor(input);

        //A U D I O
        ((OpenALLwjgl3Audio) Gdx.audio).registerMusic("mp3", PreloadedMp3.class);
        ((OpenALLwjgl3Audio) Gdx.audio).registerMusic("ogg", PreloadOgg.class);
        TaikoEditor.music = new MusicWrapper();


        hoverText = new CursorHoverText();
    }

    private void postCreate() {
        if (launchWidth != -1 && launchHeight != -1) {
            if (borderless)
                Gdx.graphics.setUndecorated(true); //updating size directly in the create method results in issues
            Gdx.graphics.setWindowedMode(launchWidth, launchHeight);
            if (borderless) {
                ((Lwjgl3Graphics)Gdx.graphics).getWindow().setPosition(0, 0);
            }
            else {
                ((Lwjgl3Graphics)Gdx.graphics).getWindow().setPosition(
                        ((Lwjgl3Graphics)Gdx.graphics).getWindow().getPositionX() + 200 - (launchWidth / 2),
                        ((Lwjgl3Graphics)Gdx.graphics).getWindow().getPositionY() + 200 - (launchHeight / 2));
            }
        }

        SettingsMaster.updateDimensions();

        sb = new SpriteBatch();
        sr = new ShapeRenderer();

        assetMaster.loadAssetLists();
        assetMaster.addSpecialLoaders();
        SettingsMaster.load();
        LocalizationMaster.loadDefaultFolder();

        BindingMaster.initialize();

        if (useFastMenu) {
            addLayer(new FastMenuLayer().getLoader());
        }
        else {
            addLayer(new MenuLayer().getLoader());
        }
    }

    private boolean first = true;

    @Override
    public void render() {
        if (end)
        {
            //dispose();
            Gdx.app.exit(); //causes disposal
            return;
        }

        if (first) {
            first = false;
            postCreate();
            return;
        }

        if (paused)
            return;

        if (useSync)
            sync.sync(fps);

        float elapsed = Gdx.graphics.getDeltaTime();

        gameRender(gameUpdate(elapsed), elapsed);
        updateLayers();
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
    private void gameRender(int renderIndex, float elapsed)
    {
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
                layers.get(renderIndex).render(sb, sr); //, elapsed);
            }

            hoverText.render(sb, sr);

            sb.end();
        }
    }
    private void updateLayers()
    {
        // Add and remove layers (and input)
        for (ProgramLayer l : addBottomLayers)
        {
            layers.add(0, l);
            editorLogger.info("Added " + l.getClass().getSimpleName());
            if (l instanceof InputLayer)
                input.addProcessor(((InputLayer) l).getProcessor());
        }
        addBottomLayers.clear();

        for (ProgramLayer l : addTopLayers)
        {
            layers.add(l);
            editorLogger.info("Added " + l.getClass().getSimpleName());
            if (l instanceof InputLayer)
                input.addProcessor(0, ((InputLayer) l).getProcessor());
        }
        addTopLayers.clear();

        for (ProgramLayer l : removeLayers)
        {
            l.dispose();
            layers.remove(l);
            editorLogger.info("Removed " + l.getClass().getSimpleName());
            if (l instanceof InputLayer)
                input.removeProcessor(((InputLayer) l).getProcessor());
        }
        removeLayers.clear();
    }


    @Override
    public void dispose() {
        //dispose of layers
        for (ProgramLayer layer : layers)
            layer.dispose();

        layers.clear();
        addBottomLayers.clear();
        addTopLayers.clear();
        removeLayers.clear();

        SvFunctionLayer.disposeFunctions();
        assetMaster.dispose();
        sb.dispose();
    }

    public static void end()
    {
        end = true;
    }


    public static void addLayer(ProgramLayer layer)
    {
        addTopLayers.add(layer);
    }
    public static void addLayerToBottom(ProgramLayer layer)
    {
        addBottomLayers.add(layer);
    }
    public static void removeLayer(ProgramLayer layer)
    {
        removeLayers.add(layer);
    }

    private static void createCursors()
    {
        Pixmap blank = new Pixmap(1, 1, Pixmap.Format.RGBA8888);

        blank.setColor(0.0f, 0.0f, 0.0f, 0.0f);
        blank.fill();

        hiddenCursor = Gdx.graphics.newCursor(blank, 0, 0);

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

    //Callback of MenuLayer. Todo: Allow TextRenderer to be instantiated sooner, and then initialized here to actually load fonts.
    public static void initialize()
    {
        try
        {
            textRenderer.setFont(assetMaster.getFont("default"));
            hoverText.initialize(assetMaster.getFont("aller small"), 16);
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
}
