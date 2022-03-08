package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.UIElement;
import alchyr.taikoedit.core.input.AdjustedInputProcessor;
import alchyr.taikoedit.core.input.BoundInputMultiplexer;
import alchyr.taikoedit.core.input.BoundInputProcessor;
import alchyr.taikoedit.core.ui.*;
import alchyr.taikoedit.editor.maps.components.hitobjects.Hit;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.osuSafe;

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

    //editor settings
    //divided into 5 columns, one of which is wider than the others. 1/6 1/6 1/6 2/6 1/6
    private static final int SMALL_SECTION_WIDTH, LARGE_SECTION_WIDTH, X_1, X_2, X_3, X_4, X_5, LABEL_Y_START;
    private static final int LABEL_Y_SPACING = 50;

    static {
        float displayAreaWidth = SettingsMaster.getWidth() - BUTTON_WIDTH - 20;
        SMALL_SECTION_WIDTH = Math.min((int) (displayAreaWidth / 6), 150);
        LARGE_SECTION_WIDTH = SMALL_SECTION_WIDTH * 2;
        X_1 = BUTTON_WIDTH + 10;
        X_2 = X_1 + SMALL_SECTION_WIDTH;
        X_3 = X_2 + SMALL_SECTION_WIDTH;
        X_4 = X_3 + SMALL_SECTION_WIDTH;
        X_5 = X_4 + LARGE_SECTION_WIDTH;

        LABEL_Y_START = (int) (SettingsMaster.getHeight() * 0.8f);
    }


    private SettingsProcessor mainProcessor;
    private BoundInputMultiplexer processor;

    private Texture pixel;

    private Page[] pages;
    private Button[] pageButtons;

    private ImageButton exitButton;

    private static int currentPage = 0;


    public SettingsLayer()
    {
        mainProcessor = new SettingsProcessor(this);
        this.type = LAYER_TYPE.FULL_STOP;

        initialize();
        mainProcessor.bind();
    }

    private static final DecimalFormat oneDecimal = new DecimalFormat("#0.#", osuSafe);
    @Override
    public void initialize() {
        BitmapFont font = assetMaster.getFont("aller medium");
        processor = new BoundInputMultiplexer(mainProcessor);

        exitButton = new ImageButton(SettingsMaster.getWidth() - 40, SettingsMaster.getHeight() - 40, assetMaster.get("ui:exit"), (Texture) assetMaster.get("ui:exith")).setClick(this::close);

        pages = new Page[2];
        pageButtons = new Button[2];

        pageButtons[0] = new Button(BUTTON_X, BUTTON_Y + BUTTON_HEIGHT / 2.0f, BUTTON_WIDTH, BUTTON_HEIGHT, "Editor", font).setClick((i)->this.swapPages(0)).useBorderRendering();
        pageButtons[1] = new Button(BUTTON_X, BUTTON_Y - BUTTON_HEIGHT / 2.0f, BUTTON_WIDTH, BUTTON_HEIGHT, "Hotkeys", font).setClick((i)->this.swapPages(1)).useBorderRendering();

        Page editorSettings = new Page(null);

        //editor settings page
        {
            int y = LABEL_Y_START;
            editorSettings.addLabel(X_1 + 10, y, font, "Don");
            editorSettings.addLabel(xPositions[0] = new Label(X_2 + 10, y, font, "x:" + SettingsMaster.donX));
            editorSettings.addLabel(yPositions[0] = new Label(X_3 + 10, y, font, "y:" + SettingsMaster.donY));
            Button don = new Button(X_1, y - LABEL_Y_SPACING / 2.0f, SMALL_SECTION_WIDTH * 3, LABEL_Y_SPACING, null, font).setClick((b)->togglePositioning(0)).useBorderRendering();

            y -= LABEL_Y_SPACING;
            editorSettings.addLabel(X_1 + 10, y, font, "Kat");
            editorSettings.addLabel(xPositions[1] = new Label(X_2 + 10, y, font, "x:" + SettingsMaster.katX));
            editorSettings.addLabel(yPositions[1] = new Label(X_3 + 10, y, font, "y:" + SettingsMaster.katY));
            Button kat = new Button(X_1, y - LABEL_Y_SPACING / 2.0f, SMALL_SECTION_WIDTH * 3, LABEL_Y_SPACING, null, font).setClick((b)->togglePositioning(1)).useBorderRendering();

            xField = new TextField(X_5, y, SMALL_SECTION_WIDTH, "x:", "", 3, font).setType(TextField.TextType.INTEGER);
            xField.setOnEndInput(this::setXPosition);
            xField.lock();
            editorSettings.addTextField(xField);

            positioningArea = new ObjectPositioningArea(X_4 + LARGE_SECTION_WIDTH / 2.0f, y - LABEL_Y_SPACING / 2.0f, LARGE_SECTION_WIDTH * 0.8f, 180);
            editorSettings.addUIElement(positioningArea);

            y -= LABEL_Y_SPACING;
            editorSettings.addLabel(X_1 + 10, y, font, "Big Don");
            editorSettings.addLabel(xPositions[2] = new Label(X_2 + 10, y, font, "x:" + SettingsMaster.bigDonX));
            editorSettings.addLabel(yPositions[2] = new Label(X_3 + 10, y, font, "y:" + SettingsMaster.bigDonY));
            Button bigDon = new Button(X_1, y - LABEL_Y_SPACING / 2.0f, SMALL_SECTION_WIDTH * 3, LABEL_Y_SPACING, null, font).setClick((b)->togglePositioning(2)).useBorderRendering();

            yField = new TextField(X_5, y, SMALL_SECTION_WIDTH, "y:", "", 3, font).setType(TextField.TextType.INTEGER);
            yField.setOnEndInput(this::setYPosition);
            yField.lock();
            editorSettings.addTextField(yField);

            y -= LABEL_Y_SPACING;
            editorSettings.addLabel(X_1 + 10, y, font, "Big Kat");
            editorSettings.addLabel(xPositions[3] = new Label(X_2 + 10, y, font, "x:" + SettingsMaster.bigKatX));
            editorSettings.addLabel(yPositions[3] = new Label(X_3 + 10, y, font, "y:" + SettingsMaster.bigKatY));
            Button bigKat = new Button(X_1, y - LABEL_Y_SPACING / 2.0f, SMALL_SECTION_WIDTH * 3, LABEL_Y_SPACING, null, font).setClick((b)->togglePositioning(3)).useBorderRendering();

            y -= LABEL_Y_SPACING * 2;

            musicVolume = new TextField(X_2 + 10, y, LARGE_SECTION_WIDTH, "Music Volume:", oneDecimal.format(SettingsMaster.getMusicVolume() * 100), 5, font)
                    .setType(TextField.TextType.NUMERIC).setOnEndInput(this::updateMusicVolume);
            effectVolume = new TextField(X_4 + 10, y, LARGE_SECTION_WIDTH, "Effect Volume:", oneDecimal.format(SettingsMaster.effectVolume * 100), 5, font)
                    .setType(TextField.TextType.NUMERIC).setOnEndInput(this::updateEffectVolume);

            editorSettings.addTextField(musicVolume);
            editorSettings.addTextField(effectVolume);

            positioningButtons.add(don);
            editorSettings.addButton(don);
            positioningButtons.add(kat);
            editorSettings.addButton(kat);
            positioningButtons.add(bigDon);
            editorSettings.addButton(bigDon);
            positioningButtons.add(bigKat);
            editorSettings.addButton(bigKat);
        }

        pages[0] = editorSettings;
        pages[1] = new Page(null);

        pages[0].bind();
        pages[1].bind();

        swapPages(0);

        pixel = assetMaster.get("ui:pixel");
    }

    private void close() {
        TaikoEditor.removeLayer(this);
    }
    private void swapPages(int page) {
        for (int i = 0; i < pageButtons.length; ++i) {
            pageButtons[i].renderBorder = i == page;
        }
        if (page >= 0 && page < pages.length) {
            currentPage = page;
            processor.setProcessors(pages[currentPage], mainProcessor);
        }
    }

    //page 1 stuff
    private ObjectPositioningArea positioningArea;
    private final List<Button> positioningButtons = new ArrayList<>();
    private final List<Integer> activePositioning = new ArrayList<>();
    private final Label[] xPositions = new Label[4];
    private final Label[] yPositions = new Label[4];
    TextField xField, yField, musicVolume, effectVolume;
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

            SettingsMaster.save();
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

            SettingsMaster.save();
        }
        catch (NumberFormatException e) {
            f.setText("");
        }
    }
    private void updateMusicVolume(String t, TextField f) {
        try {
            float vol = Float.parseFloat(t);

            vol = Math.min(1.0f, Math.max(0.0f, vol / 100.0f));

            SettingsMaster.setMusicVolume(vol);

            SettingsMaster.save();
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

            SettingsMaster.save();
        }
        catch (Exception ignored) {
        }
        f.setText(oneDecimal.format(SettingsMaster.effectVolume * 100));
    }

    @Override
    public void update(float elapsed) {
        processor.update(elapsed);
        //pages[currentPage].update(elapsed);

        for (Button b : pageButtons)
            b.update(elapsed);

        exitButton.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(Color.BLACK);
        sb.draw(pixel, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight());

        pages[currentPage].render(sb, sr);

        for (Button b : pageButtons)
            b.render(sb, sr);

        exitButton.render(sb, sr);
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    private static class SettingsProcessor extends BoundInputProcessor {
        private final SettingsLayer sourceLayer;

        public SettingsProcessor(SettingsLayer source)
        {
            super(BindingMaster.getBindingGroup("Basic"), true);
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

            bindings.addMouseBind((x, y, b)->currentPage == 0 && !sourceLayer.activePositioning.isEmpty(),
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



    private static class ObjectPositioningArea implements UIElement {
        private float cX, cY, width, height, scale, dx, dy;

        public ObjectPositioningArea(float cX, float cY, float maxWidth, float maxHeight) {
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

            SettingsMaster.save();

            return true;
        }

        @Override
        public void update(float elapsed) {

        }

        @Override
        public void render(SpriteBatch sb, ShapeRenderer sr) {
            sb.end();
            //grid first
            sr.begin(ShapeRenderer.ShapeType.Line);

            float left = cX - width / 2 + dx, right = cX + width / 2 + dx, top = cY + height / 2 + dy, bottom = cY - height / 2 + dy, temp = top;
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
    }
}