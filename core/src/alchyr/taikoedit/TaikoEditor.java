package alchyr.taikoedit;

import alchyr.taikoedit.audio.MusicWrapper;
import alchyr.taikoedit.core.GameLayer;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.layers.MenuLayer;
import alchyr.taikoedit.management.AssetMaster;
import alchyr.taikoedit.management.LocalizationMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.TextRenderer;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class TaikoEditor extends ApplicationAdapter {
    public static final Logger editorLogger = LogManager.getLogger("Alchemy");

    private SpriteBatch sb;
    private ShapeRenderer sr;

    public static AssetMaster assetMaster;
    public static TextRenderer textRenderer;

    private static Cursor hiddenCursor;
    private static Cursor defaultCursor;

    public final static ArrayList<GameLayer> layers = new ArrayList<>();

    private static final ArrayList<GameLayer> addTopLayers = new ArrayList<>();
    private static final ArrayList<GameLayer> addBottomLayers = new ArrayList<>();
    private static final ArrayList<GameLayer> removeLayers = new ArrayList<>();

    private static InputMultiplexer input = new InputMultiplexer();

    private static boolean end = false;

    //update/framerate control
    private boolean paused;

    @Override
    public void create() {
        /*FileHandle h = Gdx.files.getFileHandle("taikoedit/data/worldgen/nodupe.txt", Files.FileType.Internal);
        String data = h.readString();
        String[] dataLines = data.split("\r\n");
        StringBuilder result = new StringBuilder();
        HashMap<String, Boolean> registeredStrings = new HashMap<>();
        for (String s : dataLines)
        {
            if (!s.contains("iy") && !registeredStrings.containsKey(s))
            {
                registeredStrings.put(s, true);
                result.append(s);
                result.append('\n');
            }
        }

        FileHandle outFile = Gdx.files.getFileHandle("C:\\Users\\User\\Documents\\output.txt", Files.FileType.Absolute);
        outFile.writeString(result.toString(), false);*/

        createCursors();
        hideCursor();

        end = false;


    	if (assetMaster != null)
		{
			assetMaster.dispose();
		}
        assetMaster = new AssetMaster();

    	if (sb != null)
    	    sb.dispose();
    	if (sr != null)
    	    sr.dispose();

        sb = new SpriteBatch();
        sr = new ShapeRenderer();

        layers.clear();
        addTopLayers.clear();
        addBottomLayers.clear();
        removeLayers.clear();

        assetMaster.loadAssetLists();
        assetMaster.addSpecialLoaders();
        SettingsMaster.load();
        LocalizationMaster.loadDefaultFolder();

        //For later: new LoadingLayer("loading", new LoadingLayer("menu", new MenuLayer(), true));
        //Load loading screen assets first, which will be maintained for rest of game

        addLayer(new MenuLayer().getLoader());

        //set up input
        input = new InputMultiplexer();
        Gdx.input.setInputProcessor(input);

        //create test game
        /*currentGame = new GameData();
        currentGame.NAME = "TEST";
        currentGame.SEED = new Random().nextLong();

        SystemMaster.generate(currentGame.SEED);*/

        EditorLayer.music = new MusicWrapper();
    }

    @Override
    public void render() {
        if (end)
        {
            //dispose();
            Gdx.app.exit(); //causes disposal
            return;
        }

        if (paused)
            return;

        float elapsed = Gdx.graphics.getDeltaTime();

        gameRender(gameUpdate(elapsed), elapsed);
        updateLayers();
    }


    private int gameUpdate(float elapsed)
    {
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

            sb.end();
        }
    }
    private void updateLayers()
    {
        // Add and remove layers (and input)
        for (GameLayer l : addBottomLayers)
        {
            layers.add(0, l);
            if (l instanceof InputLayer)
                input.addProcessor(0, ((InputLayer) l).getProcessor());
        }
        addBottomLayers.clear();

        for (GameLayer l : addTopLayers)
        {
            layers.add(l);
            if (l instanceof InputLayer)
                input.addProcessor(((InputLayer) l).getProcessor());
        }
        addTopLayers.clear();

        for (GameLayer l : removeLayers)
        {
            l.dispose();
            layers.remove(l);
            if (l instanceof InputLayer)
                input.removeProcessor(((InputLayer) l).getProcessor());
        }
        removeLayers.clear();
    }


    @Override
    public void dispose() {
        //dispose of layers
        for (GameLayer layer : layers)
            layer.dispose();

        layers.clear();
        addBottomLayers.clear();
        addTopLayers.clear();
        removeLayers.clear();

        assetMaster.dispose();
        sb.dispose();
    }

    public static void end()
    {
        end = true;
    }


    public static void addLayer(GameLayer layer)
    {
        addTopLayers.add(layer);
    }
    public static void addLayerToBottom(GameLayer layer)
    {
        addBottomLayers.add(layer);
    }
    public static void removeLayer(GameLayer layer)
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

    //Callback of MenuLayer. Todo: Allow TextReader to be instantiated sooner, and then initialized here to actually load fonts.
    public static void initialize()
    {
        textRenderer = new TextRenderer(assetMaster.getFont("default"));
        showCursor();
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
