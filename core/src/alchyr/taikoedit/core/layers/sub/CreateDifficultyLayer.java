package alchyr.taikoedit.core.layers.sub;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.core.input.TextInputProcessor;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.*;
import alchyr.taikoedit.editor.maps.FullMapInfo;
import alchyr.taikoedit.editor.maps.MapInfo;
import alchyr.taikoedit.editor.maps.Mapset;
import alchyr.taikoedit.editor.views.ObjectView;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.List;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.util.GeneralUtils.oneDecimal;

public class CreateDifficultyLayer extends ProgramLayer implements InputLayer {
    private final CreateDifficultyProcessor processor;
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
    private final ToggleButton keepObjects, keepVolume, keepSv;
    //private final TextField artist, romanizedArtist, title, romanizedTitle, source, tags;

    private final Button cancelButton;
    private final Button saveButton;

    //Other data
    private final EditorLayer sourceLayer;

    private final Mapset set;
    private final EditorBeatmap base;

    //Editing existing difficulty
    public CreateDifficultyLayer(EditorLayer editor, Mapset set, EditorBeatmap base)
    {
        this.type = LAYER_TYPE.UPDATE_STOP;

        sourceLayer = editor;

        this.set = set;
        this.base = base;

        processor = new CreateDifficultyProcessor(this);

        pix = assetMaster.get("ui:pixel");
        BitmapFont font = assetMaster.getFont("aller medium");
        BitmapFont big = assetMaster.getFont("default");

        labels = new ArrayList<>();
        textFields = new ArrayList<>();

        saveButton = new Button(SettingsMaster.getMiddle() + 100, 100, "Save", assetMaster.getFont("aller medium")).setClick(this::save);
        cancelButton = new Button(SettingsMaster.getMiddle() - 100, 100, "Cancel", assetMaster.getFont("aller medium")).setClick(this::cancel);

        textOverlay = new TextOverlay(font, SettingsMaster.getHeight() / 2, 100);

        //left half for new difficulty copy settings, right half for normal difficulty settings
        float leftX = 70, rightX = SettingsMaster.getMiddle() + leftX, leftCenter = SettingsMaster.getWidth() / 4, rightCenter = SettingsMaster.getMiddle() + leftCenter;

        //Metadata stuff
        float y = SettingsMaster.getHeight() - 60;
        labels.add(new Label(leftCenter, y, big, "Creating From", Label.LabelAlign.CENTER));

        y -= 60;
        labels.add(new Label(leftCenter, y, font, "[" + base.getName() + "]", Label.LabelAlign.CENTER));

        y -= 40;
        keepObjects = new ToggleButton(leftX, y, "Keep Objects", font, true);

        y -= 40;
        keepVolume = new ToggleButton(leftX, y, "Keep Volume and Kiai", font, true);

        y -= 40;
        keepSv = new ToggleButton(leftX, y, "Keep SV Changes", font, false);

        //Diff stuff
        y = SettingsMaster.getHeight() - 60;
        labels.add(new Label(rightCenter, y, big, "Difficulty", Label.LabelAlign.CENTER));

        y -= 60;
        diffName = new TextField(rightX, y, SettingsMaster.getMiddle() - leftX, "Name:", "", METADATA_LIMIT, font);
        textFields.add(diffName.setOnEnter((s)->{diffName.disable(); return true;}));

        y -= 40;
        hp = new TextField(rightX, y, SettingsMaster.getMiddle() - leftX, "HP:", oneDecimal.format(base.getFullMapInfo().hp), 4, font).setType(TextField.TextType.NUMERIC);
        hp.setOnEndInput(this::testDiffValue);
        textFields.add(hp.setOnEnter((s)->{hp.disable(); return true;}));
        y -= 30;
        hpSlider = new Slider(rightCenter, y, SettingsMaster.getMiddle() - 100, 0, 10, 0.5f, 0.1f);
        hpSlider.setValue(base.getFullMapInfo().hp);
        hpSlider.onValueChange(this::setHP);

        y -= 50;
        od = new TextField(rightX, y, SettingsMaster.getMiddle() - leftX, "OD:", oneDecimal.format(base.getFullMapInfo().od), 4, font).setType(TextField.TextType.NUMERIC);
        od.setOnEndInput(this::testDiffValue);
        textFields.add(od.setOnEnter((s)->{od.disable(); return true;}));
        y -= 30;
        odSlider = new Slider(rightCenter, y, SettingsMaster.getMiddle() - 100, 0, 10, 0.5f, 0.1f);
        odSlider.setValue(base.getFullMapInfo().od);
        odSlider.onValueChange(this::setOD);

        processor.bind();
    }

    private void save(int button)
    {
        try {
            String newDiffname = diffName.text;
            if (newDiffname.isEmpty()) {
                textOverlay.setText("Please enter a difficulty name.", 2.0f);
                return;
            }

            for (MapInfo info : set.getMaps()) {
                if (info.getDifficultyName().equals(newDiffname)) {
                    final MapInfo overwrite = info;
                    TaikoEditor.addLayer(new ConfirmationLayer("A difficulty with this name already exists. Create anyways?", "Yes", "No", false)
                            .onConfirm(()->{
                                if (create()) {
                                    set.getMaps().remove(overwrite);
                                    sourceLayer.closeViewSet(overwrite);
                                    TaikoEditor.removeLayer(this);
                                }
                            }));
                    return;
                }
            }

            if (create()) {
                TaikoEditor.removeLayer(this);
            }
        }
        catch (Exception e) {
            sourceLayer.textOverlay.setText("Failed to save!", 2.0f);
        }
    }
    private boolean create() {
        FullMapInfo newBase = new FullMapInfo(base.getFullMapInfo(), diffName.text);

        float newHp = Math.min(10, Math.max(0, Float.parseFloat(hp.text)));
        float newOd = Math.min(10, Math.max(0, Float.parseFloat(od.text)));

        newBase.hp = newHp;
        newBase.od = newOd;

        EditorBeatmap newMap = new EditorBeatmap(base, newBase, keepObjects.enabled, keepSv.enabled, keepVolume.enabled);

        if (newMap.save()) {
            this.set.add(newBase);

            sourceLayer.addMap(newMap);
            sourceLayer.textOverlay.setText("Difficulty \"" + newMap.getName() + "\" created!", 1.0f);
            sourceLayer.addView(new ObjectView(sourceLayer, newMap), true);
            return true;
        }
        else {
            textOverlay.setText("Failed to save new difficulty!", 2.0f);
            return false;
        }
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

        keepObjects.update(elapsed);
        keepVolume.update(elapsed);
        keepSv.update(elapsed);

        textOverlay.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight() + 5);

        sb.setColor(dividerColor);
        sb.draw(pix, SettingsMaster.getMiddle(), 0, 1, SettingsMaster.getHeight() + 5);

        keepObjects.render(sb, sr);
        keepVolume.render(sb, sr);
        keepSv.render(sb, sr);

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

    @Override
    public void dispose() {
        super.dispose();
        processor.dispose();
    }

    private static class CreateDifficultyProcessor extends TextInputProcessor {
        private final CreateDifficultyLayer sourceLayer;

        public CreateDifficultyProcessor(CreateDifficultyLayer source)
        {
            super(BindingMaster.getBindingGroupCopy("Basic"), true);
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

                        if (sourceLayer.keepObjects.click(p.x, p.y, b))
                            return null;
                        if (sourceLayer.keepVolume.click(p.x, p.y, b))
                            return null;
                        if (sourceLayer.keepSv.click(p.x, p.y, b))
                            return null;

                        MouseHoldObject mouseHold = sourceLayer.odSlider.click(p.x, p.y);
                        if (mouseHold != null)
                            return mouseHold;

                        return sourceLayer.hpSlider.click(p.x, p.y);
                    });
        }
    }
}
