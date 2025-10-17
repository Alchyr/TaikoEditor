package alchyr.taikoedit.editor.maps.components.hitobjects;

import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.structures.MapObject;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.audioMaster;
import static alchyr.taikoedit.management.assets.skins.Skins.currentSkin;

public class Hit extends HitObject {
    public static final Color don = new Color(245 / 255.0f, 55 / 255.0f, 40 / 255.0f, 1.0f);
    public static final Color kat = new Color(60 / 255.0f, 118 / 255.0f, 231 / 255.0f, 1.0f);

    private boolean isRim;

    public Hit(long pos, boolean isRim)
    {
        type = HitObjectType.CIRCLE;
        setPos(pos);
        this.isRim = isRim;
        x = isRim ? SettingsMaster.katX : SettingsMaster.donX;
        y = isRim ? SettingsMaster.katY : SettingsMaster.donY;

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
        setPos(pos);
        this.isRim = isRim;

        newCombo = false;
        colorSkip = 0;

        normal = false;
        whistle = false;
        finish = isFinish;
        clap = isRim;

        hitSample = null;

        updatePosition();
    }

    public Hit(Hit base)
    {
        this.type = HitObjectType.CIRCLE;
        setPos(base.getPrecisePos());
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
                    setPos(Double.parseDouble(params[i]));
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
                        StringBuilder sb = new StringBuilder();
                        for (int s = 4; s < samples.length; ++s) {
                            sb.append(samples[s]);
                        }
                        sampleFile = sb.toString();
                    }
            }
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y, float alpha) {
        if (testHidden()) return;

        Color c = isRim ? kat : don;
        c.a = alpha;
        sb.setColor(c);

        if (finish) {
            currentSkin.finisher.renderC(sb, sr, 1 + x + (float) (this.getPos() - pos) * viewScale, y, currentSkin.largeScale, c);
        }
        else {
            currentSkin.hit.renderC(sb, sr, 1 + x + (float) (this.getPos() - pos) * viewScale, y, currentSkin.normalScale, c);
        }
        /*sb.draw(circle, x + (float) (this.getPos() - pos) * viewScale - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                scale, scale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);*/

        if (showNc && newCombo) {
            sb.setColor(Color.WHITE);
            sb.draw(circle, -10 + x + (float) (this.getPos() - pos) * viewScale, y - 10, 20, 20);
        }

        if (selected)
        {
            renderSelection(sb, sr, pos, viewScale, x, y);
        }
        c.a = 1;
    }

    public void gameplayRender(SpriteBatch sb, ShapeRenderer sr, float viewScale, float baseX, float x, int y, float alpha) {
        long pos = getPos();
        x += baseX;

        Color c = isRim ? kat : don;
        c.a = alpha;

        if (finish) {
            currentSkin.finisher.renderC(sb, sr, 1 + x + (float) (this.getPos() - pos) * viewScale, y, currentSkin.largeScale, c);
        }
        else {
            currentSkin.hit.renderC(sb, sr, 1 + x + (float) (this.getPos() - pos) * viewScale, y, currentSkin.normalScale, c);
        }

        if (selected)
        {
            renderSelection(sb, sr, pos, viewScale, x, y);
        }
        c.a = 1;
    }
    public void grayRender(SpriteBatch sb, ShapeRenderer sr, float viewScale, float baseX, float x, int y, float alpha) {
        long pos = getPos();
        x += baseX;

        Color c = isRim ? kat.cpy().mul(disabled) : don.cpy().mul(disabled);
        c.a = alpha * 0.75f;

        if (finish) {
            currentSkin.finisher.renderC(sb, sr, 1 + x + (float) (this.getPos() - pos) * viewScale, y, currentSkin.largeScale, c);
        }
        else {
            currentSkin.hit.renderC(sb, sr, 1 + x + (float) (this.getPos() - pos) * viewScale, y, currentSkin.normalScale, c);
        }

        if (selected)
        {
            renderSelection(sb, sr, pos, viewScale, x, y);
        }
        c.a = 1;
    }

    @Override
    public void renderSelection(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y) {
        if (testHidden()) return;

        float scale = finish ? currentSkin.largeScale : currentSkin.normalScale;

        currentSkin.selection.renderC(sb, sr, 1 + x + (float) (this.getPos() - pos) * viewScale, y, scale);
    }

    @Override
    public void renderSelectionColored(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y, Color c) {
        if (testHidden()) return;

        float scale = finish ? currentSkin.largeScale : currentSkin.normalScale;

        currentSkin.selection.renderC(sb, sr, 1 + x + (float) (this.getPos() - pos) * viewScale, y, scale, c);
    }

    @Override
    public void playSound()
    {
        if (finish)
        {
            if (isRim)
            {
                audioMaster.playSfx(currentSkin.sfxKatFinish, this.volume, true);
            }
            else
            {
                audioMaster.playSfx(currentSkin.sfxDonFinish, this.volume, true);
            }
        }
        else
        {
            if (isRim)
            {
                audioMaster.playSfx(currentSkin.sfxKat, this.volume, true);
            }
            else
            {
                audioMaster.playSfx(currentSkin.sfxDon, this.volume, true);
            }
        }
    }

    public void updatePosition() {
        if (finish) {
            x = isRim ? SettingsMaster.bigKatX : SettingsMaster.bigDonX;
            y = isRim ? SettingsMaster.bigKatY : SettingsMaster.bigDonY;
        }
        else {
            x = isRim ? SettingsMaster.katX : SettingsMaster.donX;
            y = isRim ? SettingsMaster.katY : SettingsMaster.donY;
        }
    }
    public void setIsRim(boolean isRim) {
        this.isRim = isRim;
        this.clap = isRim;

        if (!isRim)
            whistle = false;

        updatePosition();
    }
    public boolean isRim()
    {
        return isRim;
    }

    @Override
    public void setIsFinish(boolean finish) {
        super.setIsFinish(finish);

        updatePosition();
    }

    @Override
    public String toString() {
        return x + "," + y + "," + limitedDecimals.format(getPrecisePos()) + "," + getTypeFlag() + "," + getHitsoundFlag() + "," + getHitSamples();
    }
    public String toString(double beatLength, double sliderMultiplier) {
        return x + "," + y + "," + limitedDecimals.format(getPrecisePos()) + "," + getTypeFlag() + "," + getHitsoundFlag() + "," + getHitSamples();
    }

    @Override
    public MapObject shiftedCopy(long newPos) {
        Hit copy = new Hit(this);
        copy.setPos(newPos);
        return copy;
    }
}
