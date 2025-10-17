package alchyr.diffcalc.live.taiko.difficulty.preprocessing.rhythm;

import alchyr.diffcalc.live.DifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.rhythm.data.SamePatternsGroupedHitObjects;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.rhythm.data.SameRhythmHitObjectGrouping;

import java.util.function.Function;

public class TaikoRhythmData {
    public SameRhythmHitObjectGrouping sameRhythmGroupedHitObjects;
    public SamePatternsGroupedHitObjects samePatternsGroupedHitObjects;

    public final double Ratio;

    public TaikoRhythmData(TaikoDifficultyHitObject current)
    {
        DifficultyHitObject previous = current.previous(0);

        if (previous == null) {
            Ratio = 1;
            return;
        }

        double actualRatio = current.deltaTime / previous.deltaTime;
        Ratio = minBy(common_ratios, r -> Math.abs(r - actualRatio));
    }

    private static final double[] common_ratios = {
            1.0 / 1,
            2.0 / 1,
            1.0 / 2,
            3.0 / 1,
            1.0 / 3,
            3.0 / 2,
            2.0 / 3,
            5.0 / 4,
            4.0 / 5
    };

    private double minBy(double[] base, Function<Double, Double> conv) {
        double min, minConv;

        min = base[0];
        minConv = conv.apply(min);

        for (int i = 1; i < base.length; ++i) {
            double converted = conv.apply(base[i]);
            if (converted < minConv) {
                min = base[i];
                minConv = converted;
            }
        }
        return min;
    }
}
