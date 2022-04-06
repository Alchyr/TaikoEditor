package alchyr.taikoedit.editor.maps.components.hitobjects;

import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.ILongObject;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.structures.PositionalObject;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.management.assets.skins.Skins.currentSkin;

public class Spinner extends HitObject implements ILongObject {
    private static final Color spinner = Color.LIGHT_GRAY.cpy();

    private long duration;
    private long endPos;

    public Spinner(int start, int duration)
    {
        this.type = HitObjectType.SPINNER;

        setPos(start);
        this.duration = duration;
        this.endPos = getPos() + this.duration;
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
        type = HitObjectType.SPINNER;
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
                    break;
                case 5:
                    endPos = Long.parseLong(params[i]);
                    duration = endPos - getPos();
                    break;
                case 6:
                    //hitsamples
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

    public Spinner(Spinner base) {
        this.type = HitObjectType.SPINNER;
        setPos(base.getPrecisePos());
        this.x = base.x;
        this.y = base.y;
        this.newCombo = base.newCombo;
        this.colorSkip = base.colorSkip;
        this.normal = base.normal;
        this.whistle = base.whistle;
        this.finish = base.finish;
        this.clap = base.clap;

        this.duration = base.duration;
        this.endPos = base.endPos;

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

    @Override
    public void setPos(long newPos) {
        super.setPos(newPos);
        endPos = getPos() + duration;
    }
    @Override
    public long getDuration() {
        return duration;
    }
    @Override
    public long getEndPos() {
        return endPos;
    }
    @Override
    public void setDuration(long duration) {
        this.duration = duration;
        this.endPos = this.getPos() + this.duration;
    }
    @Override
    public void setEndPos(long endPos) {
        this.endPos = endPos;
        this.duration = this.endPos - this.getPos();
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y, float alpha) {
        spinner.a = alpha;
        float startX = 1 + x + (float) (this.getPos() - pos) * viewScale;
        float endX = 1 + x + (float) (this.endPos - pos) * viewScale;
        sb.setColor(spinner);
        spinner.a = 1;
        if (duration > 0)
        {
            float bodyStart = Math.max(0, startX), bodyEnd = Math.min(SettingsMaster.getWidth(), endX);
            currentSkin.body.renderC(sb, sr, (bodyStart + bodyEnd) / 2f, y, (bodyEnd - bodyStart) / currentSkin.body.getWidth(), currentSkin.largeScale, 0, spinner);
            //sb.draw(body, startX, y - (CIRCLE_OFFSET * currentSkin.largeScale), endX - startX, CIRCLE_SIZE * currentSkin.largeScale);
            currentSkin.end.renderC(sb, sr, endX, y, currentSkin.largeScale, spinner);
            /*sb.draw(circle, endX - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                    currentSkin.largeScale, currentSkin.largeScale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);*/
        }

        currentSkin.hit.renderC(sb, sr, startX, y, currentSkin.largeScale, spinner);
        /*sb.draw(circle, startX - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                currentSkin.largeScale, currentSkin.largeScale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);*/


        if (selected)
        {
            renderSelection(sb, sr, pos, viewScale, x, y);
        }
    }

    @Override
    public void gameplayRender(SpriteBatch sb, ShapeRenderer sr, float sv, float baseX, float x, int y, float alpha) {
        currentSkin.gameplaySpinnerColor.a = alpha;
        sb.setColor(currentSkin.gameplaySpinnerColor);

        currentSkin.gameplaySpinner.renderC(sb, sr, 1 + baseX + Math.max(x, 0), y, currentSkin.gameplaySpinnerScale, spinner);
        /*sb.draw(circle, baseX + Math.max(x, 0) - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                currentSkin.largeScale, currentSkin.largeScale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);*/
    }

    @Override
    public void renderSelection(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y) {
        sb.setColor(Color.WHITE);

        if (duration > 0)
        {
            currentSkin.selection.renderC(sb, sr, 1 + x + (float) (this.endPos - pos) * viewScale, y, currentSkin.largeScale);
            /*sb.draw(currentSkin.selection, (x + (float) (this.endPos - pos) * viewScale) - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                    currentSkin.largeScale, currentSkin.largeScale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);*/
        }
        currentSkin.selection.renderC(sb, sr, 1 + x + (float) (this.getPos() - pos) * viewScale, y, currentSkin.largeScale);
        /*sb.draw(currentSkin.selection, (x + (float) (this.getPos() - pos) * viewScale) - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                currentSkin.largeScale, currentSkin.largeScale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);*/
    }

    @Override
    public String toString() {
        return x + "," + y + "," + limitedDecimals.format(getPrecisePos()) + "," + getTypeFlag() + "," + getHitsoundFlag() + "," + (getPos() + duration) + "," + getHitSamples();
    }
    public String toString(double beatLength, double sliderMultiplier) {
        return x + "," + y + "," + limitedDecimals.format(getPrecisePos()) + "," + getTypeFlag() + "," + getHitsoundFlag() + "," + (getPos() + duration) + "," + getHitSamples();
    }

    @Override
    public PositionalObject shiftedCopy(long newPos) {
        Spinner copy = new Spinner(this);
        copy.setPos(newPos);
        return copy;
    }
}
