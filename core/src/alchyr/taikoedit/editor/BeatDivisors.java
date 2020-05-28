package alchyr.taikoedit.editor;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.maps.components.TimingPoint;

import java.util.*;

public class BeatDivisors {
    public static final int[] commonSnappings = new int[] {
            1, 2, 3, 4, 5, 6, 7, 8, 12, 16
    };

    //should be shared between ALL beat divisors as well as in editorlayer
    private DivisorOptions divisorOptions;

    private EditorBeatmap timingMap; //The map whose timing will be used to generate objects.

    private final HashMap<Integer, HashSet<Snap>> divisorSnappings;
    private TreeMap<Integer, Snap> combinedSnaps;

    private int lastStart, lastEnd;
    private NavigableMap<Integer, Snap> activeSnaps;

    //When initially generated, construct all standard divisors. If timing is changed or another custom divisor is desired, they will be generated on-demand.


    public BeatDivisors(DivisorOptions divisorOptions, EditorBeatmap timingMap)
    {
        this.divisorOptions = divisorOptions;
        this.divisorOptions.addDependent(this);

        this.timingMap = timingMap;

        divisorSnappings = new HashMap<>();
        combinedSnaps = new TreeMap<>();

        generateCommonSnappings();
    }

    public void refresh()
    {
        combinedSnaps.clear();
        generateCombinedSnaps();
    }

    public NavigableMap<Integer, Snap> getSnaps(int startPos, int endPos)
    {
        if (combinedSnaps.isEmpty())
        {
            generateCombinedSnaps();
        }

        if (startPos != lastStart || endPos != lastEnd)
        {
            Integer start = combinedSnaps.floorKey(startPos);
            Integer end = combinedSnaps.ceilingKey(endPos);

            if (start != null && end != null)
            {
                activeSnaps = combinedSnaps.subMap(start, true, end, true);
            }
            else if (end != null)
            {
                activeSnaps = combinedSnaps.subMap(combinedSnaps.firstKey(), true, end, true);
            }
            else if (start != null)
            {
                activeSnaps = combinedSnaps.subMap(start, true, combinedSnaps.lastKey(), true);
            }
            else
            {
                activeSnaps = combinedSnaps;
            }
        }
        return activeSnaps;
    }
    public TreeMap<Integer, Snap> getSnaps()
    {
        if (combinedSnaps.isEmpty())
        {
            generateCombinedSnaps();
        }
        return combinedSnaps;
    }


    private void generateCombinedSnaps()
    {
        for (int divisor : divisorOptions.activeSnappings)
            for (Snap s : getSnappings(divisor))
                combinedSnaps.put(s.pos, s);
    }


    private void generateCommonSnappings()
    {
        for (int i : commonSnappings)
        {
            generateSnappings(i);
        }
    }

    private HashSet<Snap> getSnappings(int divisor)
    {
        if (!divisorSnappings.containsKey(divisor))
        {
            generateSnappings(divisor);
            for (int existingDivisor : divisorSnappings.keySet())
            {
                if (existingDivisor != divisor && existingDivisor % divisor == 0) //The newly generated divisor is a sub-set of this existing set. Re-generate it.
                {
                    generateSnappings(existingDivisor);
                }
            }
        }
        return divisorSnappings.get(divisor);
    }

    private void generateSnappings(int divisor)
    {
        HashSet<Snap> snappings = new HashSet<>(); //Sorted set as it must be iterated through in a reliable order.

        if (divisor <= 0)
        {
            divisorSnappings.put(divisor, snappings);
            return;
        }

        HashSet<Integer> subSnaps = new HashSet<>(); //Contains all the points that shouldn't be repeated. HashSet as it is used entirely for contains() operations.
        for (Map.Entry<Integer, HashSet<Snap>> snaps : divisorSnappings.entrySet())
        {
            if (divisor % snaps.getKey() == 0) //This snap is a sub-snap of the currently generating one
            {
                for (Snap snap : snaps.getValue())
                    subSnaps.add(snap.pos);
            }
        }

        TimingPoint currentPoint = null, nextPoint = null;
        for (ArrayList<TimingPoint> t : timingMap.timingPoints.values())
        {
            //There *shouldn't* be stacked timing points. But if there are, just use the last one.
            //TODO: Create a warning for stacked timing points.
            currentPoint = nextPoint;
            nextPoint = t.get(t.size() - 1);
            int until = nextPoint.pos;
            if (currentPoint == null)
            {
                generateReverseSnappings(snappings, subSnaps, divisor, nextPoint.pos, nextPoint.value, nextPoint.meter);
                continue;
            }

            subGenerateSnappings(snappings, subSnaps, divisor, currentPoint.pos, currentPoint.value, currentPoint.meter, until, currentPoint.omitted);
        }

        if (nextPoint != null)
        {
            subGenerateSnappings(snappings, subSnaps, divisor, nextPoint.pos, nextPoint.value, nextPoint.meter, EditorLayer.music.getMsLength(), nextPoint.omitted);
        }

        divisorSnappings.put(divisor, snappings);
    }

    private void subGenerateSnappings(HashSet<Snap> snapList, HashSet<Integer> ignoreSnaps, int divisor, double start, double rate, int meter, int endPoint, boolean skipLine)
    {
        int pos;
        int beatSegment = divisor - 1, beat = -1;

        for (double t = start; t < endPoint; t += rate / divisor)
        {
            beatSegment = ++beatSegment % divisor;
            if (beatSegment == 0)
                ++beat;

            pos = (int) t;

            beat = beat % meter;

            if (ignoreSnaps.contains(pos) || ignoreSnaps.contains(pos + 1) || ignoreSnaps.contains(pos - 1))
                continue;

            if (beat == 0 && beatSegment == 0)
            {
                if (!skipLine)
                {
                    snapList.add(new Snap(pos, 0));
                    continue;
                }

                skipLine = false;
            }
            snapList.add(new Snap(pos, divisor));
        }
    }

    private void generateReverseSnappings(HashSet<Snap> snapList, HashSet<Integer> ignoreSnaps, int divisor, double start, double rate, int meter)
    {
        int pos;
        int beatSegment = divisor, beat = meter;

        for (double t = start - rate / divisor; t > 0; t -= rate / divisor)
        {
            if (beat == 0)
                beat += meter;

            --beatSegment;
            if (beatSegment == 0)
            {
                beatSegment += divisor;
                --beat;
            }

            pos = (int) t;

            if (ignoreSnaps.contains(pos) || ignoreSnaps.contains(pos + 1) || ignoreSnaps.contains(pos - 1))
                continue;

            snapList.add(new Snap(pos, beat == 0 ? 0 : divisor));
        }
    }

    public void dispose()
    {
        timingMap = null;
        divisorOptions = null;
        divisorSnappings.clear();
        combinedSnaps = null;
        activeSnaps = null;
    }
}
