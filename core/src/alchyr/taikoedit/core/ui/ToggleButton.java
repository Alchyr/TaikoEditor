package alchyr.taikoedit.core.ui;

import alchyr.taikoedit.core.UIElement;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.util.function.Consumer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class ToggleButton implements UIElement {
    private static final float TOGGLE_SCALE = 0.5f;
    private static final int TOGGLE_SIZE = 60;
    private static final int EXTRA_SPACING = 4;

    private static final float BUFFER = 4;

    private float x, y, toggleY, width, height, x2, y2, dx, dy;
    private int centerY;
    private final int textCenter; //Offset from x

    private final Texture off = assetMaster.get("ui:toggleoff");
    private final Texture on = assetMaster.get("ui:toggleon");
    private final Texture enable = assetMaster.get("ui:togglehover");
    private final Texture disable = assetMaster.get("ui:toggledisable");

    private String text;
    private BitmapFont font;

    public boolean enabled;
    private boolean hovered;

    public String action = "";
    private Consumer<Boolean> onToggle = null;

    public ToggleButton(float leftX, float centerY, String text, BitmapFont font, boolean defaultValue)
    {
        float toggleSize = (TOGGLE_SIZE * SettingsMaster.SCALE * TOGGLE_SCALE);
        width = toggleSize + EXTRA_SPACING + textRenderer.setFont(font).getWidth(text);
        height = Math.max(textRenderer.getHeight(text) + BUFFER, toggleSize + BUFFER);

        x = leftX;
        textCenter = MathUtils.floor((TOGGLE_SIZE * SettingsMaster.SCALE * TOGGLE_SCALE) + EXTRA_SPACING + textRenderer.setFont(font).getWidth(text) * 0.5f);
        y = centerY - height / 2.0f;
        toggleY = centerY - toggleSize / 2.0f;
        this.centerY = MathUtils.floor(centerY);
        x2 = x + width;
        y2 = y + height;

        dx = 0;
        dy = 0;

        this.hovered = false;

        this.text = text;
        this.font = font;

        this.enabled = defaultValue;
    }

    public ToggleButton setAction(String action)
    {
        this.action = action;
        return this;
    }

    public ToggleButton setOnToggle(Consumer<Boolean> onToggle) {
        this.onToggle = onToggle;
        return this;
    }

    @Override
    public void move(float dx, float dy) {
        x += dx;
        x2 += dx;

        y += dy;
        y2 += dy;
        toggleY += dy;
        this.centerY = MathUtils.floor((y + y2) / 2.0f);
    }

    public boolean contains(float mouseX, float mouseY) {
        return x + dx < mouseX && y + dy < mouseY && mouseX < x2 + dx && mouseY < y2 + dy;
    }

    public boolean click(float mouseX, float mouseY, int button)
    {
        if (x + dx < mouseX && y + dy < mouseY && mouseX < x2 + dx && mouseY < y2 + dy)
        {
            hovered = true;

            enabled = !enabled;
            if (onToggle != null)
                onToggle.accept(enabled);
            return true;
        }
        return false;
    }

    public void setState(boolean enabled) {
        if (enabled != this.enabled) {
            this.enabled = enabled;
            if (onToggle != null)
                onToggle.accept(enabled);
        }
    }

    @Override
    public void update(float elapsed)
    {
        hovered = x + dx < Gdx.input.getX() && y + dy < SettingsMaster.gameY() && Gdx.input.getX() < x2 + dx && SettingsMaster.gameY() < y2 + dy;
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(Color.WHITE);
        if (hovered) {
            if (enabled) {
                sb.draw(disable, this.x, this.toggleY, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, SettingsMaster.SCALE * TOGGLE_SCALE, SettingsMaster.SCALE * TOGGLE_SCALE, 0, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, false, false);
            }
            else {
                sb.draw(enable, this.x, this.toggleY, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, SettingsMaster.SCALE * TOGGLE_SCALE, SettingsMaster.SCALE * TOGGLE_SCALE, 0, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, false, false);
            }
        }
        else if (enabled) {
            sb.draw(on, this.x, this.toggleY, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, SettingsMaster.SCALE * TOGGLE_SCALE, SettingsMaster.SCALE * TOGGLE_SCALE, 0, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, false, false);
        }
        else {
            sb.draw(off, this.x, this.toggleY, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, SettingsMaster.SCALE * TOGGLE_SCALE, SettingsMaster.SCALE * TOGGLE_SCALE, 0, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, false, false);
        }
        if (text != null)
        {
            textRenderer.setFont(font).resetScale().renderTextCentered(sb, text, this.x + this.textCenter, this.centerY);
        }
    }

    public void render(SpriteBatch sb, ShapeRenderer sr, float x, float y) {
        dx = x; //adjustment to hover/click check position
        dy = y;

        sb.setColor(Color.WHITE);
        if (hovered) {
            if (enabled) {
                sb.draw(disable, this.x + x, this.toggleY + y, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, SettingsMaster.SCALE * TOGGLE_SCALE, SettingsMaster.SCALE * TOGGLE_SCALE, 0, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, false, false);
            }
            else {
                sb.draw(enable, this.x + x, this.toggleY + y, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, SettingsMaster.SCALE * TOGGLE_SCALE, SettingsMaster.SCALE * TOGGLE_SCALE, 0, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, false, false);
            }
        }
        else if (enabled) {
            sb.draw(on, this.x + x, this.toggleY + y, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, SettingsMaster.SCALE * TOGGLE_SCALE, SettingsMaster.SCALE * TOGGLE_SCALE, 0, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, false, false);
        }
        else {
            sb.draw(off, this.x + x, this.toggleY + y, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, SettingsMaster.SCALE * TOGGLE_SCALE, SettingsMaster.SCALE * TOGGLE_SCALE, 0, 0, 0, TOGGLE_SIZE, TOGGLE_SIZE, false, false);
        }
        if (text != null)
        {
            textRenderer.setFont(font).resetScale().renderTextCentered(sb, text, this.x + this.textCenter + x, this.centerY + y);
        }
    }

    public float startX()
    {
        return x + dx;
    }
    public float endX()
    {
        return x2 + dx;
    }


    /*public void setText(String newText)
    {
        setText(newText, false);
    }

    public void setText(String newText, boolean resize)
    {
        this.text = newText;
        if (resize)
        {
            width = textRenderer.setFont(font).getWidth(text);
            height = textRenderer.getHeight(text);

            x = centerX - width / 2.0f;
            y = centerY - height / 2.0f;
            x2 = x + width;
            y2 = y + height;
        }
    }*/
}
