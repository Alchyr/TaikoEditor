package alchyr.taikoedit.core.ui;

import alchyr.taikoedit.core.UIElement;
import alchyr.taikoedit.core.input.TextInputProcessor;
import alchyr.taikoedit.core.input.sub.TextInputReceiver;
import alchyr.taikoedit.util.GeneralUtils;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class TextField implements UIElement, TextInputReceiver {
    private static final float BUFFER = 4;
    private static final float BLIP_BUFFER = 2;
    private static final float BLIP_WIDTH = 2;

    private TextType type;

    public enum TextType {
        NORMAL,
        NUMERIC,
        INTEGER
    }

    private final Texture pix = assetMaster.get("ui:pixel");

    private float x, y, textX, width, height, x2, y2, dx, dy;
    private int centerY;

    private String label;
    public String text;
    private final BitmapFont font;
    private final Set<Character> filtered = new HashSet<>();

    private int charLimit;

    private Function<String, Boolean> onEnter = null;
    private BiConsumer<String, TextField> onEndInput = null;

    //blip
    private boolean renderBlip;
    private float blipTimer = 0;
    private float blipX;

    private TextInputProcessor active; //currently receiving input
    private boolean enabled = true; //able to be interacted with
    private boolean blocking = false; //does it block relevant input while active

    public String action = "";

    public String displaySuffix = "";

    public TextField(float leftX, float centerY, float maxWidth, String labelText, String inputText, int charLimit, BitmapFont font)
    {
        this.font = font;
        this.label = labelText;
        this.text = inputText;

        this.charLimit = charLimit;

        float labelWidth = textRenderer.setFont(font).getWidth(labelText + " ");
        width = maxWidth;
        height = textRenderer.getHeight(labelText) + BUFFER;

        x = leftX;
        y = centerY - height / 2.0f;
        textX = x + labelWidth;
        this.centerY = MathUtils.floor(centerY);
        x2 = x + width;
        y2 = y + height;
        blipX = textX + textRenderer.getWidth(text) + BLIP_BUFFER;

        dx = 0;
        dy = 0;

        this.type = TextType.NORMAL;
        this.active = null;
    }
    public TextField setType(TextType type) {
        this.type = type;
        return this;
    }
    public TextField setDisplaySuffix(String s) {
        this.displaySuffix = s;
        return this;
    }
    public TextField setOnEnter(Function<String, Boolean> onEnter) {
        this.onEnter = onEnter;
        return this;
    }
    public TextField setOnEndInput(BiConsumer<String, TextField> onEndInput) {
        this.onEndInput = onEndInput;
        return this;
    }
    public TextField blocking() {
        this.blocking = true;
        return this;
    }

    public TextField filter(char c) {
        filtered.add(c);
        return this;
    }

    @Override
    public boolean acceptCharacter(char c) {
        if (!enabled || filtered.contains(c))
            return false;

        switch (type) {
            case INTEGER:
                return (c == 8) ||
                        (c >= '0' && c <= '9') ||
                        (c == '-' && text.isEmpty());
            case NUMERIC:
                return (c == 8) ||
                        (c >= '0' && c <= '9') ||
                        ((c == ',' || c == '.') && GeneralUtils.charCount(text, '.') < 1) ||
                        (c == '-' && text.isEmpty());
            default:
                return true;
        }
    }

    public TextField setAction(String action)
    {
        this.action = action;
        return this;
    }

    @Override
    public void move(float dx, float dy) {
        x += dx;
        x2 += dx;
        textX += dx;
        blipX += dx;

        y += dy;
        y2 += dy;
        centerY += dy;
    }

    public boolean tryClick(float clickX, float clickY) {
        if (!enabled) {
            disable();
            return false;
        }

        if (this.x + dx < clickX && this.y + dy < clickY && clickX < x2 + dx && clickY < y2 + dy)
        {
            return true;
        }
        disable();
        return false;
    }

    public boolean click(float clickX, float clickY, TextInputProcessor processor)
    {
        if (!enabled) {
            disable();
            return false;
        }

        if (x + dx < clickX && y + dy < clickY && clickX < x2 + dx && clickY < y2 + dy)
        {
            activate(processor);
            return true;
        }
        disable();
        return false;
    }

    public boolean isActive() {
        return active != null;
    }
    public void activate(TextInputProcessor processor) {
        if (!enabled)
            return;

        active = processor;
        renderBlip = true;
        blipTimer = 0.4f;
        processor.setTextReceiver(this);
    }
    public void disable() {
        if (active != null && enabled && onEndInput != null) {
            onEndInput.accept(text, this);
        }
        if (active != null)
            active.disableTextReceiver(this);
        active = null;
        renderBlip = false;
    }

    public void lock() {
        enabled = false;
        disable();
    }
    public void unlock() {
        enabled = true;
    }

    @Override
    public void update(float elapsed)
    {
        if (active != null) {
            blipTimer -= elapsed;
            if (blipTimer < 0) {
                blipTimer = 0.4f;
                renderBlip = !renderBlip;
            }
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        textRenderer.setFont(font).resetScale().renderTextYCentered(sb, enabled ? Color.WHITE : Color.GRAY, label, this.x, this.centerY);
        textRenderer.renderTextYCentered(sb, enabled ? Color.WHITE : Color.GRAY, text + displaySuffix, this.textX, this.centerY);

        if (renderBlip) {
            sb.setColor(Color.WHITE);
            sb.draw(pix, blipX, y, BLIP_WIDTH, height);
        }
    }

    public void render(SpriteBatch sb, ShapeRenderer sr, float x, float y) {
        dx = x; //adjustment to hover/click check position
        dy = y;

        textRenderer.setFont(font).resetScale().renderTextYCentered(sb, enabled ? Color.WHITE : Color.GRAY, label, this.x + x, this.centerY + y);
        textRenderer.renderTextYCentered(sb, enabled ? Color.WHITE : Color.GRAY, text + displaySuffix, this.textX + x, this.centerY + y);

        if (renderBlip) {
            sb.setColor(Color.WHITE);
            sb.draw(pix, blipX + x, this.y + y, BLIP_WIDTH, height);
        }
    }

    @Override
    public int getCharLimit() {
        return charLimit;
    }

    @Override
    public String getInitialText() {
        return text;
    }

    @Override
    public void setText(String newText) {
        switch (type) {
            case NUMERIC:
                this.text = newText.replaceAll(",", ".");
                break;
            default:
                this.text = newText;
        }
        blipX = textX + textRenderer.setFont(font).getWidth(text) + BLIP_BUFFER;
    }

    @Override
    public boolean blockInput(int key) {
        if (blocking) {
            switch (type) {
                case NUMERIC:
                    return (key >= Input.Keys.NUM_0 && key <= Input.Keys.NUM_9) ||
                            (key >= Input.Keys.NUMPAD_0 && key <= Input.Keys.NUMPAD_9) ||
                            key == Input.Keys.PERIOD || key == Input.Keys.COMMA ||
                            key == Input.Keys.MINUS ||
                            key == Input.Keys.BACKSPACE;
                case INTEGER:
                    return (key >= Input.Keys.NUM_0 && key <= Input.Keys.NUM_9) ||
                            (key >= Input.Keys.NUMPAD_0 && key <= Input.Keys.NUMPAD_9) ||
                            key == Input.Keys.MINUS ||
                            key == Input.Keys.BACKSPACE;
                default:
                    return (key >= Input.Keys.NUM_0 && key <= Input.Keys.NUM_9) ||
                            (key >= Input.Keys.NUMPAD_0 && key <= Input.Keys.NUMPAD_9) ||
                            (key >= Input.Keys.A && key <= Input.Keys.Z) ||
                            key == Input.Keys.BACKSPACE;

            }
        }
        return false;
    }

    @Override
    public BitmapFont getFont() {
        return font;
    }

    @Override
    public Function<String, Boolean> onPressEnter() {
        return onEnter;
    }
}