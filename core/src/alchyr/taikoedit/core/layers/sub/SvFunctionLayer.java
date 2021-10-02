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
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class SvFunctionLayer extends ProgramLayer implements InputLayer {
    private static SvFunctionProcessor processor;

    public boolean active = true;
    public SvFunctionProperties result = null; //null = do nothing

    public static class SvFunctionProperties {
        public final double isv, fsv;
        public final boolean svBarlines, relativeLast;
        public final Function<Double, Double> function;

        public SvFunctionProperties(double isv, double fsv, boolean svBarlines, boolean relativeLast, Function<Double, Double> func) {
            this.isv = isv;
            this.fsv = fsv;
            this.svBarlines = svBarlines;
            this.relativeLast = relativeLast;
            this.function = func;
        }
    }

    //Rendering
    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.83f);

    //Textures
    private final Texture pix;

    //Positions
    private static final int LEFT_POS = 50;
    private static final int RIGHT_POS = SettingsMaster.getWidth() - 100;
    private static final int BUTTON_OFFSET = 60;
    private final int middleY = SettingsMaster.getHeight() / 2;

    private static final int FORMULA_SIZE = (int) (150 * SettingsMaster.SCALE);
    private static final int FORMULA_START_Y = SettingsMaster.getHeight() - FORMULA_SIZE * 2;

    //Parts
    private TextOverlay textOverlay;

    private final TextField initialSv;
    private final TextField finalSv;

    private final ToggleButton svBarlines;
    private final ToggleButton relativeLast;

    private final List<ImageButton> formulaButtons = new ArrayList<>();

    private final Button confirmButton;
    private final Button cancelButton;

    //Other data
    private double isv, fsv;
    private ImageButton selectedFormula;

    private static final int GRAPH_STEP = 8;
    private final static List<Pair<Function<Double, Double>, String>> formulas = new ArrayList<>();
    private final static HashMap<Function<Double, Double>, Texture> formulaTextures = new HashMap<>();
    private final static HashMap<Function<Double, Double>, Texture> formulaHoverTextures = new HashMap<>();

    private static Function<Double, Double> linear = x -> x;
    private static Function<Double, Double> exp_3 = getExp(x -> x,1.3);
    private static Function<Double, Double> exp_6 = getExp(x -> x,1.6);
    private static Function<Double, Double> getExp(Function<Double, Double> x, double exp)
    {
        final double p = exp;
        return (y -> Math.pow(x.apply(y), p));
    }
    private static Function<Double, Double> cosIn = x -> Math.cos(x * Math.PI / 2.0 + 1.5 * Math.PI); //fast at start, slow at end
    private static Function<Double, Double> cosOut = x -> (Math.cos(x * Math.PI / 2.0 + Math.PI) + 1); //slow at start, fast at end

    static {
        formulas.add(new Pair<>(linear, "Linear"));
        formulas.add(new Pair<>(exp_3, "Exp 1.3"));
        formulas.add(new Pair<>(exp_6, "Exp 1.6"));
        formulas.add(new Pair<>(cosIn, "Sin In"));
        formulas.add(new Pair<>(cosOut, "Sin Out"));

        for (Pair<Function<Double, Double>, String> formula : formulas) {
            generateGraph(formula.a);
        }
    }
    private static void generateGraph(Function<Double, Double> formula) {
        Pixmap graph = new Pixmap(130, 130, Pixmap.Format.RGBA8888);
        Pixmap hoverGraph = new Pixmap(130, 130, Pixmap.Format.RGBA8888);
        graph.setColor(Color.CLEAR);
        graph.fill();
        hoverGraph.setColor(Color.GOLDENROD.cpy().mul(1, 1, 1, 0.5f));
        hoverGraph.fill();
        graph.setColor(Color.LIGHT_GRAY);
        hoverGraph.setColor(Color.LIGHT_GRAY);
        /*int lastX = 1, lastY = 129;
        for (int x = GRAPH_STEP; x <= 128; x += GRAPH_STEP) {
            graph.drawLine(lastX, lastY, (lastX = x + 1) + 1, lastY = (int) (129 - Math.round(formula.apply((x + 1) / 128.0) * 128.0)));
        }*/
        for (int x = 0; x <= 128; x += GRAPH_STEP) {
            graph.fillCircle(x + 1, (int) (129 - Math.round(formula.apply((x + 1) / 128.0) * 128.0)), 2);
            hoverGraph.fillCircle(x + 1, (int) (129 - Math.round(formula.apply((x + 1) / 128.0) * 128.0)), 2);
        }

        graph.setColor(Color.WHITE);
        hoverGraph.setColor(Color.WHITE);
        graph.drawRectangle(0, 0, 130, 130);
        hoverGraph.drawRectangle(0, 0, 130, 130);

        formulaTextures.put(formula, new Texture(graph));
        formulaHoverTextures.put(formula, new Texture(hoverGraph));
    }
    public static void disposeFunctions() {
        for (Pair<Function<Double, Double>, String> formula : formulas) {
            formulaTextures.get(formula.a).dispose();
            formulaHoverTextures.get(formula.a).dispose();
        }
    }

    private Function<Double, Double> trueExp = null;

    public SvFunctionLayer(double isv, double fsv)
    {
        this.type = LAYER_TYPE.NORMAL;

        pix = assetMaster.get("ui:pixel");

        processor = new SvFunctionProcessor(this);
        processor.bind();

        this.isv = isv;
        this.fsv = fsv;

        textOverlay = new TextOverlay(assetMaster.getFont("aller medium"), SettingsMaster.getHeight() / 2, 100);

        DecimalFormat df = new DecimalFormat("0.0###");
        initialSv = new TextField(LEFT_POS, middleY + BUTTON_OFFSET * 1.5f, 250f, "Initial SV:", df.format(isv), 6, assetMaster.getFont("aller medium")).setType(TextField.TextType.NUMERIC);
        finalSv = new TextField(LEFT_POS, middleY + BUTTON_OFFSET * 0.5f, 250f, "Final SV:", df.format(fsv), 6, assetMaster.getFont("aller medium")).setType(TextField.TextType.NUMERIC);

        svBarlines = new ToggleButton(LEFT_POS, middleY - BUTTON_OFFSET * 0.5f, "Barlines", assetMaster.getFont("aller medium"), true);
        relativeLast = new ToggleButton(LEFT_POS, middleY - BUTTON_OFFSET * 1.5f, "Relative to Final BPM", assetMaster.getFont("aller medium"), true);

        loadFormulas();

        confirmButton = new Button(RIGHT_POS, middleY + BUTTON_OFFSET * 0.5f, "Generate", assetMaster.getFont("aller medium"), this::confirm);
        cancelButton = new Button(RIGHT_POS, middleY - BUTTON_OFFSET * 0.5f, "Cancel", assetMaster.getFont("aller medium"), this::cancel);
    }

    private void loadFormulas() {
        updateTrueExp();

        int index = 0, col, row;
        for (Pair<Function<Double, Double>, String> formula : formulas) {
            col = (index % 3) - 1;
            row = index / 3;
            int finalIndex = index;
            formulaButtons.add(new ImageButton(SettingsMaster.getMiddle() + col * FORMULA_SIZE, FORMULA_START_Y - row * FORMULA_SIZE,
                    formulaTextures.get(formula.a), formulaHoverTextures.get(formula.a),
                    formula.b, assetMaster.getFont("aller medium"),
                    (button)->selectFormula(finalIndex)).setAction(formula.b));

            ++index;
        }

        selectedFormula = formulaButtons.get(0);
    }
    private void updateTrueExp() {
        if (trueExp != null) {
            //cleanup existing
            formulas.removeIf((f)->f.a == trueExp);
            formulaTextures.remove(trueExp).dispose();
            formulaHoverTextures.remove(trueExp).dispose();
        }

        if (isv == fsv) {
            trueExp = x -> x;
        }
        else {
            trueExp = x -> (isv - isv * Math.pow(fsv / isv, x)) / (isv - fsv);
        }

        formulas.add(new Pair<>(trueExp, "True Exp"));
        generateGraph(trueExp);

        //If not already generated list will just be empty
        for (ImageButton button : formulaButtons) {
            if (button.action.equals("True Exp")) {
                button.setTextures(formulaTextures.get(trueExp), formulaHoverTextures.get(trueExp));
            }
        }
    }
    private void selectFormula(int index) {
        selectedFormula = formulaButtons.get(index);
    }

    @Override
    public void update(float elapsed) {
        textOverlay.update(elapsed);
        initialSv.update();
        finalSv.update();
        svBarlines.update();
        relativeLast.update();
        for (ImageButton b : formulaButtons)
            b.update();
        confirmButton.update();
        cancelButton.update();
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight());

        initialSv.render(sb, sr);
        finalSv.render(sb, sr);

        svBarlines.render(sb, sr);
        relativeLast.render(sb, sr);

        sb.setColor(Color.FOREST);
        sb.draw(pix, selectedFormula.getX(), selectedFormula.getY(), selectedFormula.getWidth(), selectedFormula.getHeight());

        for (ImageButton b : formulaButtons)
            b.render(sb, sr);

        cancelButton.render(sb, sr);
        confirmButton.render(sb, sr);

        textOverlay.render(sb, sr);
    }

    private void cancel(int ignored) {
        cancel();
    }
    private void cancel()
    {
        result = null;
        close();
    }
    private void close() {
        active = false;

        if (trueExp != null) {
            //cleanup existing
            formulas.removeIf((f)->f.a == trueExp);
            formulaTextures.remove(trueExp).dispose();
            formulaHoverTextures.remove(trueExp).dispose();
        }

        TaikoEditor.removeLayer(this);
    }
    private void confirm(int button)
    {
        boolean success = true;
        try {
            double testIsv = Double.parseDouble(initialSv.text);
            double testFsv = Double.parseDouble(finalSv.text);

            if (testIsv <= 0) {
                success = false;
                textOverlay.setText("Invalid initial SV.", 3.0f);
            }
            else if (testFsv <= 0) {
                success = false;
                textOverlay.setText("Invalid final SV.", 3.0f);
            }
            else {
                isv = testIsv;
                fsv = testFsv;
            }
        }
        catch (Exception e) {
            success = false;
            textOverlay.setText("Failed to parse SV value.", 3.0f);
        }
        if (success) {
            result = new SvFunctionProperties(isv, fsv, svBarlines.enabled, relativeLast.enabled, formulas.get(formulaButtons.indexOf(selectedFormula)).a);
            active = false;
            close();
        }
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    private static class SvFunctionProcessor extends TextInputProcessor {
        private final SvFunctionLayer sourceLayer;

        public SvFunctionProcessor(SvFunctionLayer source)
        {
            super(BindingMaster.getBindingGroup("Basic"));
            this.sourceLayer = source;
        }

        @Override
        public void bind() {
            bindings.bind("Exit", ()->{
                    sourceLayer.cancel();
                return true;
            });
        }

        @Override
        public boolean keyTyped(char character) {
            super.keyTyped(character);
            try {
                double lastIsv = sourceLayer.isv, lastFsv = sourceLayer.fsv;
                sourceLayer.isv = Double.parseDouble(sourceLayer.initialSv.text);
                sourceLayer.fsv = Double.parseDouble(sourceLayer.finalSv.text);

                if (sourceLayer.isv <= 0)
                    sourceLayer.isv = lastIsv;
                if (sourceLayer.fsv <= 0)
                    sourceLayer.fsv = lastFsv;

                if (sourceLayer.isv != lastIsv || sourceLayer.fsv != lastFsv) {
                    sourceLayer.updateTrueExp();
                }
            }
            catch (Exception ignored) {
            }
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            super.keyDown(keycode);
            return true;
        }

        @Override
        public boolean keyUp(int keycode) {
            super.keyUp(keycode);
            return true;
        }

        @Override
        public boolean onTouchDown(int gameX, int gameY, int pointer, int button) {
            if (button != 0 && button != 1)
                return false; //left or right click only

            sourceLayer.initialSv.click(gameX, gameY, this);

            if (sourceLayer.finalSv.click(gameX, gameY, this))
                return true;

            if (sourceLayer.svBarlines.click(gameX, gameY, button))
                return true;
            if (sourceLayer.relativeLast.click(gameX, gameY, button))
                return true;

            for (ImageButton b : sourceLayer.formulaButtons)
                if (b.click(gameX, gameY, button)) return true;

            if (sourceLayer.cancelButton.click(gameX, gameY, button))
                return true;
            if (sourceLayer.confirmButton.click(gameX, gameY, button))
                return true;
            return true;
        }

        @Override
        public boolean onTouchUp(int gameX, int gameY, int pointer, int button) {
            return true;
        }

        @Override
        public boolean onTouchDragged(int gameX, int gameY, int pointer) {
            return true;
        }

        @Override
        public boolean onMouseMoved(int gameX, int gameY) {
            return true;
        }

        @Override
        public boolean scrolled(int amount) {
            return true;
        }
    }
}