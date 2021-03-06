package alchyr.taikoedit.maps.components;

import alchyr.taikoedit.maps.components.hitobjects.Hit;
import alchyr.taikoedit.maps.components.hitobjects.Slider;
import alchyr.taikoedit.maps.components.hitobjects.Spinner;
import alchyr.taikoedit.util.structures.PositionalObject;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.soundMaster;

public abstract class HitObject extends PositionalObject {
    protected final static int CIRCLE_SIZE = 100;
    protected final static int CIRCLE_OFFSET = CIRCLE_SIZE / 2;
    protected final static float LARGE_SCALE = 1.5f;

    protected static Texture circle;
    protected static Texture selection;
    protected static Texture body;


    /*
                     x,  y,  time,type,hitSound,objectParams,hitSample
    Example circle:  130,145,0,   1,   1,                    0:0:0:0:
    Example slider:  263,127,500, 2,   1,       L|263:273,1,140
    Example spinner: 256,192,1500,12,  1,       2000,        0:0:0:0:
*/
    /** Data from osu! wiki on File Format.
     * x (Integer) and y (Integer): Position in osu! pixels of the object.
     * time (Integer): Time when the object is to be hit, in milliseconds from the beginning of the beatmap's audio.
     * type (Integer): Bit flags indicating the type of the object. See the type section.
     * hitSound (Integer): Bit flags indicating the hitsound applied to the object. See the hitsounds section.
     * objectParams (Comma-separated list): Extra parameters specific to the object's type.
     * hitSample (Colon-separated list): Information about which samples are played when the object is hit. It is closely related to hitSound; see the hitsounds section. If it is not written, it defaults to 0:0:0:0:.
     */

    public int x = 0, y = 0;
    public HitType type;
    public boolean newCombo, normal, whistle, finish, clap;
    public int colorSkip;

    public int[] hitSample; //Colon (:) separated list

    public float volume = 1.0f;

    public static HitObject create(String data)
    {
        String[] params = data.split(",");

        HitType type = HitType.CIRCLE;

        if (params.length > 3)
        {
            int objectType = Integer.parseInt(params[3]);
            if ((objectType & SPINNER) > 0)
                type = HitType.SPINNER;
            else if ((objectType & SLIDER) > 0)
                type = HitType.SLIDER;
            else
                type = HitType.CIRCLE;
        }

        switch (type)
        {
            case SPINNER:
                return new Spinner(params);
            case SLIDER:
                return new Slider(params);
            default:
                return new Hit(params);
        }
    }

    public static void loadTextures()
    {
        circle = assetMaster.get("editor:hit");
        selection = assetMaster.get("editor:selection");
        body = assetMaster.get("editor:body");
    }

    public void playSound()
    {
        if (finish)
        {
            soundMaster.playSfx("hitsound:don finish", this.volume, true);
        }
        else
        {
            soundMaster.playSfx("hitsound:don", this.volume, true);
        }
    }

    @Override
    public abstract String toString(); //Force override.
    public abstract String toString(double beatLength, double sliderMultiplier);

    protected int getTypeFlag()
    {
        int base = 0;
        switch (type)
        {
            case CIRCLE:
                base = 1;
                break;
            case SLIDER:
                base = SLIDER;
                break;
            case SPINNER:
                base = SPINNER;
                break;
        }

        if (newCombo)
            base |= NEWCOMBO;

        return base | (colorSkip << 4);
    }

    protected int getHitsoundFlag()
    {
        int base = normal ? NORMAL : 0; //NORMAL doesn't seem to actually do anything in standard or taiko maps, at least.
        base |= whistle ? WHISTLE : 0;
        base |= finish ? FINISH : 0;
        base |= clap ? CLAP : 0;

        return base;
    }

    protected String getHitSamples()
    {
        if (hitSample == null) {
            return "0:0:0:0:";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hitSample.length; ++i)
            sb.append(hitSample[i]).append(":");
        return sb.toString();
    }

    public enum HitType {
        CIRCLE,
        SLIDER,
        SPINNER
    }

    //hitsound flags
    public static final int NORMAL = 1;
    public static final int WHISTLE = 2;
    public static final int FINISH = 4;
    public static final int CLAP = 8;

    private static final int SLIDER = 2;
    protected static final int NEWCOMBO = 4;
    private static final int SPINNER = 8;
    protected static final int COLORSKIP = 0b1110000;
}
