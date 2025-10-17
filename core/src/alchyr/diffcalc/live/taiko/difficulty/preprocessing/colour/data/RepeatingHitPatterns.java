package alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour.data;

import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;

import java.util.ArrayList;
import java.util.List;

public class RepeatingHitPatterns {
    private static final int max_repetition_interval = 16;
    public final List<AlternatingMonoPattern> alternatingMonoPatterns = new ArrayList<>();
    public TaikoDifficultyHitObject firstHitObject() {
        return alternatingMonoPatterns.get(0).firstHitObject();
    }
    public final RepeatingHitPatterns previous;
    public int repetitionInterval = max_repetition_interval + 1;

    public RepeatingHitPatterns(RepeatingHitPatterns previous) {
        this.previous = previous;
    }

    private boolean isRepetitionOf(RepeatingHitPatterns other) {
        if (alternatingMonoPatterns.size() != other.alternatingMonoPatterns.size()) return false;

        for (int i = 0; i < Math.min(alternatingMonoPatterns.size(), 2); ++i) {
            if (!alternatingMonoPatterns.get(i).hasIdenticalMonoLength(other.alternatingMonoPatterns.get(i))) return false;
        }

        return true;
    }

    public void findRepetitionInterval() {
        if (previous == null) {
            repetitionInterval = max_repetition_interval + 1;
            return;
        }

        RepeatingHitPatterns other = previous;
        int interval = 1;

        while (interval < max_repetition_interval) {
            if (isRepetitionOf(other)) {
                repetitionInterval = Math.min(interval, max_repetition_interval);
                return;
            }

            other = other.previous;
            if (other == null) break;

            ++interval;
        }

        repetitionInterval = max_repetition_interval + 1;
    }
}
