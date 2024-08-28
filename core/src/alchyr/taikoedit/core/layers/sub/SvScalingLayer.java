package alchyr.taikoedit.core.layers.sub;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.input.TextInputProcessor;
import alchyr.taikoedit.core.ui.*;
import alchyr.taikoedit.editor.DivisorOptions;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.structures.Pair;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static alchyr.taikoedit.TaikoEditor.*;

public class SvScalingLayer extends ProgramLayer implements InputLayer {
    private final SvScalingProcessor processor;

    public static class SvScalingProperties {
        public final double scalingPoint, scalingMult;

        public SvScalingProperties(double scalingPoint, double scalingMult) {
            this.scalingPoint = scalingPoint;
            this.scalingMult = scalingMult;
        }
    }

    //Rendering
    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.83f);

    //Textures
    private final Texture pix;

    //Positions
    private final int middleY = SettingsMaster.getHeight() / 2;
    private static final int INPUT_POS = SettingsMaster.getWidth() / 4;
    private static final int BUTTON_OFFSET = 60;

    //Parts
    private TextOverlay textOverlay;

    private final TextField scalingPoint;
    private final TextField scalingAmount;

    private final Button confirmButton;
    private final Button cancelButton;

    //Other data
    private double scalePoint, scaleAmount;
    private Consumer<SvScalingProperties> completion;

    public SvScalingLayer(double scalingPoint, int scalingAmount, Consumer<SvScalingProperties> completion)
    {
        this.type = LAYER_TYPE.NORMAL;

        pix = assetMaster.get("ui:pixel");

        processor = new SvScalingProcessor(this);

        this.scalePoint = scalingPoint;
        this.scaleAmount = scalingAmount;
        
        BitmapFont font = assetMaster.getFont("aller medium");

        textOverlay = new TextOverlay(font, SettingsMaster.getHeight() / 2, 100);

        DecimalFormat df = new DecimalFormat("0.0###", osuDecimalFormat);
        this.scalingPoint = new TextField(INPUT_POS, middleY + BUTTON_OFFSET * 1.5f, 250f, "Scaling Center:", df.format(scalingPoint), 6, font).setType(TextField.TextType.NUMERIC).blocking();
        this.scalingAmount = new TextField(INPUT_POS, middleY + BUTTON_OFFSET * 0.5f, 250f, "Scaling Percentage:", Integer.toString(scalingAmount), 6, font).setType(TextField.TextType.NUMERIC).setDisplaySuffix("%").blocking();

        try { //Remove slight value inconsistencies when decimalformat rounds off very small decimals
            this.scalePoint = Double.parseDouble(this.scalingPoint.text);
        }
        catch (Exception e) {
            editorLogger.error("Failed to load initial values into SvScalingLayer.", e);
            e.printStackTrace();
        }

        confirmButton = new Button(SettingsMaster.getMiddleX(), middleY - BUTTON_OFFSET * 0.5f, "Scale", font).setClick(this::confirm);
        cancelButton = new Button(SettingsMaster.getMiddleX(), middleY - BUTTON_OFFSET * 1.5f, "Cancel", font).setClick(this::cancel);

        this.completion = completion;

        processor.bind();
    }

    @Override
    public void update(float elapsed) {
        processor.update(elapsed);

        textOverlay.update(elapsed);
        scalingAmount.update(elapsed);
        scalingPoint.update(elapsed);

        confirmButton.update(elapsed);
        cancelButton.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight() + 5);

        scalingAmount.render(sb, sr);
        scalingPoint.render(sb, sr);

        cancelButton.render(sb, sr);
        confirmButton.render(sb, sr);

        textOverlay.render(sb, sr);
    }

    private void cycleInput() {
        if (scalingPoint.isActive()) {
            scalingPoint.disable();
            scalingAmount.activate(processor);
        }
        else {
            scalingPoint.activate(processor);
        }
    }

    private void cancel()
    {
        close();
    }
    private void close() {
        TaikoEditor.removeLayer(this);
    }
    private void confirm()
    {
        scalingPoint.disable();
        scalingAmount.disable();

        boolean success = true;

        try {
            double testScalingPoint = Double.parseDouble(scalingPoint.text);
            double testScalingAmount = Double.parseDouble(scalingAmount.text);

            if (testScalingPoint < 0) {
                success = false;
                textOverlay.setText("Invalid scaling point.", 3.0f);
            }
            else if (testScalingAmount < 0) {
                success = false;
                textOverlay.setText("Invalid scaling amount.", 3.0f);
            }
            else {
                scaleAmount = testScalingAmount;
                scalePoint = testScalingPoint;
            }
        }
        catch (NumberFormatException e) {
            success = false;
            textOverlay.setText("Invalid value.", 3.0f);
        }

        if (success) {
            completion.accept(new SvScalingProperties(scalePoint, scaleAmount));
            close();
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

    private static class SvScalingProcessor extends TextInputProcessor {
        private final SvScalingLayer sourceLayer;

        public SvScalingProcessor(SvScalingLayer source)
        {
            super(BindingMaster.getBindingGroupCopy("Basic"), true);
            this.sourceLayer = source;
        }

        @Override
        public void bind() {
            bindings.bind("Exit", sourceLayer::cancel);
            bindings.bind("Confirm", sourceLayer::confirm);
            bindings.bind("TAB", sourceLayer::cycleInput);

            bindings.addMouseBind((x, y, b)->(b == 0) || (b == 1),
                    (p, b) -> {
                        boolean clicked = sourceLayer.scalingPoint.click(p.x, p.y, this);
                        clicked |= sourceLayer.scalingAmount.click(p.x, p.y, this);

                        if (clicked) return null;

                        if (sourceLayer.cancelButton.click(p.x, p.y, b))
                            return null;
                        if (sourceLayer.confirmButton.click(p.x, p.y, b))
                            return null;
                        return null;
                    });
        }
    }
}