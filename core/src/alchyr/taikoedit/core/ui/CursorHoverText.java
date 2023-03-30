package alchyr.taikoedit.core.ui;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class CursorHoverText {
    private final int HORIZONAL_BUFFER = 8;

    private final int TEXT_OFFSET = 14;

    private final Color textColor = new Color(1.0f, 1.0f, 1.0f, 0.0f);
    private final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.0f);

    private Texture pix;
    private BitmapFont font = null;

    private String text = "";
    private float fadeDelay = 0.0f;

    private int centerY;
    private int height;
    private int width;

    public CursorHoverText()
    {
        width = 1;
        height = 1;
    }

    public void initialize(BitmapFont font, int height) {
        this.font = font;
        pix = assetMaster.get("ui:pixel");
        centerY = SettingsMaster.getHeight() / 2;
        this.height = height;
    }

    public void setText(String text)
    {
        this.text = text;
        this.fadeDelay = 0.1f;
        textColor.a = 1;

        width = (int) textRenderer.setFont(font).getWidth(text) + HORIZONAL_BUFFER;
    }

    public void update(float elapsed)
    {
        if (fadeDelay > elapsed)
            fadeDelay -= elapsed;
        else
        {
            fadeDelay = 0;
            textColor.a = 0;
        }
    }

    public void render(SpriteBatch sb, ShapeRenderer sr)
    {
        if (font != null && textColor.a > 0) {
            float x = Gdx.input.getX() + (Gdx.input.getX() > SettingsMaster.getMiddleX() ? -(width + TEXT_OFFSET) : TEXT_OFFSET);
            float y = SettingsMaster.screenToGameY(Gdx.input.getY());
            y += (y > centerY ? -TEXT_OFFSET : TEXT_OFFSET);
            backColor.a = textColor.a;
            sb.setColor(backColor);
            sb.draw(pix, x, y - (height / 2.0f), width, height);
            TaikoEditor.textRenderer.setFont(font).renderTextYCentered(sb, textColor, text, x + (HORIZONAL_BUFFER / 2.0f), y);
        }
    }
}
