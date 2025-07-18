package alchyr.taikoedit.core.layers.sub;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.input.TextInputProcessor;
import alchyr.taikoedit.core.ui.Button;
import alchyr.taikoedit.core.ui.TextField;
import alchyr.taikoedit.core.ui.TextOverlay;
import alchyr.taikoedit.management.BindingMaster;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.function.BiConsumer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class ServerSetupLayer extends ProgramLayer implements InputLayer {
    public static final int CLIENT_LIMIT = 8;

    private final ServerSetupProcessor processor;

    //Rendering
    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.83f);

    //Textures
    private final Texture pix;

    //Positions
    private final int middleY = SettingsMaster.getHeight() / 2;
    private static final int INPUT_POS = (SettingsMaster.getWidth() / 2) - 100;
    private static final int BUTTON_OFFSET = 60;

    //Parts
    private TextOverlay textOverlay;

    private final TextField serverPort;
    private final TextField connectionLimit;

    private final Button confirmButton;
    private final Button cancelButton;

    //Other data
    private int lastValidPort, lastValidClientLimit;
    private BiConsumer<Integer, Integer> completion;

    public ServerSetupLayer(int port, BiConsumer<Integer, Integer> completion)
    {
        this.type = LAYER_TYPE.NORMAL;

        pix = assetMaster.get("ui:pixel");

        processor = new ServerSetupProcessor(this);
        
        BitmapFont font = assetMaster.getFont("aller medium");

        textOverlay = new TextOverlay(font, SettingsMaster.getHeight() / 2, 100);

        lastValidPort = port;
        lastValidClientLimit = 1;
        this.serverPort = new TextField(INPUT_POS, middleY + BUTTON_OFFSET, 250f, "Port (30000-60000):", Integer.toString(port), 5, font).setType(TextField.TextType.INTEGER).blocking();
        serverPort.setOnEndInput((text, field)->{
            try {
                int testPort = Integer.parseInt(text);
                if (testPort < 30000 || testPort > 60000) {
                    field.setText(Integer.toString(lastValidPort));
                    textOverlay.setText("Invalid port (Only accepts ports in range 30000-60000)", 3.0f);
                }
                else {
                    lastValidPort = testPort;
                }
            }
            catch (Exception e) {
                field.setText(Integer.toString(lastValidPort));
                textOverlay.setText("Invalid value", 3.0f);
            }
        });
        this.connectionLimit = new TextField(INPUT_POS, middleY, 250f, "Client Limit (1-" + CLIENT_LIMIT + "):", Integer.toString(lastValidClientLimit), 2, font).setType(TextField.TextType.INTEGER).blocking();
        connectionLimit.setOnEndInput((text, field)->{
            try {
                int amt = Integer.parseInt(text);
                if (amt < 1 || amt > CLIENT_LIMIT) {
                    field.setText(Integer.toString(lastValidClientLimit));
                    textOverlay.setText("Invalid client limit", 2.0f);
                }
                else {
                    lastValidClientLimit = amt;
                }
            }
            catch (Exception e) {
                field.setText(Integer.toString(lastValidClientLimit));
                textOverlay.setText("Invalid client limit", 3.0f);
            }
        });

        confirmButton = new Button(SettingsMaster.getMiddleX(), middleY - BUTTON_OFFSET * 1.5f, "Open", font).setClick(this::confirm);
        cancelButton = new Button(SettingsMaster.getMiddleX(), middleY - BUTTON_OFFSET * 2.5f, "Cancel", font).setClick(this::cancel);

        this.completion = completion;

        processor.bind();
    }

    @Override
    public void update(float elapsed) {
        processor.update(elapsed);

        textOverlay.update(elapsed);
        serverPort.update(elapsed);
        connectionLimit.update(elapsed);

        confirmButton.update(elapsed);
        cancelButton.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight() + 5);

        textRenderer.renderTextCentered(sb, "After opening the server, a pass will be copied to your clipboard.\nSend this pass to people that you want to join.\nThey should copy it, and then click the join button in the main menu.\nPort forwarding is required.\nWhen someone joins, all open difficulties will be saved and the undo/redo queue will be cleared.",
                SettingsMaster.getMiddleX(), SettingsMaster.getHeight() * 0.75f);

        serverPort.render(sb, sr);
        connectionLimit.render(sb, sr);

        cancelButton.render(sb, sr);
        confirmButton.render(sb, sr);

        textOverlay.render(sb, sr);
    }

    private void cycleInput() {
        if (serverPort.isActive()) {
            serverPort.disable();
            connectionLimit.activate(processor);
        }
        else if (connectionLimit.isActive()) {
            connectionLimit.disable();
        }
        else {
            serverPort.activate(processor);
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
        serverPort.disable();
        connectionLimit.disable();

        boolean success = true;

        try {
            int testPort = Integer.parseInt(serverPort.text);

            if (testPort < 30000 || testPort > 60000) {
                success = false;
            }
            else {
                lastValidPort = testPort;
            }

            int testLimit = Integer.parseInt(connectionLimit.text);

            if (testLimit < 1 || testLimit > CLIENT_LIMIT) {
                success = false;
            }
            else {
                lastValidClientLimit = testLimit;
            }
        }
        catch (NumberFormatException e) {
            success = false;
            textOverlay.setText("Invalid value.", 3.0f);
        }

        if (success) {
            completion.accept(lastValidPort, lastValidClientLimit);
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

    private static class ServerSetupProcessor extends TextInputProcessor {
        private final ServerSetupLayer sourceLayer;

        public ServerSetupProcessor(ServerSetupLayer source)
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
                        boolean clicked = sourceLayer.serverPort.click(p.x, p.y, this)
                                || sourceLayer.connectionLimit.click(p.x, p.y, this);

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