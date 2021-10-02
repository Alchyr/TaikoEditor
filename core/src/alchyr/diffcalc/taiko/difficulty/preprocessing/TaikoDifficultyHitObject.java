package alchyr.diffcalc.taiko.difficulty.preprocessing;

import alchyr.diffcalc.DifficultyHitObject;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.hitobjects.Hit;

import java.util.Arrays;
import java.util.Comparator;

public class TaikoDifficultyHitObject extends DifficultyHitObject {
    public static TaikoDifficultyHitObject defaultInfo = new TaikoDifficultyHitObject();

    /// <summary>
    /// The rhythm required to hit this hit object.
    /// </summary>
    public TaikoDifficultyRhythm rhythm;

    /// <summary>
    /// The length of the current color pattern.
    /// </summary>
    public int colorLength;

    /// <summary>
    /// The hit type of this hit object.
    /// </summary>
    public HitType hitType;

    /// <summary>
    /// The type of a <see cref="Hit"/>.
    /// </summary>
    public enum HitType
    {
        /// <summary>
        /// A <see cref="Hit"/> that can be hit by the centre portion of the drum.
        /// </summary>
        Centre,

        /// <summary>
        /// A <see cref="Hit"/> that can be hit by the rim portion of the drum.
        /// </summary>
        Rim
    }

    /// <summary>
    /// The index of the object in the beatmap.
    /// </summary>
    //public final int ObjectIndex;

    /// <summary>
    /// Whether the object should carry a penalty due to being hittable using special techniques
    /// making it easier to do so.
    /// </summary>
    //public bool StaminaCheese;

    /// <summary>
    /// Creates a new difficulty hit object.
    /// </summary>
    /// <param name="hitObject">The gameplay <see cref="HitObject"/> associated with this difficulty object.</param>
    /// <param name="lastObject">The gameplay <see cref="HitObject"/> preceding <paramref name="hitObject"/>.</param>
    /// <param name="lastLastObject">The gameplay <see cref="HitObject"/> preceding <paramref name="lastObject"/>.</param>
    /// <param name="clockRate">The rate of the gameplay clock. Modified by speed-changing mods.</param>
    /// <param name="objectIndex">The index of the object in the beatmap.</param>
    public TaikoDifficultyHitObject(HitObject hitObject, TaikoDifficultyHitObject lastObject)//, int objectIndex)
    {
        super(hitObject, lastObject.baseObject);

        hitType = ((Hit)hitObject).isRim() ? HitType.Rim : HitType.Centre;
        colorLength = 1;

        if (deltaTime > 500) {
            rhythm = no_change_rhythm;
        }
        else {
            rhythm = getClosestRhythm(lastObject);
            if (hitType == lastObject.hitType) {
                colorLength = lastObject.colorLength + 1;
            }
        }

        //TaikoEditor.editorLogger.info("Rhythm ratio: " + rhythm.toString() + " | Color length: " + colorLength);

        //ObjectIndex = objectIndex;
    }

    public TaikoDifficultyHitObject(HitObject hitObject)//, int objectIndex)
    {
        super(hitObject);

        hitType = ((Hit)hitObject).isRim() ? HitType.Rim : HitType.Centre;

        rhythm = no_change_rhythm; //there is no rhythm change yet, this is the first object.
        colorLength = 1;

        //TaikoEditor.editorLogger.info("Rhythm ratio: " + rhythm.toString() + " | Color length: " + colorLength);

        //ObjectIndex = objectIndex;
    }

    private TaikoDifficultyHitObject()
    {
        super();

        hitType = HitType.Centre;
        rhythm = no_change_rhythm;
        colorLength = 1;
    }

    /// <summary>
    /// List of most common rhythm changes in taiko maps.
    /// </summary>
    /// <remarks>
    /// The general guidelines for the values are:
    /// <list type="bullet">
    /// <item>rhythm changes with ratio closer to 1 (that are <i>not</i> 1) are harder to play,</item>
    /// <item>speeding up is <i>generally</i> harder than slowing down (with exceptions of rhythm changes requiring a hand switch).</item>
    /// </list>
    /// </remarks>
    private static final TaikoDifficultyRhythm[] common_rhythms;
    private static final TaikoDifficultyRhythm no_change_rhythm;

    static {
       common_rhythms = new TaikoDifficultyRhythm[]
               {
                   no_change_rhythm = new TaikoDifficultyRhythm(1, 1, 0.0),
                   new TaikoDifficultyRhythm(1, 4, 0.1),
                   new TaikoDifficultyRhythm(2, 1, 0.3),
                   new TaikoDifficultyRhythm(1, 2, 0.5),
                   new TaikoDifficultyRhythm(3, 1, 0.3),
                   new TaikoDifficultyRhythm(1, 3, 0.35),
                   new TaikoDifficultyRhythm(3, 2, 0.6), // purposefully higher (requires hand switch in full alternating gameplay style)
                   new TaikoDifficultyRhythm(2, 3, 0.4),
                   new TaikoDifficultyRhythm(5, 4, 0.5),
                   new TaikoDifficultyRhythm(4, 5, 0.7)
               };

        Arrays.sort(common_rhythms, Comparator.comparingDouble(a -> a.Ratio));
    }

    /// <summary>
    /// Returns the closest rhythm change from <see cref="common_rhythms"/> required to hit this object.
    /// </summary>
    /// <param name="lastObject">The gameplay <see cref="HitObject"/> preceding this one.</param>
    /// <param name="lastLastObject">The gameplay <see cref="HitObject"/> preceding <paramref name="lastObject"/>.</param>
    /// <param name="clockRate">The rate of the gameplay clock.</param>
    private TaikoDifficultyRhythm getClosestRhythm(TaikoDifficultyHitObject lastObject)
    {
        if (lastObject.deltaTime == 0)
            return no_change_rhythm;

        double ratio = deltaTime / lastObject.deltaTime;

        for (int i = common_rhythms.length - 1; i >= 0; --i)
        {
            if (ratio > common_rhythms[i].Ratio)
            {
                //ratio is between this and the next one, meaning whichever is closer is the closest.
                if (i == common_rhythms.length - 1) //there is no larger ratio to compare
                    return common_rhythms[i];

                //return the closer of the two.
                return Math.abs(common_rhythms[i].Ratio - ratio) < Math.abs(common_rhythms[i + 1].Ratio - ratio) ? common_rhythms[i] : common_rhythms[i + 1];
            }
        }

        //ratio is very small.
        return common_rhythms[0];
    }


    public Similarity getSimilarity(TaikoDifficultyHitObject other)
    {
        Similarity result = Similarity.NONE;

        if (rhythm == other.rhythm && Math.abs(deltaTime - other.deltaTime) <= 2)
        {
            result = Similarity.RHYTHM;
        }

        if (colorLength == other.colorLength && hitType == other.hitType)
        {
            result = result == Similarity.NONE ? Similarity.COLOR : Similarity.MATCH;
        }

        return result;
    }

    public enum Similarity {
        MATCH,
        RHYTHM,
        COLOR,
        NONE
    }



    //This stuff is for debug purposes.
    public double BURST_BASE = 0;
    public double BURST_DEBUG = 0;

    public double BASE_COLOR_DEBUG = 0;
    public double SWAP_BONUS_DEBUG = 0;
    public double RHYTHM_BONUS_DEBUG = 0;
    public double COMBINED_DEBUG = 0;
}