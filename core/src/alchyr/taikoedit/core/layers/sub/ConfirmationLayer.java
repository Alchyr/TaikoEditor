package alchyr.taikoedit.core.layers.sub;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.input.TextInputProcessor;
import alchyr.taikoedit.core.ui.*;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.VoidMethod;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class ConfirmationLayer extends ProgramLayer implements InputLayer {
    private static ConfirmationLayerProcessor processor;

    public boolean active = true;
    public boolean result = false;

    //Rendering
    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.75f);

    //Textures
    private final Texture pix;

    //Positions
    private static final int LEFT_POS = (int) (SettingsMaster.getWidth() * 0.35f);
    private static final int RIGHT_POS = (int) (SettingsMaster.getWidth() * 0.65f);
    private static final int BUTTON_OFFSET = 120;
    private final int middleY = SettingsMaster.getHeight() / 2;

    //Parts
    private String text;

    private final Button confirmButton;
    private final Button denyButton;

    private VoidMethod onConfirm, onDeny, onCancel;

    public ConfirmationLayer(String text, String confirm, String deny)
    {
        this.type = LAYER_TYPE.UPDATE_STOP;

        pix = assetMaster.get("ui:pixel");

        processor = new ConfirmationLayerProcessor(this);
        processor.bind();

        this.text = text;

        denyButton = new Button(LEFT_POS, middleY - BUTTON_OFFSET, deny, assetMaster.getFont("default"), this::no);
        confirmButton = new Button(RIGHT_POS, middleY - BUTTON_OFFSET, confirm, assetMaster.getFont("default"), this::yes);
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
        confirmButton.update();
        denyButton.update();
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight());

        textRenderer.renderTextCentered(sb, text, SettingsMaster.getMiddle(), middleY, Color.WHITE);

        denyButton.render(sb, sr);
        confirmButton.render(sb, sr);
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
    private void no(int button) {
        result = false;
        if (onDeny != null)
            onDeny.run();
        close();
    }
    private void yes(int button)
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

    private static class ConfirmationLayerProcessor extends TextInputProcessor {
        private final ConfirmationLayer sourceLayer;

        public ConfirmationLayerProcessor(ConfirmationLayer source)
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
        public boolean onTouchDown(int gameX, int gameY, int pointer, int button) {
            if (button != 0 && button != 1)
                return false; //left or right click only

            if (sourceLayer.denyButton.click(gameX, gameY, button))
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