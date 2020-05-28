package alchyr.taikoedit.maps.components.hitobjects;

import alchyr.taikoedit.maps.components.HitObject;
import alchyr.taikoedit.maps.components.ILongObject;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class Spinner extends HitObject implements ILongObject {
    private static final Color spinner = Color.LIGHT_GRAY.cpy();

    private int duration;
    public int endPos;

    public Spinner(int start, int duration)
    {
        this.type = HitType.SPINNER;

        this.pos = start;
        this.duration = duration;
        this.endPos = this.pos + this.duration;
        this.x = 256;
        this.y = 192;
        this.newCombo = true;

        colorSkip = 0;

        normal = false;
        whistle = false;
        finish = false;
        clap = false;

        hitSample = null;
    }

    public Spinner(String[] params)
    {
        type = HitType.SPINNER;
        for (int i = 0; i < params.length; ++i) //to avoid out of bounds.
        {
            switch (i)
            {
                case 0:
                    x = Integer.parseInt(params[i]);
                    break;
                case 1:
                    y = Integer.parseInt(params[i]);
                    break;
                case 2:
                    pos = Integer.parseInt(params[i]);
                    break;
                case 3:
                    int objectType = Integer.parseInt(params[i]);

                    newCombo = (objectType & NEWCOMBO) > 0;
                    colorSkip = (objectType & COLORSKIP) >>> 4;
                    break;
                case 4:
                    int hitSound = Integer.parseInt(params[i]);
                    normal = (hitSound & NORMAL) > 0;
                    whistle = (hitSound & WHISTLE) > 0;
                    finish = (hitSound & FINISH) > 0;
                    clap = (hitSound & CLAP) > 0;
                    break;
                case 5:
                    endPos = Integer.parseInt(params[i]);
                    duration = endPos - pos;
                    break;
                case 6:
                    //hitsamples
                    String[] samples = params[i].split(":");
                    hitSample = new int[samples.length];
                    for (int n = 0; n < samples.length; ++n)
                    {
                        hitSample[n] = Integer.parseInt(samples[n]);
                    }
            }
        }
    }

    @Override
    public void setPosition(int newPos) {
        super.setPosition(newPos);
        endPos = pos + duration;
    }
    @Override
    public int getDuration() {
        return duration;
    }
    @Override
    public int getEndPos() {
        return endPos;
    }
    @Override
    public void setDuration(int duration) {
        this.duration = duration;
        this.endPos = this.pos + this.duration;
    }
    @Override
    public void setEndPos(int endPos) {
        this.endPos = endPos;
        this.duration = this.endPos - this.pos;
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr, int pos, float viewScale, float x, float y, float alpha) {
        spinner.a = alpha;
        float startX = x + (this.pos - pos) * viewScale;
        float endX = x + (this.endPos - pos) * viewScale;
        sb.setColor(spinner);
        if (duration > 0)
        {
            sb.draw(body, startX, y - (CIRCLE_OFFSET * LARGE_SCALE), endX - startX, CIRCLE_SIZE * LARGE_SCALE);
        }
        sb.draw(circle, endX - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                LARGE_SCALE, LARGE_SCALE, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);

        sb.draw(circle, startX - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                LARGE_SCALE, LARGE_SCALE, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);


        if (selected)
        {
            renderSelection(sb, sr, pos, viewScale, x, y);
        }
    }

    @Override
    public void renderSelection(SpriteBatch sb, ShapeRenderer sr, int pos, float viewScale, float x, float y) {
        sb.setColor(Color.WHITE);

        sb.draw(selection, (x + (this.endPos - pos) * viewScale) - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                LARGE_SCALE, LARGE_SCALE, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);
        sb.draw(selection, (x + (this.pos - pos) * viewScale) - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                LARGE_SCALE, LARGE_SCALE, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);
    }

    @Override
    public String toString() {
        return x + "," + y + "," + pos + "," + getTypeFlag() + "," + getHitsoundFlag() + "," + (pos + duration) + "," + getHitSamples();
    }
    public String toString(double beatLength, double sliderMultiplier) {
        return x + "," + y + "," + pos + "," + getTypeFlag() + "," + getHitsoundFlag() + "," + (pos + duration) + "," + getHitSamples();
    }
}
