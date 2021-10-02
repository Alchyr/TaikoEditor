package alchyr.taikoedit.editor.maps.components.hitobjects;

import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.util.structures.PositionalObject;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.audioMaster;

public class Hit extends HitObject {
    private static final Color don = Color.RED.cpy();
    private static final Color kat = Color.BLUE.cpy();

    private boolean isRim;

    public Hit(long pos, boolean isRim)
    {
        type = HitObjectType.CIRCLE;
        this.pos = pos;
        this.isRim = isRim;
        x = 0;
        y = 0;

        newCombo = false;
        colorSkip = 0;

        normal = false;
        whistle = false;
        finish = false;
        clap = isRim;

        hitSample = null;
    }
    public Hit(long pos, boolean isRim, boolean isFinish)
    {
        type = HitObjectType.CIRCLE;
        this.pos = pos;
        this.isRim = isRim;
        x = 0;
        y = 0;

        newCombo = false;
        colorSkip = 0;

        normal = false;
        whistle = false;
        finish = isFinish;
        clap = isRim;

        hitSample = null;
    }

    public Hit(Hit base)
    {
        this.type = HitObjectType.CIRCLE;
        this.pos = base.pos;
        this.x = base.x;
        this.y = base.y;
        this.newCombo = base.newCombo;
        this.colorSkip = base.colorSkip;
        this.normal = base.normal;
        this.whistle = base.whistle;
        this.finish = base.finish;
        this.clap = base.clap;
        this.isRim = base.isRim;

        if (base.hitSample != null)
        {
            this.hitSample = new int[base.hitSample.length];
            System.arraycopy(base.hitSample, 0, hitSample, 0, base.hitSample.length);
        }
        else
        {
            this.hitSample = null;
        }
    }

    public Hit(String[] params)
    {
        type = HitObjectType.CIRCLE;
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
                    pos = Long.parseLong(params[i]);
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

                    isRim = whistle || clap;
                    break;
                case 5:
                    //Hit samples
                    String[] samples = params[i].split(":");
                    hitSample = new int[Math.min(samples.length, 4)];
                    for (int n = 0; n < hitSample.length; ++n)
                    {
                        hitSample[n] = Integer.parseInt(samples[n]);
                    }
                    if (samples.length > 4) {
                        sampleFile = samples[5];
                    }
            }
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y, float alpha) {
        Color c = isRim ? kat : don;
        c.a = alpha;
        sb.setColor(c);

        float scale = finish ? LARGE_SCALE : 1.0f;

        sb.draw(circle, x + (float) (this.pos - pos) * viewScale - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                scale, scale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);

        if (selected)
        {
            renderSelection(sb, sr, pos, viewScale, x, y);
        }
    }

    @Override
    public void renderSelection(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y) {
        sb.setColor(Color.WHITE);

        float scale = finish ? LARGE_SCALE : 1.0f;

        sb.draw(selection, x + (float) (this.pos - pos) * viewScale - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                scale, scale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);
    }

    @Override
    public void playSound()
    {
        if (finish)
        {
            if (isRim)
            {
                audioMaster.playSfx("hitsound:kat finish", this.volume, true);
            }
            else
            {
                audioMaster.playSfx("hitsound:don finish", this.volume, true);
            }
        }
        else
        {
            if (isRim)
            {
                audioMaster.playSfx("hitsound:kat", this.volume, true);
            }
            else
            {
                audioMaster.playSfx("hitsound:don", this.volume, true);
            }
        }
    }

    public void setIsRim(boolean isRim) {
        this.isRim = isRim;
        this.clap = isRim;

        if (!isRim)
            whistle = false;
    }
    public boolean isRim()
    {
        return isRim;
    }

    @Override
    public String toString() {
        return x + "," + y + "," + pos + "," + getTypeFlag() + "," + getHitsoundFlag() + "," + getHitSamples();
    }
    public String toString(double beatLength, double sliderMultiplier) {
        return x + "," + y + "," + pos + "," + getTypeFlag() + "," + getHitsoundFlag() + "," + getHitSamples();
    }

    @Override
    public PositionalObject shiftedCopy(long newPos) {
        Hit copy = new Hit(this);
        copy.setPosition(newPos);
        return copy;
    }
}