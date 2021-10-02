package alchyr.taikoedit.core.ui;

import alchyr.taikoedit.core.UIElement;
import alchyr.taikoedit.core.input.TextInputProcessor;
import alchyr.taikoedit.core.input.sub.TextInputReceiver;
import alchyr.taikoedit.util.GeneralUtils;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

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
        NUMERIC
    }

    private final Texture pix = assetMaster.get("ui:pixel");

    private float x, y, textX, width, height, x2, y2, dx, dy;
    private int centerY;

    private String label;
    public String text;
    private final BitmapFont font;

    private int charLimit;

    Function<String, Boolean> onEnter = null;

    //blip
    private boolean renderBlip;
    private float blipTimer = 0;
    private float blipX;

    public boolean active;

    public String action = "";

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

        this.active = false;
    }
    public TextField setType(TextType type) {
        this.type = type;
        return this;
    }
    public TextField setOnEnter(Function<String, Boolean> onEnter) {
        this.onEnter = onEnter;
        return this;
    }

    @Override
    public boolean acceptCharacter(char c) {
        switch (type) {
            case NUMERIC:
                return (c == 8) ||
                        (c >= '0' && c <= '9') ||
                        ((c == ',' || c == '.') && GeneralUtils.charCount(text, '.') < 1);
            default:
                return true;
        }
    }

    public TextField setAction(String action)
    {
        this.action = action;
        return this;
    }

    public boolean click(int mouseX, int mouseY, TextInputProcessor processor)
    {
        if (x + dx < mouseX && y + dy < mouseY && mouseX < x2 + dx && mouseY < y2 + dy)
        {
            activate(processor);
            return true;
        }
        disable(processor);
        return false;
    }

    public void activate(TextInputProcessor processor) {
        active = true;
        renderBlip = true;
        blipTimer = 0.4f;
        processor.setTextReceiver(this);
    }
    public void disable(TextInputProcessor processor) {
        processor.disableTextReceiver(this);
        active = false;
        renderBlip = false;
    }

    @Override
    public void update()
    {
        if (active) {
            blipTimer -= Gdx.graphics.getDeltaTime();
            if (blipTimer < 0) {
                blipTimer = 0.4f;
                renderBlip = !renderBlip;
            }
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        textRenderer.setFont(font).resetScale().renderTextYCentered(sb, label, this.x, this.centerY);
        textRenderer.renderTextYCentered(sb, text, this.textX, this.centerY);

        if (renderBlip) {
            sb.setColor(Color.WHITE);
            sb.draw(pix, blipX, y, BLIP_WIDTH, height);
        }
    }

    public void render(SpriteBatch sb, ShapeRenderer sr, float x, float y) {
        dx = x; //adjustment to hover/click check position
        dy = y;

        textRenderer.setFont(font).resetScale().renderTextYCentered(sb, label, this.x + x, this.centerY + y);
        textRenderer.renderTextYCentered(sb, text, this.textX + x, this.centerY + y);

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