package alchyr.diffcalc.live.taiko.difficulty.utils;

import alchyr.diffcalc.live.Precision;

import java.util.ArrayList;
import java.util.List;

public class IntervalGroupingUtils {
    public static final double MARGIN_OF_ERROR = 5.0;

    public static <T extends HasInterval> List<List<T>> groupByInterval(List<T> objects) {
        List<List<T>> groups = new ArrayList<>();

        int i = 0;
        while (i < objects.size()) {
            ArrayList<T> next = new ArrayList<>();
            i = createNextGroup(objects, next, i);
            groups.add(next);
        }

        return groups;
    }

    private static <T extends HasInterval> int createNextGroup(List<T> objects, List<T> building, int i) {
        building.add(objects.get(i));
        ++i;

        for (; i < objects.size() - 1; ++i) {
            if (!Precision.AlmostEquals(objects.get(i).getInterval(), objects.get(i + 1).getInterval(), MARGIN_OF_ERROR)) {
                if (objects.get(i + 1).getInterval() > objects.get(i).getInterval() + MARGIN_OF_ERROR) {
                    building.add(objects.get(i));
                    ++i;
                }

                return i;
            }

            building.add(objects.get(i));
        }

        if (objects.size() > 2 && i < objects.size() && Precision.AlmostEquals(objects.get(objects.size() - 1).getInterval(), objects.get(objects.size() - 2).getInterval(), MARGIN_OF_ERROR)) {
            building.add(objects.get(i));
            ++i;
        }

        return i;
    }
}
