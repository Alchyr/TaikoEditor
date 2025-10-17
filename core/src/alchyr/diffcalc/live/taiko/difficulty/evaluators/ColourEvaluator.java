package alchyr.diffcalc.live.taiko.difficulty.evaluators;

import alchyr.diffcalc.live.DifficultyCalculationUtils;
import alchyr.diffcalc.live.DifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour.TaikoColourData;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour.data.AlternatingMonoPattern;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour.data.MonoStreak;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour.data.RepeatingHitPatterns;
import alchyr.taikoedit.util.GeneralUtils;

import java.util.ArrayList;
import java.util.List;

public class ColourEvaluator {
    /// <summary>
    /// Calculates a consistency penalty based on the number of consecutive consistent intervals,
    /// considering the delta time between each colour sequence.
    /// </summary>
    /// <param name="hitObject">The current hitObject to consider.</param>
    /// <param name="threshold"> The allowable margin of error for determining whether ratios are consistent.</param>
    /// <param name="maxObjectsToCheck">The maximum objects to check per count of consistent ratio.</param>
    private static double consistentRatioPenalty(TaikoDifficultyHitObject hitObject) {
        return consistentRatioPenalty(hitObject, 0.01, 64);
    }
    private static double consistentRatioPenalty(TaikoDifficultyHitObject hitObject, double threshold, int maxObjectsToCheck)
    {
        int consistentRatioCount = 0;
        double totalRatioCount = 0.0;

        List<Double> recentRatios = new ArrayList<>();
        TaikoDifficultyHitObject current = hitObject;
        TaikoDifficultyHitObject previousHitObject = (TaikoDifficultyHitObject)current.previous(1);

        for (int i = 0; i < maxObjectsToCheck; i++)
        {
            // Break if there is no valid previous object
            if (current.index <= 1)
                break;

            double currentRatio = current.rhythmData.Ratio;
            double previousRatio = previousHitObject.rhythmData.Ratio;

            recentRatios.add(currentRatio);

            // A consistent interval is defined as the percentage difference between the two rhythmic ratios with the margin of error.
            if (Math.abs(1 - currentRatio / previousRatio) <= threshold)
            {
                consistentRatioCount++;
                totalRatioCount += currentRatio;
                break;
            }

            current = previousHitObject;
        }

        // Ensure no division by zero
        if (consistentRatioCount > 0)
            return 1 - totalRatioCount / (consistentRatioCount + 1) * 0.80;

        if (recentRatios.size() <= 1) return 1.0;

        // As a fallback, calculate the maximum deviation from the average of the recent ratios to ensure slightly off-snapped objects don't bypass the penalty.
        double avg = GeneralUtils.listAvg(recentRatios);
        double maxRatioDeviation = GeneralUtils.listMax(recentRatios, r -> Math.abs(r - avg));

        double consistentRatioPenalty = 0.7 + 0.3 * DifficultyCalculationUtils.Smootherstep(maxRatioDeviation, 0.0, 1.0);

        return consistentRatioPenalty;
    }

    /// <summary>
    /// Evaluate the difficulty of the first hitobject within a colour streak.
    /// </summary>
    public static double EvaluateDifficultyOf(DifficultyHitObject hitObject)
    {
        TaikoDifficultyHitObject taikoObject = (TaikoDifficultyHitObject)hitObject;
        TaikoColourData colourData = taikoObject.colourData;
        double difficulty = 0.0d;

        if (colourData.monoStreak != null && colourData.monoStreak.firstObject() == hitObject) // Difficulty for MonoStreak
            difficulty += evaluateMonoStreakDifficulty(colourData.monoStreak);

        if (colourData.alternatingMonoPattern != null && colourData.alternatingMonoPattern.firstHitObject() == hitObject) // Difficulty for AlternatingMonoPattern
            difficulty += evaluateAlternatingMonoPatternDifficulty(colourData.alternatingMonoPattern);

        if (colourData.repeatingHitPattern != null && colourData.repeatingHitPattern.firstHitObject() == hitObject) // Difficulty for RepeatingHitPattern
            difficulty += evaluateRepeatingHitPatternsDifficulty(colourData.repeatingHitPattern);

        double consistencyPenalty = consistentRatioPenalty(taikoObject);
        difficulty *= consistencyPenalty;

        return difficulty;
    }

    private static double evaluateMonoStreakDifficulty(MonoStreak monoStreak) {
        return DifficultyCalculationUtils.Logistic(Math.E * monoStreak.index - 2 * Math.E) * evaluateAlternatingMonoPatternDifficulty(monoStreak.parent) * 0.5;
    }

    private static double evaluateAlternatingMonoPatternDifficulty(AlternatingMonoPattern alternatingMonoPattern) {
        return DifficultyCalculationUtils.Logistic(Math.E * alternatingMonoPattern.index - 2 * Math.E) * evaluateRepeatingHitPatternsDifficulty(alternatingMonoPattern.parent);
    }

    private static double evaluateRepeatingHitPatternsDifficulty(RepeatingHitPatterns repeatingHitPattern) {
        return 2 * (1 - DifficultyCalculationUtils.Logistic(Math.E * repeatingHitPattern.repetitionInterval - 2 * Math.E));
    }

}
