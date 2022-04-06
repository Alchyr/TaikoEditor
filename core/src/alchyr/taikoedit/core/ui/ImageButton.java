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

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class ImageButton implements UIElement {
    private int x, y, width, height, x2, y2;
    float dx, dy;
    private int centerX, centerY;
    private int imgWidth, imgHeight, hoverWidth, hoverHeight;
    private boolean flipX, flipY;

    private Texture image;
    private Texture hoveredImage;
    private String text;
    private BitmapFont font;

    public boolean hovered;

    private Consumer<Integer> onClick = null;

    public String action = "";

    public ImageButton(Texture image, Texture hoverImage)
    {
        this(0, 0, image, hoverImage, "", assetMaster.getFont("default"));
    }
    public ImageButton(int x, int y, Texture image, Texture hoverImage)
    {
        this(x, y, image, hoverImage, "", assetMaster.getFont("default"));
    }
    public ImageButton(int x, int y, Texture image, String text)
    {
        this(x, y, image, image, text, assetMaster.getFont("default"));
    }
    public ImageButton(int x, int y, Texture image, Texture hoveredImage, String text, BitmapFont font)
    {
        this(x, y, image.getWidth(), image.getHeight());
        this.image = image;
        imgWidth = image.getWidth();
        imgHeight = image.getHeight();
        this.hoveredImage = hoveredImage;
        hoverWidth = hoveredImage.getWidth();
        hoverHeight = hoveredImage.getHeight();
        this.text = text;
        this.font = font;
    }
    private ImageButton(int x, int y, int width, int height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        flipX = flipY = false;

        dx = 0;
        dy = 0;

        x2 = x + width;
        y2 = y + height;

        centerX = MathUtils.floor(x + width / 2.0f);
        centerY = MathUtils.floor(y + height / 2.0f);

        hovered = false;
    }

    public ImageButton setClick(Consumer<Integer> onClick) {
        this.onClick = onClick;
        return this;
    }

    public ImageButton setClick(VoidMethod onClick) {
        this.onClick = (i)->onClick.run();
        return this;
    }

    public ImageButton setAction(String action)
    {
        this.action = action;
        return this;
    }

    //Assumed that they are the same size.
    public void setTextures(Texture image, Texture hover) {
        this.image = image;
        this.hoveredImage = hover;
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

    public ImageButton setFlip(boolean x, boolean y) {
        flipX = x;
        flipY = y;
        return this;
    }

    @Override
    public void update(float elapsed)
    {
        hovered = x + dx < Gdx.input.getX() && y + dy < SettingsMaster.gameY() && Gdx.input.getX() < x2 + dx && SettingsMaster.gameY() < y2 + dy;
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        render(sb, sr, Color.WHITE);
    }
    public void render(SpriteBatch sb, ShapeRenderer sr, Color c) {
        sb.setColor(c);
        if (hovered) {
            sb.draw(hoveredImage, x, y, 0, 0, hoverWidth, hoverHeight, 1, 1, 0, 0, 0, hoverWidth, hoverHeight, flipX, flipY);
        }
        else {
            sb.draw(image, x, y, 0, 0, imgWidth, imgHeight, 1, 1, 0, 0, 0, imgWidth, imgHeight, flipX, flipY);
        }
        if (text != null)
        {
            textRenderer.setFont(font).resetScale().renderTextCentered(sb, text, centerX, this.centerY);
        }
    }

    public void render(SpriteBatch sb, ShapeRenderer sr, float x, float y) {
        dx = x; //adjustment to hover/click check position
        dy = y;

        if (hovered) {
            sb.draw(hoveredImage, this.x + x, this.y + y, 0, 0, hoverWidth, hoverHeight, 1, 1, 0, 0, 0, hoverWidth, hoverHeight, flipX, flipY);
        }
        else {
            sb.setColor(Color.WHITE.cpy());
            sb.draw(image, this.x + x, this.y + y, 0, 0, imgWidth, imgHeight, 1, 1, 0, 0, 0, imgWidth, imgHeight, flipX, flipY);
        }
        if (text != null)
        {
            textRenderer.setFont(font).resetScale().renderTextCentered(sb, text, this.centerX + x, this.centerY + y);
        }
    }

    public int getX() {
        return x;
    }
    public int getY() {
        return y;
    }
    public int getWidth() {
        return width;
    }
    public int getHeight() {
        return height;
    }
}