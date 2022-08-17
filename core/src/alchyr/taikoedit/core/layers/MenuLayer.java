package alchyr.taikoedit.core.layers;

import alchyr.networking.p2p.ConnectionHost;
import alchyr.networking.p2p.ConnectionSub;
import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.input.BoundInputProcessor;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.core.input.sub.TextInput;
import alchyr.taikoedit.core.layers.sub.ConfirmationLayer;
import alchyr.taikoedit.core.layers.sub.UpdatingLayer;
import alchyr.taikoedit.core.layers.tests.BindingTestLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.core.ui.MapSelect;
import alchyr.taikoedit.core.ui.Scrollable;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.MapMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.management.assets.skins.Skins;
import alchyr.taikoedit.management.assets.loaders.OsuBackgroundLoader;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;

import static alchyr.taikoedit.TaikoEditor.*;
import static alchyr.taikoedit.management.assets.skins.Skins.currentSkin;

public class MenuLayer extends LoadedLayer implements InputLayer {
    private boolean initialized;

    private final MenuProcessor processor;

    private MapSelect mapSelect;
    private boolean updateMaps = false;

    private boolean useOsuBackground = false;
    private Texture osuBackground = null;
    private int bgWidth, bgHeight;

    private BitmapFont font;
    private Texture pixel;
    private int searchHeight, searchY;
    private float searchTextOffsetX, searchTextOffsetY;

    private TextInput searchInput;

    private ImageButton exitButton;
    private ImageButton settingsButton;
    //private ImageButton connectButton;
    private ImageButton updateButton;

    private static final Color bgColor = new Color(0.3f, 0.3f, 0.25f, 1.0f);

    private String updateID = null;

    public MenuLayer()
    {
        processor = new MenuProcessor(this);
        this.type = LAYER_TYPE.FULL_STOP;

        //buttons = new ArrayList<>();
        //mapOptions = new ArrayList<>();
        //hashedMapOptions = new HashMap<>();

        initialized = false;
    }

    @Override
    public void initialize() {
        useOsuBackground = !OsuBackgroundLoader.loadedBackgrounds.isEmpty() && MathUtils.randomBoolean();
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

            exitButton = new ImageButton(SettingsMaster.getWidth() - 40, SettingsMaster.getHeight() - 40, assetMaster.get("ui:exit"), (Texture) assetMaster.get("ui:exith")).setClick(this::quit);
            settingsButton = new ImageButton(SettingsMaster.getWidth() - 80, SettingsMaster.getHeight() - 40, assetMaster.get("ui:settings"), (Texture) assetMaster.get("ui:settingsh")).setClick(this::settings);
            //connectButton = new ImageButton(SettingsMaster.getWidth() - 120, SettingsMaster.getHeight() - 40, assetMaster.get("ui:connect"), (Texture) assetMaster.get("ui:connecth")).setClick(this::openConnect);

            initialized = true;

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
                        updateButton = new ImageButton(SettingsMaster.getWidth() - 160, SettingsMaster.getHeight() - 40, assetMaster.get("ui:update"), (Texture) assetMaster.get("ui:updateh")).setClick(this::versionUpdate);
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
            updateCheck.start();
        }
        processor.bind();
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

        mapSelect.update(elapsed);

        exitButton.update(elapsed);
        settingsButton.update(elapsed);
        //connectButton.update(elapsed);
        if (updateButton != null) {
            updateButton.update(elapsed);
            if (updateButton.hovered) {
                hoverText.setText("Update Available");
            }
        }
        if (settingsButton.hovered) {
            hoverText.setText("Settings");
        }
        else if (exitButton.hovered) {
            hoverText.setText("Exit");
        }
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

        settingsButton.render(sb, sr);
        exitButton.render(sb, sr);
        //connectButton.render(sb, sr);

        if (updateButton != null)
            updateButton.render(sb, sr);

        sb.draw(pixel, 0, searchY - 1, SettingsMaster.getWidth(), 2);
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    private boolean canOpen = true;
    private void chooseMap(MapSelect.MapOpenInfo info)
    {
        if (canOpen) {
            EditorLayer edit = new EditorLayer(this, info.getSet(), info.getInitialDifficulty());

            canOpen = false;
            TaikoEditor.removeLayer(this);
            TaikoEditor.addLayer(edit.getLoader());
        }
    }

    private void test()
    {
        TaikoEditor.removeLayer(this);
        TaikoEditor.addLayer(new BindingTestLayer().getLoader());
    }

    private void settings(int button)
    {
        TaikoEditor.addLayer(new SettingsLayer());
    }

    private void openConnect() {

    }

    private boolean checkUpdate(int x, int y, int b) {
        return updateButton != null && updateButton.contains(x, y);
    }
    private MouseHoldObject doUpdate(Vector2 pos, int button) {
        updateButton.effect(button);
        return null;
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
        return new LoadingLayer()
                .loadLists("base")
                .addLayers(true,
                        new EditorLoadingLayer()
                            .loadLists("ui", "font", "background", "menu", "editor", "hitsound")
                            .addTask(MapMaster::load).addTracker(MapMaster::getProgress).addTask(Skins::load)
                            .addCallback(TaikoEditor::initialize).addCallback(()->canOpen = true)
                            .addLayers(true,
                                    ()->{
                                        if (currentSkin == null) {
                                            return new ProgramLayer[] { this };
                                        }
                                        else {
                                            LoadingLayer loader = currentSkin.getLoader(this);
                                            if (loader != null) {
                                                return new ProgramLayer[] { loader };
                                            }
                                            else {
                                                return new ProgramLayer[] { this };
                                            }
                                        }
                                    }
                            )
                );
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
        if (test != null)
            test.dispose();
        if (sub != null)
            sub.dispose();
    }

    private ConnectionHost test = null;
    private ConnectionSub sub = null;
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
                sourceLayer.test = new ConnectionHost();
                sourceLayer.test.prepConnection();
            });

            bindings.bind("2", ()->{
                try {
                    Transferable data = Toolkit.getDefaultToolkit()
                            .getSystemClipboard()
                            .getContents(this);
                    if (data != null) {
                        if (data.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                            String clipboardData = (String) data.getTransferData(DataFlavor.stringFlavor);
                            sourceLayer.sub = new ConnectionSub(clipboardData);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            bindings.bind("3", ()->{
                try {
                    if (sourceLayer.test != null) {
                        Transferable data = Toolkit.getDefaultToolkit()
                                .getSystemClipboard()
                                .getContents(this);
                        if (data != null) {
                            if (data.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                                String clipboardData = (String) data.getTransferData(DataFlavor.stringFlavor);
                                sourceLayer.test.finishConnection(clipboardData);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });*/

            bindings.bind("Up", ()->{
                sourceLayer.mapSelect.scroll(-5);
            }, new Scrollable.ScrollKeyHold(sourceLayer.mapSelect, -25));
            bindings.bind("Down", ()->{
                sourceLayer.mapSelect.scroll(5);
            }, new Scrollable.ScrollKeyHold(sourceLayer.mapSelect, 25));

            bindings.bind("Refresh", ()->sourceLayer.mapSelect.reloadDatabase());

            bindings.addMouseBind(sourceLayer.settingsButton::contains, sourceLayer.settingsButton::effect);
            bindings.addMouseBind(sourceLayer.exitButton::contains, sourceLayer.exitButton::effect);
            //bindings.addMouseBind(sourceLayer.connectButton::contains, sourceLayer.connectButton::effect);
            bindings.addMouseBind(sourceLayer::checkUpdate, sourceLayer::doUpdate);
            bindings.addMouseBind((x, y, b)->true,
                    (p, b)->{
                        MapSelect.MapOpenInfo info = sourceLayer.mapSelect.click(p.x, p.y);
                        if (info != null && info.getSet() != null) {
                            sourceLayer.chooseMap(info);
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
