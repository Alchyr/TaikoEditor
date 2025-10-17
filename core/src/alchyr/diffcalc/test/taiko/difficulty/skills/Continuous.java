package alchyr.diffcalc.test.taiko.difficulty.skills;

import alchyr.diffcalc.test.DifficultyHitObject;
import alchyr.diffcalc.test.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.diffcalc.test.taiko.difficulty.preprocessing.TaikoDifficultyRhythm;
import alchyr.taikoedit.editor.maps.components.HitObject;

import java.util.*;

public class Continuous extends Skill {
    private static final TaikoRhythmFactor[] COMMON_FACTORS = new TaikoRhythmFactor[] {
            new TaikoRhythmFactor(2, 0.65), //Since these are checked in order, this makes the "first" 2 most valuable.
            new TaikoRhythmFactor(2, 0.6),
            new TaikoRhythmFactor(2, 0.55),
            new TaikoRhythmFactor(2, 0.5),
            new TaikoRhythmFactor(3, 0.6),
            new TaikoRhythmFactor(3, 0.55),
            new TaikoRhythmFactor(3, 0.5),
    };
    private static final int RHYTHM_BASE = 432;
    private static final double MAX_SIMPLICITY = 9999.0;
    private static final double OLD_FACTOR_LOSS = 0.5; //Factors that were temporarily removed are worth this much of their original value.
    private static final double SIMPLICITY_WEIGHT_DECAY = 0.88;

    private static final int MAX_LENGTH = 12;
    private static final int TRACK_LENGTH = 9;

    private static final double SKILL_MULTIPLIER = 1;
    private static final double DECAY_BASE = 0.945;


    private double circleDelta = 0;
    private double lastCircleDelta = 0;
    //private double rhythmValue = RHYTHM_BASE;

    /*
    Current problem: Too simple.
    Basically only goes down if map is *really* excessively complex.

    Options:
        Rhythm changes have associated difficulty, rather than current simplicity calculations.
        Downside: Spam rhythm changes which are actually just easy = high rating. Old system's problem.
     */


    @Override
    protected double SkillMultiplier() {
        return SKILL_MULTIPLIER;
    }

    @Override
    protected double StrainDecayBase() {
        return DECAY_BASE;
    }

    //Simplicity:
    //Average of simplicity starting calculation on every object?
    List<SimplicityTracker> simplicityTrackers = new ArrayList<>(MAX_LENGTH);

    public Continuous() {
        for (int i = 0; i < MAX_LENGTH; ++i) {
            simplicityTrackers.add(new SimplicityTracker());
        }
    }

    @Override
    protected double StrainValueOf(DifficultyHitObject current) {
        circleDelta += current.deltaTime;
        if (current.baseObject.type != HitObject.HitObjectType.CIRCLE) {
            return 0.0;
        }
        if (circleDelta >= 1000) {
            for (int i = 999; i < circleDelta; i += 1000) {
                if (simplicityTrackers.size() == MAX_LENGTH)
                    simplicityTrackers.remove(0);
                simplicityTrackers.add(new SimplicityTracker());
            }
        }
        if (circleDelta >= 500) { //After a long gap, the first two objects are considered to be 1/1 in spacing.
            lastCircleDelta = 0;
            circleDelta = 0;
            //Simplicity tracking is not reset, so after a long slow section simplicity will be very high due to residual max simplicity.
            //Avoids excessive volatility immediately after a slow section.
        }

        TaikoDifficultyHitObject taikoCurrent = (TaikoDifficultyHitObject)current;

        TaikoDifficultyRhythm rhythm = getClosestRhythm(circleDelta, lastCircleDelta);
        lastCircleDelta = circleDelta;
        circleDelta = 0;

        if (simplicityTrackers.size() == MAX_LENGTH)
            simplicityTrackers.remove(0);
        simplicityTrackers.add(new SimplicityTracker());

        double averageSimplicity = 0;
        double weight = 1;
        double totalWeight = 0;
        for (int i = simplicityTrackers.size() - 1; i >= 0; --i) {
            if (i >= simplicityTrackers.size() - TRACK_LENGTH)
                simplicityTrackers.get(i).add(rhythm);
            averageSimplicity += simplicityTrackers.get(i).getSimplicity() * weight;

            totalWeight += weight;
            weight *= SIMPLICITY_WEIGHT_DECAY;
        }
        averageSimplicity /= totalWeight;

        taikoCurrent.RHYTHM_BONUS_DEBUG = averageSimplicity;
        return taikoCurrent.RHYTHM_BONUS_DEBUG;
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
                        new TaikoDifficultyRhythm(1, 3, 0.35), //Speedup
                        new TaikoDifficultyRhythm(1, 2, 0.5),
                        new TaikoDifficultyRhythm(2, 3, 0.4),
                        //new TaikoDifficultyRhythm(3, 4, 0.4),
                        //new TaikoDifficultyRhythm(4, 5, 0.7),
                        no_change_rhythm = new TaikoDifficultyRhythm(1, 1, 0.0),
                        //new TaikoDifficultyRhythm(5, 4, 0.5), //Slowdown
                        //new TaikoDifficultyRhythm(4, 3, 0.6),
                        new TaikoDifficultyRhythm(3, 2, 0.6),
                        new TaikoDifficultyRhythm(2, 1, 0.3),
                        new TaikoDifficultyRhythm(3, 1, 0.3),
                        new TaikoDifficultyRhythm(4, 1, 0.15)
                };

        Arrays.sort(common_rhythms, Comparator.comparingDouble(a -> a.Ratio));
    }

    /// <summary>
    /// Returns the closest rhythm change from <see cref="common_rhythms"/> required to hit this object.
    /// </summary>
    /// <param name="lastObject">The gameplay <see cref="HitObject"/> preceding this one.</param>
    /// <param name="lastLastObject">The gameplay <see cref="HitObject"/> preceding <paramref name="lastObject"/>.</param>
    /// <param name="clockRate">The rate of the gameplay clock.</param>
    private static TaikoDifficultyRhythm getClosestRhythm(double deltaTime, double lastDeltaTime)
    {
        if (lastDeltaTime == 0)
            return no_change_rhythm;

        double ratio = deltaTime / lastDeltaTime;

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

    private static class SimplicityTracker {
        private double rhythmValue = RHYTHM_BASE;
        private double simplicity;

        private List<TaikoRhythmFactor> minFactors = new ArrayList<>();

        public SimplicityTracker() {
            Collections.addAll(minFactors, COMMON_FACTORS);
            updateSimplicity();
        }

        public void add(TaikoDifficultyRhythm rhythm) {
            rhythmValue = Math.min(rhythmValue * rhythm.Ratio, RHYTHM_BASE);
            updateSimplicity();
        }

        public double getSimplicity() {
            return simplicity;
        }

        private void updateSimplicity() {
            simplicity = 0;
            long wholeValue = Math.round(rhythmValue);
            for (TaikoRhythmFactor factor : COMMON_FACTORS) {
                if (wholeValue % factor.factor == 0) {
                    wholeValue /= factor.factor;
                    simplicity += factor.value * (minFactors.contains(factor) ? 1 : OLD_FACTOR_LOSS); //Factors that were at some point removed permanently lose value
                    /*if (simplicity >= MAX_SIMPLICITY)
                        return MAX_SIMPLICITY;*/
                }
                else {
                    minFactors.remove(factor); //no longer a factor
                }
            }

            //return simplicity;
        }


        @Override
        public String toString() {
            return "SimplicityTracker{" +
                    "simplicity:" + simplicity +
                    '}';
        }
    }

    private static class TaikoRhythmFactor {
        public final int factor;
        public final double value;

        public TaikoRhythmFactor(int factor, double value) {
            this.factor = factor;
            this.value = value;
        }
    }
}
