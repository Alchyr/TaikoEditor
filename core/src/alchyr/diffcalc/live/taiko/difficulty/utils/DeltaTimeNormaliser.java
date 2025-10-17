package alchyr.diffcalc.live.taiko.difficulty.utils;

import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;

import java.util.*;
import java.util.stream.Collectors;

public class DeltaTimeNormaliser {
    public static Map<TaikoDifficultyHitObject, Double> Normalise(List<TaikoDifficultyHitObject> hitObjects, double marginOfError) {
        List<Double> deltaTimes = hitObjects.stream().map((obj) -> obj.deltaTime).distinct().sorted().collect(Collectors.toList());

        List<List<Double>> sets = new ArrayList<>();
        List<Double> current = null;

        for (double val : deltaTimes) {
            if (current != null && Math.abs(val - current.get(0)) <= marginOfError) {
                current.add(val);
                continue;
            }

            current = new ArrayList<>(3);
            current.add(val);
        }

        Map<Double, Double> medianLookup = new HashMap<>();

        for (List<Double> set : sets) {
            set.sort(null);
            int mid = set.size() / 2;
            double median = set.size() % 2 == 1 ? set.get(mid) : (set.get(mid - 1) + set.get(mid)) / 2;

            for (double val : set) {
                medianLookup.put(val, median);
            }
        }

        Map<TaikoDifficultyHitObject, Double> normalised = new HashMap<>();
        hitObjects.forEach((obj) -> normalised.put(obj, medianLookup.getOrDefault(obj.deltaTime, obj.deltaTime)));
        return normalised;
    }
}
