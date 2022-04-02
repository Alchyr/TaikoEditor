package alchyr.taikoedit.editor;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;

import java.util.*;

public class BeatDivisors {
    public static final int[] commonSnappings = new int[] {
            1, 2, 3, 4, 5, 6, 7, 8, 12, 16
    };

    //should be shared between ALL beat divisors as well as in editorlayer
    private DivisorOptions divisorOptions;

    private EditorBeatmap timingMap; //The map whose timing will be used to generate objects.

    private final HashMap<Integer, HashSet<Snap>> divisorSnappings;
    private TreeMap<Long, Snap> combinedSnaps;
    private TreeMap<Long, Snap> allSnaps;
    private TreeMap<Long, Snap> barlineSnaps;

    private int lastStart, lastEnd;
    private NavigableMap<Long, Snap> activeSnaps;

    //When initially generated, construct all standard divisors. If timing is changed or another custom divisor is desired, they will be generated on-demand.


    public BeatDivisors(DivisorOptions divisorOptions, EditorBeatmap timingMap)
    {
        this.divisorOptions = divisorOptions;
        this.divisorOptions.addDependent(this);

        this.timingMap = timingMap;

        divisorSnappings = new HashMap<>();
        combinedSnaps = new TreeMap<>();
        allSnaps = new TreeMap<>();
        barlineSnaps = new TreeMap<>();

        generateCommonSnappings();
    }

    public void refresh()
    {
        combinedSnaps.clear();
        allSnaps.clear();
        generateCombinedSnaps();
    }

    public NavigableMap<Long, Snap> getSnaps(double startPos, double endPos)
    {
        if (combinedSnaps.isEmpty() && !divisorOptions.activeSnappings.isEmpty())
        {
            generateCombinedSnaps();
        }

        if (startPos != lastStart || endPos != lastEnd)
        {
            Long start = combinedSnaps.floorKey((long) startPos);
            Long end = combinedSnaps.ceilingKey((long) endPos);

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
    public TreeMap<Long, Snap> getSnaps()
    {
        if (combinedSnaps.isEmpty() && !divisorOptions.activeSnappings.isEmpty())
        {
            generateCombinedSnaps();
        }
        return combinedSnaps;
    }
    public TreeMap<Long, Snap> getAllSnaps()
    {
        return allSnaps;
    }
    public TreeMap<Long, Snap> getBarlines() {
        return barlineSnaps;
    }


    private void generateCombinedSnaps()
    {
        for (int divisor : divisorOptions.activeSnappings)
            for (Snap s : getSnappings(divisor))
                combinedSnaps.put((long) s.pos, s);

        for (int divisor : divisorOptions.snappingOptions)
            for (Snap s : getSnappings(divisor)) {
                allSnaps.put((long) s.pos, s);
            }
    }


    private void generateCommonSnappings()
    {
        for (int i : commonSnappings)
        {
            generateSnappings(i);
        }
    }

    public HashSet<Snap> getSnappings(int divisor)
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
        HashSet<Snap> snappings = new HashSet<>();

        if (divisor <= 0) //0 = no snaps, negative = Why
        {
            divisorSnappings.put(divisor, snappings);
            return;
        }

        HashSet<Long> subSnaps = new HashSet<>(); //Contains all the points that shouldn't be repeated. HashSet as it is used entirely for contains() operations.
        for (Map.Entry<Integer, HashSet<Snap>> snaps : divisorSnappings.entrySet())
        {
            if (divisor % snaps.getKey() == 0) //This snap is a sub-snap of the currently generating one, skip them
            {
                for (Snap snap : snaps.getValue())
                    subSnaps.add((long) snap.pos); //rounded to avoid issues when comparing doubles
            }
        }

        TimingPoint currentPoint, nextPoint = null;
        for (ArrayList<TimingPoint> t : timingMap.timingPoints.values())
        {
            //There *shouldn't* be stacked timing points. But if there are, just use the last one.
            //TODO: Create a warning for stacked timing points.
            currentPoint = nextPoint;
            nextPoint = t.get(t.size() - 1);
            long until = nextPoint.getPos();
            if (currentPoint == null) //first point, generate in reverse from the next point
            {
                generateReverseSnappings(snappings, subSnaps, divisor, nextPoint.getPos(), nextPoint.value, nextPoint.meter);
                continue;
            }

            subGenerateSnappings(snappings, subSnaps, divisor, currentPoint.getPos(), currentPoint.value, currentPoint.meter, until, currentPoint.omitted);
        }

        if (nextPoint != null)
        {
            subGenerateSnappings(snappings, subSnaps, divisor, nextPoint.getPos(), nextPoint.value, nextPoint.meter, TaikoEditor.music.getMsLength(), nextPoint.omitted);
        }

        divisorSnappings.put(divisor, snappings);
    }

    private void subGenerateSnappings(HashSet<Snap> snapList, HashSet<Long> ignoreSnaps, int divisor, long start, double rate, int meter, double endPoint, boolean skipLine)
    {
        long pos;
        int beatSegment = divisor - 1, beat = -1;

        if (rate / divisor == 0) {
            return;
        }

        double test = start - 1, mult = 1;
        //In normal progression, multiplier will remain at 1.
        //However, in extreme cases such as near-infinite bpm, mult will continue to increase as snaps are generated in the same position until it grows large enough to cause a change in value.

        for (double t = start; t < endPoint; t += (mult * rate) / divisor)
        {
            if (t == test) { //infinite loop test due to absurdly small rate value resulting in no change although it isn't 0
                //Occurs due to ultra-high (infinite?) bpm resulting in effectively no gap between snaps
                mult += 1;
                if (mult > 64)
                    return;
            }
            else {
                mult = 1;
            }
            test = t;

            beatSegment = ++beatSegment % divisor;
            if (beatSegment == 0)
                ++beat;

            pos = (long) t;

            beat = beat % meter;

            if (ignoreSnaps.contains(pos) || ignoreSnaps.contains(pos + 1) || ignoreSnaps.contains(pos - 1))
                continue;

            if (beat == 0 && beatSegment == 0)
            {
                if (!skipLine)
                {
                    Snap barline = new Snap(t, 0);
                    barlineSnaps.put((long) barline.pos, barline);
                    snapList.add(barline);
                    continue;
                }

                skipLine = false;
            }
            snapList.add(new Snap(t, divisor));
        }
    }

    private void generateReverseSnappings(HashSet<Snap> snapList, HashSet<Long> ignoreSnaps, int divisor, double start, double rate, int meter)
    {
        long pos;
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

            pos = (long) t;

            if (ignoreSnaps.contains(pos) || ignoreSnaps.contains(pos + 1) || ignoreSnaps.contains(pos - 1))
                continue;

            if (beat == 0) {
                Snap barline = new Snap(t, 0);
                barlineSnaps.put((long) barline.pos, barline);
                snapList.add(barline);
            }
            else {
                snapList.add(new Snap(t, divisor));
            }
        }
    }

    public void dispose()
    {
        timingMap = null;
        divisorOptions = null;
        divisorSnappings.clear();
        combinedSnaps = null;
        allSnaps = null;
        activeSnaps = null;
    }
}
