package alchyr.taikoedit.editor.maps.components;

import alchyr.taikoedit.editor.views.EffectView;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.structures.MapObject;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.text.DecimalFormat;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.osuDecimalFormat;

public class TimingPoint extends MapObject {
    public static final Color RED = Color.RED.cpy();
    public static final Color GREEN = new Color(0.25f, 0.75f, 0.0f, 1.0f);
    public static final Color YELLOW = new Color(0.8f, 0.8f, 0.0f, 1.0f);
    private static final Color OMIT_MARK = Color.LIGHT_GRAY.cpy();

    private static final Color selection = new Color(1.0f, 0.6f, 0.0f, 1.0f);

    private static final int OMIT_MARK_SIZE = 2 * (int) (6 * SettingsMaster.SCALE) + 1;
    private static final float OMIT_MARK_OFFSET = OMIT_MARK_SIZE / 2f;

    public static final double MIN_SV = 0.01;

    //time,beatLength,meter,sampleSet,sampleIndex,volume,uninherited,effects
    /** Data from osu! wiki on File Format.
     * time (Integer): Start time of the timing section, in milliseconds from the beginning of the beatmap's audio. The end of the timing section is the next timing point's time (or never, if this is the last timing point).
     * beatLength (Decimal): This property has two meanings:
     * For uninherited timing points, the duration of a beat, in milliseconds.
     * For inherited timing points, a negative inverse slider velocity multiplier, as a percentage. For example, -50 would make all sliders in this timing section twice as fast as SliderMultiplier.
     * meter (Integer): Amount of beats in a measure. Inherited timing points ignore this property.
     * sampleSet (Integer): Default sample set for hit objects (0 = beatmap default, 1 = normal, 2 = soft, 3 = drum).
     * sampleIndex (Integer): Custom sample index for hit objects. 0 indicates osu!'s default hitsounds.
     * volume (Integer): Volume percentage for hit objects.
     * uninherited (0 or 1): Whether the timing point is uninherited (red line).
     * effects (Integer): Bit flags that give the timing point extra effects. See the effects section.
     */

    public double value = 500; //For red lines, bpm. For green lines, sv multiplier. (Converted to usable value. For bpm, kept as is. For multiplier, converted.)
    public int meter = 4, sampleSet = 1, sampleIndex = 0, volume = 100;
    public boolean uninherited = true, kiai = false, omitted = false; //kiai and omitted are flags in effects

    public static final int KIAI = 1;
    public static final int OMITTED = 8;

    private static final Texture pix = assetMaster.get("ui:pixel");

    public TimingPoint(long pos)
    {
        setPos(pos);

        this.uninherited = false;
        this.value = 1;
        this.omitted = false;
    }

    public TimingPoint(String data)
    {
        String[] params = data.split(",");

        for (int i = 0; i < params.length; ++i) //to avoid out of bounds.
        {
            switch (i)
            {
                case 0:
                    setPos(Double.parseDouble(params[i]));
                    break;
                case 1:
                    value = Double.parseDouble(params[i]);
                    break;
                case 2:
                    meter = Integer.parseInt(params[i]);
                    break;
                case 3:
                    sampleSet = Integer.parseInt(params[i]);
                    break;
                case 4:
                    sampleIndex = Integer.parseInt(params[i]);
                    break;
                case 5:
                    volume = Integer.parseInt(params[i]);
                    break;
                case 6:
                    uninherited = params[i].equals("1");
                    if (!uninherited) //Not a Red Line
                    {
                        value = -100 / value;
                    }
                    break;
                case 7:
                    int effects = Integer.parseInt(params[i]);

                    kiai = (effects & KIAI) > 0;
                    omitted = (effects & OMITTED) > 0;
                    break;
            }
        }
    }

    public TimingPoint(TimingPoint base)
    {
        setPos(base.getPrecisePos());
        this.uninherited = base.uninherited;
        this.value = base.value;
        this.volume = base.volume;
        this.meter = base.meter;
        this.sampleSet = base.sampleSet;
        this.sampleIndex = base.sampleIndex;
        this.kiai = base.kiai;
        this.omitted = base.omitted;
    }

    public double getBPM()
    {
        return 60000 / value;
    }
    public void setBPM(double bpm)
    {
        this.value = 60000 / bpm;
    }

    private static final DecimalFormat optionalDecimals = new DecimalFormat("##0.#############", osuDecimalFormat);
    private static final DecimalFormat scientific = new DecimalFormat("0.####E0", osuDecimalFormat);
    @Override
    public String toString()
    {
        double d = uninherited ? value : -100 / value;
        if (Math.abs(d) >= 100000 || Math.abs(d) <= 0.000001) {
            return limitedDecimals.format(getPrecisePos()) + "," + scientific.format(d) + "," + meter + "," + sampleSet + "," + sampleIndex + "," + volume + "," + (uninherited ? 1 : 0) + "," + ((kiai ? KIAI : 0) | (omitted ? OMITTED : 0));
        }
        else {
            return limitedDecimals.format(getPrecisePos()) + "," + optionalDecimals.format(d) + "," + meter + "," + sampleSet + "," + sampleIndex + "," + volume + "," + (uninherited ? 1 : 0) + "," + ((kiai ? KIAI : 0) | (omitted ? OMITTED : 0));
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y, float alpha) {
        if (testHidden()) return;

        if (selected)
        {
            renderSelection(sb, sr, pos, viewScale, x, y);
        }

        Color c = uninherited ? RED : GREEN;
        float origA = c.a;
        c.a = alpha;
        sb.setColor(c);
        c.a = origA;

        sb.draw(pix, x + (float) (this.getPos() - pos) * viewScale, y, 1, EffectView.HEIGHT);

        renderOmittedMark(sb, x, y, pos, viewScale, alpha);
    }

    public void renderColored(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y, Color c, float alpha) {
        if (testHidden()) return;

        if (selected)
        {
            renderSelection(sb, sr, pos, viewScale, x, y);
        }

        float origA = c.a;
        c.a = alpha;
        sb.setColor(c);

        sb.draw(pix, x + (float) (this.getPos() - pos) * viewScale, y, 1, EffectView.HEIGHT);
        c.a = origA;

        renderOmittedMark(sb, x, y, pos, viewScale, alpha);
    }

    public void renderOmittedMark(SpriteBatch sb, float x, float y, double pos, float viewScale, float alpha) {
        if (omitted && uninherited) {
            x += 1;
            OMIT_MARK.a = alpha;
            sb.setColor(OMIT_MARK);

            sb.draw(pix, x + (float) (this.getPos() - pos) * viewScale - OMIT_MARK_OFFSET, y + OMIT_MARK_SIZE * 1.75f, OMIT_MARK_OFFSET, 0,
                    OMIT_MARK_SIZE, 1, 1, 1, 45f, 0, 0, 1, 1, false, false);
            sb.draw(pix, x + (float) (this.getPos() - pos) * viewScale - OMIT_MARK_OFFSET, y + OMIT_MARK_SIZE * 1.75f, OMIT_MARK_OFFSET, 0,
                    OMIT_MARK_SIZE, 1, 1, 1, 135f, 0, 0, 1, 1, false, false);
        }
    }

    @Override
    public void renderSelection(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y) {
        renderSelection(sb, sr, pos, viewScale, x, y, selection);
    }

    public void renderSelection(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y, Color c) {
        if (testHidden()) return;

        sb.setColor(c);

        sb.draw(pix, x + (float) (this.getPos() - pos) * viewScale - 1, y, 3, EffectView.HEIGHT);
    }

    /*@Override
    public void tempModification(double change) {
        if (!this.uninherited) {
            this.value = Math.max(MIN_SV, this.lastRegisteredValue - (change / 20.0));
        }
    }
    public void tempSet(double newVal) {
        if (!this.uninherited) {
            this.value = newVal;
        }
    }*/

    /*@Override
    public double registerChange() {
        if (!this.uninherited) {
            double returnVal = lastRegisteredValue;
            lastRegisteredValue = value;
            return returnVal;
        }
        return 0;
    }*/
    @Override
    public void setValue(double value) {
        this.value = value;
    }
    @Override
    public double getValue() {
        return this.value;
    }
    public String valueText(DecimalFormat format) {
        return uninherited ? format.format(getBPM()) : format.format(value);
    }

    @Override
    public void setVolume(int volume) {
        this.volume = volume;
    }
    @Override
    public int getVolume() {
        return volume;
    }

    @Override
    public MapObject shiftedCopy(long newPos) {
        TimingPoint copy = new TimingPoint(this);
        copy.setPos(newPos);
        return copy;
    }

    public TimingPoint inherit()
    {
        if (uninherited) {
            this.uninherited = false;
            this.value = 1;
            this.omitted = false;
        }
        return this;
    }
    public TimingPoint uninherit()
    {
        if (!uninherited) {
            this.uninherited = true;
            setBPM(120);
            this.omitted = false;
        }
        return this;
    }
}
