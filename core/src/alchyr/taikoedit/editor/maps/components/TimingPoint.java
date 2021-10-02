package alchyr.taikoedit.editor.maps.components;

import alchyr.taikoedit.editor.views.SvView;
import alchyr.taikoedit.util.structures.PositionalObject;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.text.DecimalFormat;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class TimingPoint extends PositionalObject {
    private static final Color red = Color.RED.cpy();
    private static final Color green = new Color(0.25f, 0.75f, 0.0f, 1.0f);
    private static final Color yellow = new Color(0.8f, 0.8f, 0.0f, 1.0f);

    private static final Color selection = new Color(1.0f, 0.6f, 0.0f, 1.0f);

    private static final double MIN_SV = 0.01;

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
     * uninherited (0 or 1): Whether or not the timing point is uninherited.
     * effects (Integer): Bit flags that give the timing point extra effects. See the effects section.
     */

    public double value = 500; //For red lines, bpm. For green lines, sv multiplier. (Converted to usable value. For bpm, kept as is. For multiplier, converted.)
    public double lastRegisteredValue = 500; //doesn't change until the new value is tracked in undo/redo stuff
    public int meter = 4, sampleSet = 1, sampleIndex = 0, volume = 100;
    public boolean uninherited = true, kiai = false, omitted = false; //kiai and omitted are flags in effects

    public static final int KIAI = 1;
    public static final int OMITTED = 8;

    private static Texture pix;


    public TimingPoint(long pos)
    {
        this.pos = pos;

        this.uninherited = false;
        this.lastRegisteredValue = this.value = 1;
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
                    pos = Long.parseLong(params[i]);
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
        lastRegisteredValue = value;
    }

    public TimingPoint(TimingPoint base)
    {
        this.pos = base.pos;
        this.uninherited = base.uninherited;
        this.lastRegisteredValue = this.value = base.value;
        this.meter = base.meter;
        this.sampleSet = base.sampleSet;
        this.sampleIndex = base.sampleIndex;
        this.volume = base.volume;
        this.kiai = base.kiai;
        this.omitted = base.omitted;
    }

    public static void loadTexture()
    {
        pix = assetMaster.get("ui:pixel");
    }

    public double getBPM()
    {
        return 60000 / value;
    }

    private static final DecimalFormat optionalDecimals = new DecimalFormat("##0.#############");
    private static final DecimalFormat scientific = new DecimalFormat("0.####E0");
    @Override
    public String toString()
    {
        double d = uninherited ? value : -100 / value;
        if (Math.abs(d) >= 100000 || Math.abs(d) <= 0.000001) {
            return pos + "," + scientific.format(d) + "," + meter + "," + sampleSet + "," + sampleIndex + "," + volume + "," + (uninherited ? 1 : 0) + "," + ((kiai ? KIAI : 0) | (omitted ? OMITTED : 0));
        }
        else {
            return pos + "," + optionalDecimals.format(d) + "," + meter + "," + sampleSet + "," + sampleIndex + "," + volume + "," + (uninherited ? 1 : 0) + "," + ((kiai ? KIAI : 0) | (omitted ? OMITTED : 0));
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y, float alpha) {
        if (selected)
        {
            renderSelection(sb, sr, pos, viewScale, x, y);
        }

        Color c = uninherited ? red : green;
        c.a = alpha;
        sb.setColor(c);

        sb.draw(pix, x + (int) (this.pos - pos) * viewScale, y, 1, SvView.HEIGHT);
    }

    public void renderYellow(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y, float alpha) {
        if (selected)
        {
            renderSelection(sb, sr, pos, viewScale, x, y);
        }

        yellow.a = alpha;
        sb.setColor(yellow);

        sb.draw(pix, x + (int) (this.pos - pos) * viewScale, y, 1, SvView.HEIGHT);
    }

    @Override
    public void renderSelection(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y) {
        sb.setColor(selection);

        sb.draw(pix, x + (int) (this.pos - pos) * viewScale - 1, y, 3, SvView.HEIGHT);
    }

    @Override
    public void tempModification(double change) {
        if (!this.uninherited) {
            this.value = Math.max(MIN_SV, this.lastRegisteredValue - (change / 20.0));
        }
    }
    @Override
    public double registerChange() {
        if (!this.uninherited) {
            double returnVal = lastRegisteredValue;
            lastRegisteredValue = value;
            return returnVal;
        }
        return 0;
    }
    @Override
    public void setValue(double value) {
        this.lastRegisteredValue = this.value = value;
    }

    @Override
    public PositionalObject shiftedCopy(long newPos) {
        TimingPoint copy = new TimingPoint(this);
        copy.setPosition(newPos);
        return copy;
    }

    public TimingPoint inherit()
    {
        if (uninherited) {
            this.uninherited = false;
            this.lastRegisteredValue = this.value = 1;
            this.omitted = false;
        }
        return this;
    }
}
