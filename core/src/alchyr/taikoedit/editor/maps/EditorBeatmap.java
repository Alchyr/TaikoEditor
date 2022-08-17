package alchyr.taikoedit.editor.maps;

import alchyr.taikoedit.editor.BeatDivisors;
import alchyr.taikoedit.editor.DivisorOptions;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.Timeline;
import alchyr.taikoedit.editor.changes.*;
import alchyr.taikoedit.editor.views.EffectView;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.ILongObject;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.editor.maps.components.hitobjects.Slider;
import alchyr.taikoedit.editor.views.GameplayView;
import alchyr.taikoedit.util.GeneralUtils;
import alchyr.taikoedit.management.assets.FileHelper;
import alchyr.taikoedit.util.structures.Pair;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;
import com.badlogic.gdx.utils.Queue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

//Will be referenced by displays for rendering, and modifications in editor will be performed on it to ensure all displays are connected to the same map object
public class EditorBeatmap {
    private static final int BOOKMARK_REMOVE_DIST = 1000; //ms gap on either side of deletion attempt where a bookmark can be removed

    public boolean dirty = false; //Are there unsaved changes

    //For hitobjects/timing points use a structure that allows for fast find/insertion at the desired position but also fast iteration?
    public final PositionalObjectTreeMap<TimingPoint> timingPoints; //red lines
    public final PositionalObjectTreeMap<TimingPoint> effectPoints; //green lines
    public final PositionalObjectTreeMap<TimingPoint> allPoints; //should not be modified directly? Accessibility is intended for iteration? Should probably make a readonly accessor but meh
    public final PositionalObjectTreeMap<HitObject> objects;

    private final TreeMap<Long, Integer> volumeMap;
    private final TreeMap<Long, Boolean> kiaiMap; //each boolean is a spot where kiai is turned on or off.

    public boolean autoBreaks = true; //set to false if invalid breaks on load, which will disable automatic modification of breaks.

    private BeatDivisors divisor;


    private FullMapInfo fullMapInfo;


    //For edit objects
    private NavigableMap<Long, ArrayList<HitObject>> editObjects;
    private long lastObjectStart = Long.MIN_VALUE, lastObjectEnd = Long.MIN_VALUE;

    private NavigableMap<Long, ArrayList<TimingPoint>> editEffectPoints;
    private long lastEffectPointStart = Long.MIN_VALUE, lastEffectPointEnd = Long.MIN_VALUE;

    private NavigableMap<Long, ArrayList<TimingPoint>> visibleTimingPoints;
    private long lastTimingPointStart = Long.MIN_VALUE, lastTimingPointEnd = Long.MIN_VALUE;


    //NVM: When editing timing is implemented, add a hook "OnTimingChanged" which will be used by BeatDivisors to re-generate whatever values are necessary
    //This should also be used to ensure that if sameSong is true, timing changes will be kept synced with the other difficulties.
    //Not necessary, timing will never be implemented for offset reasons.

    //Not the most "ideal" method, but it's quick and easy.
    private EffectView effectView = null;
    private GameplayView gameplayView = null;
    private Timeline timeline = null;


    //Loading map from file
    public EditorBeatmap(Mapset set, MapInfo map)
    {
        timingPoints = new PositionalObjectTreeMap<>();
        effectPoints = new PositionalObjectTreeMap<>();
        allPoints = new PositionalObjectTreeMap<>();
        objects = new PositionalObjectTreeMap<>();

        volumeMap = new TreeMap<>();
        kiaiMap = new TreeMap<>();

        parse(set, map);
    }
    //Creating new map
    public EditorBeatmap(EditorBeatmap base, FullMapInfo map, boolean keepObjects, boolean keepSv, boolean keepVolume)
    {
        timingPoints = new PositionalObjectTreeMap<>();
        effectPoints = new PositionalObjectTreeMap<>();
        allPoints = new PositionalObjectTreeMap<>();
        objects = new PositionalObjectTreeMap<>();

        volumeMap = new TreeMap<>();
        kiaiMap = new TreeMap<>();

        this.fullMapInfo = map;

        for (Map.Entry<Long, ArrayList<TimingPoint>> points : base.timingPoints.entrySet()) {
            for (TimingPoint p : points.getValue()) {
                TimingPoint cpy = new TimingPoint(p);
                if (!keepVolume) {
                    cpy.volume = 100;
                    cpy.kiai = false;
                }
                timingPoints.add(cpy);
                volumeMap.put(cpy.getPos(), cpy.volume);
            }
        }

        if (keepSv && keepVolume) {
            for (Map.Entry<Long, ArrayList<TimingPoint>> points : base.effectPoints.entrySet()) {
                for (TimingPoint p : points.getValue()) {
                    effectPoints.add(new TimingPoint(p));
                    volumeMap.put(p.getPos(), p.volume);
                }
            }
        }
        else if (keepSv) {
            for (Map.Entry<Long, ArrayList<TimingPoint>> points : base.effectPoints.entrySet()) {
                for (TimingPoint p : points.getValue()) {
                    TimingPoint cpy = new TimingPoint(p);
                    cpy.volume = 100;
                    cpy.kiai = false;
                    effectPoints.add(new TimingPoint(cpy));
                    volumeMap.put(cpy.getPos(), cpy.volume);
                }
            }
        }
        else if (keepVolume) {
            //Keep volume/kiai changes but not sv changes.
            //It's easier to filter the unnecessary points out after combining into the allPoints map.
            for (Map.Entry<Long, ArrayList<TimingPoint>> points : base.effectPoints.entrySet()) {
                for (TimingPoint p : points.getValue()) {
                    TimingPoint cpy = new TimingPoint(p);
                    cpy.setValue(1);
                    effectPoints.add(new TimingPoint(cpy));
                }
            }
        }

        allPoints.addAll(timingPoints);
        allPoints.addAll(effectPoints);

        boolean kiaiActive = false;
        int lastVolume = Integer.MIN_VALUE;

        if (keepVolume && !keepSv) {
            Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> stackIterator = allPoints.entrySet().iterator();
            Iterator<TimingPoint> pointIterator;
            ArrayList<TimingPoint> nextStack;
            TimingPoint next;

            boolean stackKiai = false;

            while (stackIterator.hasNext()) {
                nextStack = stackIterator.next().getValue();

                if (nextStack != null) {
                    pointIterator = nextStack.iterator();

                    while (pointIterator.hasNext()) {
                        next = pointIterator.next();

                        if (!next.uninherited && next.volume == lastVolume && next.kiai == kiaiActive) {
                            //Remove green lines with the same settings as the last non-removed line
                            pointIterator.remove();
                            if (next.uninherited) {
                                timingPoints.removeObject(next);
                            }
                            else {
                                effectPoints.removeObject(next);
                            }
                        }
                        else {
                            lastVolume = next.volume;
                            kiaiActive = next.kiai;
                        }
                    }

                    if (nextStack.isEmpty()) {
                        stackIterator.remove();
                    }
                    else {
                        next = GeneralUtils.listLast(nextStack);
                        volumeMap.put(next.getPos(), next.volume);
                        if (next.kiai != stackKiai) {
                            kiaiMap.put(next.getPos(), next.kiai);
                            stackKiai = next.kiai;
                        }
                    }
                }
            }
        }
        else {
            //generate kiai map
            for (Map.Entry<Long, ArrayList<TimingPoint>> stack : allPoints.entrySet()) {
                TimingPoint t = GeneralUtils.listLast(stack.getValue());

                if (t.kiai != kiaiActive) {
                    kiaiMap.put(t.getPos(), t.kiai);
                    kiaiActive = t.kiai;
                }
            }
        }

        if (keepObjects) {
            for (Map.Entry<Long, ArrayList<HitObject>> stack : base.objects.entrySet()) {
                for (HitObject o : stack.getValue()) {
                    HitObject cpy = (HitObject) o.shiftedCopy(o.getPos());
                    objects.add(cpy);
                }
            }
        }
        else {
            fullMapInfo.breakPeriods.clear();
        }

        if (volumeMap.isEmpty()) {
            volumeMap.put(Long.MAX_VALUE, 60);
        }
        updateVolume(objects);
    }

    public FullMapInfo getFullMapInfo() {
        return fullMapInfo;
    }


    /* EDITING METHODS */
    private final Queue<MapChange> undoQueue = new Queue<>();
    private final Queue<MapChange> redoQueue = new Queue<>();

    // These should be used if the map is changed using ANYTHING other than undo and redo
    //Redo queue is added to when undo is used, and cleared when any change is made.
    //Undo queue fills up... Forever? Changes are added to END of undo queue, and removed from end as well.
    public boolean canUndo() {
        return !undoQueue.isEmpty();
    }
    public boolean undo()
    {
        if (!undoQueue.isEmpty())
        {
            dirty = true;
            redoQueue.addLast(undoQueue.removeLast().undo());
            return redoQueue.last().invalidateSelection;
        }
        return false;
    }
    public boolean canRedo() {
        return !redoQueue.isEmpty();
    }
    public boolean redo()
    {
        if (!redoQueue.isEmpty())
        {
            dirty = true;
            undoQueue.addLast(redoQueue.removeLast().perform());
            return undoQueue.last().invalidateSelection;
        }
        return false;
    }

    public void addObject(HitObject o, BiFunction<PositionalObject, PositionalObject, Boolean> shouldReplace)
    {
        dirty = true;
        undoQueue.addLast(new ObjectAddition(this, o, shouldReplace).perform());
        redoQueue.clear();
    }
    public void delete(MapChange.ChangeType type, NavigableMap<Long, ArrayList<PositionalObject>> deletion)
    {
        dirty = true;
        undoQueue.addLast(new Deletion(this, type, deletion).perform());
        redoQueue.clear();
    }
    public void delete(MapChange.ChangeType type, PositionalObject o)
    {
        dirty = true;
        undoQueue.addLast(new Deletion(this, type, o).perform());
        redoQueue.clear();
    }
    public void paste(PositionalObjectTreeMap<PositionalObject> pasteObjects, BiFunction<PositionalObject, PositionalObject, Boolean> shouldReplace) {
        dirty = true;
        undoQueue.addLast(new ObjectAddition(this, pasteObjects, shouldReplace).perform());
        redoQueue.clear();
    }
    public void pasteLines(PositionalObjectTreeMap<PositionalObject> pasteLines) {
        dirty = true;
        undoQueue.addLast(new LineAddition(this, pasteLines).perform());
        redoQueue.clear();
    }
    /*public void pasteLines(PositionalObjectTreeMap<PositionalObject> pasteLines) {
        dirty = true;
        undoQueue.addLast(new ObjectAddition(this, pasteLines).perform());
        redoQueue.clear();
    }*/
    /*public void replace(MapChange.ChangeType type, PositionalObjectTreeMap<PositionalObject> deleted, PositionalObjectTreeMap<PositionalObject> added) {
        dirty = true;
        undoQueue.addLast(new Replacement(this, type, deleted, added).perform());
        redoQueue.clear();
    }*/
    public void reverse(MapChange.ChangeType type, boolean resnap, PositionalObjectTreeMap<PositionalObject> reversed) {
        dirty = true;
        undoQueue.addLast(new Reverse(this, type, resnap, reversed).perform());
        redoQueue.clear();
    }
    public void registerMovement(MapChange.ChangeType type, PositionalObjectTreeMap<PositionalObject> movementObjects, long offset)
    {
        dirty = true;
        undoQueue.addLast(new Movement(this, type, movementObjects, offset).redo());
        redoQueue.clear();
    }

    public void registerDurationChange(ILongObject obj, long change)
    {
        dirty = true;
        undoQueue.addLast(new DurationChange(this, obj, change));
        adjustedEnd(obj, change);
        redoQueue.clear();
    }
    public void registerValueChange(PositionalObjectTreeMap<PositionalObject> modifiedObjects)
    {
        dirty = true;
        undoQueue.addLast(new ValueModificationChange(this, modifiedObjects));
        redoQueue.clear();
        gameplayChanged();
    }
    public void registerVolumeChange(PositionalObjectTreeMap<PositionalObject> modifiedObjects, PositionalObjectTreeMap<PositionalObject> allChangeObjects) {
        dirty = true;
        undoQueue.addLast(new VolumeModificationChange(this, modifiedObjects, allChangeObjects));
        redoQueue.clear();
    }

    public void registerChange(MapChange change) {
        dirty = true;
        undoQueue.addLast(change);
        redoQueue.clear();
    }


    //General Data
    public NavigableSet<Integer> getBookmarks()
    {
        return fullMapInfo.bookmarks;
    }
    public void addBookmark(int time) {
        dirty = true;
        fullMapInfo.bookmarks.add(time);
    }
    public void removeBookmark(int time) {
        if (fullMapInfo.bookmarks.remove(time)) {
            dirty = true;
            return;
        }
        Integer floor = fullMapInfo.bookmarks.floor(time), ceil = fullMapInfo.bookmarks.ceiling(time);
        if (floor == null && ceil == null)
            return;

        if (floor == null) {
            if (ceil - time < BOOKMARK_REMOVE_DIST) {
                fullMapInfo.bookmarks.remove(ceil);
                dirty = true;
            }
        }
        else if (ceil == null || (time - floor < ceil - time)) {
            if (time - floor < BOOKMARK_REMOVE_DIST) {
                fullMapInfo.bookmarks.remove(floor);
                dirty = true;
            }
        }
        else {
            if (ceil - time < BOOKMARK_REMOVE_DIST) {
                fullMapInfo.bookmarks.remove(ceil);
                dirty = true;
            }
        }
    }

    public long getBreakEndDelay() {
        if (fullMapInfo.ar == 5f) {
            return 1200;
        }
        else if (fullMapInfo.ar < 5f) {
            return Math.round(1200 + 600 * (5 - fullMapInfo.ar) / 5.0);
        }
        else {
            return Math.round(1200 - 750 * (fullMapInfo.ar - 5.0) / 5.0);
        }
    }
    public List<Pair<Long, Long>> getBreaks() { return fullMapInfo.breakPeriods; }




    //Divisors
    public int getDefaultDivisor()
    {
        return fullMapInfo.beatDivisor;
    }

    public void setDivisorObject(BeatDivisors divisor)
    {
        this.divisor = divisor;
    }
    public BeatDivisors generateDivisor(DivisorOptions divisorOptions)
    {
        return divisor = new BeatDivisors(divisorOptions, this);
    }

    public NavigableMap<Long, Snap> getActiveSnaps(double startPos, double endPos)
    {
        return divisor.getSnaps(startPos, endPos);
    }
    //Currently in use snappings (visible or not)
    public TreeMap<Long, Snap> getCurrentSnaps()
    {
        return divisor.getSnaps();
    }
    //All snappings
    public TreeMap<Long, Snap> getAllSnaps()
    {
        return divisor.getAllSnaps();
    }
    public HashSet<Snap> getSnaps(int divisor)
    {
        return this.divisor.getSnappings(divisor);
    }
    public TreeMap<Long, Snap> getBarlineSnaps() { return divisor.getBarlines(); }




    //Objects
    public NavigableMap<Long, ArrayList<HitObject>> getEditObjects(long startPos, long endPos)
    {
        if (startPos != lastObjectStart || endPos != lastObjectEnd)
        {
            lastObjectStart = startPos;
            lastObjectEnd = endPos;
            editObjects = objects.extendedDescendingSubMap(startPos, endPos);
        }
        return editObjects;
    }
    public NavigableMap<Long, ArrayList<HitObject>> getEditObjects()
    {
        return editObjects;
    }
    public NavigableMap<Long, ArrayList<HitObject>> getSubMap(long startPos, long endPos)
    {
        return objects.descendingSubMap(startPos, true, endPos, true);
    }

    public void updateVolume(NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> objects)
    {
        if (objects.isEmpty())
            return;

        Map.Entry<Long, Integer> volumeEntry = volumeMap.floorEntry(objects.firstKey());
        long nextPos = Integer.MIN_VALUE;
        Map.Entry<Long, Integer> next = volumeMap.higherEntry(objects.firstKey());
        if (next != null)
            nextPos = next.getKey();

        for (Map.Entry<Long, ? extends ArrayList<? extends PositionalObject>> entry : objects.entrySet())
        {
            while (next != null && nextPos <= entry.getKey())
            {
                volumeEntry = next;

                next = volumeMap.higherEntry(nextPos);
                if (next != null)
                    nextPos = next.getKey();
            }

            float volume = volumeEntry != null ? volumeEntry.getValue() / 100.0f : (next != null ? next.getValue() / 100.0f : 1.0f);

            for (PositionalObject h : entry.getValue())
                ((HitObject)h).volume = volume;
        }
    }
    public void updateVolume(List<Pair<Long, ArrayList<HitObject>>> objects)
    {
        if (objects.isEmpty())
            return;

        Map.Entry<Long, Integer> volumeEntry = volumeMap.floorEntry(objects.get(0).a);
        long nextPos = Integer.MIN_VALUE;
        Map.Entry<Long, Integer> next = volumeMap.higherEntry(objects.get(0).a);
        if (next != null)
            nextPos = next.getKey();

        for (Pair<Long, ArrayList<HitObject>> entry : objects)
        {
            while (next != null && nextPos <= entry.a)
            {
                volumeEntry = next;

                next = volumeMap.higherEntry(nextPos);
                if (next != null)
                    nextPos = next.getKey();
            }

            float volume = volumeEntry != null ? volumeEntry.getValue() / 100.0f : (next != null ? next.getValue() / 100.0f : 1.0f);

            for (HitObject h : entry.b)
                h.volume = volume;
        }
    }

    public void updateVolume(HitObject h)
    {
        Map.Entry<Long, Integer> volumeEntry = volumeMap.floorEntry(h.getPos());
        if (volumeEntry == null)
            volumeEntry = volumeMap.ceilingEntry(h.getPos());
        h.volume = volumeEntry != null ? volumeEntry.getValue() / 100.0f : 1.0f;
    }

    //Updates volume of all objects from a given position to the next point after given position
    public void updateVolume(long startPos) {
        Long end = allPoints.higherKey(startPos);
        if (end == null)
            end = Long.MAX_VALUE;
        Map.Entry<Long, Integer> volumeEntry = volumeMap.floorEntry(startPos);
        if (volumeEntry == null)
            volumeEntry = volumeMap.ceilingEntry(startPos);
        float volume = volumeEntry == null ? 1.0f : volumeEntry.getValue() / 100.0f;
        for (ArrayList<HitObject> stack : objects.subMap(startPos, true, end, false).values()) {
            for (HitObject o : stack)
                o.volume = volume;
        }
    }

    //Automatic break adjustments are not tracked for undo/redo.
    //Should be called before the object(s) are actually added.
    public void preAddObject(HitObject h) {
        if (autoBreaks && (!objects.containsKey(h.getPos()) || h instanceof ILongObject)) { //wasn't already something here
            //Things to check when an object is added:
            //First - Is it in the middle of an existing break?
            //If it is, remove that break.
            //Second - If it is more than 5000 ms from preceding/following objects, generate necessary breaks.

            Iterator<Pair<Long, Long>> breakIterator = getBreaks().iterator();
            int addIndex = 0;
            Pair<Long, Long> breakSection = null;
            long breakStart, breakEnd = Long.MAX_VALUE;

            Map.Entry<Long, ArrayList<HitObject>> tempStackA, tempStackB;

            if (breakIterator.hasNext()) {
                //prep break info
                breakSection = breakIterator.next();

                tempStackA = objects.ceilingEntry(breakSection.b);
                if (tempStackA != null) {
                    breakEnd = tempStackA.getKey();
                }
            }

            while (h.getPos() >= breakEnd) { //This stack is past this break, and cannot affect it.
                if (breakIterator.hasNext()) {
                    breakSection = breakIterator.next();
                    ++addIndex;

                    breakEnd = Long.MAX_VALUE;

                    tempStackA = objects.ceilingEntry(breakSection.b);
                    if (tempStackA != null) {
                        breakEnd = tempStackA.getKey();
                    }
                }
                else {
                    breakSection = null; //no more breaks.
                    break;
                }
            }
            if (breakSection != null) {
                breakStart = Long.MIN_VALUE;

                tempStackA = objects.floorEntry(breakSection.a);
                if (tempStackA != null) {
                    breakStart = getEnd(tempStackA.getValue());
                }

                if (h.getEndPos() > breakStart) {
                    //overlaps with current break.
                    long firstBreakStart = breakStart;

                    //Handle the section after the object's start.
                    //A very long object could require adjusting multiple breaks.
                    while (h.getEndPos() > breakStart) { //assumes non-overlapping break times.
                        if (h.getEndPos() > breakEnd - (850 + getBreakEndDelay())) {
                            //Totally covers this break.
                            breakIterator.remove(); //Remove it, move on.
                            if (breakIterator.hasNext()) {
                                //prep break info
                                breakSection = breakIterator.next();

                                tempStackA = objects.floorEntry(breakSection.a);
                                if (tempStackA != null) {
                                    breakStart = getEnd(tempStackA.getValue());
                                }
                                tempStackA = objects.ceilingEntry(breakSection.b);
                                if (tempStackA != null) {
                                    breakEnd = tempStackA.getKey();
                                }
                            }
                            else {
                                break;
                            }
                        }
                        else { //Only partially covers this break with valid break room remaining.
                            breakSection.a = Math.max(breakSection.a, h.getEndPos() + 200);
                            breakStart = h.getEndPos();
                            if (breakSection.b < breakSection.a + 650)
                                breakSection.b = breakSection.a + 650;
                        }
                    }

                    //Handle the section before the new object's position. This is done last so that the iterator can be used.
                    if (h.getPos() >= firstBreakStart + (850 + getBreakEndDelay())) {
                        //There's room to leave a break here.
                        getBreaks().add(addIndex, new Pair<>(firstBreakStart + 200, h.getPos() - getBreakEndDelay()));
                        sortBreaks();
                    }

                    return;
                }
            }
            //doesn't overlap a break.

            tempStackA = objects.lowerEntry(h.getPos());
            tempStackB = objects.higherEntry(h.getEndPos());

            if (tempStackB != null) {
                if (tempStackB.getKey() - h.getEndPos() >= 5000) {
                    //break time
                    getBreaks().add(addIndex, new Pair<>(h.getEndPos() + 200, tempStackB.getKey() - getBreakEndDelay()));
                    sortBreaks();
                }
            }
            else if (tempStackA != null) {
                //Added after any existing objects.
                //Could require a break before it.
                long start = getEnd(tempStackA.getValue());
                long dist = h.getPos() - start;

                if (dist >= 5000) {
                    getBreaks().add(addIndex, new Pair<>(start + 200, h.getPos() - getBreakEndDelay()));
                    sortBreaks();
                }
            }
            //If neither are null, this object was added between two existing objects in a section without a break.
            //Assuming breaks are working as they should, that means this section is guaranteed to not require a break.
            //That, or it was placed in a break that was already removed, which has a potential break which will be adjusted instead.
            //If both are null, there's no gaps to even add a break to, so nothing to worry about.
        }
    }
    public void preAddObjects(NavigableMap<Long, ArrayList<PositionalObject>> added) {
        if (autoBreaks) {
            //Things to check when an object is added:
            //First - Is it in the middle of an existing break?
            //If it is, remove that break.
            //Second - If it is more than 5000 ms from preceding/following objects, generate necessary breaks.
            if (added.isEmpty()) {
                return;
            }

            List<Pair<Long, Long>> breaks = getBreaks();
            ListIterator<Pair<Long, Long>> breakIterator = breaks.listIterator();

            Pair<Long, Long> breakSection = null;
            long breakStart = Long.MIN_VALUE, breakEnd = Long.MAX_VALUE;
            boolean update = true;

            Map<Long, Long> potentialBreaks = new HashMap<>(); //end time, start time
            long endPos = Long.MIN_VALUE, lastEndPos; //Tracking of object times

            Map.Entry<Long, ArrayList<HitObject>> tempStackA, tempStackB;

            if (breakIterator.hasNext()) {
                //prep break info
                breakSection = breakIterator.next();

                tempStackA = objects.ceilingEntry(breakSection.b);
                if (tempStackA != null) {
                    breakEnd = tempStackA.getKey();
                }
            }

            //Check start point
            tempStackA = objects.floorEntry(added.firstKey());
            if (tempStackA != null) {
                endPos = getEnd(tempStackA.getValue());
            }

            //Start processing
            for (Map.Entry<Long, ArrayList<PositionalObject>> stack : added.entrySet()) {
                lastEndPos = endPos; //end of last stack
                endPos = getEnd(stack.getValue()); //end of this stack

                //If position already has object and none of added objects are long objects, skip (and also get actual end pos of stack)
                if (objects.containsKey(stack.getKey())) {
                    if (endPos <= getEnd(objects.get(stack.getKey()))) {
                        //No change caused by this object.
                        continue;
                    }
                }

                //Find the first break that can overlap
                while (stack.getKey() >= breakEnd) { //This stack is past this break, and cannot affect it.
                    if (breakIterator.hasNext()) {
                        breakSection = breakIterator.next();
                        update = true;
                        breakEnd = Long.MAX_VALUE;

                        tempStackA = objects.ceilingEntry(breakSection.b);
                        if (tempStackA != null) {
                            breakEnd = tempStackA.getKey();
                        }
                    }
                    else {
                        breakSection = null; //no more breaks.
                        breakEnd = Long.MAX_VALUE;
                        break;
                    }
                }

                //Possibly overlaps?
                if (breakSection != null) {
                    if (update) {
                        update = false;
                        breakStart = Long.MIN_VALUE;
                        tempStackA = objects.floorEntry(breakSection.a);
                        if (tempStackA != null) {
                            breakStart = getEnd(tempStackA.getValue());
                        }
                    }

                    //overlaps with current break.
                    if (endPos > breakStart) {
                        //First, handle the section before the new object's position.
                        if (stack.getKey() >= breakStart + (850 + getBreakEndDelay())) {
                            //There's room to leave a break here.
                            //breakSection is currently the "previous" of breakIterator
                            breakIterator.previous(); //shift back

                            breakIterator.add(new Pair<>(Math.min(breakSection.a, Math.min(breakStart + 5000, stack.getKey() - (650 + getBreakEndDelay()))), stack.getKey() - getBreakEndDelay()));

                            breakIterator.next(); //return to current position
                        }

                        //Handle the section after the object's start.
                        //A very long object could require adjusting multiple breaks.
                        while (breakSection != null && endPos > breakStart) { //assumes non-overlapping break times.
                            if (endPos > breakEnd - (850 + getBreakEndDelay())) {
                                //Totally covers this break.
                                breakIterator.remove(); //Remove it and move to next break.
                                if (breakIterator.hasNext()) {
                                    //prep break info
                                    breakSection = breakIterator.next();

                                    breakStart = Long.MIN_VALUE;
                                    breakEnd = Long.MAX_VALUE;
                                    tempStackA = objects.floorEntry(breakSection.a);
                                    if (tempStackA != null) {
                                        breakStart = getEnd(tempStackA.getValue());
                                    }
                                    tempStackA = objects.ceilingEntry(breakSection.b);
                                    if (tempStackA != null) {
                                        breakEnd = tempStackA.getKey();
                                    }
                                }
                                else {
                                    breakSection = null;
                                    breakEnd = Long.MAX_VALUE;
                                }
                            }
                            else { //Only partially covers this break with valid break room remaining.
                                breakSection.a = Math.max(breakSection.a, endPos + 200);
                                breakStart = endPos;
                                if (breakSection.b < breakSection.a + 650)
                                    breakSection.b = breakSection.a + 650;
                            }
                        }

                        continue;
                    }
                }

                //Doesn't overlap a break. Check if breaks need to be added/potential breaks need to be adjusted.
                tempStackA = objects.lowerEntry(stack.getKey());
                tempStackB = objects.higherEntry(endPos);

                //Future objects will handle gaps behind them themselves, as well as adjusting potential gaps that they cover.
                //You can tell if lastEndPos is valid because objects can only be placed at times >= 0.
                long lastObjPos = lastEndPos;
                if (tempStackA != null) {
                    lastObjPos = Math.max(lastObjPos, getEnd(tempStackA.getValue()));
                }
                if (lastObjPos > Long.MIN_VALUE) {
                    long dist = stack.getKey() - lastObjPos;
                    if (dist >= 5000) {
                        potentialBreaks.put(stack.getKey(), lastObjPos);
                    }
                }

                if (tempStackB != null) { //room after for a break. Following added objects are taken into account by just adjusting the potential break.
                    long dist = tempStackB.getKey() - endPos;

                    if (potentialBreaks.containsKey(tempStackB.getKey())) {
                        //This object is directly after another object inside a potential break.
                        //This potential break thus needs adjusting.
                        if (dist < 5000) { //This gap has shrunk and no longer requires a break.
                            potentialBreaks.remove(tempStackB.getKey());
                        }
                        else { //still requires a break, but starting point has changed.
                            potentialBreaks.put(tempStackB.getKey(), endPos);
                        }
                    }
                    else if (dist >= 5000) {
                        //break time - but not right away.
                        potentialBreaks.put(tempStackB.getKey(), endPos);
                    }
                }
            }
            
            //now into reality they go
            if (!potentialBreaks.isEmpty()) {
                for (Map.Entry<Long, Long> newBreak : potentialBreaks.entrySet()) {
                    //data is in the form end (key), start (value)
                    breaks.add(new Pair<>(newBreak.getValue() + 200, newBreak.getKey() - getBreakEndDelay()));
                }
                sortBreaks();
            }
        }
    }
    public void removeObject(PositionalObject o) {
        if (o instanceof HitObject) {
            HitObject h = (HitObject) o;

            if (objects.removeObject(h) != null && autoBreaks) {
                if (!objects.containsKey(h.getPos()) || h instanceof ILongObject) {
                    //First, log all unique gaps generated by this.
                    //Then check each of them.
                    boolean sort = false;
                    Set<Long> added = new HashSet<>();
                    List<Long> startPoints = new ArrayList<>();
                    Map.Entry<Long, ArrayList<HitObject>> stack;
                    Long endTime;

                    stack = objects.floorEntry(h.getPos());
                    if (stack != null) {
                        added.add(getEnd(stack.getValue()));
                        startPoints.add(getEnd(stack.getValue()));
                    }
                    else {
                        startPoints.add(Long.MIN_VALUE + 1);
                    }

                    if (h.getEndPos() > h.getPos()) {
                        for (Map.Entry<Long, ArrayList<HitObject>> stackA : objects.subMap(h.getPos(), false, h.getEndPos(), false).entrySet()) {
                            endTime = getEnd(stackA.getValue());
                            if (!added.contains(endTime)) {
                                added.add(endTime);
                                startPoints.add(endTime);
                            }
                        }
                    }
                    startPoints.sort(Long::compareTo);

                    //For each gap, check if a break exists within this gap. If more than 1, delete all but 1, then > If 1, adjust it appropriately. If none, add a new one (if gap is > 5000)
                    boolean breakLegal;
                    Pair<Long, Long> adjust, breakSection;
                    Iterator<Pair<Long, Long>> breakIterator;

                    for (Long start : startPoints) {
                        adjust = null;
                        breakLegal = start != Long.MIN_VALUE + 1;

                        endTime = objects.higherKey(start);
                        if (endTime == null) {
                            breakLegal = false;
                            endTime = Long.MAX_VALUE;
                        }

                        breakIterator = getBreaks().iterator();
                        while (breakIterator.hasNext()) {
                            breakSection = breakIterator.next();

                            if (breakSection.a >= start && breakSection.b <= endTime) {
                                if (adjust == null && breakLegal) {
                                    adjust = breakSection;
                                }
                                else {
                                    breakIterator.remove();
                                }
                            }
                            else if (breakSection.a >= endTime) {
                                break;
                            }
                        }

                        if (adjust == null) { //no existing break
                            if (breakLegal && endTime - start >= 5000) { //break required
                                getBreaks().add(new Pair<>(start + 200, endTime - getBreakEndDelay()));
                                sort = true;
                            }
                        }
                        else {
                            //Break exists.
                            //Adjust start and end times ONLY if they are invalid (too close/too far)
                            long dist = endTime - start;

                            if (dist < 850 + getBreakEndDelay()) {
                                //Gap is too short.
                                getBreaks().remove(adjust);
                                continue;
                            }

                            //Adjust the break.
                            long origDist;
                            if (h.getPos() >= adjust.b && h.getPos() < endTime) {
                                origDist = h.getPos() - adjust.b;
                            }
                            else {
                                origDist = endTime - adjust.b;
                            }
                            dist = endTime - adjust.b;

                            if (origDist == getBreakEndDelay() || dist < getBreakEndDelay()) {
                                adjust.b = endTime - getBreakEndDelay();
                            }
                            else if (dist > 5000) {
                                adjust.b = endTime - 5000;
                            }

                            if (h.getEndPos() <= adjust.a && h.getEndPos() > start) {
                                origDist = adjust.a - h.getEndPos();
                            }
                            else {
                                origDist = adjust.a - start;
                            }
                            dist = adjust.a - start;

                            if (origDist == 200 || dist < 200) {
                                adjust.a = start + 200;
                            }
                            else if (dist > 5000) {
                                adjust.a = start + 5000;
                            }
                        }
                    }

                    if (sort)
                        sortBreaks();
                }
            }
        }
    }
    public void removeObjects(NavigableMap<Long, ArrayList<PositionalObject>> remove) {
        if (objects.removeAll(remove) && autoBreaks) {
            if (objects.size() <= 1) {
                getBreaks().clear();
                return;
            }
            //First, log all unique gaps generated by this.
            //Then check each of them.
            boolean sort = false;
            Set<Long> added = new HashSet<>();
            List<Long> startPoints = new ArrayList<>();
            Map.Entry<Long, ArrayList<HitObject>> stack;
            Long endTime;

            for (Map.Entry<Long, ArrayList<PositionalObject>> stackA : remove.entrySet()) {
                stack = objects.floorEntry(stackA.getKey());
                if (stack != null) {
                    long end = getEnd(stack.getValue());
                    if (!added.contains(end)) {
                        added.add(end);
                        startPoints.add(end);
                    }
                }
                else {
                    if (!added.contains(Long.MIN_VALUE + 1)) {
                        added.add(Long.MIN_VALUE + 1);
                        startPoints.add(Long.MIN_VALUE + 1);
                    }
                }

                endTime = getEnd(stackA.getValue());
                if (endTime > stackA.getKey()) {
                    for (Map.Entry<Long, ArrayList<HitObject>> stackB : objects.subMap(stackA.getKey(), false, endTime, false).entrySet()) {
                        endTime = getEnd(stackB.getValue());
                        if (!added.contains(endTime)) {
                            added.add(endTime);
                            startPoints.add(endTime);
                        }
                    }
                }
            }
            startPoints.sort(Long::compareTo);

            //For each gap, check if a break exists within this gap. If more than 1, delete all but 1, then > If 1, adjust it appropriately. If none, add a new one (if gap is > 5000)
            boolean breakLegal;
            Pair<Long, Long> adjust, breakSection;
            Iterator<Pair<Long, Long>> breakIterator;

            for (Long start : startPoints) {
                adjust = null;
                breakLegal = start != Long.MIN_VALUE + 1;

                endTime = objects.higherKey(start);
                if (endTime == null) {
                    breakLegal = false;
                    endTime = Long.MAX_VALUE;
                }

                breakIterator = getBreaks().iterator();
                while (breakIterator.hasNext()) {
                    breakSection = breakIterator.next();

                    if (breakSection.a >= start && breakSection.b <= endTime) {
                        if (adjust == null && breakLegal) {
                            adjust = breakSection;
                        }
                        else {
                            breakIterator.remove();
                        }
                    }
                    else if (breakSection.a >= endTime) {
                        break;
                    }
                }

                if (adjust == null) { //no existing break
                    if (breakLegal && endTime - start >= 5000) { //break required
                        getBreaks().add(new Pair<>(start + 200, endTime - getBreakEndDelay()));
                        sort = true;
                    }
                }
                else {
                    //Break exists.
                    //Adjust start and end times ONLY if they are invalid (too close/too far)
                    long dist = endTime - start;

                    if (dist < 850 + getBreakEndDelay()) {
                        //Gap is too short.
                        getBreaks().remove(adjust);
                        continue;
                    }

                    Long original = remove.ceilingKey(adjust.b);
                    if (original == null || original > endTime)
                        original = endTime;
                    long origDist = original - adjust.b;
                    dist = endTime - adjust.b;

                    if (origDist == getBreakEndDelay() || dist < getBreakEndDelay()) {
                        adjust.b = endTime - getBreakEndDelay();
                    }
                    else if (dist > 5000) {
                        adjust.b = endTime - 5000;
                    }

                    original = remove.floorKey(adjust.a);
                    if (original == null || original < start)
                        original = start;
                    origDist = adjust.a - original;
                    dist = adjust.a - start;

                    if (origDist == 200 || dist < 200) {
                        adjust.a = start + 200;
                    }
                    else if (dist > 5000) {
                        adjust.a = start + 5000;
                    }
                }
            }

            if (sort)
                sortBreaks();
        }
    }
    //If made longer, make sure any break directly following this object is adjusted/removed if necessary.
    //If made shorter, add a break if necessary. Otherwise, do nothing.
    //Should be called after the change occurs.
    public void adjustedEnd(ILongObject h, long changeAmount) {
        if (autoBreaks && h instanceof HitObject) {
            ArrayList<HitObject> stack = objects.get(((HitObject) h).getPos());
            if (stack != null) {
                long end = getEnd(stack);
                if (end < h.getEndPos() - changeAmount) {
                    //Current end is less than the original end of this object. Might need to add a break.
                    long origEnd = h.getEndPos() - changeAmount;
                    //Move backwards from the original endpoint to the new endpoint

                    Iterator<Map.Entry<Long, ArrayList<HitObject>>> objectIterator = objects.descendingSubMap(end, false, origEnd, false).entrySet().iterator();

                    //In most cases, there shouldn't be any objects. This code will only do anything if there were objects stacked on the long object.
                    //There shouldn't be any breaks, as this section was covered by the long object.
                    Map.Entry<Long, ArrayList<HitObject>> entry, followingEntry = entry = objects.ceilingEntry(origEnd), lastEntry;
                    if (objectIterator.hasNext()) {
                        while (objectIterator.hasNext()) {
                            lastEntry = entry;
                            entry = objectIterator.next();
                            if (lastEntry != null) {
                                end = getEnd(entry.getValue());
                                if (lastEntry.getKey() - end >= 5000) {
                                    getBreaks().add(new Pair<>(end, lastEntry.getKey()));
                                }
                            }
                        }
                        sortBreaks();
                    }

                    //If a break exists after the original ending position, adjust it.
                    if (followingEntry != null) { //Entry is the stack closest to the end of the long object.
                        if (followingEntry.getKey() - origEnd >= 850 + getBreakEndDelay()) { //possible for there to have been a break
                            Iterator<Pair<Long, Long>> breakIterator = getBreaks().iterator();
                            Pair<Long, Long> breakSection;
                            while (breakIterator.hasNext()) {
                                breakSection = breakIterator.next();

                                if (breakSection.a > origEnd) { //The first break after.
                                    if (breakSection.b < followingEntry.getKey()) {
                                        //This Is, Indeed, A Break in the Right Place. It must be Adjusted. Maybe.
                                        if (breakSection.a - origEnd == 200) { //Indeed, No Extra Spacing here
                                            entry = objects.floorEntry(breakSection.a);
                                            if (entry != null) { //And It Does, Indeed, Have a Starting Point
                                                breakSection.a = getEnd(entry.getValue()) + 200;
                                            }
                                        }
                                    }
                                    return;
                                }
                            }
                        }
                        //No following break found
                        entry = objects.floorEntry(origEnd);
                        if (entry != null) { //Perhaps one is necessary.
                            end = getEnd(entry.getValue());
                            if (followingEntry.getKey() - end >= 5000) {
                                getBreaks().add(new Pair<>(end, followingEntry.getKey())); //It is.
                                sortBreaks();
                            }
                        }
                    } //If entry is null, there can be no break after the object so nothing has to be done.
                }
                else if (changeAmount > 0 && end == h.getEndPos()) {
                    //it got longer and Is the longest object
                    if (getBreaks().isEmpty()) {
                        return; //If it got longer, there's no need to add a break.
                    }
                    //All that has to be done is shorten/remove any breaks covered by the newly adjusted object.

                    long pos = end - changeAmount; //Start from the old endpoint and go until the new endpoint

                    Iterator<Map.Entry<Long, ArrayList<HitObject>>> objectIterator = objects.subMap(pos, false, end, false).entrySet().iterator();

                    //In most cases, there shouldn't be any objects. This code will only do anything if objects were covered.
                    Map.Entry<Long, ArrayList<HitObject>> entry = null, lastEntry;

                    Iterator<Pair<Long, Long>> breakIterator = getBreaks().iterator();
                    Pair<Long, Long> breakSection;

                    while (breakIterator.hasNext()) {
                        breakSection = breakIterator.next();

                        if (breakSection.b < pos) {
                            continue;
                        }

                        //This break is >= current pos.
                        entry = objects.floorEntry(breakSection.a);
                        if (entry == null) {
                            return; //It's Fucked
                        }
                        else {
                            //Adjust this break, if necessary.
                            long breakStart = getEnd(entry.getValue()), breakEnd;
                            if (breakStart > end) //This break is not next to/covered by
                                return;

                            entry = objects.ceilingEntry(breakSection.b);
                            if (entry != null) {
                                breakEnd = entry.getKey();
                            }
                            else {
                                return;
                            }

                            if (end > breakEnd - (850 + getBreakEndDelay())) {
                                //Totally covers this break.
                                breakIterator.remove(); //Remove it, move on.
                            }
                            else { //Only partially covers this break with valid break room remaining.
                                breakSection.a = Math.max(breakSection.a, end + 200);
                                if (breakSection.b < breakSection.a + 650)
                                    breakSection.b = breakSection.a + 650;
                            }
                        }
                    }
                }
            }
        }
    }

    private static final HashSet<Long> updatePositions = new HashSet<>();
    private long removedPoint(long pos) {
        volumeMap.remove(pos); //remove volume point
        ArrayList<TimingPoint> timing = timingPoints.get(pos); //if there's still another line at that position
        if (timing != null)
            volumeMap.put(pos, timing.get(timing.size() - 1).volume); //put volume of that point

        Boolean removed = kiaiMap.remove(pos);
        if (removed != null) { // -1
            long cap = Math.max(getAllSnaps().lastKey(), pos) + 1;
            Map.Entry<Long, Boolean> kiaiEntry = kiaiMap.higherEntry(pos);
            if (kiaiEntry != null)
                cap = kiaiEntry.getKey();

            Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> points = allPoints.subMap(pos, false, cap, false).entrySet().iterator();
            TimingPoint next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;

            while (next != null) {
                if (next.kiai == removed) {
                    kiaiMap.put(next.getPos(), next.kiai); // -1 +1 = 0
                    break;
                }
                next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;
            }
            if (next == null) {
                //No swaps between. Just remove the higher entry.
                if (kiaiEntry != null) {
                    kiaiMap.remove(kiaiEntry.getKey()); //-1 -1 = -2
                }
                else {
                    if (removed) { //removed a kiai start
                        editorLogger.error("Removal of kiai start caused kiai end map entry generation");
                    }
                    kiaiMap.put(cap, false);
                }
            }
        }
        return pos;
    }
    private long addedPoint(long pos, TimingPoint p) {
        volumeMap.put(pos, p.volume);

        //Update kiai.
        Map.Entry<Long, Boolean> kiaiEntry = kiaiMap.floorEntry(pos);
        if ((kiaiEntry != null && kiaiEntry.getValue() != p.kiai) || (kiaiEntry == null && p.kiai)) { //point has different kiai setting than point before it
            kiaiMap.put(p.getPos(), p.kiai);

            long cap = Math.max(getAllSnaps().lastKey(), p.getPos()) + 1;
            kiaiEntry = kiaiMap.higherEntry(p.getPos());
            if (kiaiEntry != null)
                cap = kiaiEntry.getKey();

            Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> points = allPoints.subMap(p.getPos(), false, cap, false).entrySet().iterator();
            TimingPoint next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;

            while (next != null) {
                if (next.kiai != p.kiai) {
                    kiaiMap.put(next.getPos(), next.kiai); // +1 +1 = +2
                    break;
                }
                next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;
            }
            if (next == null) {
                if (kiaiEntry == null) {
                    //no higher swap and none of the lines after this have the opposite value.
                    if (!p.kiai) {
                        editorLogger.error("Unexpected non-kiai line caused kiai map entry generation");
                    }
                    kiaiMap.put(cap, false); // +1 +1 = +2
                }
                else {
                    //No swaps between. Just remove the higher entry.
                    kiaiMap.remove(kiaiEntry.getKey()); // +1 -1 = +0
                }
            }
        }
        return pos;
    }
    public void updateKiai(List<TimingPoint> changed) {
        Map.Entry<Long, Boolean> kiaiEntry;

        for (TimingPoint p : changed) {
            if (kiaiMap.containsKey(p.getPos()) && kiaiMap.get(p.getPos()) != p.kiai) { //Was originally a swapping point.
                if (kiaiMap.remove(p.getPos()) != null) { // -1
                    long cap = Math.max(getAllSnaps().lastKey(), p.getPos()) + 1;
                    kiaiEntry = kiaiMap.higherEntry(p.getPos());
                    if (kiaiEntry != null)
                        cap = kiaiEntry.getKey();

                    Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> points = allPoints.subMap(p.getPos(), false, cap, false).entrySet().iterator();
                    TimingPoint next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;

                    while (next != null) {
                        if (next.kiai != p.kiai) {
                            kiaiMap.put(next.getPos(), next.kiai); // -1 +1 = 0
                            break;
                        }
                        next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;
                    }
                    if (next == null) {
                        //No swaps between. Just remove the higher entry.
                        if (kiaiEntry != null) {
                            kiaiMap.remove(kiaiEntry.getKey()); //-1 -1 = -2
                        }
                        else {
                            if (!p.kiai) {
                                throw new Error("Unexpected non-kiai line caused kiai end map entry generation");
                            }
                            kiaiMap.put(cap, false);
                        }
                    }
                }
            }
            else { //No swap here originally.
                kiaiEntry = kiaiMap.floorEntry(p.getPos());
                if ((kiaiEntry != null && kiaiEntry.getValue() != p.kiai) || (kiaiEntry == null && p.kiai)) { //point has different kiai setting than point before it
                    kiaiMap.put(p.getPos(), p.kiai);

                    long cap = Math.max(getAllSnaps().lastKey(), p.getPos()) + 1;
                    kiaiEntry = kiaiMap.higherEntry(p.getPos());
                    if (kiaiEntry != null)
                        cap = kiaiEntry.getKey();

                    Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> points = allPoints.subMap(p.getPos(), false, cap, false).entrySet().iterator();
                    TimingPoint next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;

                    while (next != null) {
                        if (next.kiai != p.kiai) {
                            kiaiMap.put(next.getPos(), next.kiai); // +1 +1 = +2
                            break;
                        }
                        next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;
                    }
                    if (next == null) {
                        if (kiaiEntry == null) {
                            //no higher swap and none of the lines after this have the opposite value.
                            if (!p.kiai) {
                                throw new Error("Unexpected non-kiai line caused kiai map entry generation");
                            }
                            kiaiMap.put(cap, false); // +1 +1 = +2
                        }
                        else {
                            //No swaps between. Just remove the higher entry.
                            kiaiMap.remove(kiaiEntry.getKey()); // +1 -1 = +0
                        }
                    }
                }
            }
        }
    }
    public void updateEffectPoints(Iterable<? extends Map.Entry<Long, ? extends List<?>>> added, Iterable<? extends Map.Entry<Long, ? extends List<?>>> removed) {
        if (effectView != null) {
            if (removed != null) {
                TimingPoint temp;
                for (Map.Entry<Long, ? extends List<?>> p : removed) {
                    updatePositions.add(removedPoint(p.getKey()));
                }

                if (added != null) {
                    for (Map.Entry<Long, ? extends List<?>> e : added) {
                        temp = (TimingPoint) GeneralUtils.listLast(e.getValue());
                        updatePositions.add(addedPoint(e.getKey(), temp));
                    }
                }

                //All objects from after a removed or added point to the next point have to have their volume updated
                for (Long pos : updatePositions) {
                    updateVolume(pos);
                }

                effectView.recheckSvLimits();
            }
            else if (added != null) {
                TimingPoint effective;
                for (Map.Entry<Long, ? extends List<?>> e : added) {
                    if (!e.getValue().isEmpty()) {
                        effective = (TimingPoint) GeneralUtils.listLast(e.getValue());
                        addedPoint(e.getKey(), effective);
                        effectView.testNewSvLimit(effective.value);
                        updateVolume(e.getKey());
                    }
                }
            }
        }
        if (timeline != null) {
            timeline.updateTimingPoints(this, added, removed);
        }
        updatePositions.clear();
    }

    //Used when only values of green lines are adjusted
    public void updateSv() {
        if (effectView != null) {
            effectView.recheckSvLimits();
        }
    }
    public void updateEffectPoints(TimingPoint added, List<Pair<Long, ArrayList<TimingPoint>>> removed) {
        if (effectView != null) {
            updateEffectPoints(Collections.singleton(new Pair<>(added.getPos(), Collections.singletonList(added))), removed);
        }
    }
    public void updateEffectPoints(List<Pair<Long, ArrayList<TimingPoint>>> added, TimingPoint removed) {
        if (effectView != null) {
            updateEffectPoints(added, Collections.singleton(new Pair<>(removed.getPos(), Collections.singletonList(removed))));
        }
    }

    public void gameplayChanged() {
        if (gameplayView != null && gameplayView.autoRefresh())
            gameplayView.calculateTimes();
    }

    //Effects
    public NavigableMap<Long, ArrayList<TimingPoint>> getEditEffectPoints(long startPos, long endPos)
    {
        if (startPos != lastEffectPointStart || endPos != lastEffectPointEnd)
        {
            lastEffectPointStart = startPos;
            lastEffectPointEnd = endPos;
            editEffectPoints = effectPoints.extendedDescendingSubMap(startPos, endPos);
        }
        return editEffectPoints;
    }
    public NavigableMap<Long, ArrayList<TimingPoint>> getEditEffectPoints()
    {
        return editEffectPoints;
    }
    public NavigableMap<Long, ArrayList<TimingPoint>> getSubEffectMap(long startPos, long endPos)
    {
        return effectPoints.descendingSubMap(startPos, true, endPos, true);
    }

    public void bindEffectView(EffectView view)
    {
        this.effectView = view;
    }
    public void removeEffectView(EffectView view)
    {
        if (this.effectView.equals(view))
        {
            this.effectView = null;
        }
    }
    public void bindGameplayView(GameplayView view)
    {
        this.gameplayView = view;
    }
    public void removeGameplayView(GameplayView view)
    {
        if (this.gameplayView.equals(view))
        {
            this.gameplayView = null;
        }
    }
    public void setTimeline(Timeline line) {
        this.timeline = line;
    }

    //Timing
    public NavigableMap<Long, ArrayList<TimingPoint>> getVisibleTimingPoints(long startPos, long endPos) {
        if (startPos != lastTimingPointStart || endPos != lastTimingPointEnd)
        {
            lastTimingPointStart = startPos;
            lastTimingPointEnd = endPos;
            visibleTimingPoints = timingPoints.extendedDescendingSubMap(startPos, endPos);
        }
        return visibleTimingPoints;
    }

    public TreeMap<Long, Boolean> getKiai() {
        return kiaiMap;
    }

    //Map properties
    public float getBaseSV() {
        return fullMapInfo.sliderMultiplier;
    }


    public void dispose()
    {
        timingPoints.clear();
        effectPoints.clear();
        allPoints.clear();
        objects.clear();

        volumeMap.clear();
        kiaiMap.clear();
    }



    public boolean is(MapInfo info)
    {
        return fullMapInfo.is(info);
    }


    /// Save/load ///
    private void parse(Mapset set, MapInfo map)
    {
        fullMapInfo = new FullMapInfo(set, map);

        if (!map.getMapFile().isFile())
            return;

        List<String> lines = FileHelper.readFileLines(map.getMapFile());

        if (lines == null || lines.isEmpty())
            return;

        int section = -1, eventSection = -1;

        //Sv tracking variables
        long currentPos;
        Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> timing = null, effect = null;
        Map.Entry<Long, ArrayList<TimingPoint>> nextTiming = null, nextEffect = null;
        double svRate = 1.4, currentBPM = 120;
        int volume = 100;

        TimingPoint temp;

        //-1 Header
        //0 General
        //1 Editor
        //2 Metadata
        //3 Difficulty
        //4 Events
        //5 TimingPoints
        //6 HitObjects

        //Events:
            //-1 start
            //0 background
            //1 break
            //2 anything else

        for (String line : lines)
        {
            if (line.isEmpty())
                continue;

            if (line.startsWith("["))
            {
                switch (line)
                {
                    case "[General]":
                        section = 0;
                        break;
                    case "[Editor]":
                        section = 1;
                        break;
                    case "[Metadata]":
                        section = 2;
                        break;
                    case "[Difficulty]":
                        section = 3;
                        break;
                    case "[Events]":
                        section = 4;
                        break;
                    case "[TimingPoints]":
                        section = 5;
                        break;
                    case "[HitObjects]":
                        section = 6;
                        //Prepare to track sv for the purpose of calculating slider length
                        svRate = fullMapInfo.sliderMultiplier;
                        timing = timingPoints.entrySet().iterator();
                        effect = effectPoints.entrySet().iterator();

                        if (timing.hasNext())
                        {
                            nextTiming = timing.next();
                            currentBPM = nextTiming.getValue().get(0).value;
                            volume = nextTiming.getValue().get(0).volume;
                            svRate = fullMapInfo.sliderMultiplier;
                            if (timing.hasNext())
                                nextTiming = timing.next();
                            else
                                nextTiming = null; //Only one timing point.
                        }
                        else
                        {
                            nextTiming = null; //what the fuck why are there no timing points >:(
                            currentBPM = 120; //This is what osu uses as default so it's what I'm gonna use. Though really, there shouldn't be any objects if there's no timing points.
                        }

                        if (effect.hasNext())
                            nextEffect = effect.next(); //First SV doesn't apply until the first timing point is reached.
                        break;
                    case "[Colours]": //I don't give a fuck about colors this is taiko you can't even see them in game OR in editor
                        section = 7;
                        break;
                }
            }
            else
            {
                switch (section) {
                    case 0: //General
                        if (line.contains(":"))
                        {
                            switch (line.substring(0, line.indexOf(":")))
                            {
                                case "AudioLeadIn":
                                    fullMapInfo.audioLeadIn = Integer.parseInt(line.substring(12).trim());
                                    break;
                                case "PreviewTime":
                                    fullMapInfo.previewTime = Integer.parseInt(line.substring(12).trim());
                                    break;
                                case "Countdown":
                                    fullMapInfo.countdown = line.substring(10).trim().equals("1");
                                    break;
                                case "SampleSet":
                                    fullMapInfo.sampleSet = line.substring(10).trim();
                                    break;
                                case "StackLeniency":
                                    fullMapInfo.stackLeniency = line.substring(14).trim();
                                    break;
                                case "LetterboxInBreaks":
                                    fullMapInfo.letterboxInBreaks = line.substring(18).trim().equals("1");
                                    break;
                                case "WidescreenStoryboard":
                                    fullMapInfo.widescreenStoryboard = line.substring(21).trim().equals("1");
                                    break;
                            }
                        }
                        break;
                    case 1: //Editor
                        if (line.contains(":"))
                        {
                            switch (line.substring(0, line.indexOf(":")))
                            {
                                case "Bookmarks":
                                    for (String s : line.substring(10).split(","))
                                    {
                                        fullMapInfo.bookmarks.add(Integer.parseInt(s.trim()));
                                    }
                                    break;
                                case "DistanceSpacing":
                                    fullMapInfo.distanceSpacing = line.substring(16).trim();
                                    break;
                                case "BeatDivisor":
                                    fullMapInfo.beatDivisor = Integer.parseInt(line.substring(12).trim());
                                    break;
                                case "GridSize":
                                    fullMapInfo.gridSize = line.substring(9).trim();
                                    break;
                                case "TimelineZoom":
                                    fullMapInfo.timelineZoom = line.substring(13).trim();
                                    break;
                            }
                        }
                        break;
                    case 2: //Metadata
                        if (line.contains(":"))
                        {
                            switch (line.substring(0, line.indexOf(":")))
                            {
                                case "Title":
                                    fullMapInfo.title = line.substring(6);
                                    fullMapInfo.titleUnicode = line.substring(6);
                                    break;
                                case "TitleUnicode":
                                    fullMapInfo.titleUnicode = line.substring(13);
                                    break;
                                case "Artist":
                                    fullMapInfo.artist = line.substring(7);
                                    fullMapInfo.artistUnicode = line.substring(7);
                                    break;
                                case "ArtistUnicode":
                                    fullMapInfo.artistUnicode = line.substring(14);
                                    break;
                                case "Creator":
                                    fullMapInfo.creator = line.substring(8);
                                    break;
                                case "Version":
                                    fullMapInfo.setDifficultyName(line.substring(8));
                                    break;
                                case "Source":
                                    fullMapInfo.source = line.substring(7);
                                    break;
                                case "Tags":
                                    fullMapInfo.tags = line.substring(5).trim().split(" ");
                                    break;
                                case "BeatmapID":
                                    fullMapInfo.beatmapID = Integer.parseInt(line.substring(10).trim());
                                    break;
                                case "BeatmapSetID":
                                    fullMapInfo.beatmapSetID = Integer.parseInt(line.substring(13).trim());
                                    break;
                            }
                        }
                        break;
                    case 3: //Difficulty
                        if (line.contains(":"))
                        {
                            switch (line.substring(0, line.indexOf(":")))
                            {
                                case "HPDrainRate":
                                    fullMapInfo.hp = Float.parseFloat(line.substring(12).trim());
                                    break;
                                case "CircleSize":
                                    fullMapInfo.cs = Float.parseFloat(line.substring(11).trim());
                                    break;
                                case "OverallDifficulty":
                                    fullMapInfo.od = Float.parseFloat(line.substring(18).trim());
                                    break;
                                case "ApproachRate":
                                    fullMapInfo.ar = Float.parseFloat(line.substring(13).trim());
                                    break;
                                case "SliderMultiplier":
                                    fullMapInfo.sliderMultiplier = Float.parseFloat(line.substring(17).trim());
                                    break;
                                case "SliderTickRate":
                                    fullMapInfo.sliderTickRate = Float.parseFloat(line.substring(15).trim());
                                    break;
                            }
                        }
                        break;
                    case 4: //Events
                        if (line.startsWith("//"))
                        {
                            //In events
                            switch (line)
                            {
                                case "//Background and Video events":
                                    eventSection = 0;
                                    continue;
                                case "//Break Periods":
                                    eventSection = 1;
                                    continue;
                                case "//Storyboard Layer 0 (Background)": //This line and all event lines past it should be included in storyboard text
                                    eventSection = 2;
                                    break;
                            }
                        }
                        if (line.startsWith("2") || line.startsWith("Break")) {
                            String[] parts = line.split(",");
                            fullMapInfo.breakPeriods.add(new Pair<>(Long.parseLong(parts[1].trim()), Long.parseLong(parts[2].trim())));
                        }
                        //The rest
                        else if (eventSection == 0) { //Background and Video events
                            if (!line.startsWith("//")) {
                                String[] parts = line.split(",");

                                if (parts.length == 5) {
                                    if (parts[2].startsWith("\"") && parts[2].endsWith("\"")) {
                                        parts[2] = parts[2].substring(1, parts[2].length() - 1);
                                    }
                                    if (FileHelper.isImageFilename(parts[2])) {
                                        String bgFile = FileHelper.concat(map.getMapFile().getParent(), parts[2]);
                                        map.setBackground(bgFile);
                                        set.background = bgFile;
                                    }
                                }
                            }
                            fullMapInfo.backgroundEvents.add(line.split(","));
                        } else {
                            fullMapInfo.fullStoryboard.add(line);
                        }
                        break;
                    case 5: //TimingPoints
                        TimingPoint p = new TimingPoint(line); //ordering is not guaranteed at this point
                        if (p.uninherited)
                            timingPoints.add(p);
                        else
                            effectPoints.add(p);
                        break;
                    case 6: //HitObjects
                        HitObject h = HitObject.create(line);
                        currentPos = h.getPos();

                        long lastTimingPos = Long.MIN_VALUE;
                        long lastEffectPos = Long.MIN_VALUE;
                        int timingVolume = 100;

                        while (timing != null && nextTiming != null && nextTiming.getKey() <= currentPos)
                        {
                            temp = GeneralUtils.listLast(nextTiming.getValue());
                            currentBPM = temp.value;
                            volume = timingVolume = temp.volume;
                            lastTimingPos = nextTiming.getKey();
                            svRate = fullMapInfo.sliderMultiplier; //return to base sv

                            if (timing.hasNext())
                                nextTiming = timing.next();
                            else
                                nextTiming = null;

                            volumeMap.put(lastTimingPos, volume);
                        }
                        while (effect != null && nextEffect != null && nextEffect.getKey() <= currentPos)
                        {
                            temp = GeneralUtils.listLast(nextEffect.getValue());
                            lastEffectPos = nextEffect.getKey();
                            svRate = fullMapInfo.sliderMultiplier * temp.value;
                            volume = temp.volume;

                            if (effect.hasNext())
                                nextEffect = effect.next();
                            else
                                nextEffect = null;

                            volumeMap.put(lastEffectPos, volume); //green lines override volume of red lines on the same position.
                        }
                        if (lastEffectPos < lastTimingPos)
                        {
                            svRate = fullMapInfo.sliderMultiplier; //return to base sv and volume of the timing point
                            volume = timingVolume;
                        }

                        if (h.type == HitObject.HitObjectType.SLIDER)
                        {
                            ((Slider)h).calculateDuration(currentBPM, svRate);
                        }
                        h.volume = volume / 100.0f;
                        objects.add(h);
                        break;
                }
            }
        }

        if (volumeMap.isEmpty()) //wtf no points at all
            volumeMap.put(Long.MAX_VALUE, 60);
        allPoints.addAll(timingPoints);
        allPoints.addAll(effectPoints);

        boolean kiai = false, nextKiai;
        for (Map.Entry<Long, ArrayList<TimingPoint>> stack : allPoints.entrySet()) {
            nextKiai = stack.getValue().get(stack.getValue().size() - 1).kiai;
            if (nextKiai != kiai) {
                kiaiMap.put(stack.getKey(), kiai = nextKiai);
            }
        }

        sortBreaks();
        autoBreaks = testBreaks();
    }

    public void sortBreaks() {
        fullMapInfo.breakPeriods.sort(Comparator.comparingLong(a -> a.a));
    }

    private boolean testBreaks() {
        Long test;
        Optional<HitObject> longest;
        for (Pair<Long, Long> breakPeriod : getBreaks()) {
            test = null;

            longest = objects.floorEntry(breakPeriod.b).getValue().stream().max(Comparator.comparingLong(HitObject::getEndPos));
            if (longest.isPresent()) {
                test = longest.get().getEndPos();
            }

            if (test == null || breakPeriod.a - test < 200) { //break is too close to the object before it, or no preceding object or it's during the break
                return false;
            }
            test = objects.ceilingKey(breakPeriod.a);
            if (test == null || test < breakPeriod.b + getBreakEndDelay()) { //object during break
                return false;
            }
        }
        return true;
    }

    public boolean save()
    {
        FileOutputStream out = null;
        BufferedWriter w = null;
        try
        {
            File newFile = fullMapInfo.generateMapFile();

            if (fullMapInfo.getMapFile().exists())
            {
                String backup = newFile.getPath();
                backup = backup.substring(0, backup.lastIndexOf('.')) + ".BACKUP";
                File backupFile = new File(backup);
                if (backupFile.exists())
                    backupFile.delete();
                try
                {
                    if (fullMapInfo.getMapFile().renameTo(backupFile))
                    {
                        editorLogger.info("Created backup successfully.");
                    }
                    else
                    {
                        editorLogger.error("Failed to create backup.");
                    }
                }
                catch (Exception e)
                {
                    //No backup :(
                    editorLogger.error("Failed to create backup.");
                    e.printStackTrace();
                }
            }
            out = new FileOutputStream(newFile, false);
            w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

            w.write(fullMapInfo.toString());
            w.write(timingPoints());
            w.write(hitObjects());

            w.close();
            out.close();

            dirty = false;

            fullMapInfo.setMapFile(newFile);
            return true;
        }
        catch (Exception e)
        {
            if (w != null)
            {
                try
                {
                    w.close();
                }
                catch (Exception ignored) {}
            }
            if (out != null)
            {
                try
                {
                    out.close();
                }
                catch (Exception ignored) {}
            }
            editorLogger.error("Failed to save beatmap.");
            e.printStackTrace();
            return false;
        }
    }

    private String timingPoints()
    {
        if (timingPoints.isEmpty() && effectPoints.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder("\r\n[TimingPoints]\r\n");

        Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> timing, effect;
        timing = timingPoints.entrySet().iterator();
        effect = effectPoints.entrySet().iterator();

        Map.Entry<Long, ArrayList<TimingPoint>> nextTiming = null, nextEffect = null;

        if (timing.hasNext())
            nextTiming = timing.next();

        if (effect.hasNext())
            nextEffect = effect.next();

        while (nextTiming != null || nextEffect != null)
        {
            if (nextTiming == null)
            {
                for (TimingPoint t : nextEffect.getValue())
                    sb.append(t.toString()).append("\r\n");

                if (effect.hasNext())
                    nextEffect = effect.next();
                else
                    nextEffect = null;
            }
            else if (nextEffect == null || (nextTiming.getKey() <= nextEffect.getKey()))
            {
                for (TimingPoint t : nextTiming.getValue())
                    sb.append(t.toString()).append("\r\n");

                if (timing.hasNext())
                    nextTiming = timing.next();
                else
                    nextTiming = null;
            }
            else //next effect is not null and is before next timing
            {
                for (TimingPoint t : nextEffect.getValue())
                    sb.append(t.toString()).append("\r\n");

                if (effect.hasNext())
                    nextEffect = effect.next();
                else
                    nextEffect = null;
            }
        }

        return sb.toString();
    }
    private String hitObjects()
    {
        if (timingPoints.isEmpty() && effectPoints.isEmpty() && hitObjects().isEmpty())
            return "";


        //Sv tracking variables
        long currentPos;
        Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> timing, effect;
        Map.Entry<Long, ArrayList<TimingPoint>> nextTiming = null, nextEffect = null;
        double svRate = 1.4, currentBPM = 120;

        svRate = fullMapInfo.sliderMultiplier;
        timing = timingPoints.entrySet().iterator();
        effect = effectPoints.entrySet().iterator();

        if (timing.hasNext())
        {
            nextTiming = timing.next();
            currentBPM = nextTiming.getValue().get(0).value;
            if (timing.hasNext())
                nextTiming = timing.next();
            else
                nextTiming = null; //Only one timing point.
        }

        if (effect.hasNext())
            nextEffect = effect.next(); //First SV doesn't apply until the first timing point is reached.



        StringBuilder sb = new StringBuilder("\r\n\r\n[HitObjects]\r\n");



        for (Map.Entry<Long, ArrayList<HitObject>> stacked : objects.entrySet())
        {
            currentPos = stacked.getKey();

            long lastTimingPos = Long.MIN_VALUE;
            long lastEffectPos = Long.MIN_VALUE;

            while (nextTiming != null && nextTiming.getKey() <= currentPos)
            {
                currentBPM = nextTiming.getValue().get(nextTiming.getValue().size() - 1).value;
                lastTimingPos = nextTiming.getKey();
                svRate = fullMapInfo.sliderMultiplier; //return to base sv

                if (timing.hasNext())
                    nextTiming = timing.next();
                else
                    nextTiming = null;
            }
            while (nextEffect != null && nextEffect.getKey() <= currentPos)
            {
                lastEffectPos = nextEffect.getKey();
                svRate = fullMapInfo.sliderMultiplier * nextEffect.getValue().get(nextEffect.getValue().size() - 1).value;

                if (effect.hasNext())
                    nextEffect = effect.next();
                else
                    nextEffect = null;
            }
            if (lastEffectPos < lastTimingPos)
            {
                svRate = fullMapInfo.sliderMultiplier; //return to base sv
            }


            for (HitObject h : stacked.getValue())
                sb.append(h.toString(currentBPM, svRate)).append("\r\n");
        }

        return sb.toString();
    }

    public String getName()
    {
        return fullMapInfo.getDifficultyName();
    }






    /// EXPERIMENTAL TJA SAVE SUPPORT ///

    //ASSUMPTION: The version of TJA used requires 16 notes per line.
    //No fancy features will be implemented, only the basics.

    public boolean saveTJA()
    {
        return false;
        /*
        FileOutputStream out = null;
        BufferedWriter w = null;
        try
        {
            String tjaFile = fullMapInfo.mapFile.getPath();
            tjaFile = tjaFile.substring(0, tjaFile.lastIndexOf(".")) + ".tja";

            out = new FileOutputStream(new File(tjaFile), false);
            w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

            Map.Entry<Integer, ArrayList<TimingPoint>> firstEntry = timingPoints.firstEntry();

            //required part of file header
            double bpm = firstEntry.getValue().get(0).getBPM();
            int offset = firstEntry.getValue().get(0).pos;

            w.write("// CONVERTED FROM .osu\n");
            w.write("TITLE:" + fullMapInfo.titleUnicode + "\n");
            w.write("TITLEEN:" + fullMapInfo.title + "\n"); //Is this even supported?


            //To write this as TJA, the objects must be converted into Measures.
            //Measure snappings will be determined by finding closest snaps, 1 millisecond before or after.
            //Unsnapped notes will cause an error, which should be reported to TextOverlay.

            w.close();
            out.close();
            return true;
        }
        catch (Exception e)
        {
            if (w != null)
            {
                try
                {
                    w.close();
                }
                catch (Exception ignored) {}
            }
            if (out != null)
            {
                try
                {
                    out.close();
                }
                catch (Exception ignored) {}
            }
            editorLogger.error("Failed to save beatmap.");
            e.printStackTrace();
            return false;
        }*/
    }

    /**
     * @return the endpoint of the longest object in the stack, or Long.MIN_VALUE if it is empty.
     */
    private long getEnd(ArrayList<? extends PositionalObject> stack) {
        Optional<? extends PositionalObject> longest = stack.stream().max(Comparator.comparingLong((a)->((HitObject) a).getEndPos()));
        return longest.map((o)->((HitObject)o).getEndPos()).orElse(Long.MIN_VALUE);
    }
}
