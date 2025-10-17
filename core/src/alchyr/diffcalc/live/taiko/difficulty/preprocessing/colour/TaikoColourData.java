package alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour;

import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour.data.AlternatingMonoPattern;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour.data.MonoStreak;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour.data.RepeatingHitPatterns;

public class TaikoColourData {
    public MonoStreak monoStreak;
    public AlternatingMonoPattern alternatingMonoPattern;
    public RepeatingHitPatterns repeatingHitPattern;
    public TaikoDifficultyHitObject previousColorChange() {
        return monoStreak == null ? null : monoStreak.firstObject().previousNote(0);
    }

    public TaikoDifficultyHitObject nextColorChange() {
        return monoStreak == null ? null : monoStreak.lastObject().nextNote(0);
    }
}
