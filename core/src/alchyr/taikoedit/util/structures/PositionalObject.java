package alchyr.taikoedit.util.structures;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.text.DecimalFormat;

import static alchyr.taikoedit.TaikoEditor.osuDecimalFormat;

public abstract class PositionalObject implements Comparable<PositionalObject> {
    protected static final DecimalFormat limitedDecimals = new DecimalFormat("##0.###########", osuDecimalFormat);

    private double pos = 0;

    public long getPos() {
        return Math.round(pos);
    }
    public double getPrecisePos() {
        return pos;
    }
    public void setPos(long newPos) {
        pos = newPos;
    }
    public void setPos(double newPos) {
        pos = newPos;
    }

    public boolean selected = false;

    //pos - time within song
    //viewScale - just a scaling of object distance
    //x - Where an object with the same time as pos should be
    //y - y
    public abstract void render(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y, float alpha);
    public void render(SpriteBatch sb, ShapeRenderer sr, float pos, float viewScale, float x, float y) {
        render(sb, sr, pos, viewScale, x, y, 1.0f);
    }
    public abstract void renderSelection(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y); //render selection effect.

    @Override
    public int compareTo(PositionalObject o) {
        return Double.compare(pos, o.pos);
    }

    public abstract PositionalObject shiftedCopy(long newPos);

    //Should work linearly.
    public void tempModification(double verticalChange) {
    }
    public double registerChange() {
        return 0;
    }
    public void setValue(double value) {
    }
    public double getValue() {
        return 0;
    }

    public void volumeModification(double verticalChange) {
    }
    public int registerVolumeChange() {
        return 0;
    }
    public void setVolume(int vol) {
    }
    public int getVolume() {
        return 0;
    }
}
