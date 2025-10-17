package alchyr.diffcalc.live.taiko.difficulty.evaluators;

import alchyr.diffcalc.live.DifficultyCalculationUtils;
import alchyr.diffcalc.live.DifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.rhythm.TaikoRhythmData;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.rhythm.data.SameRhythmHitObjectGrouping;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RhythmEvaluator {
    /// <summary>
    /// Evaluate the difficulty of a hitobject considering its interval change.
    /// </summary>
    public static double EvaluateDifficultyOf(DifficultyHitObject hitObject, double hitWindow)
    {
        TaikoRhythmData rhythmData = ((TaikoDifficultyHitObject)hitObject).rhythmData;
        double difficulty = 0.0d;

        double sameRhythm = 0;
        double samePattern = 0;
        double intervalPenalty = 0;

        if (rhythmData.sameRhythmGroupedHitObjects != null && rhythmData.sameRhythmGroupedHitObjects.firstHitObject() == hitObject) // Difficulty for SameRhythmGroupedHitObjects
        {
            sameRhythm += 10.0 * evaluateDifficultyOf(rhythmData.sameRhythmGroupedHitObjects, hitWindow);
            intervalPenalty = repeatedIntervalPenalty(rhythmData.sameRhythmGroupedHitObjects, hitWindow);
        }

        if (rhythmData.samePatternsGroupedHitObjects != null && rhythmData.samePatternsGroupedHitObjects.firstHitObject() == hitObject) // Difficulty for SamePatternsGroupedHitObjects
            samePattern += 1.15 * ratioDifficulty(rhythmData.samePatternsGroupedHitObjects.intervalRatio());

        difficulty += Math.max(sameRhythm, samePattern) * intervalPenalty;

        return difficulty;
    }

    private static double evaluateDifficultyOf(SameRhythmHitObjectGrouping sameRhythmGroupedHitObjects, double hitWindow)
    {
        double intervalDifficulty = ratioDifficulty(sameRhythmGroupedHitObjects.hitObjectIntervalRatio);
        Double previousInterval = sameRhythmGroupedHitObjects.previous == null ? null : sameRhythmGroupedHitObjects.previous.hitObjectInterval;

        intervalDifficulty *= repeatedIntervalPenalty(sameRhythmGroupedHitObjects, hitWindow);

        // If a previous interval exists and there are multiple hit objects in the sequence:
        if (previousInterval != null && sameRhythmGroupedHitObjects.hitObjects.size() > 1)
        {
            double expectedDurationFromPrevious = (double)previousInterval * sameRhythmGroupedHitObjects.hitObjects.size();
            double durationDifference = sameRhythmGroupedHitObjects.duration() - expectedDurationFromPrevious;

            if (durationDifference > 0)
            {
                intervalDifficulty *= DifficultyCalculationUtils.Logistic(
                        durationDifference / hitWindow,
                        0.7,
                    1.0,
                    1);
            }
        }

        // Penalise patterns that can be hit within a single hit window.
        intervalDifficulty *= DifficultyCalculationUtils.Logistic(
                sameRhythmGroupedHitObjects.duration() / hitWindow,
                0.6,
            1,
            1);

        return Math.pow(intervalDifficulty, 0.75);
    }

    /// <summary>
    /// Determines if the changes in hit object intervals is consistent based on a given threshold.
    /// </summary>
    private static double repeatedIntervalPenalty(SameRhythmHitObjectGrouping sameRhythmGroupedHitObjects, double hitWindow) {
        return repeatedIntervalPenalty(sameRhythmGroupedHitObjects, hitWindow, 0.1);
    }
    private static double repeatedIntervalPenalty(SameRhythmHitObjectGrouping sameRhythmGroupedHitObjects, double hitWindow, double threshold)
    {
        double longIntervalPenalty = sameInterval(sameRhythmGroupedHitObjects, 3, threshold);

        double shortIntervalPenalty = sameRhythmGroupedHitObjects.hitObjects.size() < 6
                ? sameInterval(sameRhythmGroupedHitObjects, 4, threshold)
                : 1.0; // Returns a non-penalty if there are 6 or more notes within an interval.

        // The duration penalty is based on hit object duration relative to hitWindow.
        double durationPenalty = Math.max(1 - sameRhythmGroupedHitObjects.duration() * 2 / hitWindow, 0.5);

        return Math.min(longIntervalPenalty, shortIntervalPenalty) * durationPenalty;
    }

    private static double sameInterval(SameRhythmHitObjectGrouping startObject, int intervalCount, double threshold)
    {
        List<Double> intervals = new ArrayList<>();
        SameRhythmHitObjectGrouping currentObject = startObject;

        for (int i = 0; i < intervalCount && currentObject != null; i++)
        {
            intervals.add(currentObject.hitObjectInterval);
            currentObject = currentObject.previous;
        }

        intervals.removeIf(Objects::isNull);

        if (intervals.size() < intervalCount)
            return 1.0; // No penalty if there aren't enough valid intervals.

        for (int i = 0; i < intervals.size(); i++)
        {
            for (int j = i + 1; j < intervals.size(); j++)
            {
                double ratio = intervals.get(i) / intervals.get(j);
                if (Math.abs(1 - ratio) <= threshold) // If any two intervals are similar, apply a penalty.
                    return 0.80;
            }
        }

        return 1.0; // No penalty if all intervals are different.
    }

    /// <summary>
    /// Calculates the difficulty of a given ratio using a combination of periodic penalties and bonuses.
    /// </summary>
    private static double ratioDifficulty(double ratio) {
        return ratioDifficulty(ratio, 8);
    }
    private static double ratioDifficulty(double ratio, int terms)
    {
        double difficulty = 0;

        // Validate the ratio by ensuring it is a normal number in cases where maps breach regular mapping conditions.
        ratio = Double.isFinite(ratio) ? ratio : 0;

        for (int i = 1; i <= terms; ++i)
        {
            difficulty += termPenalty(ratio, i, 4, 1);
        }

        difficulty += terms / (1 + ratio);

        // Give bonus to near-1 ratios
        difficulty += DifficultyCalculationUtils.BellCurve(ratio, 1, 0.5);

        // Penalize ratios that are VERY near 1
        difficulty -= DifficultyCalculationUtils.BellCurve(ratio, 1, 0.3);

        difficulty = Math.max(difficulty, 0);
        difficulty /= Math.sqrt(8);

        return difficulty;
    }

    /// <summary>
    /// Multiplier for a given denominator term.
    /// </summary>
    private static double termPenalty(double ratio, int denominator, double power, double multiplier) {
        return -multiplier * Math.pow(Math.cos(denominator * Math.PI * ratio), power);
    }
}
