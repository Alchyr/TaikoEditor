package alchyr.diffcalc.test.taiko.difficulty.skills;

import alchyr.diffcalc.test.DifficultyHitObject;
import alchyr.diffcalc.test.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.diffcalc.test.taiko.difficulty.preprocessing.TaikoDifficultyRhythm;
import alchyr.taikoedit.editor.maps.components.HitObject;

import java.util.*;

public class General extends Skill {
    //General
    private static final double SKILL_MULTIPLIER = 1;
    private static final double DECAY_BASE = 0.25;

    //Relative value tuning knobs
    private static final double BASE_VALUE = 0.12; //base object value. Relatively low, most of it comes from "base color value"
    private static final double BASE_COLOR_SCALE = 0.3;
    private static final double RHYTHM_SCALE = 1.5; //Multiplier for final value of rhythm on object strain
    private static final double SWAP_BONUS_SCALE = 3.5;


    //Rhythm
    private static final double MIN_RATIO = 0.2;
    private static final double MAX_RATIO = 5;

    private static final double RHYTHM_WEIGHT_DECAY = 0.77; //Higher weight decay (lower value) makes individual rhythm changes more impactful. Lower overall values.
    private static final int MAX_LENGTH = 9; //Higher values here decrease the impact of individual rhythm changes, and increase the value of multiple consecutive.
    private static final int TRACK_LENGTH = 9; //
    private static final double DISTANCE_SCALING = 0.84; //Controls how little values change as distance increases. Makes multiple consecutive rhythm changes more impactful. Higher value = less change.
    private static final double NEGATION_SCALE = 0.3; //Controls reduction of value from opposite rhythms. Lower number = less value.
    private static final double WITH_COLOR_CHANGE = 0.73;

    private static final double RHYTHM_REPEAT_SCALING = 1; //Between 0 and 1. Higher values mean repeats affect rhythm value more harshly.
    private static final double RHYTHM_REPEAT_ADJUST = 1 - RHYTHM_REPEAT_SCALING;

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
    static {
        common_rhythms = new TaikoDifficultyRhythm[]
                {
                        new TaikoDifficultyRhythm(1, 4, 0.2), //Speedup
                        new TaikoDifficultyRhythm(1, 3, 0.25),
                        new TaikoDifficultyRhythm(1, 2, 0.3),
                        new TaikoDifficultyRhythm(2, 3, 0.4),
                        new TaikoDifficultyRhythm(3, 4, 0.45),
                        new TaikoDifficultyRhythm(4, 5, 0.5),
                        new TaikoDifficultyRhythm(1, 1, 0.0), //No change
                        new TaikoDifficultyRhythm(5, 4, 0.4), //Slowdown
                        new TaikoDifficultyRhythm(4, 3, 0.35),
                        new TaikoDifficultyRhythm(3, 2, 0.3),
                        new TaikoDifficultyRhythm(2, 1, 0.25),
                        new TaikoDifficultyRhythm(3, 1, 0.2),
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
    private static TaikoDifficultyRhythm getClosestRhythm(double ratio)
    {
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

    //Color
    private static final double BASE_COLOR_CAP_ODD = 1.2;
    private static final double BASE_COLOR_CAP_EVEN = 1;

    private static final double IMMEDIATE_SWAP_LOSS = 0.725;
    private static final double REPEAT_LENGTH_LOSS = 0.83;
    private static final double SAME_POLARITY_LOSS = 0.7;

    private int sameColorLength = 1;
    private int lastColorLength = 0;
    private TaikoDifficultyHitObject.HitType previousHitType = null;
    private int[] lastCentreLength = new int[2];
    private int[] lastRimLength = new int[2];



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
    List<RhythmTracker> rhythmTrackers = new ArrayList<>(MAX_LENGTH);

    public General() {
        for (int i = 0; i < MAX_LENGTH; ++i) {
            rhythmTrackers.add(new RhythmTracker());
        }
    }

    @Override
    public void Process(DifficultyHitObject current) {
        super.Process(current);
        ((TaikoDifficultyHitObject) current).COMBINED_DEBUG = CurrentStrain;
    }

    @Override
    protected double StrainValueOf(DifficultyHitObject current) {
        circleDelta += current.deltaTime;
        if (current.baseObject.type != HitObject.HitObjectType.CIRCLE) {
            return 0.0;
        }

        ////////////////////// Properties //////////////////////
        TaikoDifficultyHitObject taikoCurrent = (TaikoDifficultyHitObject)current;

        if (circleDelta >= 500) { //After a long gap, the first two objects are considered to be 1/1 in spacing.
            if (circleDelta >= 1000) {
                for (int i = 999; i < circleDelta; i += 1000) {
                    if (rhythmTrackers.size() == MAX_LENGTH)
                        rhythmTrackers.remove(0);
                    rhythmTrackers.add(new RhythmTracker());
                }
            }

            lastCircleDelta = 0;
            circleDelta = 0;
            lastCentreLength[0] = 0;
            lastCentreLength[1] = 0;
            lastRimLength[0] = 0;
            lastRimLength[1] = 0;
            lastColorLength = 0;
            sameColorLength = 0;
            previousHitType = null;
        }
        if (current.lastObject.type == HitObject.HitObjectType.CIRCLE && taikoCurrent.hitType != previousHitType) {
            lastColorLength = sameColorLength;
            sameColorLength = 0;
            previousHitType = taikoCurrent.hitType;
        }
        ++sameColorLength;

        ////////////////////// Rhythm //////////////////////
        double rhythm = rhythmBonus();

        taikoCurrent.RHYTHM_BONUS_DEBUG = rhythm;

        ////////////////////// Color //////////////////////
        double color = 0;
        if (sameColorLength % 2 == 0) {
            color = 0.5 * log(sameColorLength + 4, 4) - 0.1;
            color = Math.min(BASE_COLOR_CAP_EVEN, color);
        }
        else {
            color = .7 * Math.log10(sameColorLength + 4) + 0.3;
            color = Math.min(BASE_COLOR_CAP_ODD, color);
        }
        color *= BASE_COLOR_SCALE;

        taikoCurrent.BASE_COLOR_DEBUG = color;

        color += swapBonus(taikoCurrent);

        return BASE_VALUE + color + rhythm * RHYTHM_SCALE;
    }

    private double rhythmBonus() {
        double rhythm = 0;
        double ratio = 1;

        if (lastCircleDelta != 0)
            ratio = circleDelta / lastCircleDelta;

        lastCircleDelta = circleDelta;
        circleDelta = 0;

        if (rhythmTrackers.size() == MAX_LENGTH)
            rhythmTrackers.remove(0);
        rhythmTrackers.add(new RhythmTracker());

        double weight = 1;
        double totalWeight = 0;
        for (int i = rhythmTrackers.size() - 1; i >= 0; --i) {
            //Further from max trackers, greater distance. From 1 to number of tracked.
            if (i >= rhythmTrackers.size() - TRACK_LENGTH)
                rhythmTrackers.get(i).add(ratio, rhythmTrackers.size() - i);

            rhythm += rhythmTrackers.get(i).getValue() * weight;

            totalWeight += weight;
            weight *= RHYTHM_WEIGHT_DECAY;
        }

        if (Math.abs(ratio - 1) < 0.05) {
            return 0;
        }

        rhythm /= totalWeight;

        rhythm *= RHYTHM_SCALE;

        if (sameColorLength <= 2) {
            //Rhythm change on the first or second object of a pattern is a bit easier
            rhythm *= WITH_COLOR_CHANGE;
        }

        return rhythm;
    }

    private double swapBonus(TaikoDifficultyHitObject current) {
        double bonus = 0;
        if (sameColorLength == 1 && lastColorLength > 0) {
            bonus = (1 - (0.8 / (lastColorLength + 1.5))) * SWAP_BONUS_SCALE; //Almost linear. This isn't intended to benefit extended monos, those benefit from burst difficulty.
            if (lastColorLength == 1)
                bonus *= IMMEDIATE_SWAP_LOSS;

            if (current.hitType == TaikoDifficultyHitObject.HitType.Rim) {
                if (lastColorLength % 2 == lastRimLength[0] % 2) //aabba, abbba, aaaba, etc.
                    bonus *= SAME_POLARITY_LOSS;

                if (lastColorLength == lastCentreLength[0]) //same length of don twice in a row
                    bonus *= REPEAT_LENGTH_LOSS;
                if (lastColorLength == lastCentreLength[1])
                    bonus *= REPEAT_LENGTH_LOSS;

                lastCentreLength[1] = lastCentreLength[0];
                lastCentreLength[0] = lastColorLength;
            }
            else {
                if (lastColorLength % 2 == lastCentreLength[0] % 2)
                    bonus *= SAME_POLARITY_LOSS;

                if (lastColorLength == lastRimLength[0])
                    bonus *= REPEAT_LENGTH_LOSS;
                if (lastColorLength == lastRimLength[1])
                    bonus *= REPEAT_LENGTH_LOSS;

                lastRimLength[1] = lastRimLength[0];
                lastRimLength[0] = lastColorLength;
            }

            current.SWAP_BONUS_DEBUG = bonus;
        }
        return bonus;
    }

    private static class RhythmTracker {
        private double rhythmValue = 0;
        private final List<RhythmChange> rhythmChanges = new ArrayList<>();
        private final List<RhythmChange> negatedChanges = new ArrayList<>();

        //private List<TaikoRhythmFactor> minFactors = new ArrayList<>();

        public double getValue() {
            return rhythmValue;
        }

        public void add(double ratio, int distance) {
            ratio = limitRatio(ratio);

            long oppositeRatio = getApproximateOpposite(ratio);
            long approximateRatio = getApproximateRatio(ratio);

            double repeats = 0;
            double multiplier = (1 - DISTANCE_SCALING * distance / TRACK_LENGTH);
            boolean negated = false;

            for (int i = 0; i < rhythmChanges.size(); ++i) {
                if (rhythmChanges.get(i).approximateRatio == approximateRatio)
                    repeats += 0.6;

                if (rhythmChanges.get(i).approximateRatio == oppositeRatio && !negated) {
                    //Opposite rhythm change already exists
                    //rhythmChanges.get(i).negate(distanceMultiplier);
                    negatedChanges.add(rhythmChanges.remove(i));
                    --i;
                    multiplier *= NEGATION_SCALE;

                    updateValue();

                    negated = true;
                }
            }

            for (RhythmChange change : negatedChanges) {
                if (change.approximateRatio == approximateRatio)
                    repeats += 1;
            }

            repeats = (RHYTHM_REPEAT_SCALING / (repeats + 1)) + RHYTHM_REPEAT_ADJUST;

            rhythmChanges.add(new RhythmChange(ratio, repeats, multiplier));
            updateValue();
        }

        private void updateValue() {
            rhythmValue = 0;
            for (RhythmChange change : rhythmChanges)
                rhythmValue += change.getValue();

            for (RhythmChange change : rhythmChanges)
                rhythmValue += change.getValue();
        }

        private static double limitRatio(double ratio) {
            if (ratio < MIN_RATIO)
                return MIN_RATIO;
            return Math.min(ratio, MAX_RATIO);
        }
        private static long getApproximateOpposite(double ratio) {
            return getApproximateRatio(1 / ratio);
        }
        private static long getApproximateRatio(double ratio) {
            if (ratio < 1) {
                //.25 * 100 = 25
                //.254 * 100 = 25
                return Math.round(ratio * 100);
            }
            else {
                //1.5, 1.53 = 15 * 10 = 150
                return Math.round(ratio * 10) * 10;
            }
        }

        @Override
        public String toString() {
            return "RhythmTracker{" +
                    "Value:" + rhythmValue +
                    '}';
        }

        private static class RhythmChange {
            private final long approximateRatio;
            private final double baseValue;
            private double value;

            public RhythmChange(double ratio, double repeats, double distanceMultiplier) {
                this.value = this.baseValue = getValue(ratio) * repeats * distanceMultiplier;

                this.approximateRatio = getApproximateRatio(ratio);
            }

            public double getValue() {
                return value;
            }

            /*public void negate(double distanceMultiplier) {
                value -= this.baseValue * distanceMultiplier * NEGATION_SCALE;
            }*/

            private double getValue(double ratio) {
                return getClosestRhythm(ratio).Difficulty;
            }
        }
    }

    private static double log(double val, double base) {
        return Math.log(val) / Math.log(base);
    }
}
