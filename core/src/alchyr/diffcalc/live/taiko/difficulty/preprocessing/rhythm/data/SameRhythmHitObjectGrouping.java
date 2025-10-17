package alchyr.diffcalc.live.taiko.difficulty.preprocessing.rhythm.data;

import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.utils.DeltaTimeNormaliser;
import alchyr.diffcalc.live.taiko.difficulty.utils.HasInterval;
import alchyr.diffcalc.live.taiko.difficulty.utils.IntervalGroupingUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SameRhythmHitObjectGrouping implements HasInterval {
    private static final double snap_tolerance = IntervalGroupingUtils.MARGIN_OF_ERROR;

    public final List<TaikoDifficultyHitObject> hitObjects;

    public TaikoDifficultyHitObject firstHitObject() {
        return hitObjects.get(0);
    }

    public final SameRhythmHitObjectGrouping previous;

    public double startTime() {
        return firstHitObject().startTime;
    }

    public double duration() {
        return hitObjects.get(hitObjects.size() - 1).startTime - startTime();
    }

    public final Double hitObjectInterval;
    public final double hitObjectIntervalRatio;

    private double interval = Double.POSITIVE_INFINITY;
    @Override
    public double getInterval() {
        return interval;
    }

    public SameRhythmHitObjectGrouping(SameRhythmHitObjectGrouping previous, List<TaikoDifficultyHitObject> hitObjects) {
        this.previous = previous;
        this.hitObjects = hitObjects;

        Map<TaikoDifficultyHitObject, Double> normaliserHitObjects = DeltaTimeNormaliser.Normalise(hitObjects, snap_tolerance);
        List<Double> normalisedHitObjectDeltaTime = hitObjects.stream().skip(1).map(normaliserHitObjects::get).collect(Collectors.toList());

        double modalDelta = normalisedHitObjectDeltaTime.isEmpty() ? 0 : Math.round(normalisedHitObjectDeltaTime.get(0));

        if (!normalisedHitObjectDeltaTime.isEmpty()) {
            if (previous != null && previous.hitObjectInterval != null && Math.abs(modalDelta - previous.hitObjectInterval) <= snap_tolerance)
                hitObjectInterval = previous.hitObjectInterval;
            else
                hitObjectInterval = modalDelta;
        }
        else {
            hitObjectInterval = null;
        }

        hitObjectIntervalRatio = previous != null && previous.hitObjectInterval != null && hitObjectInterval != null ?
                hitObjectInterval / previous.hitObjectInterval : 1.0;

        if (previous != null) {
            if (Math.abs(startTime() - previous.startTime()) <= snap_tolerance) {
                interval = 0;
            }
            else {
                interval = startTime() - previous.startTime();
            }
        }
    }
}
