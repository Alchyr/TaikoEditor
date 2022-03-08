package alchyr.taikoedit.core.layers.sub;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.core.input.TextInputProcessor;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.*;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.interfaces.functional.VoidMethod;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.util.GeneralUtils.oneDecimal;

public class DifficultySettingsLayer extends ProgramLayer implements InputLayer {
    private static DifficultySettingsProcessor processor;
    private static final int METADATA_LIMIT = 80;

    //Rendering
    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.9f);
    private static final Color dividerColor = new Color(0.8f, 0.8f, 0.8f, 0.8f);

    //Textures
    private final Texture pix;

    //Positions
    private final int middleY = SettingsMaster.getHeight() / 2;
    //private final int minRenderY = -(middleY + 30);

    //For smoothness

    //Parts
    private final List<Label> labels;
    private final List<TextField> textFields;

    private final TextOverlay textOverlay;

    //right
    private final TextField diffName;
    private final TextField hp;
    private final Slider hpSlider;
    private final TextField od;
    private final Slider odSlider;

    //left
    private boolean changedMetadata = false;
    //private final TextField artist, romanizedArtist, title, romanizedTitle, source, tags;

    private final Button cancelButton;
    private final Button saveButton;

    //Other data
    private final EditorLayer sourceLayer;

    private final EditorBeatmap map;

    //Editing existing difficulty
    public DifficultySettingsLayer(EditorLayer editor, EditorBeatmap map)
    {
        this.type = LAYER_TYPE.UPDATE_STOP;

        sourceLayer = editor;

        this.map = map;

        processor = new DifficultySettingsProcessor(this);

        pix = assetMaster.get("ui:pixel");
        BitmapFont font = assetMaster.getFont("aller medium");
        BitmapFont big = assetMaster.getFont("default");

        labels = new ArrayList<>();
        textFields = new ArrayList<>();

        saveButton = new Button(SettingsMaster.getMiddle() + 100, 100, "Save", assetMaster.getFont("aller medium")).setClick(this::save);
        cancelButton = new Button(SettingsMaster.getMiddle() - 100, 100, "Cancel", assetMaster.getFont("aller medium")).setClick(this::cancel);

        textOverlay = new TextOverlay(font, SettingsMaster.getHeight() / 2, 100);

        //left half for mapset settings, right half for difficulty settings
        float leftX = 70, rightX = SettingsMaster.getMiddle() + leftX, leftCenter = SettingsMaster.getWidth() / 4, rightCenter = SettingsMaster.getMiddle() + leftCenter;


        //Metadata stuff
        float y = SettingsMaster.getHeight() - 60;
        labels.add(new Label(leftCenter, y, big, "Mapset", Label.LabelAlign.CENTER));

        y -= 60;
        labels.add(new Label(leftX, y, font, "(Not yet available.)", Label.LabelAlign.LEFT_CENTER));
        /*artist = new TextField(leftX, y, SettingsMaster.getWidth() - leftX, "Artist:", map.getFullMapInfo().artistUnicode, METADATA_LIMIT, font).setOnEndInput((s, f)->changedMetadata = true);
        textFields.add(artist.setOnEnter((s)->{artist.disable(); return true; }));

        y -= 40;
        romanizedArtist = new TextField(leftX, y, SettingsMaster.getWidth() - leftX, "Romanized Artist:", map.getFullMapInfo().artist, METADATA_LIMIT, font);
        textFields.add(romanizedArtist.setOnEnter((s)->{romanizedArtist.disable(); return true; }));

        y -= 40;
        title = new TextField(leftX, y, SettingsMaster.getWidth() - leftX, "Title:", map.getFullMapInfo().titleUnicode, METADATA_LIMIT, font);
        textFields.add(title.setOnEnter((s)->{title.disable(); return true; }));

        y -= 40;
        romanizedTitle = new TextField(leftX, y, SettingsMaster.getWidth() - leftX, "Romanized Title:", map.getFullMapInfo().title, METADATA_LIMIT, font);
        textFields.add(romanizedTitle.setOnEnter((s)->{romanizedTitle.disable(); return true; }));

        y -= 40;
        source = new TextField(leftX, y, SettingsMaster.getWidth() - leftX, "Source:", map.getFullMapInfo().source, METADATA_LIMIT, font);
        textFields.add(source.setOnEnter((s)->{source.disable(); return true; }));

        y -= 40;
        tags = new TextField(leftX, y, SettingsMaster.getWidth() - leftX, "Tags:", map.getFullMapInfo().tagText(), METADATA_LIMIT, font);
        textFields.add(tags.setOnEnter((s)->{tags.disable(); return true; }));*/

        //Diff stuff
        y = SettingsMaster.getHeight() - 60;
        labels.add(new Label(rightCenter, y, big, "Difficulty", Label.LabelAlign.CENTER));

        y -= 60;
        diffName = new TextField(rightX, y, SettingsMaster.getMiddle() - leftX, "Name:", map.getName(), METADATA_LIMIT, font);
        textFields.add(diffName.setOnEnter((s)->{diffName.disable(); return true;}));

        y -= 40;
        hp = new TextField(rightX, y, SettingsMaster.getMiddle() - leftX, "HP:", oneDecimal.format(map.getFullMapInfo().hp), 4, font).setType(TextField.TextType.NUMERIC);
        hp.setOnEndInput(this::testDiffValue);
        textFields.add(hp.setOnEnter((s)->{hp.disable(); return true;}));
        y -= 30;
        hpSlider = new Slider(rightCenter, y, SettingsMaster.getMiddle() - 100, 0, 10, 0.5f, 0.1f);
        hpSlider.onValueChange(this::setHP);

        y -= 50;
        od = new TextField(rightX, y, SettingsMaster.getMiddle() - leftX, "OD:", oneDecimal.format(map.getFullMapInfo().od), 4, font).setType(TextField.TextType.NUMERIC);
        od.setOnEndInput(this::testDiffValue);
        textFields.add(od.setOnEnter((s)->{od.disable(); return true;}));
        y -= 30;
        odSlider = new Slider(rightCenter, y, SettingsMaster.getMiddle() - 100, 0, 10, 0.5f, 0.1f);
        odSlider.onValueChange(this::setOD);

        processor.bind();
    }

    private void save()
    {
        try {
            if (diffName.text.isEmpty()) {
                textOverlay.setText("Please enter a difficulty name.", 2.0f);
                return;
            }

            float newHp = Math.min(10, Math.max(0, Float.parseFloat(hp.text)));
            float newOd = Math.min(10, Math.max(0, Float.parseFloat(od.text)));

            map.getFullMapInfo().hp = newHp;
            map.getFullMapInfo().od = newOd;

            map.getFullMapInfo().setDifficultyName(diffName.text);

            ViewSet set = sourceLayer.getViewSet(map);
            set.updateDiffNamePosition();
            if (map.save()) {
                sourceLayer.textOverlay.setText("Difficulty \"" + map.getName() + "\" saved!", 0.5f);
            }
            else {
                sourceLayer.textOverlay.setText("Failed to save!", 2.0f);
            }
        }
        catch (Exception e) {
            sourceLayer.textOverlay.setText("Failed to save!", 2.0f);
        }
        TaikoEditor.removeLayer(this);
    }
    private void testDiffValue(String s, TextField f) {
        try {
            float newVal = Float.parseFloat(s);

            newVal = Math.min(10, Math.max(0, newVal));
            f.setText(oneDecimal.format(newVal));
        }
        catch (Exception e) {
            f.setText("5.0");
        }
    }
    private void setHP(float value) {
        hp.setText(oneDecimal.format(Math.min(10, Math.max(0, value))));
    }
    private void setOD(float value) {
        od.setText(oneDecimal.format(Math.min(10, Math.max(0, value))));
    }

    @Override
    public void update(float elapsed) {
        processor.update(elapsed);

        for (TextField f : textFields)
            f.update(elapsed);

        saveButton.update(elapsed);
        cancelButton.update(elapsed);

        textOverlay.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight());

        sb.setColor(dividerColor);
        sb.draw(pix, SettingsMaster.getMiddle(), 0, 1, SettingsMaster.getHeight());

        for (Label l : labels)
            l.render(sb, sr);

        for (TextField f : textFields)
            f.render(sb, sr);

        hpSlider.render(sb, sr);
        odSlider.render(sb, sr);

        saveButton.render(sb, sr);
        cancelButton.render(sb, sr);

        textOverlay.render(sb, sr);

        sb.setColor(Color.WHITE);
    }

    private void cancel()
    {
        TaikoEditor.removeLayer(this);
    }


    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    private static class DifficultySettingsProcessor extends TextInputProcessor {
        private final DifficultySettingsLayer sourceLayer;

        public DifficultySettingsProcessor(DifficultySettingsLayer source)
        {
            super(BindingMaster.getBindingGroup("Basic"), true);
            this.sourceLayer = source;
        }

        @Override
        public void bind() {
            bindings.bind("Exit", sourceLayer::cancel);

            bindings.addMouseBind((x, y, b)->(b == 0) || (b == 1),
                    (p, b) -> {
                        boolean clicked = false;

                        for (TextField f : sourceLayer.textFields) {
                            if (f.click(p.x, p.y, this))
                                clicked = true;
                        }
                        if (clicked)
                            return null;

                        if (sourceLayer.saveButton.click(p.x, p.y, b))
                            return null;
                        if (sourceLayer.cancelButton.click(p.x, p.y, b))
                            return null;

                        MouseHoldObject mouseHold = sourceLayer.odSlider.click(p.x, p.y);
                        if (mouseHold != null)
                            return mouseHold;

                        return sourceLayer.hpSlider.click(p.x, p.y);
                    });
        }
    }
}