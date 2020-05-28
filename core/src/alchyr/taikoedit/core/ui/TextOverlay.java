package alchyr.taikoedit.core.ui;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class TextOverlay {
    private final Color textColor = new Color(1.0f, 1.0f, 1.0f, 0.0f);
    private final Color backColor = new Color(0.2f, 0.2f, 0.2f, 0.0f);
    private float fadeDelay = 0.0f;

    private final float cX, cY, bY;
    private final int height;

    private final Texture pix = assetMaster.get("ui:pixel");
    private final BitmapFont font;

    private String text = "";

    public TextOverlay(BitmapFont font, int centerY, int thickness)
    {
        this.font = font;
        cX = SettingsMaster.getMiddle();
        cY = centerY;
        bY = centerY - thickness / 2.0f;

        this.height = thickness;
    }

    public void setText(String text, float fadeDelay)
    {
        this.text = text;
        this.fadeDelay = fadeDelay;
        textColor.a = 1;
    }

    public void update(float elapsed)
    {
        if (fadeDelay > elapsed)
            fadeDelay -= elapsed;
        else
        {
            fadeDelay = 0;
            textColor.a -= elapsed;
            if (textColor.a < 0)
                textColor.a = 0;
        }
    }

    public void render(SpriteBatch sb, ShapeRenderer sr)
    {
        backColor.a = textColor.a * 0.7f;
        sb.setColor(backColor);
        sb.draw(pix, 0, bY, SettingsMaster.getWidth(), height);

        TaikoEditor.textRenderer.setFont(font).renderTextCentered(sb, text, cX, cY, textColor);
    }
}
