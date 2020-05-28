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

import static alchyr.taikoedit.TaikoEditor.*;

public class Button implements UIElement {
    private static final float UNDERLINE_THICKNESS = 3;
    private static float UNDERLINE_OFFSET = 5 * SettingsMaster.SCALE;

    private static final float X_BUFFER = 4;

    private float x, y, width, height, x2, y2, dx, dy;
    private int centerX, centerY;
    private float underlineOffset;

    private Texture back;
    private String text;
    private BitmapFont font;

    private boolean hovered;

    private Consumer<Integer> onClick;

    public String action = "";

    public Button(float x, float y, float width, float height, Consumer<Integer> onClick)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        dx = 0;
        dy = 0;

        x2 = x + width;
        y2 = y + height;

        centerX = MathUtils.floor(x + width / 2.0f);
        centerY = MathUtils.floor(y + height / 2.0f);

        hovered = false;

        this.onClick = onClick;
    }

    public Button(float x, float y, float width, float height, Texture image, Consumer<Integer> onClick)
    {
        this(x, y, width, height, image, null, onClick);
    }
    public Button(float x, float y, float width, float height, String text, Consumer<Integer> onClick)
    {
        this(x, y, width, height, null, text, onClick);
    }
    public Button(float x, float y, float width, float height, Texture image, String text, Consumer<Integer> onClick)
    {
        this(x, y, width, height, image, text, assetMaster.getFont("default"), onClick);
    }
    public Button(float x, float y, float width, float height, String text, BitmapFont font, Consumer<Integer> onClick)
    {
        this(x, y, width, height, null, text, font, onClick);
    }
    public Button(float x, float y, float width, float height, Texture image, String text, BitmapFont font, Consumer<Integer> onClick)
    {
        this(x, y, width, height, onClick);
        this.back = image;
        this.text = text;
        this.font = font;
        underlineOffset = UNDERLINE_OFFSET;
    }

    public Button(float centerX, float centerY, String text, BitmapFont font, Consumer<Integer> onClick)
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

        this.back = null;
        this.text = text;
        this.font = font;

        this.onClick = onClick;
    }

    public Button setAction(String action)
    {
        this.action = action;
        return this;
    }

    public boolean click(int mouseX, int mouseY, int key)
    {
        if (x + dx < mouseX && y + dy < mouseY && mouseX < x2 + dx && mouseY < y2 + dy)
        {
            hovered = true;

            if (onClick != null)
                onClick.accept(key);
            return true;
        }
        return false;
    }

    @Override
    public void update()
    {
        hovered = x + dx < Gdx.input.getX() && y + dy < SettingsMaster.getHeight() - Gdx.input.getY() && Gdx.input.getX() < x2 + dx && SettingsMaster.getHeight() - Gdx.input.getY() < y2 + dy;
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        if (back != null)
        {
            sb.setColor(Color.WHITE.cpy());
            sb.draw(back, x, y);
        }
        if (text != null)
        {
            if (hovered)
            {
                //textRenderer.setFont(font).resetScale().renderTextCentered(sb, text, centerX + 1 * SettingsMaster.SCALE, centerY + 1 * SettingsMaster.SCALE);

                sb.end();

                sr.begin(ShapeRenderer.ShapeType.Filled);

                sr.setColor(Color.WHITE);
                sr.rect(x, y - underlineOffset, width, UNDERLINE_THICKNESS);

                sr.end();

                sb.begin();
            }

            textRenderer.setFont(font).resetScale().renderTextCentered(sb, text, this.centerX, this.centerY);
        }
    }

    public void render(SpriteBatch sb, ShapeRenderer sr, int x, int y) {
        dx = x; //adjustment to hover/click check position
        dy = y;

        if (back != null)
        {
            sb.setColor(Color.WHITE.cpy());
            sb.draw(back, this.x + x, this.y + y);
        }
        if (text != null)
        {
            if (hovered)
            {
                //textRenderer.setFont(font).resetScale().renderTextCentered(sb, text, centerX + 1 * SettingsMaster.SCALE, centerY + 1 * SettingsMaster.SCALE);

                sb.end();

                sr.begin(ShapeRenderer.ShapeType.Filled);

                sr.setColor(Color.WHITE);
                sr.rect(this.x + x, this.y + y - underlineOffset, width, UNDERLINE_THICKNESS);

                sr.end();

                sb.begin();
            }

            textRenderer.setFont(font).resetScale().renderTextCentered(sb, text, this.centerX + x, this.centerY + y);
        }
    }
}
