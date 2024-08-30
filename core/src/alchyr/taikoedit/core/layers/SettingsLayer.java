package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.UIElement;
import alchyr.taikoedit.core.input.*;
import alchyr.taikoedit.core.layers.sub.GetHotkeyLayer;
import alchyr.taikoedit.core.ui.*;
import alchyr.taikoedit.editor.maps.components.hitobjects.Hit;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.LocalizationMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.management.assets.skins.SkinProvider;
import alchyr.taikoedit.management.assets.skins.Skins;
import alchyr.taikoedit.management.localization.LocalizedText;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.text.DecimalFormat;
import java.util.*;

import static alchyr.taikoedit.TaikoEditor.*;

//SETTINGS:

/*
Multiple pages?
Page 1: Editor Settings
object position thing (with mini grid to click on)
Click on labels on left to choose which ones to edit. Click on grid or type new values and then confirm.
    Don     x:___ y:___    ___ <grid showing positions
    Kat     x:___ y:___   |   | x: if only one selected, will match that one and can be edited. If multiple non-matching are selected, will clear itself
    Big Don x:___ y:___   |   | y:
    Big Kat x:___ y:___   |___| Accept

    Music Volume: 100 Effect Volume: 100
    maybe one day i'll add a slider, but for now just text

Volume (music, effect)

Page 2: Hotkeys

Page 3: Colors?

 */
public class SettingsLayer extends ProgramLayer implements InputLayer {
    private static final float BUTTON_X = 0, BUTTON_Y = SettingsMaster.getHeight() / 2;
    private static final int BUTTON_WIDTH = 100, BUTTON_HEIGHT = 40;
    private static final String verString;
    static {
        String base = String.valueOf(VERSION);
        if (base.length() == 3)
            verString = "v" + base.charAt(0) + "." + base.charAt(1) + "." + base.charAt(2);
        else
            verString = "v???";
    }
    private static final String credits = "Made by Alchyr.\nCertain icons are from Font Awesome.\n" + verString;

    //editor settings
    //divided into 5 columns, one of which is wider than the others. 1/6 1/6 1/6 2/6 1/6
    private static final int SMALL_SECTION_WIDTH, LARGE_SECTION_WIDTH, X_1, X_2, X_3, X_4, X_5, X_6, X_CENTER, LABEL_Y_START, HOTKEY_Y_START;
    private static final int LABEL_Y_SPACING = 50;
    private static final int HOTKEY_SPACING = 40;

    static {
        float displayAreaWidth = SettingsMaster.getWidth() - BUTTON_WIDTH - 20;
        SMALL_SECTION_WIDTH = Math.min((int) (displayAreaWidth / 8), 99999); // 150);
        LARGE_SECTION_WIDTH = SMALL_SECTION_WIDTH * 2;
        X_1 = BUTTON_WIDTH + 10;
        X_2 = X_1 + SMALL_SECTION_WIDTH;
        X_3 = X_2 + SMALL_SECTION_WIDTH;
        X_4 = X_3 + SMALL_SECTION_WIDTH;
        X_5 = X_4 + LARGE_SECTION_WIDTH;
        X_6 = X_5 + SMALL_SECTION_WIDTH;

        X_CENTER = (int) (X_1 + displayAreaWidth / 2);

        LABEL_Y_START = SettingsMaster.getHeight() - 80;
        HOTKEY_Y_START = (int) (SettingsMaster.getHeight() * 0.9f);
    }


    private SettingsProcessor mainProcessor;
    private BoundInputMultiplexer processor;

    private Page[] pages;
    private Button[] pageButtons;

    private BitmapFont font;
    private float creditsY = 50;

    public TextOverlay textOverlay;

    private ImageButton exitButton;

    private final Set<BindingGroup> adjustedGroups = new HashSet<>();

    private static int currentPage = 0;


    private Texture bg;


    public SettingsLayer(Texture background)
    {
        this.type = LAYER_TYPE.FULL_STOP;
        this.bg = background;
    }

    public SettingsLayer()
    {
        this(null);
    }

    private static final DecimalFormat oneDecimal = new DecimalFormat("#0.#", osuDecimalFormat);
    @Override
    public void initialize() {
        font = assetMaster.getFont("aller medium");
        creditsY = 10 + textRenderer.setFont(font).getHeight(credits);

        mainProcessor = new SettingsProcessor(this);
        processor = new BoundInputMultiplexer(mainProcessor);

        exitButton = new ImageButton(SettingsMaster.getWidth() - 40, SettingsMaster.getHeight() - 40, assetMaster.get("ui:exit"), (Texture) assetMaster.get("ui:exith")).setClick(this::close);

        textOverlay = new TextOverlay(font, SettingsMaster.getHeight() / 2, 100);

        pages = new Page[2];
        pageButtons = new Button[2];

        pageButtons[0] = new Button(BUTTON_X, BUTTON_Y + BUTTON_HEIGHT / 2.0f, BUTTON_WIDTH, BUTTON_HEIGHT, "Editor", font).setClick((i)->this.swapPages(0)).useBorderRendering();
        pageButtons[1] = new Button(BUTTON_X, BUTTON_Y - BUTTON_HEIGHT / 2.0f, BUTTON_WIDTH, BUTTON_HEIGHT, "Hotkeys", font).setClick((i)->this.swapPages(1)).useBorderRendering();

        pages[0] = editorSettingsPage(font, bg);
        pages[1] = hotkeysPage(font);

        pages[0].bind();
        pages[1].bind();

        swapPages(currentPage);

        mainProcessor.bind();
    }

    private void close() {
        pages[currentPage].releaseInput(false);
        pages[currentPage].hidden();
        TaikoEditor.removeLayer(this);

        for (BindingGroup group : adjustedGroups) {
            group.updateInputMap();
        }
        if (!adjustedGroups.isEmpty()) {
            BindingMaster.save();
        }
    }
    private void swapPages(int page) {
        for (int i = 0; i < pageButtons.length; ++i) {
            pageButtons[i].renderBorder = i == page;
        }
        if (page >= 0 && page < pages.length) {
            if (page != currentPage) {
                pages[currentPage].releaseInput(false);
                pages[currentPage].hidden();
            }
            currentPage = page;
            processor.setProcessors(pages[currentPage], mainProcessor);
        }
    }
    private void updateName(String t, TextField f) {
        SettingsMaster.NAME = t;
        SettingsMaster.saveGeneralSettings();
    }
    private void updateMusicVolume(String t, TextField f) {
        try {
            float vol = Float.parseFloat(t);

            vol = Math.min(1.0f, Math.max(0.0f, vol / 100.0f));

            SettingsMaster.setMusicVolume(vol);

            SettingsMaster.saveGeneralSettings();
        }
        catch (Exception ignored) {
        }
        f.setText(oneDecimal.format(SettingsMaster.getMusicVolume() * 100));
    }
    private void updateEffectVolume(String t, TextField f) {
        try {
            float vol = Float.parseFloat(t);

            vol = Math.min(1.0f, Math.max(0.0f, vol / 100.0f));

            SettingsMaster.effectVolume = vol;

            SettingsMaster.saveGeneralSettings();
        }
        catch (Exception ignored) {
        }
        f.setText(oneDecimal.format(SettingsMaster.effectVolume * 100));
    }

    private void updateUseLazerSnappings(boolean useLazerSnappings) {
        SettingsMaster.lazerSnaps = useLazerSnappings;
        SettingsMaster.saveGeneralSettings();

        if (EditorLayer.activeEditor != null) {
            textOverlay.setText("Reopen map to update snappings.", 2.0f);
        }
    }

    @Override
    public void update(float elapsed) {
        processor.update(elapsed);
        //pages[currentPage].update(elapsed);

        for (Button b : pageButtons)
            b.update(elapsed);

        exitButton.update(elapsed);
        if (exitButton.hovered) {
            hoverText.setText("Exit");
        }

        textOverlay.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        pages[currentPage].render(sb, sr);

        for (Button b : pageButtons)
            b.render(sb, sr);

        exitButton.render(sb, sr);

        textOverlay.render(sb, sr);

        textRenderer.setFont(font).renderText(sb, credits, 10, creditsY);
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    @Override
    public void dispose() {
        super.dispose();
        mainProcessor.dispose();

        for (Page page : pages)
            page.dispose();
    }

    private static class SettingsProcessor extends BoundInputProcessor {
        private final SettingsLayer sourceLayer;

        public SettingsProcessor(SettingsLayer source)
        {
            super(BindingMaster.getBindingGroupCopy("Basic"), true);
            this.sourceLayer = source;
        }

        @Override
        public void bind() {
            bindings.bind("Exit", sourceLayer::close);

            bindings.addMouseBind((x, y, b)->{
                for (Button button : sourceLayer.pageButtons) {
                    if (button.contains(x, y)) {
                        return true;
                    }
                }
                return false;
            }, (p, b)-> {
                for (Button button : sourceLayer.pageButtons) {
                    if (button.click(p.x, p.y, b)) {
                        return null;
                    }
                }
                return null;
            });

            bindings.addMouseBind((x, y, b)->currentPage == 0 && !sourceLayer.activePositioning.isEmpty() && sourceLayer.positioningArea.contains(x, y),
                    (p, b)-> {
                        if (sourceLayer.positioningArea.position(p.x, p.y, sourceLayer)) {
                            sourceLayer.activePositioning.clear();
                            sourceLayer.xField.setText("");
                            sourceLayer.xField.disable();
                            sourceLayer.yField.setText("");
                            sourceLayer.yField.disable();
                            for (Button button : sourceLayer.positioningButtons)
                                button.renderBorder = false;
                        }
                        return null;
                    });

            bindings.addMouseBind(sourceLayer.exitButton::contains, sourceLayer.exitButton::effect);
        }
    }




    // Page Generation
    private Page editorSettingsPage(BitmapFont font, Texture bg) {
        Page editorSettings = new Page(null);

        //editor settings page
        int y = LABEL_Y_START;
        editorSettings.addLabel(X_1 + 10, y, font, "Name: ");
        TextField nameField = new TextField(X_1 + 10, y, LARGE_SECTION_WIDTH, "Name:", SettingsMaster.NAME, 32, font)
                .setOnEndInput(this::updateName);
        nameField.setOnEnter(nameField::disable);

        editorSettings.add(nameField);

        y -= (int) (LABEL_Y_SPACING * 1.5f);

        editorSettings.addLabel(X_1 + 10, y, font, "Don");
        editorSettings.add(xPositions[0] = new Label(X_2 + 10, y, font, "x:" + SettingsMaster.donX));
        editorSettings.add(yPositions[0] = new Label(X_3 + 10, y, font, "y:" + SettingsMaster.donY));
        Button don = new Button(X_1, y - LABEL_Y_SPACING / 2.0f, SMALL_SECTION_WIDTH * 3, LABEL_Y_SPACING, null, font).setClick((b)->togglePositioning(0)).useBorderRendering();

        y -= LABEL_Y_SPACING;
        editorSettings.addLabel(X_1 + 10, y, font, "Kat");
        editorSettings.add(xPositions[1] = new Label(X_2 + 10, y, font, "x:" + SettingsMaster.katX));
        editorSettings.add(yPositions[1] = new Label(X_3 + 10, y, font, "y:" + SettingsMaster.katY));
        Button kat = new Button(X_1, y - LABEL_Y_SPACING / 2.0f, SMALL_SECTION_WIDTH * 3, LABEL_Y_SPACING, null, font).setClick((b)->togglePositioning(1)).useBorderRendering();

        xField = new TextField(X_5, y, SMALL_SECTION_WIDTH, "x:", "", 3, font).setType(TextField.TextType.INTEGER);
        xField.setOnEndInput(this::setXPosition);
        xField.setOnEnter(xField::disable);
        xField.lock();
        editorSettings.add(xField);

        positioningArea = new ObjectPositioningArea(X_4 + LARGE_SECTION_WIDTH / 2.0f, y - LABEL_Y_SPACING / 2.0f, LARGE_SECTION_WIDTH * 0.8f, 180, bg);
        editorSettings.addUIElement(positioningArea);

        y -= LABEL_Y_SPACING;
        editorSettings.addLabel(X_1 + 10, y, font, "Big Don");
        editorSettings.add(xPositions[2] = new Label(X_2 + 10, y, font, "x:" + SettingsMaster.bigDonX));
        editorSettings.add(yPositions[2] = new Label(X_3 + 10, y, font, "y:" + SettingsMaster.bigDonY));
        Button bigDon = new Button(X_1, y - LABEL_Y_SPACING / 2.0f, SMALL_SECTION_WIDTH * 3, LABEL_Y_SPACING, null, font).setClick((b)->togglePositioning(2)).useBorderRendering();

        yField = new TextField(X_5, y, SMALL_SECTION_WIDTH, "y:", "", 3, font).setType(TextField.TextType.INTEGER);
        yField.setOnEndInput(this::setYPosition);
        yField.setOnEnter(yField::disable);
        yField.lock();
        editorSettings.add(yField);

        y -= LABEL_Y_SPACING;
        editorSettings.addLabel(X_1 + 10, y, font, "Big Kat");
        editorSettings.add(xPositions[3] = new Label(X_2 + 10, y, font, "x:" + SettingsMaster.bigKatX));
        editorSettings.add(yPositions[3] = new Label(X_3 + 10, y, font, "y:" + SettingsMaster.bigKatY));
        Button bigKat = new Button(X_1, y - LABEL_Y_SPACING / 2.0f, SMALL_SECTION_WIDTH * 3, LABEL_Y_SPACING, null, font).setClick((b)->togglePositioning(3)).useBorderRendering();

        y -= (int) (LABEL_Y_SPACING * 1.5f);

        TextField musicVolume = new TextField(X_2 + 10 - (SMALL_SECTION_WIDTH / 2), y, LARGE_SECTION_WIDTH, "Music Volume:", oneDecimal.format(SettingsMaster.getMusicVolume() * 100), 5, font)
                .setType(TextField.TextType.NUMERIC).setOnEndInput(this::updateMusicVolume);
        TextField effectVolume = new TextField(X_4 + 10 - (SMALL_SECTION_WIDTH / 2), y, LARGE_SECTION_WIDTH, "Effect Volume:", oneDecimal.format(SettingsMaster.effectVolume * 100), 5, font)
                .setType(TextField.TextType.NUMERIC).setOnEndInput(this::updateEffectVolume);

        musicVolume.setOnEnter(musicVolume::disable);
        effectVolume.setOnEnter(effectVolume::disable);

        editorSettings.add(musicVolume);
        editorSettings.add(effectVolume);

        y -= LABEL_Y_SPACING;

        ToggleButton lazerSnaps = new ToggleButton(X_2 + 10 - (SMALL_SECTION_WIDTH / 2), y, "Use Lazer Snappings", font, SettingsMaster.lazerSnaps)
                .setOnToggle(this::updateUseLazerSnappings);
        editorSettings.add(lazerSnaps);


        y = LABEL_Y_START;
        Label skinLabel = editorSettings.addLabel(X_6, y, font, "Skin: ");
        DropdownBox<SkinProvider> skinSelect = new DropdownBox<>(X_6 + skinLabel.getWidth() + 6, y, 1.5f * SMALL_SECTION_WIDTH, SettingsMaster.getHeight() * 0.5f, Skins.skins, font)
                .setOption(Skins.currentSkin);
        skinSelect.setOnSelect((old, current)->{
                    if (!current.equals(old)) {
                        LoadingLayer followup = new LoadingLayer();
                        followup.addTask(()->{
                            if (current.state == SkinProvider.LoadState.LOADED) {
                                Skins.currentSkin = current;
                                TaikoEditor.onMain(old::unload);
                                SettingsMaster.saveGeneralSettings();
                            }
                            else {
                                skinSelect.setOption(old);
                                skinSelect.removeOption(current);
                                TaikoEditor.onMain(current::unload);
                                if (current.failMsg != null && !current.failMsg.isEmpty())
                                    textOverlay.setText(current.failMsg, 3.0f);
                            }
                        });
                        followup.type = LAYER_TYPE.UPDATE_STOP;
                        LoadingLayer skinLoader = current.getLoader(followup);
                        skinLoader.type = LAYER_TYPE.UPDATE_STOP;
                        addLayer(skinLoader);
                    }
                });

        editorSettings.add(skinSelect);

        positioningButtons.add(don);
        editorSettings.add(don);
        positioningButtons.add(kat);
        editorSettings.add(kat);
        positioningButtons.add(bigDon);
        editorSettings.add(bigDon);
        positioningButtons.add(bigKat);
        editorSettings.add(bigKat);

        return editorSettings;
    }

    private Page hotkeysPage(BitmapFont font) {
        Page page = new Page(null);

        LocalizedText keyNames = LocalizationMaster.getLocalizedText("keys", "names");
        if (keyNames == null)
            return page;

        String[] keyNameArray = keyNames.get("");
        if (keyNameArray == null)
            return page;

        Map<String, LocalizedText> hotkeyGroups = LocalizationMaster.getLocalizedGroup("hotkeys");

        Map<String, BindingGroup> bindingGroups = BindingMaster.getBindingGroups();

        int x = X_2 + 10;
        int y = HOTKEY_Y_START;

        List<UIElement> orderedElements = new ArrayList<>();

        for (Map.Entry<String, BindingGroup> group : bindingGroups.entrySet()) {
            if (group.getValue().isModifiable()) {
                //Group Label
                LocalizedText groupLocalization = hotkeyGroups.getOrDefault(group.getKey(), LocalizationMaster.noText);
                orderedElements.add(page.addLabel(X_CENTER, y, font, groupLocalization.getOrDefault("SetName", 0, group.getKey())));
                y -= HOTKEY_SPACING;

                for (InputBinding binding : group.getValue().getOrderedBindings()) {
                    //Binding label
                    Button addInput = new Button(x - 40, y - 15, 30, 30, "+", font).useBorderRendering(true);
                    orderedElements.add(page.add(addInput));
                    Label bindingLabel = page.addLabel(x, y, font, groupLocalization.getOrDefault(binding.getInputID(), 0));
                    orderedElements.add(bindingLabel);

                    addInput.setClick(()->{
                        GetHotkeyLayer hotkeyLayer = new GetHotkeyLayer(keyNameArray, null);
                        hotkeyLayer.onConfirm(()->{
                            InputBinding.InputInfo info = new InputBinding.InputInfo(hotkeyLayer.keycode, hotkeyLayer.ctrl, hotkeyLayer.alt, hotkeyLayer.shift);
                            binding.addInput(info);

                            float newY = bindingLabel.getY() - HOTKEY_SPACING;
                            Button adjustInput = new Button(X_3, newY - HOTKEY_SPACING / 2.0f, SMALL_SECTION_WIDTH * 3, HOTKEY_SPACING, null, font).useBorderRendering();
                            adjustInput.renderBorder = true;
                            Button removeInput = new Button(X_3 - 40, newY - 15, 30, 30, "-", font).useBorderRendering(true);
                            Label inputLabel = page.addLabel(X_3 + 10, newY, font, info.toString(keyNameArray));

                            int index = orderedElements.indexOf(bindingLabel) + 1;
                            orderedElements.add(index++, page.add(removeInput));
                            orderedElements.add(index++, page.add(adjustInput));
                            orderedElements.add(index++, inputLabel);

                            //These internal methods don't have to register the group as changed since for them to happen the group was already marked as changed
                            removeInput.setClick(()->{
                                binding.removeInput(info);
                                page.remove(removeInput);
                                page.remove(adjustInput);
                                page.remove(inputLabel);

                                boolean adjust = false;
                                Iterator<UIElement> elementIterator = orderedElements.iterator();
                                while (elementIterator.hasNext()) {
                                    UIElement e = elementIterator.next();
                                    if (e.equals(removeInput) || e.equals(adjustInput) || e.equals(inputLabel)) {
                                        elementIterator.remove();
                                        adjust = true;
                                    }
                                    else if (adjust) {
                                        e.move(0, HOTKEY_SPACING);
                                    }
                                }
                                page.adjustMaximumScroll(-HOTKEY_SPACING);
                            });

                            adjustInput.setClick(()->{
                                GetHotkeyLayer subHotkeyLayer = new GetHotkeyLayer(keyNameArray, info);
                                subHotkeyLayer.onConfirm(()->{
                                    info.set(subHotkeyLayer.keycode, subHotkeyLayer.ctrl, subHotkeyLayer.alt, subHotkeyLayer.shift, false);
                                    inputLabel.setText(info.toString(keyNameArray));
                                });
                                addLayer(subHotkeyLayer);
                            });

                            for (; index < orderedElements.size(); ++index) {
                                orderedElements.get(index).move(0, -HOTKEY_SPACING);
                            }
                            page.adjustMaximumScroll(HOTKEY_SPACING);

                            adjustedGroups.add(group.getValue());
                        });
                        addLayer(hotkeyLayer);
                    });

                    y -= HOTKEY_SPACING;

                    //Input labels
                    for (InputBinding.InputInfo info : binding.getInputs()) {
                        Button adjustInput = new Button(X_3, y - HOTKEY_SPACING / 2.0f, SMALL_SECTION_WIDTH * 3, HOTKEY_SPACING, null, font).useBorderRendering();
                        adjustInput.renderBorder = true;
                        Button removeInput = new Button(X_3 - 40, y - 15, 30, 30, "-", font).useBorderRendering(true);
                        Label inputLabel = page.addLabel(X_3 + 10, y, font, info.toString(keyNameArray));

                        orderedElements.add(page.add(removeInput));
                        orderedElements.add(page.add(adjustInput));
                        orderedElements.add(inputLabel);

                        removeInput.setClick(()->{
                            binding.removeInput(info);
                            page.remove(removeInput);
                            page.remove(adjustInput);
                            page.remove(inputLabel);

                            boolean adjust = false;
                            Iterator<UIElement> elementIterator = orderedElements.iterator();
                            while (elementIterator.hasNext()) {
                                UIElement e = elementIterator.next();
                                if (e.equals(removeInput) || e.equals(adjustInput) || e.equals(inputLabel)) {
                                    elementIterator.remove();
                                    adjust = true;
                                }
                                else if (adjust) {
                                    e.move(0, HOTKEY_SPACING);
                                }
                            }
                            page.adjustMaximumScroll(-HOTKEY_SPACING);

                            adjustedGroups.add(group.getValue());
                        });

                        adjustInput.setClick(()->{
                            GetHotkeyLayer hotkeyLayer = new GetHotkeyLayer(keyNameArray, info);
                            hotkeyLayer.onConfirm(()->{
                                info.set(hotkeyLayer.keycode, hotkeyLayer.ctrl, hotkeyLayer.alt, hotkeyLayer.shift, false);
                                inputLabel.setText(info.toString(keyNameArray));

                                adjustedGroups.add(group.getValue());
                            });
                            addLayer(hotkeyLayer);
                        });

                        y -= HOTKEY_SPACING;
                    }
                }
            }
        }

        //y positive: no scrolling
        //y negative, moving below screen, small amount of scrolling
        page.setMaximumScroll(-(y - HOTKEY_SPACING));

        return page;
    }

    //page 1 stuff
    private ObjectPositioningArea positioningArea;
    private static class ObjectPositioningArea implements UIElement {
        private float cX, cY, width, height, scale, dx, dy;

        private final Texture bg;
        private int bgWidth, bgHeight, bgOffsetX, bgOffsetY,
                bgSrcWidth, bgSrcHeight, bgSrcOffsetX, bgSrcOffsetY;

        public ObjectPositioningArea(float cX, float cY, float maxWidth, float maxHeight, Texture bg) {
            height = maxWidth * 12 / 16; //height if using maxWidth
            if (height > maxHeight) { //too big
                width = maxHeight * 16 / 12;
                height = maxHeight;
            }
            else {
                width = maxWidth;
            }
            scale = width / 512.0f;

            this.cX = cX;
            this.cY = cY;

            dx = 0;
            dy = 0;

            this.bg = bg;
            if (bg != null) {
                float bgScale = Math.max(width / bg.getWidth(), height / bg.getHeight());
                bgScale *= 1.28;

                bgWidth = Math.round(bg.getWidth() * bgScale);
                bgHeight = Math.round(bg.getHeight() * bgScale);

                bgSrcHeight = (int) (bg.getHeight() * (height / bgHeight));
                bgSrcOffsetY = (int) ((bg.getHeight() - bgSrcHeight) * 0.5f);
                bgHeight = (int) height;

                bgSrcWidth = (int) (bg.getWidth() * (width / bgWidth));
                bgSrcOffsetX = (int) ((bg.getWidth() - bgSrcWidth) * 0.5f);
                bgWidth = (int) width;

                bgOffsetX = bgWidth / 2;
                bgOffsetY = bgHeight / 2;
            }
        }

        public boolean position(float gameX, float gameY, SettingsLayer source) {
            if (gameX < cX - width / 2.0f || gameY < cY - height / 2.0f || gameX > cX + width / 2.0f || gameY > cY + height / 2.0f) {
                return false;
            }

            int x = Math.max(0, (Math.min(512, (int) ((gameX - (cX - width / 2.0f)) / scale))));
            int y = Math.max(0, (Math.min(384, (int) (((cY + height / 2.0f) - gameY) / scale))));

            String xString = "x:" + x;
            String yString = "y:" + y;
            for (int i : source.activePositioning) {
                source.xPositions[i].setText(xString);
                source.yPositions[i].setText(yString);

                switch (i) {
                    case 0:
                        SettingsMaster.donX = x;
                        SettingsMaster.donY = y;
                        break;
                    case 1:
                        SettingsMaster.katX = x;
                        SettingsMaster.katY = y;
                        break;
                    case 2:
                        SettingsMaster.bigDonX = x;
                        SettingsMaster.bigDonY = y;
                        break;
                    case 3:
                        SettingsMaster.bigKatX = x;
                        SettingsMaster.bigKatY = y;
                        break;
                }
            }

            SettingsMaster.saveGeneralSettings();

            return true;
        }

        @Override
        public void move(float dx, float dy) {
            cX += dx;
            cY += dy;
        }

        @Override
        public void update(float elapsed) {

        }

        @Override
        public void render(SpriteBatch sb, ShapeRenderer sr) {
            float left = cX - width / 2 + dx, right = cX + width / 2 + dx, top = cY + height / 2 + dy, bottom = cY - height / 2 + dy, temp = top;

            if (bg != null) {
                sb.setColor(Color.WHITE);
                sb.draw(bg, left, bottom, bgOffsetX, bgOffsetY, bgWidth, bgHeight, 1, 1, 0, bgSrcOffsetX, bgSrcOffsetY, bgSrcWidth, bgSrcHeight, false, false);
            }

            sb.end();
            //grid first
            sr.begin(ShapeRenderer.ShapeType.Line);

            sr.setColor(Color.WHITE);
            sr.rect(left, bottom, width, height);

            sr.setColor(Color.GRAY);
            //horizontal lines
            temp -= height / 6.0f;
            sr.line(left, temp, right, temp);
            temp -= height / 6.0f;
            sr.line(left, temp, right, temp);
            temp -= height / 6.0f;
            sr.line(left, temp, right, temp);
            temp -= height / 6.0f;
            sr.line(left, temp, right, temp);
            temp -= height / 6.0f;
            sr.line(left, temp, right, temp);

            //vertical lines
            temp = left + width / 8.0f;
            sr.line(temp, top, temp, bottom);
            temp += width / 8.0f;
            sr.line(temp, top, temp, bottom);
            temp += width / 8.0f;
            sr.line(temp, top, temp, bottom);
            temp += width / 8.0f;
            sr.line(temp, top, temp, bottom);
            temp += width / 8.0f;
            sr.line(temp, top, temp, bottom);
            temp += width / 8.0f;
            sr.line(temp, top, temp, bottom);
            temp += width / 8.0f;
            sr.line(temp, top, temp, bottom);

            sr.end();
            sr.begin(ShapeRenderer.ShapeType.Filled);

            //preview objects
            //0,0 in osu coordinates is top left
            sr.setColor(Hit.kat);
            sr.circle(left + SettingsMaster.bigKatX * scale, top - SettingsMaster.bigKatY * scale, 40 * scale);
            sr.setColor(Hit.don);
            sr.circle(left + SettingsMaster.bigDonX * scale, top - SettingsMaster.bigDonY * scale, 40 * scale);
            sr.circle(left + SettingsMaster.donX * scale, top - SettingsMaster.donY * scale, 25 * scale);
            sr.setColor(Hit.kat);
            sr.circle(left + SettingsMaster.katX * scale, top - SettingsMaster.katY * scale, 25 * scale);

            sr.end();
            sb.begin();
        }

        @Override
        public void render(SpriteBatch sb, ShapeRenderer sr, float dx, float dy) {
            this.dx = dx;
            this.dy = dy;

            render(sb, sr);
        }

        public boolean contains(int x, int y) {
            return !(x < cX - width / 2.0f) && !(y < cY - height / 2.0f) && !(x > cX + width / 2.0f) && !(y > cY + height / 2.0f);
        }
    }

    private final List<Button> positioningButtons = new ArrayList<>();
    private final List<Integer> activePositioning = new ArrayList<>();
    private final Label[] xPositions = new Label[4];
    private final Label[] yPositions = new Label[4];
    private TextField xField, yField;

    private void togglePositioning(int index) {
        if (!positioningButtons.get(index).renderBorder) {
            positioningButtons.get(index).renderBorder = true;

            int x = 0, y = 0;
            switch (index) {
                case 0:
                    x = SettingsMaster.donX;
                    y = SettingsMaster.donY;
                    break;
                case 1:
                    x = SettingsMaster.katX;
                    y = SettingsMaster.katY;
                    break;
                case 2:
                    x = SettingsMaster.bigDonX;
                    y = SettingsMaster.bigDonY;
                    break;
                case 3:
                    x = SettingsMaster.bigKatX;
                    y = SettingsMaster.bigKatY;
                    break;
            }

            String tx = Integer.toString(x), ty = Integer.toString(y);

            if (activePositioning.isEmpty()) {
                xField.unlock();
                yField.unlock();

                xField.setText(tx);
                yField.setText(ty);
            }
            else {
                if (!tx.equals(xField.text) || !ty.equals(yField.text)) {
                    xField.setText("");
                    yField.setText("");
                }
            }

            activePositioning.add(index);
        }
        else {
            positioningButtons.get(index).renderBorder = false;
            activePositioning.removeIf((i)->index==i);

            if (activePositioning.isEmpty()) {
                xField.setText("");
                yField.setText("");
                xField.lock();
                yField.lock();
            }
        }
    }
    private void setXPosition(String t, TextField f) {
        try {
            int x = Math.max(0, Math.min(512, Integer.parseInt(t)));

            String xString = "x:" + x;
            for (int i : activePositioning) {
                xPositions[i].setText(xString);
                switch (i) {
                    case 0:
                        SettingsMaster.donX = x;
                        break;
                    case 1:
                        SettingsMaster.katX = x;
                        break;
                    case 2:
                        SettingsMaster.bigDonX = x;
                        break;
                    case 3:
                        SettingsMaster.bigKatX = x;
                        break;
                }
            }

            SettingsMaster.saveGeneralSettings();
        }
        catch (NumberFormatException e) {
            f.setText("");
        }
    }
    private void setYPosition(String t, TextField f) {
        try {
            int y = Math.max(0, Math.min(384, Integer.parseInt(t)));

            String yString = "y:" + y;
            for (int i : activePositioning) {
                yPositions[i].setText(yString);

                switch (i) {
                    case 0:
                        SettingsMaster.donY = y;
                        break;
                    case 1:
                        SettingsMaster.katY = y;
                        break;
                    case 2:
                        SettingsMaster.bigDonY = y;
                        break;
                    case 3:
                        SettingsMaster.bigKatY = y;
                        break;
                }
            }

            SettingsMaster.saveGeneralSettings();
        }
        catch (NumberFormatException e) {
            f.setText("");
        }
    }
}