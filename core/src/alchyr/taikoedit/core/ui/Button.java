package alchyr.taikoedit.core.ui;

import alchyr.taikoedit.core.UIElement;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.interfaces.functional.VoidMethod;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.util.function.Consumer;

import static alchyr.taikoedit.TaikoEditor.*;

public class Button implements UIElement {
    private static final float BORDER_THICKNESS = Math.max(2, 3 * SettingsMaster.SCALE);
    private static final float UNDERLINE_OFFSET = Math.max(2, 5 * SettingsMaster.SCALE);

    private static final float X_BUFFER = 4;

    private float x, y, width, height, x2, y2, dx, dy;
    private int centerX, centerY;
    private float underlineOffset;

    private final Texture pixel = assetMaster.get("ui:pixel");

    private Texture back;
    private String text;
    private BitmapFont font;

    private boolean hovered;

    private Consumer<Integer> onClick = null;

    public String action = "";

    private boolean useBorderRendering, fullBorderHover;
    public boolean renderBorder;

    public Button(float x, float y, float width, float height, Texture image)
    {
        this(x, y, width, height, image, null);
    }
    public Button(float x, float y, float width, float height, String text)
    {
        this(x, y, width, height, null, text);
    }
    public Button(float x, float y, float width, float height, Texture image, String text)
    {
        this(x, y, width, height, image, text, assetMaster.getFont("default"));
    }
    public Button(float x, float y, float width, float height, String text, BitmapFont font)
    {
        this(x, y, width, height, null, text, font);
    }
    public Button(float x, float y, float width, float height, Texture image, String text, BitmapFont font)
    {
        this(x, y, width, height);
        this.back = image;
        this.text = text;
        this.font = font;
    }

    public Button(float x, float y, float width, float height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        underlineOffset = UNDERLINE_OFFSET;

        dx = 0;
        dy = 0;

        x2 = x + width;
        y2 = y + height;

        centerX = MathUtils.floor(x + width / 2.0f);
        centerY = MathUtils.floor(y + height / 2.0f);

        hovered = false;
        renderBorder = false;
        useBorderRendering = false;
        fullBorderHover = false;
    }

    public Button(float centerX, float centerY, String text, BitmapFont font)
    {
        width = textRenderer.setFont(font).getWidth(text);
        height = textRenderer.getHeight(text);
        underlineOffset = UNDERLINE_OFFSET;

        x = centerX - width / 2.0f;
        y = centerY - height / 2.0f;
        x2 = x + width;
        y2 = y + height;

        dx = 0;
        dy = 0;

        this.centerX = MathUtils.floor(centerX);
        this.centerY = MathUtils.floor(centerY);

        this.hovered = false;
        this.renderBorder = false;
        useBorderRendering = false;
        fullBorderHover = false;

        this.back = null;
        this.text = text;
        this.font = font;
    }

    public Button setClick(Consumer<Integer> onClick) {
        this.onClick = onClick;
        return this;
    }

    public Button setClick(VoidMethod onClick) {
        this.onClick = (i)->onClick.run();
        return this;
    }

    public Button setAction(String action)
    {
        this.action = action;
        return this;
    }

    public Button useBorderRendering()
    {
        this.useBorderRendering = true;
        return this;
    }
    public Button useBorderRendering(boolean fullBorderHover)
    {
        this.useBorderRendering = true;
        this.fullBorderHover = fullBorderHover;
        return this;
    }

    @Override
    public void move(float dx, float dy) {
        x += dx;
        x2 += dx;
        centerX += dx;

        y += dy;
        y2 += dy;
        centerY += dy;
    }

    public boolean contains(int mouseX, int mouseY) {
        return x + dx < mouseX && y + dy < mouseY && mouseX < x2 + dx && mouseY < y2 + dy;
    }
    public void effect(int button) {
        if (onClick != null)
            onClick.accept(button);
    }
    public boolean click(int gameX, int gameY, int button) {
        return click((float)gameX, (float)gameY, button);
    }
    public boolean click(float gameX, float gameY, int button)
    {
        if (x + dx < gameX && y + dy < gameY && gameX < x2 + dx && gameY < y2 + dy)
        {
            hovered = true;

            if (onClick != null)
                onClick.accept(button);
            return true;
        }
        return false;
    }

    @Override
    public void update(float elapsed)
    {
        hovered = x + dx < Gdx.input.getX() && y + dy < SettingsMaster.gameY() && Gdx.input.getX() < x2 + dx && SettingsMaster.gameY() < y2 + dy;
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        if (back != null)
        {
            sb.setColor(Color.WHITE);
            sb.draw(back, x, y);
        }
        if (text != null)
        {
            textRenderer.setFont(font).resetScale().renderTextCentered(sb, text, this.centerX, this.centerY);
        }
        sb.setColor(Color.WHITE);
        if (useBorderRendering)
        {
            if (hovered || renderBorder)
            {
                sb.draw(pixel, this.x, this.y, width, BORDER_THICKNESS);

                if (renderBorder || (hovered && fullBorderHover))
                {
                    sb.draw(pixel, this.x, this.y, BORDER_THICKNESS, height);
                    sb.draw(pixel, this.x, this.y + height - BORDER_THICKNESS, width, BORDER_THICKNESS);
                    sb.draw(pixel, this.x + width - BORDER_THICKNESS, this.y, BORDER_THICKNESS, height);
                }
            }
        }
        else
        {
            if (hovered)
            {
                sb.draw(pixel, this.x, this.y - underlineOffset, width, BORDER_THICKNESS);
            }
        }
    }

    public void render(SpriteBatch sb, ShapeRenderer sr, float x, float y) {
        dx = x; //adjustment to hover/click check position
        dy = y;

        if (back != null)
        {
            sb.setColor(Color.WHITE);
            sb.draw(back, this.x + x, this.y + y);
        }
        if (text != null)
        {
            textRenderer.setFont(font).resetScale().renderTextCentered(sb, text, this.centerX + x, this.centerY + y);
        }
        sb.setColor(Color.WHITE);
        if (useBorderRendering)
        {
            if (hovered || renderBorder)
            {
                sb.draw(pixel, this.x + x, this.y + y, width, BORDER_THICKNESS);

                if (renderBorder || (hovered && fullBorderHover))
                {
                    sb.draw(pixel, this.x + x, this.y + y, BORDER_THICKNESS, height);
                    sb.draw(pixel, this.x + x, this.y + y + height - BORDER_THICKNESS, width, BORDER_THICKNESS);
                    sb.draw(pixel, this.x + x + width - BORDER_THICKNESS, this.y + y, BORDER_THICKNESS, height);
                }
            }
        }
        else
        {
            if (hovered)
            {
                sb.draw(pixel, this.x + x, this.y + y - underlineOffset, width, BORDER_THICKNESS);
            }
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

    public void setText(String newText)
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
    }
}
