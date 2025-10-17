package alchyr.diffcalc.live.taiko.difficulty.preprocessing.rhythm;

import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.rhythm.data.SamePatternsGroupedHitObjects;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.rhythm.data.SameRhythmHitObjectGrouping;
import alchyr.diffcalc.live.taiko.difficulty.utils.IntervalGroupingUtils;

import java.util.ArrayList;
import java.util.List;

public class TaikoRhythmDifficultyPreprocessor {
    public static void processAndAssign(List<TaikoDifficultyHitObject> hitObjects) {
        List<SameRhythmHitObjectGrouping> rhythmGroups = createSameRhythmGroupedHitObjects(hitObjects);

        for (SameRhythmHitObjectGrouping rhythmGroup : rhythmGroups) {
            for (TaikoDifficultyHitObject obj : rhythmGroup.hitObjects) {
                obj.rhythmData.sameRhythmGroupedHitObjects = rhythmGroup;
            }
        }

        List<SamePatternsGroupedHitObjects> patternGroups = createSamePatternGroupedHitObjects(rhythmGroups);

        for (SamePatternsGroupedHitObjects patternGroup : patternGroups) {
            for (TaikoDifficultyHitObject obj : patternGroup.allHitObjects()) {
                obj.rhythmData.samePatternsGroupedHitObjects = patternGroup;
            }
        }
    }

    private static List<SameRhythmHitObjectGrouping> createSameRhythmGroupedHitObjects(List<TaikoDifficultyHitObject> hitObjects) {
        List<SameRhythmHitObjectGrouping> rhythmGroups = new ArrayList<>();

        for (List<TaikoDifficultyHitObject> grouped : IntervalGroupingUtils.groupByInterval(hitObjects)) {
            rhythmGroups.add(new SameRhythmHitObjectGrouping(rhythmGroups.isEmpty() ? null : rhythmGroups.get(rhythmGroups.size() - 1), grouped));
        }

        return rhythmGroups;
    }

    private static List<SamePatternsGroupedHitObjects> createSamePatternGroupedHitObjects(List<SameRhythmHitObjectGrouping> rhythmGroups) {
        List<SamePatternsGroupedHitObjects> patternGroups = new ArrayList<>();

        for (List<SameRhythmHitObjectGrouping> grouped : IntervalGroupingUtils.groupByInterval(rhythmGroups))
            patternGroups.add(new SamePatternsGroupedHitObjects(patternGroups.isEmpty() ? null : patternGroups.get(patternGroups.size() - 1), grouped));

        return patternGroups;
    }
}
