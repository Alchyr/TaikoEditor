package alchyr.taikoedit.core.layers.sub;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.core.input.BoundInputProcessor;
import alchyr.taikoedit.core.input.InputBinding;
import alchyr.taikoedit.core.ui.Button;
import alchyr.taikoedit.core.ui.Label;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.interfaces.functional.VoidMethod;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class GetHotkeyLayer extends ProgramLayer implements InputLayer {
    private final HotkeyLayerProcessor processor;

    public boolean active = true;
    public boolean result = false;

    private final String[] keyNames;

    public boolean ctrl = false, alt = false, shift = false;
    public int keycode = -1;

    //Rendering
    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.85f);

    //Textures
    private final Texture pix;

    //Positions
    private static final int MIDDLE_Y = SettingsMaster.getHeight() / 2;
    private static final float INSTRUCTION_Y = MIDDLE_Y + 100;
    private static final float BUTTON_Y = SettingsMaster.getHeight() * 0.25f;

    //Parts
    private String hotkeyText;
    private final Label instructionLabel;
    private final Button cancelButton;

    private VoidMethod onConfirm, onCancel;

    public GetHotkeyLayer(String[] keyNameArray, InputBinding.InputInfo inputInfo)
    {
        this.type = LAYER_TYPE.UPDATE_STOP;

        pix = assetMaster.get("ui:pixel");

        processor = new HotkeyLayerProcessor(this);

        this.keyNames = keyNameArray;
        this.hotkeyText = inputInfo == null ? "" : inputInfo.toString(keyNameArray);

        instructionLabel = new Label(SettingsMaster.getMiddle(), INSTRUCTION_Y, assetMaster.getFont("default"), "Input new hotkey.", Label.LabelAlign.CENTER);
        cancelButton = new Button(SettingsMaster.getMiddle(), BUTTON_Y, "Cancel", assetMaster.getFont("default")).setClick(this::cancel);

        processor.bind();
    }

    public GetHotkeyLayer onConfirm(VoidMethod m) {
        this.onConfirm = m;
        return this;
    }
    public GetHotkeyLayer onCancel(VoidMethod m) {
        this.onCancel = m;
        return this;
    }

    private void updateHotkeyText() {
        hotkeyText = InputBinding.InputInfo.toString(keyNames, keycode, ctrl, alt, shift).trim();
    }

    @Override
    public void update(float elapsed) {
        processor.update(elapsed);

        cancelButton.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight());

        textRenderer.renderTextCentered(sb, hotkeyText, SettingsMaster.getMiddle(), MIDDLE_Y, Color.WHITE);

        instructionLabel.render(sb, sr);
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
    private void confirm()
    {
        ctrl = BindingGroup.ctrl();
        alt = BindingGroup.alt();
        shift = BindingGroup.shift();

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

    private static class HotkeyLayerProcessor extends BoundInputProcessor {
        private final GetHotkeyLayer sourceLayer;

        public HotkeyLayerProcessor(GetHotkeyLayer source)
        {
            super(BindingMaster.getBindingGroupCopy("Basic"), true);
            this.sourceLayer = source;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (bindings.receiveKeyDown(keycode))
                return true;

            switch (keycode) {
                case Input.Keys.CONTROL_LEFT:
                case Input.Keys.CONTROL_RIGHT:
                    sourceLayer.ctrl = true;
                    break;
                case Input.Keys.ALT_LEFT:
                case Input.Keys.ALT_RIGHT:
                    sourceLayer.alt = true;
                    break;
                case Input.Keys.SHIFT_LEFT:
                case Input.Keys.SHIFT_RIGHT:
                    sourceLayer.shift = true;
                    break;
                default:
                    sourceLayer.keycode = keycode;
                    break;
            }
            sourceLayer.updateHotkeyText();

            return true;
        }

        @Override
        public boolean keyUp(int keycode) {
            if (bindings.receiveKeyUp(keycode))
                return true;

            switch (keycode) {
                case Input.Keys.CONTROL_LEFT:
                case Input.Keys.CONTROL_RIGHT:
                    sourceLayer.ctrl = BindingGroup.ctrl();
                    sourceLayer.updateHotkeyText();
                    break;
                case Input.Keys.ALT_LEFT:
                case Input.Keys.ALT_RIGHT:
                    sourceLayer.alt = BindingGroup.alt();
                    sourceLayer.updateHotkeyText();
                    break;
                case Input.Keys.SHIFT_LEFT:
                case Input.Keys.SHIFT_RIGHT:
                    sourceLayer.shift = BindingGroup.shift();
                    sourceLayer.updateHotkeyText();
                    break;
                default:
                    if (sourceLayer.keycode == keycode) {
                        bindings.queue(sourceLayer::confirm);
                    }
                    break;
            }

            return true;
        }

        @Override
        public void bind() {
            bindings.bind("Exit", sourceLayer::cancel);

            bindings.addMouseBind(sourceLayer.cancelButton::contains, sourceLayer.cancelButton::effect);
        }
    }
}