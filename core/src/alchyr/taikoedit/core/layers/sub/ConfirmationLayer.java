package alchyr.taikoedit.core.layers.sub;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.input.TextInputProcessor;
import alchyr.taikoedit.core.ui.*;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.interfaces.functional.VoidMethod;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class ConfirmationLayer extends ProgramLayer implements InputLayer {
    private final ConfirmationLayerProcessor processor;

    public boolean active = true;
    public boolean result = false;

    //Rendering
    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.85f);

    //Textures
    private final Texture pix;

    //Positions
    private static final int BUTTON_Y = 80;
    private static final int HORIZONTAL_SPACING = 160;
    private static final int VERTICAL_SPACING = 40;
    private final int middleY = SettingsMaster.getHeight() / 2;

    //Parts
    private String text;

    private final Button confirmButton;
    private final Button denyButton;
    private final Button cancelButton;

    private VoidMethod onConfirm, onDeny, onCancel;

    public ConfirmationLayer(String text, String confirm, String deny, boolean cancel)
    {
        this.type = LAYER_TYPE.UPDATE_STOP;

        pix = assetMaster.get("ui:pixel");

        processor = new ConfirmationLayerProcessor(this);

        this.text = text;

        BitmapFont font = assetMaster.getFont("default");

        float maxLength = textRenderer.setFont(font).getWidth(confirm);
        maxLength = Math.max(maxLength, textRenderer.getWidth(deny));

        float xSpacing, ySpacing;

        if (maxLength > 120) {
            xSpacing = 0;
            ySpacing = VERTICAL_SPACING;
        }
        else {
            xSpacing = HORIZONTAL_SPACING;
            ySpacing = 0;
        }

        float y = middleY - BUTTON_Y;

        if (cancel) {
            confirmButton = new Button(SettingsMaster.getMiddleX() + xSpacing, y, confirm, assetMaster.getFont("default")).setClick(this::yes);
            y -= ySpacing;
            denyButton = new Button(SettingsMaster.getMiddleX(), y, deny, assetMaster.getFont("default")).setClick(this::no);
            y -= ySpacing;
            cancelButton = new Button(SettingsMaster.getMiddleX() - xSpacing, y, "Cancel", font).setClick(this::cancel);
        }
        else {
            confirmButton = new Button(SettingsMaster.getMiddleX() - xSpacing / 2, y, confirm, assetMaster.getFont("default")).setClick(this::yes);
            denyButton = new Button(SettingsMaster.getMiddleX() + xSpacing / 2, y - ySpacing, deny, assetMaster.getFont("default")).setClick(this::no);
            cancelButton = null;
        }

        processor.bind();
    }

    public ConfirmationLayer onConfirm(VoidMethod m) {
        this.onConfirm = m;
        return this;
    }
    public ConfirmationLayer onDeny(VoidMethod m) {
        this.onDeny = m;
        return this;
    }
    public ProgramLayer onCancel(VoidMethod m) {
        this.onCancel = m;
        return this;
    }

    @Override
    public void update(float elapsed) {
        processor.update(elapsed);

        confirmButton.update(elapsed);
        denyButton.update(elapsed);
        if (cancelButton != null)
            cancelButton.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight() + 5);

        textRenderer.renderTextCentered(sb, text, SettingsMaster.getMiddleX(), middleY, Color.WHITE);

        denyButton.render(sb, sr);
        confirmButton.render(sb, sr);
        if (cancelButton != null)
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
    private void no() {
        result = false;
        if (onDeny != null)
            onDeny.run();
        close();
    }
    private void yes()
    {
        result = true;
        if (onConfirm != null)
            onConfirm.run();
        close();
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

    private static class ConfirmationLayerProcessor extends TextInputProcessor {
        private final ConfirmationLayer sourceLayer;

        public ConfirmationLayerProcessor(ConfirmationLayer source)
        {
            super(BindingMaster.getBindingGroupCopy("Basic"), true);
            this.sourceLayer = source;
        }

        @Override
        public void bind() {
            bindings.bind("Exit", sourceLayer::cancel);

            bindings.addMouseBind(sourceLayer.denyButton::contains, sourceLayer.denyButton::effect);
            bindings.addMouseBind(sourceLayer.confirmButton::contains, sourceLayer.confirmButton::effect);
            if (sourceLayer.cancelButton != null) {
                bindings.addMouseBind((x, y, b)->sourceLayer.cancelButton.contains(x, y), sourceLayer.cancelButton::effect);
            }
        }
    }
}