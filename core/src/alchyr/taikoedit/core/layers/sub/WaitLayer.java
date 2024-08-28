package alchyr.taikoedit.core.layers.sub;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.input.TextInputProcessor;
import alchyr.taikoedit.core.ui.Button;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.interfaces.functional.VoidMethod;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.function.Supplier;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class WaitLayer extends ProgramLayer implements InputLayer {
    private final WaitLayerProcessor processor;

    public boolean active = true;
    public boolean result = false;

    //Rendering
    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.85f);

    //Asset
    private final BitmapFont font;
    private final Texture pix;

    //Positions
    private static final int BUTTON_Y = 80;
    private final int middleY = SettingsMaster.getHeight() / 2;

    //Parts
    public String text;

    private final Button cancelButton;

    private boolean forceComplete = false;
    private Supplier<Boolean> isComplete;
    private VoidMethod onComplete, onCancel;

    public WaitLayer(String text, Supplier<Boolean> isComplete)
    {
        this.type = LAYER_TYPE.NORMAL;

        pix = assetMaster.get("ui:pixel");

        processor = new WaitLayerProcessor(this);

        this.isComplete = isComplete;
        this.text = text;

        font = assetMaster.getFont("default");

        cancelButton = new Button(SettingsMaster.getMiddleX(), middleY - BUTTON_Y, "Cancel", font).setClick(this::cancel);

        processor.bind();
    }

    public void setComplete() {
        forceComplete = true;
    }

    public WaitLayer onComplete(VoidMethod m) {
        this.onComplete = m;
        return this;
    }
    public ProgramLayer onCancel(VoidMethod m) {
        this.onCancel = m;
        return this;
    }

    @Override
    public void update(float elapsed) {
        processor.update(elapsed);

        if (active && (forceComplete || isComplete.get())) {
            if (onComplete != null) {
                onComplete.run();
            }
            close();
            return;
        }
        cancelButton.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight() + 5);

        textRenderer.setFont(font).renderTextCentered(sb, text, SettingsMaster.getMiddleX(), middleY, Color.WHITE);

        cancelButton.render(sb, sr);
    }

    private void cancel()
    {
        if (onCancel != null)
            onCancel.run();
        close();
    }
    private void close() {
        active = false;

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

    private static class WaitLayerProcessor extends TextInputProcessor {
        private final WaitLayer sourceLayer;

        public WaitLayerProcessor(WaitLayer source)
        {
            super(BindingMaster.getBindingGroupCopy("Basic"), true);
            this.sourceLayer = source;
        }

        @Override
        public void bind() {
            bindings.bind("Exit", sourceLayer::cancel);

            bindings.addMouseBind((x, y, b)->sourceLayer.cancelButton.contains(x, y), sourceLayer.cancelButton::effect);
        }
    }
}