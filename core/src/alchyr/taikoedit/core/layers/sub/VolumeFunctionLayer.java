package alchyr.taikoedit.core.layers.sub;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.input.TextInputProcessor;
import alchyr.taikoedit.core.ui.*;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.structures.Pair;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static alchyr.taikoedit.TaikoEditor.*;

public class VolumeFunctionLayer extends ProgramLayer implements InputLayer {
    private final VolumeFunctionProcessor processor;

    public boolean active = true;
    public VolumeFunctionProperties result = null; //null = do nothing

    public static class VolumeFunctionProperties {
        public final int iVol, fVol;
        public final boolean generateLines, selectedOnly, adjustExisting, basedOnFollowingObject;
        public final int genOffset;
        public final Function<Double, Double> function;

        public VolumeFunctionProperties(int iVol, int fVol, boolean generateLines, int genOffset, boolean selectedOnly, boolean adjustExisting, boolean basedOnFollowingObject, Function<Double, Double> func) {
            this.iVol = iVol;
            this.fVol = fVol;
            this.generateLines = generateLines;
            this.genOffset = genOffset;
            this.selectedOnly = selectedOnly;

            this.adjustExisting = adjustExisting;
            this.basedOnFollowingObject = basedOnFollowingObject;

            this.function = func;
        }
    }

    //Rendering
    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.83f);

    //Textures
    private final Texture pix;

    //Positions
    private static final int LEFT_POS = 50;
    private static final int SHIFT_STEP = 22;
    private static final int RIGHT_POS = SettingsMaster.getWidth() - 100;
    private static final int BUTTON_OFFSET = 60;
    private final int middleY = SettingsMaster.getHeight() / 2;

    private static final int FORMULA_SIZE = (int) (150 * SettingsMaster.SCALE);
    private static final int FORMULA_START_Y = SettingsMaster.getHeight() - FORMULA_SIZE * 2;

    //Parts
    private final TextOverlay textOverlay;

    private final TextField initialVolume;
    private final TextField finalVolume;

    private final ToggleButton generateLines;
    private final TextField genOffset;
    private final ToggleButton selectedOnly;

    private final ToggleButton adjustExisting;
    private final ToggleButton basedOnObjects;

    private final List<ImageButton> formulaButtons = new ArrayList<>();

    private final Button confirmButton;
    private final Button cancelButton;

    //Other data
    private int iVol;
    private int fVol;
    private boolean loadFormulas;
    private boolean updateVol = false;

    private ImageButton selectedFormula;

    public VolumeFunctionLayer(int iVol, int fVol)
    {
        this.type = LAYER_TYPE.NORMAL;

        pix = assetMaster.get("ui:pixel");

        processor = new VolumeFunctionProcessor(this);

        this.iVol = iVol;
        this.fVol = fVol;

        textOverlay = new TextOverlay(assetMaster.getFont("aller medium"), SettingsMaster.getHeight() / 2, 100);

        initialVolume = new TextField(LEFT_POS, middleY + BUTTON_OFFSET * 5f, 250f, "Initial Volume:", Integer.toString(iVol), 6, assetMaster.getFont("aller medium")).setType(TextField.TextType.INTEGER).blocking();
        finalVolume = new TextField(LEFT_POS, middleY + BUTTON_OFFSET * 4f, 250f, "Final Volume:", Integer.toString(fVol), 6, assetMaster.getFont("aller medium")).setType(TextField.TextType.INTEGER).blocking();

        generateLines = new ToggleButton(LEFT_POS, middleY + BUTTON_OFFSET * 2.5f, "Generate Lines", assetMaster.getFont("aller medium"), true);
        selectedOnly = new ToggleButton(LEFT_POS + SHIFT_STEP, middleY + BUTTON_OFFSET * 1.5f, "Selected Objects Only", assetMaster.getFont("aller medium"), false);
        genOffset = new TextField(LEFT_POS + (SHIFT_STEP * 2), middleY + BUTTON_OFFSET * 0.5f, 250f - (SHIFT_STEP * 2), "Position Offset:", "-5", 6, assetMaster.getFont("aller medium")).setType(TextField.TextType.INTEGER).blocking();

        adjustExisting = new ToggleButton(LEFT_POS, middleY - BUTTON_OFFSET, "Adjust Existing Lines", assetMaster.getFont("aller medium"), true);
        basedOnObjects = new ToggleButton(LEFT_POS + SHIFT_STEP, middleY - BUTTON_OFFSET * 2f, "Based on Following Object", assetMaster.getFont("aller medium"), false);

        loadFormulas = true;

        confirmButton = new Button(RIGHT_POS, middleY + BUTTON_OFFSET * 0.5f, "Generate", assetMaster.getFont("aller medium")).setClick(this::confirm);
        cancelButton = new Button(RIGHT_POS, middleY - BUTTON_OFFSET * 0.5f, "Cancel", assetMaster.getFont("aller medium")).setClick(this::cancel);

        processor.bind();
    }

    private void loadFormulas() {
        updateFormulas();

        boolean flipY = fVol < iVol;
        int index = 0, col, row;
        for (Pair<Function<Double, Double>, String> formula : SvFunctionLayer.formulas) {
            if ("True Exp".equals(formula.b))
                continue;

            col = (index % 3) - 1;
            row = index / 3;
            int finalIndex = index;
            formulaButtons.add(new ImageButton(SettingsMaster.getMiddle() + col * FORMULA_SIZE, FORMULA_START_Y - row * FORMULA_SIZE,
                    SvFunctionLayer.formulaTextures.get(formula.a), SvFunctionLayer.formulaHoverTextures.get(formula.a),
                    formula.b, assetMaster.getFont("aller medium"))
                    .setClick((b)->selectFormula(finalIndex)).setAction(formula.b).setFlip(false, flipY));

            ++index;
        }

        selectedFormula = formulaButtons.get(0);
    }
    private void updateFormulas() {
        boolean flipY = fVol < iVol;

        //If not already generated list will just be empty
        for (ImageButton button : formulaButtons) {
            button.setFlip(false, flipY);
        }
    }
    private void selectFormula(int index) {
        if (index < formulaButtons.size())
            selectedFormula = formulaButtons.get(index);
    }

    private void cycleInput() {
        if (initialVolume.isActive()) {
            initialVolume.disable();
            genOffset.activate(processor);
        }
        else if (genOffset.isActive()) {
            genOffset.disable();
            finalVolume.activate(processor);
        }
        else if (finalVolume.isActive()) {
            finalVolume.disable();
        }
        else {
            initialVolume.activate(processor);
        }
    }

    @Override
    public void update(float elapsed) {
        processor.update(elapsed);

        textOverlay.update(elapsed);
        initialVolume.update(elapsed);
        finalVolume.update(elapsed);

        generateLines.update(elapsed);
        genOffset.update(elapsed);
        selectedOnly.update(elapsed);

        adjustExisting.update(elapsed);
        basedOnObjects.update(elapsed);


        for (ImageButton b : formulaButtons) {
            b.update(elapsed);
        }
        confirmButton.update(elapsed);
        cancelButton.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        if (loadFormulas) {
            loadFormulas = false;
            loadFormulas();
        }
        if (updateVol) {
            updateVol = false;
            int lastIVol = iVol, lastFVol = fVol;
            try {
                iVol = Integer.parseInt(initialVolume.text);
                fVol = Integer.parseInt(finalVolume.text);

                iVol = MathUtils.clamp(iVol, 1, 100);
                fVol = MathUtils.clamp(fVol, 1, 100);

                if (iVol != lastIVol || fVol != lastFVol) {
                    updateFormulas();
                }
            }
            catch (Exception ignored) {
                iVol = lastIVol;
                fVol = lastFVol;
            }
        }

        sb.setColor(backColor);
        sb.draw(pix, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight() + 5);

        initialVolume.render(sb, sr);
        finalVolume.render(sb, sr);

        generateLines.render(sb, sr);
        genOffset.render(sb, sr);
        selectedOnly.render(sb, sr);

        adjustExisting.render(sb, sr);
        basedOnObjects.render(sb, sr);

        sb.setColor(Color.FOREST);
        sb.draw(pix, selectedFormula.getX(), selectedFormula.getY(), selectedFormula.getWidth(), selectedFormula.getHeight());

        for (ImageButton b : formulaButtons)
            b.render(sb, sr);

        cancelButton.render(sb, sr);
        confirmButton.render(sb, sr);

        textOverlay.render(sb, sr);
    }

    private void cancel()
    {
        result = null;
        close();
    }
    private void close() {
        active = false;

        TaikoEditor.removeLayer(this);
    }
    private void confirm()
    {
        initialVolume.disable();
        finalVolume.disable();
        genOffset.disable();

        boolean success = true;
        int offset = 0;

        try {
            int testIVol = Integer.parseInt(initialVolume.text);
            int testFVol = Integer.parseInt(finalVolume.text);

            if (testIVol <= 0 || testIVol > 100) {
                success = false;
                textOverlay.setText("Invalid initial volume.", 3.0f);
            }
            else if (testFVol <= 0 || testFVol > 100) {
                success = false;
                textOverlay.setText("Invalid final volume.", 3.0f);
            }
            else {
                iVol = testIVol;
                fVol = testFVol;
            }
        }
        catch (NumberFormatException e) {
            success = false;
            textOverlay.setText("Failed to parse volume value.", 3.0f);
        }

        if (generateLines.enabled && selectedOnly.enabled) {
            try {
                offset = Integer.parseInt(genOffset.text);
            }
            catch (NumberFormatException e) {
                success = false;
                textOverlay.setText("Failed to parse offset value.", 3.0f);
            }
        }


        if (success) {
            int funcIndex = formulaButtons.indexOf(selectedFormula);
            if (funcIndex >= 0 && funcIndex < SvFunctionLayer.formulas.size()) {
                Pair<Function<Double, Double>, String> funcInfo = SvFunctionLayer.formulas.get(funcIndex);
                if (funcInfo.b.equals(selectedFormula.action)) {
                    result = new VolumeFunctionProperties(iVol, fVol, generateLines.enabled, offset, selectedOnly.enabled, adjustExisting.enabled, basedOnObjects.enabled, funcInfo.a);
                }
                else {
                    textOverlay.setText("Unexpected error.", 3.0f);
                }
                close();
            }
            else {
                textOverlay.setText("Failed to determine function to use. Should not occur!", 3.0f);
            }
        }
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

    private static class VolumeFunctionProcessor extends TextInputProcessor {
        private final VolumeFunctionLayer sourceLayer;

        public VolumeFunctionProcessor(VolumeFunctionLayer source)
        {
            super(BindingMaster.getBindingGroupCopy("Basic"), true);
            this.sourceLayer = source;
        }

        @Override
        public void bind() {
            bindings.bind("Exit", sourceLayer::cancel);
            bindings.bind("Confirm", sourceLayer::confirm);
            bindings.bind("TAB", sourceLayer::cycleInput);

            for (int i = 1; i <= 9; ++i) {
                int index = i - 1;
                bindings.bind(Integer.toString(i), () -> sourceLayer.selectFormula(index));
            }

            bindings.addMouseBind((x, y, b)->(b == 0) || (b == 1),
                    (p, b) -> {
                        sourceLayer.initialVolume.click(p.x, p.y, this);
                        sourceLayer.finalVolume.click(p.x, p.y, this);

                        if (sourceLayer.genOffset.click(p.x, p.y, this))
                            return null;

                        if (sourceLayer.generateLines.click(p.x, p.y, b))
                            return null;
                        if (sourceLayer.selectedOnly.click(p.x, p.y, b))
                            return null;

                        if (sourceLayer.adjustExisting.click(p.x, p.y, b))
                            return null;
                        if (sourceLayer.basedOnObjects.click(p.x, p.y, b))
                            return null;

                        for (ImageButton button : sourceLayer.formulaButtons)
                            if (button.click(p.x, p.y, b)) return null;

                        if (sourceLayer.cancelButton.click(p.x, p.y, b))
                            return null;
                        if (sourceLayer.confirmButton.click(p.x, p.y, b))
                            return null;
                        return null;
                    });
        }

        @Override
        public boolean keyTyped(char character) {
            super.keyTyped(character);
            if (sourceLayer.initialVolume.isActive() || sourceLayer.finalVolume.isActive())
                sourceLayer.updateVol = true;
            return true;
        }
    }
}