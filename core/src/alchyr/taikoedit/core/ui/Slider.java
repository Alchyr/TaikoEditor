package alchyr.taikoedit.core.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class Slider {
    private final float drawX, centerX, y, width;

    private final int min, max;

    private float value;

    private Texture pix;

    public Slider(float centerX, float y, float width, int min, int max)
    {
        this.centerX = centerX;
        this.y = y;
        this.width = width;
        this.drawX = centerX - width / 2;
        this.max = max;
        this.min = min;

        this.value = (max + min) / 2.0f;

        pix = assetMaster.get("ui:pixel");
    }

    public void setValue(float val)
    {
        if (val < min)
            val = min;
        if (val > max)
            val = max;
        this.value = val;
    }

    public void update()
    {

    }

    public void render(SpriteBatch sb, ShapeRenderer sr)
    {
        sb.setColor(Color.WHITE);
        sb.draw(pix, drawX, y, width, 3);


    }
}
