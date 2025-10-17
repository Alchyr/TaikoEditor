package alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour.data;

import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;

import java.util.ArrayList;
import java.util.List;

public class AlternatingMonoPattern {
    public final List<MonoStreak> monoStreaks = new ArrayList<>();
    public RepeatingHitPatterns parent = null;
    public int index;
    public TaikoDifficultyHitObject firstHitObject() {
        return monoStreaks.get(0).firstObject();
    }

    public boolean isRepetitionOf(AlternatingMonoPattern other) {
        return hasIdenticalMonoLength(other) &&
                other.monoStreaks.size() == monoStreaks.size() &&
                other.monoStreaks.get(0).HitType() == monoStreaks.get(0).HitType();
    }

    public boolean hasIdenticalMonoLength(AlternatingMonoPattern other) {
        return other.monoStreaks.get(0).runLength() == monoStreaks.get(0).runLength();
    }
}
