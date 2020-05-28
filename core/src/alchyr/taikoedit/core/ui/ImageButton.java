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

public class ImageButton implements UIElement {
    private int x, y, width, height, x2, y2, dx, dy;
    private int centerX, centerY;

    private Texture image;
    private Texture hoveredImage;
    private String text;
    private BitmapFont font;

    private boolean hovered;

    private Consumer<Integer> onClick;

    public String action = "";

    public ImageButton(Texture image, Texture hoverImage, Consumer<Integer> onClick)
    {
        this(0, 0, image, hoverImage, "", assetMaster.getFont("default"), onClick);
    }
    public ImageButton(int x, int y, Texture image, String text, Consumer<Integer> onClick)
    {
        this(x, y, image, image, text, assetMaster.getFont("default"), onClick);
    }
    public ImageButton(int x, int y, Texture image, Texture hoveredImage, String text, BitmapFont font, Consumer<Integer> onClick)
    {
        this(x, y, image.getWidth(), image.getHeight(), onClick);
        this.image = image;
        this.hoveredImage = hoveredImage;
        this.text = text;
        this.font = font;
    }
    private ImageButton(int x, int y, int width, int height, Consumer<Integer> onClick)
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

    public ImageButton setAction(String action)
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
        if (hovered) {
            sb.draw(hoveredImage, x, y);
        }
        else {
            sb.setColor(Color.WHITE.cpy());
            sb.draw(image, x, y);
        }
        if (text != null)
        {
            textRenderer.setFont(font).resetScale().renderTextCentered(sb, text, centerX, y);
        }
    }

    public void render(SpriteBatch sb, ShapeRenderer sr, int x, int y) {
        dx = x; //adjustment to hover/click check position
        dy = y;

        if (hovered) {
            sb.draw(hoveredImage, this.x + x, this.y + y);
        }
        else {
            sb.setColor(Color.WHITE.cpy());
            sb.draw(image, this.x + x, this.y + y);
        }
        if (text != null)
        {
            textRenderer.setFont(font).resetScale().renderTextCentered(sb, text, this.centerX + x, this.centerY + y);
        }
    }

    public int getWidth() {
        return width;
    }
}