package alchyr.taikoedit.editor;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.management.SettingsMaster;

import java.util.*;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class BeatDivisors implements DivisorOptions.IDivisorListener {
    public static final int[] commonSnappings = new int[] {
            1, 2, 3, 4, 5, 6, 7, 8, 12, 16
    };

    //should be shared between ALL beat divisors as well as in editorlayer
    private DivisorOptions divisorOptions;

    private EditorBeatmap timingMap; //The map whose timing will be used to generate objects.

    //sets of snap points specific to divisors
    private final HashMap<Integer, HashSet<Snap>> divisorSnappings;
    private final Set<Integer> currentCombinedDivisors;

    private TreeMap<Long, Snap> combinedSnaps; //combined snappings for current enabled divisors

    private TreeMap<Long, Snap> allSnaps; //all snappings
    private TreeMap<Long, Snap> barlineSnaps; //just barlines, every X 1/1

    private double lastStart, lastEnd;
    private NavigableMap<Long, Snap> activeSnaps;

    //When initially generated, construct all standard divisors. If timing is changed or another custom divisor is desired, they will be generated on-demand.


    public BeatDivisors(DivisorOptions divisorOptions, EditorBeatmap timingMap)
    {
        this.divisorOptions = divisorOptions;
        this.divisorOptions.addDependent(this);

        this.timingMap = timingMap;

        divisorSnappings = new HashMap<>();
        combinedSnaps = new TreeMap<>();
        currentCombinedDivisors = new HashSet<>();
        allSnaps = new TreeMap<>();
        barlineSnaps = new TreeMap<>();

        generateCommonSnappings();
        editorLogger.info("Created new BeatDivisors.");
    }

    public boolean usesMap(EditorBeatmap map) {
        return timingMap.equals(map);
    }

    public void setTimingMap(EditorBeatmap editorBeatmap) {
        this.timingMap = editorBeatmap;
        refresh();
    }

    public void refresh()
    {
        //combinedSnaps.clear();
        generateCombinedSnaps();
    }
    public void reset()
    {
        divisorSnappings.clear();
        combinedSnaps.clear();
        allSnaps.clear();
        barlineSnaps.clear();
        generateCombinedSnaps();
    }

    public NavigableMap<Long, Snap> getSnaps(double startPos, double endPos)
    {
        if (combinedSnaps.isEmpty() && !divisorOptions.activeDivisors.isEmpty())
        {
            generateCombinedSnaps();
        }

        if (startPos != lastStart || endPos != lastEnd)
        {
            lastStart = startPos; //testing
            lastEnd = endPos;

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
        if (combinedSnaps.isEmpty() && !divisorOptions.activeDivisors.isEmpty())
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
        currentCombinedDivisors.clear();
        combinedSnaps.clear();

        for (int divisor : divisorOptions.activeDivisors)
            for (Snap s : getSnappings(divisor))
                combinedSnaps.put(s.pos, s);

        currentCombinedDivisors.addAll(divisorOptions.activeDivisors);
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
        if (!divisorSnappings.containsKey(divisor) || divisorSnappings.get(divisor).isEmpty())
        {
            generateSnappings(divisor);
            for (int existingDivisor : divisorSnappings.keySet())
            {
                if (existingDivisor != divisor && existingDivisor % divisor == 0) //The newly generated divisor is a sub-set of this existing set. Re-generate it.
                {
                    generateSnappings(existingDivisor); //The snappings in this re-generated set will have some of them replaced with the sub-set's snappings.
                }
            }
        }
        return divisorSnappings.get(divisor);
    }

    public HashSet<Snap> getCombinedSnaps(int divisor) {
        if (!divisorSnappings.containsKey(divisor) || divisorSnappings.get(divisor).isEmpty()) {
            boolean modifiedActive = divisorOptions.activeDivisors.contains(divisor);

            generateSnappings(divisor);
            for (int existingDivisor : divisorSnappings.keySet())
            {
                if (existingDivisor != divisor && existingDivisor % divisor == 0) //The newly generated divisor is a sub-set of this existing set. Re-generate it.
                {
                    generateSnappings(existingDivisor); //The snappings in this re-generated set will have some of them replaced with the sub-set's snappings.
                    modifiedActive |= divisorOptions.activeDivisors.contains(existingDivisor);
                }
            }
            if (modifiedActive)
                refresh();
        }
        HashSet<Snap> combined = new HashSet<>();
        for (int existingDivisor : divisorSnappings.keySet())
        {
            if (divisor % existingDivisor == 0)
                combined.addAll(divisorSnappings.get(existingDivisor));
        }
        return combined;
    }

    private void generateSnappings(int divisor)
    {
        if (timingMap == null) {
            editorLogger.warn("timingMap is null");
            return;
        }
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
                    subSnaps.add(snap.pos);
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

        long step = 0;
        //In normal progression, multiplier will remain at 1.
        //However, in extreme cases such as near-infinite bpm, mult will continue to increase as snaps are generated in the same position until it grows large enough to cause a change in value.

        for (double t = start; t < endPoint; t = start + (step * rate) / divisor)
        {
            ++beatSegment;
            ++step;
            if (beatSegment >= divisor) {
                beatSegment -= divisor;
                ++beat;
            }

            pos = SettingsMaster.roundPos(t);

            beat = beat % meter;

            if (ignoreSnaps.contains(pos) || ignoreSnaps.contains(pos + 1) || ignoreSnaps.contains(pos - 1))
                continue;

            if (beat == 0 && beatSegment == 0)
            {
                if (!skipLine)
                {
                    Snap barline = new Snap(t, 0);
                    barlineSnaps.put(barline.pos, barline);
                    snapList.add(barline);
                    allSnaps.merge(barline.pos, barline, (oldSnap, newSnap)->newSnap.divisor <= oldSnap.divisor ? newSnap : oldSnap);
                    continue;
                }

                skipLine = false;
            }
            Snap snap = new Snap(t, divisor);
            snapList.add(snap);
            allSnaps.merge(snap.pos, snap, (oldSnap, newSnap)->newSnap.divisor <= oldSnap.divisor ? newSnap : oldSnap);
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

            pos = SettingsMaster.roundPos(t);

            if (ignoreSnaps.contains(pos) || ignoreSnaps.contains(pos + 1) || ignoreSnaps.contains(pos - 1))
                continue;

            if (beat == 0) {
                Snap barline = new Snap(t, 0);
                barlineSnaps.put(barline.pos, barline);
                snapList.add(barline);
                allSnaps.merge(barline.pos, barline, (oldSnap, newSnap)->newSnap.divisor <= oldSnap.divisor ? newSnap : oldSnap);
            }
            else {
                Snap snap = new Snap(t, divisor);
                snapList.add(snap);
                allSnaps.merge(snap.pos, snap, (oldSnap, newSnap)->newSnap.divisor <= oldSnap.divisor ? newSnap : oldSnap);
            }
        }
    }

    public void dispose()
    {
        divisorOptions.removeDependent(this);
        timingMap = null;
        divisorSnappings.clear();
        combinedSnaps.clear();
        allSnaps.clear();
        activeSnaps.clear();
        editorLogger.info("Beat Divisors cleaned up.");
    }
}
