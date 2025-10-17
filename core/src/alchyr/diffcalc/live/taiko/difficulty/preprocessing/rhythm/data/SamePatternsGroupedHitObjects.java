package alchyr.diffcalc.live.taiko.difficulty.preprocessing.rhythm.data;

import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;

import java.util.List;
import java.util.stream.Collectors;

public class SamePatternsGroupedHitObjects {
    public List<SameRhythmHitObjectGrouping> groups;
    public SamePatternsGroupedHitObjects previous;

    public double groupInterval() {
        return groups.size() > 1 ? groups.get(1).getInterval() : groups.get(0).getInterval();
    }

    public double intervalRatio() {
        return groupInterval() / (previous == null ? 1.0 : previous.groupInterval());
    }

    public TaikoDifficultyHitObject firstHitObject() {
        return groups.get(0).firstHitObject();
    }

    public Iterable<TaikoDifficultyHitObject> allHitObjects() {
        return groups.stream().flatMap(group -> group.hitObjects.stream()).collect(Collectors.toList());
    }

    public SamePatternsGroupedHitObjects(SamePatternsGroupedHitObjects previous, List<SameRhythmHitObjectGrouping> groups) {
        this.previous = previous;
        this.groups = groups;
    }
}
