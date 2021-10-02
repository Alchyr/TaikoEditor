package alchyr.taikoedit.editor.maps;

import alchyr.taikoedit.editor.BeatDivisors;
import alchyr.taikoedit.editor.DivisorOptions;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.*;
import alchyr.taikoedit.editor.views.SvView;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.ILongObject;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.editor.maps.components.hitobjects.Slider;
import alchyr.taikoedit.util.assets.FileHelper;
import alchyr.taikoedit.util.structures.Pair;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;
import com.badlogic.gdx.utils.Queue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

//Will be referenced by displays for rendering, and modifications in editor will be performed on it to ensure all displays are connected to the same map object
public class EditorBeatmap {
    public boolean dirty = false; //Are there unsaved changes

    //For hitobjects/timing points use a structure that allows for fast find/insertion at the desired position but also fast iteration?
    public final PositionalObjectTreeMap<TimingPoint> timingPoints; //red lines
    public final PositionalObjectTreeMap<TimingPoint> effectPoints; //green lines
    public final PositionalObjectTreeMap<HitObject> objects;

    private final TreeMap<Long, Integer> volumeMap;

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

    private SvView effectView;


    public EditorBeatmap(Mapset set, MapInfo map)
    {
        timingPoints = new PositionalObjectTreeMap<>();
        effectPoints = new PositionalObjectTreeMap<>();
        objects = new PositionalObjectTreeMap<>();

        volumeMap = new TreeMap<>();

        parse(set, map);
    }




    /* EDITING METHODS */
    private final Queue<MapChange> undoQueue = new Queue<>();
    private final Queue<MapChange> redoQueue = new Queue<>();

    // These should be used if the map is changed using ANYTHING other than undo and redo
    //Redo queue is added to when undo is used, and cleared when any change is made.
    //Undo queue fills up... Forever? Changes are added to END of undo queue, and removed from end as well.
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

    public void addObject(HitObject o)
    {
        dirty = true;
        undoQueue.addLast(new ObjectAddition(this, o).perform());
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
    public void paste(PositionalObjectTreeMap<PositionalObject> pasteObjects) {
        dirty = true;
        undoQueue.addLast(new ObjectAddition(this, pasteObjects).perform());
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
    public void replace(MapChange.ChangeType type, PositionalObjectTreeMap<PositionalObject> deleted, PositionalObjectTreeMap<PositionalObject> added) {
        dirty = true;
        undoQueue.addLast(new Replacement(this, type, deleted, added).perform());
        redoQueue.clear();
    }
    public void reverse(MapChange.ChangeType type, boolean resnap, PositionalObjectTreeMap<PositionalObject> reversed) {
        dirty = true;
        undoQueue.addLast(new Reverse(this, type, resnap, reversed).perform());
        redoQueue.clear();
    }
    public void registerMovement(MapChange.ChangeType type, PositionalObjectTreeMap<PositionalObject> movementObjects, long offset)
    {
        dirty = true;
        if (type == MapChange.ChangeType.OBJECTS)
            updateVolume(movementObjects);
        undoQueue.addLast(new Movement(this, type, movementObjects, offset));
        redoQueue.clear();
    }

    public void registerDurationChange(ILongObject obj, long change)
    {
        dirty = true;
        undoQueue.addLast(new DurationChange(this, obj, change));
        redoQueue.clear();
    }
    public void registerValueChange(PositionalObjectTreeMap<PositionalObject> modifiedObjects, double change)
    {
        dirty = true;
        undoQueue.addLast(new ValueModificationChange(this, modifiedObjects, change));
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

    public void updateVolume(NavigableMap<Long, ArrayList<PositionalObject>> objects)
    {
        if (objects.isEmpty())
            return;

        Map.Entry<Long, Integer> volumeEntry = volumeMap.floorEntry(objects.firstKey());
        long nextPos = Integer.MIN_VALUE;
        Map.Entry<Long, Integer> next = volumeMap.higherEntry(objects.firstKey());
        if (next != null)
            nextPos = next.getKey();

        for (Map.Entry<Long, ArrayList<PositionalObject>> entry : objects.entrySet())
        {
            while (next != null && nextPos <= entry.getKey())
            {
                volumeEntry = next;

                next = volumeMap.higherEntry(nextPos);
                if (next != null)
                    nextPos = next.getKey();
            }

            float volume = volumeEntry != null ? volumeEntry.getValue() / 100.0f : 1.0f;

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

            float volume = volumeEntry != null ? volumeEntry.getValue() / 100.0f : 1.0f;

            for (HitObject h : entry.b)
                h.volume = volume;
        }
    }

    public void updateVolume(HitObject h)
    {
        Map.Entry<Long, Integer> volumeEntry = volumeMap.floorEntry(h.pos);
        h.volume = volumeEntry != null ? volumeEntry.getValue() / 100.0f : 1.0f;
    }

    //Updates volume of all objects from a given position to the next effect point after given position
    public void updateVolume(long startPos) {
        Long end = effectPoints.higherKey(startPos);
        if (end == null)
            end = Long.MAX_VALUE;
        Map.Entry<Long, Integer> volumeEntry = volumeMap.floorEntry(startPos);
        float volume = volumeEntry == null ? 1.0f : volumeEntry.getValue() / 100.0f;
        for (ArrayList<HitObject> stack : objects.subMap(startPos, true, end, false).values()) {
            for (HitObject o : stack)
                o.volume = volume;
        }
    }

    private static final HashSet<Long> updatePositions = new HashSet<>();
    public void updateEffectPoints(NavigableMap<Long, ArrayList<PositionalObject>> added, List<Pair<Long, ArrayList<TimingPoint>>> removed) {
        if (effectView != null) {
            if (removed != null) {
                effectView.recheckSvLimits();

                for (Pair<Long, ArrayList<TimingPoint>> p : removed) {
                    volumeMap.remove(p.a);
                    ArrayList<TimingPoint> timing = timingPoints.get(p.a);
                    if (timing != null)
                        volumeMap.put(p.a, timing.get(timing.size() - 1).volume);
                    updatePositions.add(p.a);
                }

                if (added != null) {
                    for (Map.Entry<Long, ArrayList<PositionalObject>> e : added.entrySet()) {
                        if (!e.getValue().isEmpty())
                        {
                            updatePositions.add(e.getKey());
                            volumeMap.put(e.getKey(), ((TimingPoint) e.getValue().get(e.getValue().size() - 1)).volume);
                        }
                    }
                }

                //All objects from after a removed or added point to the next point have to have their volume updated
                for (Long pos : updatePositions) {
                    updateVolume(pos);
                }
            }
            else if (added != null) {
                TimingPoint effective;
                for (Map.Entry<Long, ArrayList<PositionalObject>> e : added.entrySet()) {
                    if (!e.getValue().isEmpty()) {
                        effective = ((TimingPoint) e.getValue().get(e.getValue().size() - 1));
                        effectView.testNewLimit(effective.value);
                        volumeMap.put(e.getKey(), effective.volume);
                        updateVolume(e.getKey());
                    }
                }
            }
        }
        updatePositions.clear();
    }
    public void updateEffectPoints(TimingPoint added, List<Pair<Long, ArrayList<TimingPoint>>> removed) {
        if (effectView != null) {
            if (removed != null) {
                effectView.recheckSvLimits();

                for (Pair<Long, ArrayList<TimingPoint>> p : removed) {
                    volumeMap.remove(p.a);
                    ArrayList<TimingPoint> timing = timingPoints.get(p.a);
                    if (timing != null)
                        volumeMap.put(p.a, timing.get(timing.size() - 1).volume);
                    updatePositions.add(p.a);
                }

                if (added != null) {
                    updatePositions.add(added.pos);
                    volumeMap.put(added.pos, added.volume);
                }

                //All objects from after a removed or added point to the next point have to have their volume updated
                for (Long pos : updatePositions) {
                    updateVolume(pos);
                }
            }
            else if (added != null) {
                effectView.testNewLimit(added.value);
                volumeMap.put(added.pos, added.volume);
                updateVolume(added.pos);
            }
        }
        updatePositions.clear();
    }

    public void updateEffectPoints(List<Pair<Long, ArrayList<TimingPoint>>> added, NavigableMap<Long, ArrayList<PositionalObject>> removed) {
        if (effectView != null) {
            if (removed != null) {
                effectView.recheckSvLimits();

                for (Map.Entry<Long, ArrayList<PositionalObject>> p : removed.entrySet()) {
                    volumeMap.remove(p.getKey());
                    ArrayList<TimingPoint> timing = timingPoints.get(p.getKey());
                    if (timing != null)
                        volumeMap.put(p.getKey(), timing.get(timing.size() - 1).volume);
                    updatePositions.add(p.getKey());
                }

                if (added != null) {
                    for (Pair<Long, ArrayList<TimingPoint>> p : added) {
                        if (!p.b.isEmpty())
                        {
                            updatePositions.add(p.a);
                            volumeMap.put(p.a, p.b.get(p.b.size() - 1).volume);
                        }
                    }
                }

                //All objects from after a removed or added point to the next point have to have their volume updated
                for (Long pos : updatePositions) {
                    updateVolume(pos);
                }
            }
            else if (added != null) {
                TimingPoint effective;
                for (Pair<Long, ArrayList<TimingPoint>> e : added) {
                    if (!e.b.isEmpty()) {
                        effective = e.b.get(e.b.size() - 1);
                        effectView.testNewLimit(effective.value);
                        volumeMap.put(e.a, effective.volume);
                        updateVolume(e.a);
                    }
                }
            }
        }
        updatePositions.clear();
    }
    public void updateEffectPoints(List<Pair<Long, ArrayList<TimingPoint>>> added, TimingPoint removed) {
        if (effectView != null) {
            if (removed != null) {
                effectView.recheckSvLimits();

                volumeMap.remove(removed.pos);
                ArrayList<TimingPoint> timing = timingPoints.get(removed.pos);
                if (timing != null)
                    volumeMap.put(removed.pos, timing.get(timing.size() - 1).volume);
                updatePositions.add(removed.pos);

                if (added != null) {
                    for (Pair<Long, ArrayList<TimingPoint>> p : added) {
                        if (!p.b.isEmpty())
                        {
                            updatePositions.add(p.a);
                            volumeMap.put(p.a, p.b.get(p.b.size() - 1).volume);
                        }
                    }
                }

                //All objects from after a removed or added point to the next point have to have their volume updated
                for (Long pos : updatePositions) {
                    updateVolume(pos);
                }
            }
            else if (added != null) {
                for (Pair<Long, ArrayList<TimingPoint>> e : added) {
                    if (!e.b.isEmpty())
                        effectView.testNewLimit((e.b.get(e.b.size() - 1)).value);
                }
            }
        }
        updatePositions.clear();
    }
    public void updateEffectPoints(PositionalObjectTreeMap<PositionalObject> added, PositionalObjectTreeMap<PositionalObject> removed) {
        if (effectView != null) {
            if (removed != null) {
                effectView.recheckSvLimits();

                for (Map.Entry<Long, ArrayList<PositionalObject>> p : removed.entrySet()) {
                    volumeMap.remove(p.getKey());
                    ArrayList<TimingPoint> timing = timingPoints.get(p.getKey());
                    if (timing != null)
                        volumeMap.put(p.getKey(), timing.get(timing.size() - 1).volume);
                    updatePositions.add(p.getKey());
                }

                if (added != null) {
                    for (Map.Entry<Long, ArrayList<PositionalObject>> p : removed.entrySet()) {
                        if (!p.getValue().isEmpty())
                        {
                            updatePositions.add(p.getKey());
                            volumeMap.put(p.getKey(), ((TimingPoint) p.getValue().get(p.getValue().size() - 1)).volume);
                        }
                    }
                }

                //All objects from after a removed or added point to the next point have to have their volume updated
                for (Long pos : updatePositions) {
                    updateVolume(pos);
                }
            }
            else if (added != null) {
                TimingPoint effective;
                for (Map.Entry<Long, ArrayList<PositionalObject>> e : added.entrySet()) {
                    if (!e.getValue().isEmpty()) {
                        effective = ((TimingPoint) e.getValue().get(e.getValue().size() - 1));
                        effectView.testNewLimit(effective.value);
                        volumeMap.put(e.getKey(), effective.volume);
                        updateVolume(e.getKey());
                    }
                }
            }
        }
        updatePositions.clear();
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

    public void bindEffectView(SvView view)
    {
        this.effectView = view;
    }
    public void removeEffectView(SvView view)
    {
        if (this.effectView == view)
        {
            this.effectView = null;
        }
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


    //Map properties
    public float getBaseSV() {
        return fullMapInfo.sliderMultiplier;
    }


    public void dispose()
    {
        timingPoints.clear();
        effectPoints.clear();
        objects.clear();
    }



    public boolean is(MapInfo info)
    {
        return fullMapInfo.mapFile.equals(info.mapFile);
    }


    /// Save/load ///
    private void parse(Mapset set, MapInfo map)
    {
        fullMapInfo = new FullMapInfo(set, map);

        if (!map.mapFile.isFile())
            return;

        List<String> lines = FileHelper.readFileLines(map.mapFile);

        if (lines == null || lines.isEmpty())
            return;

        int section = -1, eventSection = -1;

        //Sv tracking variables
        long currentPos;
        Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> timing = null, effect = null;
        Map.Entry<Long, ArrayList<TimingPoint>> nextTiming = null, nextEffect = null;
        double svRate = 1.4, currentBPM = 120;
        int volume = 100;

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
                                    for (String s : line.substring(10).trim().split(","))
                                    {
                                        fullMapInfo.bookmarks.add(Integer.parseInt(s));
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
                                    fullMapInfo.title = line.substring(6).trim();
                                    fullMapInfo.titleUnicode = line.substring(6).trim();
                                    break;
                                case "TitleUnicode":
                                    fullMapInfo.titleUnicode = line.substring(13).trim();
                                    break;
                                case "Artist":
                                    fullMapInfo.artist = line.substring(7).trim();
                                    fullMapInfo.artistUnicode = line.substring(7).trim();
                                    break;
                                case "ArtistUnicode":
                                    fullMapInfo.artistUnicode = line.substring(14).trim();
                                    break;
                                case "Creator":
                                    fullMapInfo.creator = line.substring(8).trim();
                                    break;
                                case "Version":
                                    fullMapInfo.difficultyName = line.substring(8).trim();
                                    break;
                                case "Source":
                                    fullMapInfo.source = line.substring(7).trim();
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
                        switch (eventSection)
                        {
                            case 0: //Background and Video events
                                fullMapInfo.backgroundEvents.add(line.split(","));
                                break;
                            case 1: //Breaks
                                fullMapInfo.breakPeriods.add(line.split(","));
                                break;
                            case 2: //The rest
                                fullMapInfo.fullStoryboard.add(line);
                                break;
                        }
                        break;
                    case 5: //TimingPoints
                        TimingPoint p = new TimingPoint(line);
                        if (p.uninherited)
                            timingPoints.add(p);
                        else
                            effectPoints.add(p);

                        if (volumeMap.isEmpty())
                            volumeMap.put(0L, p.volume);
                        break;
                    case 6: //HitObjects
                        HitObject h = HitObject.create(line);
                        currentPos = h.pos;

                        long lastTimingPos = -1;
                        long lastEffectPos = -1;
                        int newVolume = 100;

                        while (timing != null && nextTiming != null && nextTiming.getKey() <= currentPos)
                        {
                            currentBPM = nextTiming.getValue().get(nextTiming.getValue().size() - 1).value;
                            volume = newVolume = nextTiming.getValue().get(nextTiming.getValue().size() - 1).volume;
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
                            lastEffectPos = nextEffect.getKey();
                            svRate = fullMapInfo.sliderMultiplier * nextEffect.getValue().get(nextEffect.getValue().size() - 1).value;
                            volume = nextEffect.getValue().get(nextEffect.getValue().size() - 1).volume;

                            if (effect.hasNext())
                                nextEffect = effect.next();
                            else
                                nextEffect = null;

                            volumeMap.put(lastEffectPos, volume); //green lines override volume of red lines on the same position.
                        }
                        if (lastEffectPos < lastTimingPos)
                        {
                            svRate = fullMapInfo.sliderMultiplier; //return to base sv
                            volume = newVolume;
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
    }

    public boolean save()
    {
        FileOutputStream out = null;
        BufferedWriter w = null;
        try
        {
            if (fullMapInfo.mapFile.exists())
            {
                String backup = fullMapInfo.mapFile.getPath();
                backup = backup.substring(0, backup.lastIndexOf('.')) + ".BACKUP";
                File backupFile = new File(backup);
                if (backupFile.exists())
                    backupFile.delete();
                try
                {
                    if (fullMapInfo.mapFile.renameTo(new File(backup)))
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
            out = new FileOutputStream(fullMapInfo.mapFile, false);
            w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

            w.write(fullMapInfo.toString());
            w.write(timingPoints());
            w.write(hitObjects());

            w.close();
            out.close();

            dirty = false;
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

        StringBuilder sb = new StringBuilder("\r\n\r\n[TimingPoints]\r\n");

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

            long lastTimingPos = -1;
            long lastEffectPos = -1;

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
        return fullMapInfo.difficultyName;
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
}
