package alchyr.taikoedit.maps.components;

import alchyr.taikoedit.util.structures.PositionalObject;

public class TimingPoint extends PositionalObject {
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
    public int meter = 4, sampleSet = 1, sampleIndex = 0, volume = 100;
    public boolean uninherited = true, kiai = false, omitted = false; //kiai and omitted are flags in effects

    public static final int KIAI = 1;
    public static final int OMITTED = 8;

    public TimingPoint(String data)
    {
        String[] params = data.split(",");

        for (int i = 0; i < params.length; ++i) //to avoid out of bounds.
        {
            switch (i)
            {
                case 0:
                    pos = Integer.parseInt(params[i]);
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
}
