package alchyr.taikoedit.core.ui;

import alchyr.taikoedit.core.UIElement;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class Label implements UIElement {
    private float x, y;
    public float getX() {
        return x;
    }
    public float getY() {
        return y;
    }

    private String label;
    private final BitmapFont font;

    private final LabelAlign align;

    public enum LabelAlign {
        LEFT_CENTER,
        CENTER
    }

    public Label(float x, float y, BitmapFont font, String text)
    {
        this(x, y, font, text, LabelAlign.LEFT_CENTER);
    }
    public Label(float x, float y, BitmapFont font, String text, LabelAlign alignment) {
        this.font = font;
        this.label = text;

        this.x = x;
        this.y = y;

        align = alignment;
    }

    public float getWidth() {
        return textRenderer.setFont(font).getWidth(label);
    }

    public void setText(String s) {
        this.label = s;
    }

    @Override
    public void move(float dx, float dy) {
        x += dx;
        y += dy;
    }

    @Override
    public void update(float elapsed)
    {
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        switch (align) {
            case LEFT_CENTER:
                textRenderer.setFont(font).resetScale().renderTextYCentered(sb, label, this.x, this.y);
                break;
            case CENTER:
                textRenderer.setFont(font).resetScale().renderTextCentered(sb, label, this.x, this.y);
                break;
        }
    }

    public void render(SpriteBatch sb, ShapeRenderer sr, float x, float y) {
        switch (align) {
            case LEFT_CENTER:
                textRenderer.setFont(font).resetScale().renderTextYCentered(sb, label, this.x + x, this.y + y);
                break;
            case CENTER:
                textRenderer.setFont(font).resetScale().renderTextCentered(sb, label, this.x + x, this.y + y);
                break;
        }
    }
}