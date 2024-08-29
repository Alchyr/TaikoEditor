package alchyr.taikoedit.core.layers;

import alchyr.networking.standard.ConnectionClient;
import alchyr.networking.standard.ConnectionServer;
import alchyr.networking.standard.Message;
import alchyr.networking.standard.MessageHandler;
import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.input.BoundInputProcessor;
import alchyr.taikoedit.core.input.sub.TextInput;
import alchyr.taikoedit.core.layers.sub.ConfirmationLayer;
import alchyr.taikoedit.core.layers.sub.UpdatingLayer;
import alchyr.taikoedit.core.layers.sub.WaitLayer;
import alchyr.taikoedit.core.ui.*;
import alchyr.taikoedit.editor.maps.MapInfo;
import alchyr.taikoedit.editor.maps.Mapset;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.MapMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.management.assets.FileHelper;
import alchyr.taikoedit.management.assets.skins.Skins;
import alchyr.taikoedit.management.assets.loaders.OsuBackgroundLoader;
import alchyr.taikoedit.util.FileDropHandler;
import alchyr.taikoedit.util.GeneralUtils;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

import static alchyr.taikoedit.TaikoEditor.*;
import static alchyr.taikoedit.management.assets.skins.Skins.currentSkin;

public class MenuLayer extends LoadedLayer implements InputLayer, FileDropHandler.Handler {
    private static final Set<String> AUDIO_EXTENSIONS;
    static {
        AUDIO_EXTENSIONS = new HashSet<>();
        AUDIO_EXTENSIONS.add("mp3");
        AUDIO_EXTENSIONS.add("ogg");
    }

    private boolean initialized;

    private final MenuProcessor processor;

    private MapSelect mapSelect;
    private final boolean delayMapload;
    private boolean updateMaps = false;

    private boolean useOsuBackground = false;
    private Texture osuBackground = null;
    private int bgWidth, bgHeight;

    private BitmapFont font;
    private Texture pixel;
    private int searchHeight, searchY;
    private float searchTextOffsetX, searchTextOffsetY;

    private TextInput searchInput;

    private List<ImageButton> buttons = new ArrayList<>();

    private final ConcurrentLinkedQueue<Function<Float, Boolean>> updaters = new ConcurrentLinkedQueue<>();

    private TextOverlay textOverlay;

    private static final Color bgColor = new Color(0.3f, 0.3f, 0.25f, 1.0f);

    private String updateID = null;

    public MenuLayer(boolean delayMapload)
    {
        processor = new MenuProcessor(this);
        this.type = LAYER_TYPE.FULL_STOP;

        this.delayMapload = delayMapload;

        //buttons = new ArrayList<>();
        //mapOptions = new ArrayList<>();
        //hashedMapOptions = new HashMap<>();

        initialized = false;
    }

    @Override
    public void initialize() {
        useOsuBackground = !OsuBackgroundLoader.loadedBackgrounds.isEmpty() && MathUtils.randomBoolean(0.8f);
        if (useOsuBackground) {
            osuBackground = assetMaster.get(OsuBackgroundLoader.loadedBackgrounds.get(MathUtils.random(OsuBackgroundLoader.loadedBackgrounds.size() - 1)));

            float bgScale = Math.max((float) SettingsMaster.getWidth() / osuBackground.getWidth(), (float) SettingsMaster.getHeight() / osuBackground.getHeight());
            bgWidth = (int) Math.ceil(osuBackground.getWidth() * bgScale);
            bgHeight = (int) Math.ceil(osuBackground.getHeight() * bgScale);
        }
        else {
            float bgScale = Math.max((float) SettingsMaster.getWidth() / currentSkin.background.getWidth(), (float) SettingsMaster.getHeight() / currentSkin.background.getHeight());
            bgWidth = (int) Math.ceil(currentSkin.background.getWidth() * bgScale);
            bgHeight = (int) Math.ceil(currentSkin.background.getHeight() * bgScale);
        }

        if (!initialized)
        {
            font = assetMaster.getFont("default");
            pixel = assetMaster.get("ui:pixel");
            searchHeight = 40;
            searchY = SettingsMaster.getHeight() - searchHeight;
            searchTextOffsetX = 10;
            searchTextOffsetY = 35;

            /*for (Map.Entry<String, Mapset> setEntry : MapMaster.mapDatabase.mapsets.entrySet())
            {
                Mapset set = setEntry.getValue();

                Button setButton = new Button(0, 0, OPTION_WIDTH, OPTION_HEIGHT, set.getShortArtist(OPTION_WIDTH * 0.9f, assetMaster.getFont("default")) + '\n' + set.getShortTitle(OPTION_WIDTH * 0.9f, assetMaster.getFont("default")) + '\n' + set.getShortCreator(OPTION_WIDTH * 0.9f, assetMaster.getFont("default")), null);
                setButton.setAction(setEntry.getKey());

                mapOptions.add(setButton);
                hashedMapOptions.put(setEntry.getKey(), setButton);
            }*/

            mapSelect = new MapSelect(searchY + 1);

            searchInput = new TextInput(128, font, true);

            int buttonX = SettingsMaster.getWidth() - 40;
            buttons.add(new ImageButton(buttonX, SettingsMaster.getHeight() - 40, assetMaster.get("ui:exit"), (Texture) assetMaster.get("ui:exith"))
                    .setClick(this::quit).setHovered(()->{ hoverText.setText("Exit"); }));
            buttons.add(new ImageButton(buttonX -= 40, SettingsMaster.getHeight() - 40, assetMaster.get("ui:settings"), (Texture) assetMaster.get("ui:settingsh"))
                    .setClick(this::settings).setHovered(()->{ hoverText.setText("Settings"); }));
            /*buttons.add(new ImageButton(buttonX -= 40, SettingsMaster.getHeight() - 40, assetMaster.get("ui:connect"), (Texture) assetMaster.get("ui:connecth"))
                    .setClick(this::openConnect).setHovered(()->{ hoverText.setText("Start Hosting"); }));*/

            buttons.add(new ImageButton(buttonX -= 40, SettingsMaster.getHeight() - 40, assetMaster.get("ui:connect"), (Texture) assetMaster.get("ui:connecth"))
                    .setClick(this::joinSession).setHovered(()->{ hoverText.setText("Join Session"); }));

            textOverlay = new TextOverlay(assetMaster.getFont("aller medium"), SettingsMaster.getHeight() / 2, 100);

            updaters.add((e)->{
                textOverlay.update(e);
                mapSelect.update(e);
                for (ImageButton button : buttons) {
                    button.update(e);
                }
                return false;
            });

            initialized = true;

            Thread updateCheck = updateCheckThread(buttonX);
            updateCheck.start();
        }
        processor.bind();
        FileDropHandler.set(this);
    }

    private Thread updateCheckThread(int buttonX) {
        Thread updateCheck = new Thread(() -> {
            try {
                URL url = new URL("https://docs.google.com/document/d/e/2PACX-1vRgkoP65WMrsJGqaiKlO6cGnqeZbBlmhEXhMqjRr4QH-IIArQR1QaLbe_ffSQXHXAl-hOf9Yye7nMei/pub");
                //HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                //connection.setRequestProperty("mimeType", "text/plain");

                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));

                StringBuilder sb = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }

                reader.close();

                int dataIndex = sb.indexOf("|DATA_START|") + 12;
                int dataEnd = sb.indexOf("|DATA_END|", dataIndex);
                if (dataIndex < 0 || dataEnd < 0) {
                    editorLogger.error("Failed to find update data.");
                    return;
                }
                String[] result = sb.substring(dataIndex, dataEnd).split(":");

                int latestID = Integer.parseInt(result[0]);

                if (latestID > VERSION) {
                    editorLogger.info("Newer version detected.");
                    updateID = result[1];
                    buttons.add(new ImageButton(buttonX - 40, SettingsMaster.getHeight() - 40, assetMaster.get("ui:update"), (Texture) assetMaster.get("ui:updateh"))
                            .setClick(this::versionUpdate).setHovered(()->hoverText.setText("Update Available")));
                }
                else {
                    editorLogger.info("Up to date.");
                }
            }
            catch (Exception e) {
                editorLogger.error("Failed to process update data.");
                e.printStackTrace();
            }
        });
        updateCheck.setName("Update Check");
        updateCheck.setDaemon(true);
        return updateCheck;
    }

    @Override
    public void update(float elapsed) {
        processor.update(elapsed);

        if (MapMaster.loading) {
            updateMaps = true;
        }
        else if (updateMaps) {
            updateMaps = false;
            if (searchInput.text.isEmpty()) {
                mapSelect.refreshMaps();
            }
            else if (!searchInput.text.trim().isEmpty()) {
                mapSelect.setMaps(MapMaster.search(searchInput.text));
            }
        }

        updaters.removeIf((func)->func.apply(elapsed));
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(bgColor);
        if (useOsuBackground) {
            sb.draw(osuBackground, 0, 0, bgWidth, bgHeight);
        }
        else {
            sb.draw(currentSkin.background, 0, 0, bgWidth, bgHeight);
        }

        mapSelect.render(sb, sr);

        //top bar
        sb.setColor(Color.BLACK);
        sb.draw(pixel, 0, searchY, SettingsMaster.getWidth(), searchHeight + 5);

        sb.setColor(Color.WHITE);
        if (searchInput.text.isEmpty()) {
            textRenderer.setFont(font).renderText(sb, "Type to search.", searchTextOffsetX, searchY + searchTextOffsetY);
        }
        else {
            searchInput.render(sb, searchTextOffsetX, searchY + searchTextOffsetY);
        }

        for (ImageButton button : buttons) {
            button.render(sb, sr);
        }

        sb.draw(pixel, 0, searchY - 1, SettingsMaster.getWidth(), 2);

        textOverlay.render(sb, sr);
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    private boolean canOpen = true;
    private LoadingLayer chooseMap(MapSelect.MapOpenInfo info, ConnectionClient client)
    {
        if (canOpen) {
            EditorLayer edit = new EditorLayer(this, info.getSet(), info.getInitialDifficulties());
            if (client != null) {
                edit.setClient(client);
            }

            canOpen = false;
            FileDropHandler.remove(this);
            TaikoEditor.removeLayer(this);
            LoadingLayer loader = edit.getLoader();
            TaikoEditor.addLayer(loader);
            return loader;
        }
        return null;
    }

    private void settings(int button)
    {
        TaikoEditor.addLayer(new SettingsLayer(useOsuBackground ? osuBackground : currentSkin.background));
    }

    private ConnectionClient client = null;

    private Thread joinThread = null;
    private void joinSession() {
        if (joinThread != null && joinThread.isAlive()) {
            return;
        }

        WaitLayer waiter = new WaitLayer("Attempting to join...", ()->false);
        //First, attempt to connect.
        //If connection is successful:
        //Host will be "paused" (can push escape to cancel and close server).
        //Current state of map will be sent (Send entire osu file as text? Don't send audio file?)
        //Perform confirmation that map was received correctly
        //pass ConnectionClient to a new EditorLayer editing That Map

        String[] params;
        try {
            Transferable data = Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .getContents(this);
            if (data == null || !data.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                textOverlay.setText("Valid information not found in clipboard", 2.0f);
                return;
            }

            String clipboardData = (String) data.getTransferData(DataFlavor.stringFlavor);
            params = clipboardData.split("\\|");

            if (params.length != 3) {
                textOverlay.setText("Valid information not found in clipboard", 2.0f);
                return;
            }
        }
        catch (Exception e) {
            textOverlay.setText("Valid information not found in clipboard", 2.0f);
            editorLogger.error("Exception occurred getting join info", e);
            return;
        }

        //First step is threaded to avoid halting on connection establishment
        joinThread = new Thread(()->{
            try {
                ConnectionClient test = new ConnectionClient(new Socket(params[0], Integer.parseInt(params[1])));

                waiter.onCancel(()-> {
                    try {
                        test.close();
                    } catch (Exception ignored) { }
                });

                if (!test.isConnected()) {
                    textOverlay.setText("Failed to connect.", 2.0f);
                    waiter.setComplete();
                    return;
                }

                test.send(params[2]);

                if (!test.waitValidation()) {
                    textOverlay.setText("Failed to join; was not validated.", 2.0f);
                    waiter.setComplete();
                    return;
                }

                waiter.text = "Waiting for map data...";

                test.startStandardReceiver();

                MessageHandler joinHandler = joinHandler(test, waiter);

                updaters.add((e)->{
                    joinHandler.update(e);
                    return !joinHandler.alive;
                });

                //Next, receive difficulties. This will receive ALL difficulties, and any existing difficulties of the same name will be overwritten.
                //Files will be added locally, with the "current state" of the map on host side (even if host's *saved* version is different.
                //Next, send all contents of undo/redo queue. With that, should be synced enough for editing.
                //If working as a client, cannot open not-open difficulties. They should be greyed out in new view dialog.
            } catch (Exception e) {
                textOverlay.setText("Failed to join, server was probably closed.", 2.0f);
                editorLogger.error("Exception occurred attempting to join session", e);
            }
        });
        joinThread.setName("Join Session Thread");
        joinThread.setDaemon(true);
        joinThread.start();
        //Make a WaitLayer, that has a "cancel" method that will be called if escape key is pressed/cancel button
        //And accepts a string to display (and this string can be changed?)
        TaikoEditor.addLayer(waiter);
    }

    private MessageHandler joinHandler(ConnectionClient client, WaitLayer waiter) {
        return new MessageHandler(client) {
            String mapper = null, artist = null, title = null;

            @Override
            public void handleMessage(Message msg) {
                if (msg.identifier == Message.UTF) {
                    String text = msg.contents[0].toString();
                    switch (text.substring(0, 3)) {
                        case "MPR":
                            mapper = text.substring(3);
                            break;
                        case "ART":
                            artist = text.substring(3);
                            break;
                        case "TTL":
                            title = text.substring(3);
                            break;
                    }

                    if (mapper != null && artist != null && title != null) {
                        timeout = -1;
                        alive = false;

                        List<Mapset> maps = MapMaster.search(mapper + " " + artist + " " + title);

                        Mapset set = null;
                        if (!maps.isEmpty()) {
                            Mapset probable = maps.get(0);
                            if (probable.getCreator().equals(mapper)) {
                                set = probable;
                                editorLogger.info("Very probably have this map; using local set.");
                            }
                        }

                        //if set is known -> get diffs -> load set
                        //new set -> get diffs -> make set -> load set
                        //Add to database after if New
                        if (set == null) {
                            editorLogger.info("Could not find set locally, requesting audio file");
                            requestAudio(client, waiter, artist, title);
                        }
                        else {
                            editorLogger.info("Found set, requesting difficulties");
                            requestDifficulties(client, waiter, set.getDirectory(), set);
                        }
                    }
                }
            }
        }.setTimeout(10f, messageHandler -> {
            editorLogger.info("Timed out waiting for map info.");
            waiter.setComplete();
            try {
                messageHandler.client.close();
            }
            catch (Exception ignored) { }
            return false;
        });
    }

    private void requestAudio(ConnectionClient client, WaitLayer waiter, String artist, String title) {
        String audioFileKey = GeneralUtils.generateCode(4);
        client.send(ConnectionServer.EVENT_FILE_REQ + audioFileKey + "AUDIO");
        waiter.text = "Waiting for map audio file...";

        MessageHandler audioReceiver = new MessageHandler(client)
        {
            @Override
            public void handleMessage(Message msg) {
                outer:
                switch (msg.identifier) {
                    case Message.UTF:
                        if ("FAIL".equals(msg.contents[0])) {
                            editorLogger.info("Failed to receive audio, closing connection.");
                            break;
                        }
                        return;
                    case Message.FILE:
                        if (!audioFileKey.equals(msg.contents[0].toString())) {
                            editorLogger.warn("Received file with incorrect pass: " + msg.contents[0]);
                            return;
                        }

                        String filename = msg.contents[1].toString();
                        List<byte[]> filedata = (List<byte[]>) msg.contents[2];

                        if (filedata == null) {
                            editorLogger.info("No file received");
                            textOverlay.setText(filename, 2.0f);
                            break;
                        }

                        String folderName = FileHelper.removeInvalidChars(artist + " - " + title);
                        String path = FileHelper.concat(SettingsMaster.osuFolder, "Songs", folderName);

                        //Find folder that is Not Existing
                        File setDirectory = new File(path);
                        int extra = 1;
                        while (setDirectory.exists()) {
                            if (extra > 100) {
                                //what the heck
                                editorLogger.info("way too many numbered folders of same artist");
                                textOverlay.setText("Failed to make empty folder for audio", 2.0f);
                                break outer;
                            }
                            path = FileHelper.concat(SettingsMaster.osuFolder, "Songs", folderName + "(" + extra + ")");
                            setDirectory = new File(path);
                        }

                        if (!setDirectory.mkdirs()) {
                            textOverlay.setText("Failed to make empty folder for audio", 2.0f);
                            break;
                        }

                        File audioFile = new File(setDirectory, filename);

                        try (FileOutputStream fileOut = new FileOutputStream(audioFile)) {
                            for (byte[] chunk : filedata) {
                                fileOut.write(chunk);
                            }
                        } catch (IOException e) {
                            textOverlay.setText("Failed to write audio file.", 2.0f);
                            editorLogger.error("Failed to write audio file.", e);
                            break;
                        }

                        editorLogger.info("Received audio file successfully: " + filename);
                        timeout = -1;
                        alive = false;

                        requestDifficulties(client, waiter, setDirectory, null);
                        return;
                }

                alive = false;
                onTimeout.apply(this);
            }
        }.setTimeout(120f, messageHandler -> {
            editorLogger.info("Timed out waiting for audio file.");
            waiter.setComplete();
            try {
                messageHandler.client.close();
            }
            catch (Exception ignored) { }
            return false;
        });
        updaters.add((e)->{
            audioReceiver.update(e);
            return !audioReceiver.alive;
        });
    }

    private void requestDifficulties(ConnectionClient client, WaitLayer waiter, File setDirectory, Mapset existing) {
        String mapFilesKey = GeneralUtils.generateCode(4);
        client.send(ConnectionServer.EVENT_FILE_REQ + mapFilesKey + "MAPS");
        waiter.text = "Waiting for map difficulties...";

        MessageHandler mapReceiver = new MessageHandler(client)
        {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.identifier) {
                    case Message.UTF:
                        switch (msg.contents[0].toString()) {
                            case "FAIL":
                                editorLogger.info("Failed to receive maps, closing connection.");
                                break;
                            case "DONE":
                                editorLogger.info("Received all maps, moving to next step.");

                                timeout = -1;
                                alive = false;

                                Mapset set;
                                if (existing != null) {
                                    set = existing;
                                    set.loadMaps();
                                }
                                else {
                                    set = new Mapset(setDirectory);
                                    MapMaster.mapDatabase.mapsets.put(set.key, set);
                                    MapMaster.mapDatabase.index(set);
                                    updateMaps = true;
                                }

                                mapSelect.setSelected(set); //Also starts audio load process

                                //last thing
                                requestEditorState(client, waiter, set);
                                return;
                        }
                        return;
                    case Message.FILE:
                        if (!mapFilesKey.equals(msg.contents[0].toString())) {
                            editorLogger.warn("Received file with incorrect pass: " + msg.contents[0]);
                            return;
                        }

                        String filename = msg.contents[1].toString();
                        List<byte[]> filedata = (List<byte[]>) msg.contents[2];

                        if (filedata == null) {
                            editorLogger.info("No file received");
                            textOverlay.setText(filename, 2.0f);
                            break;
                        }

                        File mapFile = new File(setDirectory, filename);

                        try (FileOutputStream fileOut = new FileOutputStream(mapFile)) {
                            for (byte[] chunk : filedata) {
                                fileOut.write(chunk);
                            }
                        } catch (IOException e) {
                            textOverlay.setText("Failed to write map file.", 2.0f);
                            editorLogger.error("Failed to write map file.", e);
                            break;
                        }

                        editorLogger.info("Received map file successfully: " + filename);
                        return;
                }

                alive = false;
                onTimeout.apply(this);
            }
        }.setTimeout(45f, messageHandler -> {
            editorLogger.info("Timed out waiting for maps.");
            waiter.setComplete();
            try {
                messageHandler.client.close();
            }
            catch (Exception ignored) { }
            return false;
        });
        updaters.add((e)->{
            mapReceiver.update(e);
            return !mapReceiver.alive;
        });
    }

    private void requestEditorState(ConnectionClient client, WaitLayer waiter, Mapset set) {
        client.send(ConnectionServer.EVENT_SENT + "EDITORSTATE");
        waiter.text = "Waiting for editor state...";

        MessageHandler editorStateReceiver = new MessageHandler(client)
        {
            //editor state variable holder thing
            final Set<String> openDifficulties = new HashSet<>();
            //Map<String, asdf> openDifficultyProperties; probably won't do this
            double initialMusicPos = 0;

            @Override
            public void handleMessage(Message msg) {
                switch (msg.identifier) {
                    case Message.UTF:
                        String text = msg.contents[0].toString();
                        switch (text.substring(0, 4)) {
                            case "FAIL":
                                editorLogger.info("Failed to receive editor state, closing connection.");
                                break;
                            case "DIFF":
                                String mapKey = text.substring(4, 8);
                                String diffname = text.substring(8);
                                openDifficulties.add(diffname);
                                editorLogger.info("Open difficulty: \"" + diffname + "\"");
                                break;
                            case "POSN":
                                initialMusicPos = Double.parseDouble(text.substring(4));
                                editorLogger.info("Music position: " + initialMusicPos);
                                break;
                            case "DONE":
                                editorLogger.info("Received editor state, opening editor.");

                                MapSelect.MapOpenInfo openInfo = new MapSelect.MapOpenInfo(set);

                                for (MapInfo difficulty : set.getMaps()) {
                                    if (openDifficulties.remove(difficulty.getDifficultyName())) {
                                        openInfo.addInitialMap(difficulty);
                                    }
                                }

                                if (!openDifficulties.isEmpty()) {
                                    editorLogger.info("Failed to find " + openDifficulties.size() + " difficulties");
                                    openDifficulties.forEach(editorLogger::info);
                                }

                                LoadingLayer loader = chooseMap(openInfo, client);

                                if (loader == null) {
                                    editorLogger.info("Failed to open set, already opening a map?");
                                    return;
                                }

                                timeout = -1;
                                alive = false;
                                waiter.setComplete();

                                loader.addTask(true, ()->music.seekSecond(initialMusicPos));
                                loader.addCallback(()->{
                                    client.send(ConnectionServer.EVENT_SENT + "CLIENT_READY" + client.ID);
                                });
                                return;
                        }
                        return;
                }

                alive = false;
                onTimeout.apply(this);
            }
        }.setTimeout(60f, messageHandler -> {
            editorLogger.info("Timed out waiting for editor state.");
            waiter.setComplete();
            try {
                messageHandler.client.close();
            }
            catch (Exception ignored) { }
            return false;
        });
        updaters.add((e)->{
            editorStateReceiver.update(e);
            return !editorStateReceiver.alive;
        });
    }


    @Override
    public void receiveFiles(String[] files) {
        for (String path : files) {
            File f = new File(path);

            if (!f.exists()) continue;
            if (!f.isFile()) continue;
            if (!f.canRead()) continue;

            String extension = FileHelper.getFileExtension(f.getName());

            if (!AUDIO_EXTENSIONS.contains(extension)) {
                textOverlay.setText("File is not a valid audio file.", 2.0f);

                //Copy audio file
                //Generate and save empty beatmap file

                return;
            }
        }
    }

    private void versionUpdate() {
        if (updateID != null) {
            TaikoEditor.addLayer(new ConfirmationLayer("Exit and update?", "Yes", "No", false).onConfirm(
                    ()->{
                        music.pause();
                        TaikoEditor.addLayer(new UpdatingLayer(updateID, new File("lib").getAbsolutePath()));
                    }));
        }
    }

    private void quit()
    {
        TaikoEditor.addLayer(new ConfirmationLayer("Exit?", "Yes", "No", false).onConfirm(TaikoEditor::end).onCancel(TaikoEditor::end));
    }

    @Override
    public LoadingLayer getLoader() {
        LoadingLayer menuLoad;

        if (delayMapload) {
            menuLoad = new EditorLoadingLayer()
                    .loadLists("ui", "font", "menu", "editor", "hitsound")
                    .addTask(() ->
                        MapMaster.loadDelayed(()->{
                            updateMaps = true;
                        })
                    ).addTracker(MapMaster::getProgress).addTask(Skins::load);
        }
        else {
            menuLoad = new MenuLoadingLayer()
                    .loadLists("ui", "font", "menu", "editor", "hitsound")
                    .addTask(MapMaster::load).addTracker(MapMaster::getProgress).addTask(Skins::load);
        }

        menuLoad.addCallback(TaikoEditor::initialize).addCallback(()->canOpen = true)
                .addLayers(true,
                        ()->{
                            if (currentSkin == null) {
                                return new ProgramLayer[] {
                                        this,
                                        new LoadingLayer().loadLists("background")
                                };
                            }
                            else {
                                LoadingLayer loader = currentSkin.getLoader(this);
                                if (loader != null) {
                                    return new ProgramLayer[] {
                                            loader,
                                            new LoadingLayer().loadLists("background")
                                    };
                                }
                                else {
                                    return new ProgramLayer[] {
                                            this,
                                            new LoadingLayer().loadLists("background")
                                    };
                                }
                            }
                        }
                );

        return new LoadingLayer()
                .loadLists("base")
                .addLayers(true, menuLoad);
    }

    @Override
    public LoadingLayer getReturnLoader() {
        return new EditorLoadingLayer()
                .addLayers(true, this)
                .addTask(mapSelect::playMusic).addCallback(()->canOpen = true);
    }

    @Override
    public void dispose() {
        super.dispose();

        if (client != null) {
            try {
                client.close();
            }
            catch (Exception e) {
                editorLogger.error("Exception occurred while closing client", e);
            }
        }
    }


    private static class MenuProcessor extends BoundInputProcessor {
        private final MenuLayer sourceLayer;

        public MenuProcessor(MenuLayer source)
        {
            super(BindingMaster.getBindingGroupCopy("Basic"), true);
            this.sourceLayer = source;
        }

        @Override
        public void bind() {
            bindings.bind("Exit", ()->{
                if (!sourceLayer.searchInput.text.isEmpty()) {
                    sourceLayer.searchInput.clear();
                    sourceLayer.mapSelect.refreshMaps();
                    return;
                }
                sourceLayer.quit();
            });

            /*bindings.bind("1", ()->{
                try {
                    Transferable data = Toolkit.getDefaultToolkit()
                            .getSystemClipboard()
                            .getContents(this);
                    if (data != null) {
                        if (data.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                            String clipboardData = (String) data.getTransferData(DataFlavor.stringFlavor);
                            String[] params = clipboardData.split("\\|");

                            sourceLayer.test = new ConnectionClient(new Socket(params[0], Integer.parseInt(params[1])));

                            sourceLayer.test.send(params[2]);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            bindings.bind("2", ()->{
                if (sourceLayer.test != null) {
                    sourceLayer.test.send(".____.____.____.____.____.____.____.____.____.____.____.____.____.____.____.____.____.____.____.____" +
                            ".____.____.____.____.____.____.____.____.____.____.____.____.____.____.____.____.____.____.____.____" +
                            ".____.____.____.____.____.____.____.____.____.____.____.____.____.____.____.____.____.____.____.____"); //300 chars
                }
            });*/

            bindings.bind("Up", ()->{
                sourceLayer.mapSelect.scroll(-5);
            }, new Scrollable.ScrollKeyHold(sourceLayer.mapSelect, -25));
            bindings.bind("Down", ()->{
                sourceLayer.mapSelect.scroll(5);
            }, new Scrollable.ScrollKeyHold(sourceLayer.mapSelect, 25));

            bindings.bind("Refresh", ()->sourceLayer.mapSelect.reloadDatabase());

            bindings.addMouseBind(
                (x, y, b)->{
                    for (ImageButton button : sourceLayer.buttons) {
                        if (button.contains(x, y))
                            return button;
                    }
                    return null;
                },
                (button, xy, b) -> {
                    if (button != null) {
                        button.effect(b);
                    }
                    return null;
                });
            bindings.addMouseBind((x, y, b)->true,
                    (p, b)->{
                        MapSelect.MapOpenInfo info = sourceLayer.mapSelect.click(p.x, p.y);
                        if (info != null && info.getSet() != null) {
                            sourceLayer.chooseMap(info, null);
                        }
                        return null;
                    });
        }

        @Override
        public boolean keyTyped(char character) {
            if (sourceLayer.searchInput.keyTyped(character)) {
                sourceLayer.updateMaps = true;
                return true;
            }

            return super.keyTyped(character);
        }

        @Override
        public boolean onTouchDown(int gameX, int gameY, int pointer, int button) {
            //editorLogger.trace("Game coordinates: " + gameX + ", " + gameY);
            return super.onTouchDown(gameX, gameY, pointer, button);
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            sourceLayer.mapSelect.scroll(amountY * 3);

            return true;
        }
    }
}
