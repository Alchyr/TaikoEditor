package alchyr.taikoedit.maps;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.editor.BeatDivisors;
import alchyr.taikoedit.editor.DivisorOptions;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.*;
import alchyr.taikoedit.maps.components.HitObject;
import alchyr.taikoedit.maps.components.ILongObject;
import alchyr.taikoedit.maps.components.TimingPoint;
import alchyr.taikoedit.maps.components.hitobjects.Slider;
import alchyr.taikoedit.util.assets.FileHelper;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;
import com.badlogic.gdx.utils.Queue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

//Will be referenced by displays for rendering, and modifications in editor will be performed on it to ensure all displays are connected to the same map object
public class EditorBeatmap {

    //For hitobjects/timing points use a structure that allows for fast find/insertion at the desired position but also fast iteration?
    public final PositionalObjectTreeMap<TimingPoint> timingPoints; //red lines
    public final PositionalObjectTreeMap<TimingPoint> effectPoints; //green lines
    public final PositionalObjectTreeMap<HitObject> objects;
    //Extend tree map to provide more support for multiple stacked objects

    private BeatDivisors divisor;


    private FullMapInfo fullMapInfo;


    //For edit objects
    private NavigableMap<Integer, ArrayList<HitObject>> editObjects;
    private int lastStart = Integer.MIN_VALUE, lastEnd = Integer.MIN_VALUE;


    //TODO: When editing timing is implemented, add a hook "OnTimingChanged" which will be used by BeatDivisors to re-generate whatever values are necessary
    //This should also be used to ensure that if sameSong is true, timing changes will be kept synced with the other difficulties.


    public EditorBeatmap(Mapset set, MapInfo map)
    {
        timingPoints = new PositionalObjectTreeMap<>();
        effectPoints = new PositionalObjectTreeMap<>();
        objects = new PositionalObjectTreeMap<>();

        parse(set, map);
    }




    /* EDITING METHODS */
    private int savedUndoSize = 0; //When the map is saved, the current size of the undo queue is also saved. If this size is different, the map has been changed.
    private final Queue<MapChange> undoQueue = new Queue<>();
    private final Queue<MapChange> redoQueue = new Queue<>();

    // These should be used if the map is changed using ANYTHING other than undo and redo
    //Redo queue is added to when undo is used, and cleared when any change is made.
    //Undo queue fills up... Forever? Changes are added to END of undo queue, and removed from end as well.
    public void undo()
    {
        if (!undoQueue.isEmpty())
        {
            redoQueue.addLast(undoQueue.removeLast().undo());
        }
    }
    public void redo()
    {
        if (!redoQueue.isEmpty())
        {
            undoQueue.addLast(redoQueue.removeLast().perform());
        }
    }

    public void addObject(HitObject o)
    {
        undoQueue.addLast(new ObjectAddition(this, o).perform());
        redoQueue.clear();
    }

    public void delete(MapChange.ChangeType type, NavigableMap<Integer, ArrayList<PositionalObject>> deletion)
    {
        undoQueue.addLast(new Deletion(this, type, deletion).perform());
        redoQueue.clear();
    }
    public void delete(MapChange.ChangeType type, PositionalObject o)
    {
        undoQueue.addLast(new Deletion(this, type, o).perform());
        redoQueue.clear();
    }

    public void registerMovedObjects(PositionalObjectTreeMap<PositionalObject> movementObjects, int offset)
    {
        undoQueue.addLast(new Movement(this, MapChange.ChangeType.OBJECTS, movementObjects, offset));
        redoQueue.clear();
    }

    public void registerDurationChange(ILongObject obj, int change)
    {
        undoQueue.addLast(new DurationChange(this, obj, change));
        redoQueue.clear();
    }







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






    public NavigableMap<Integer, ArrayList<HitObject>> getEditObjects(int startPos, int endPos)
    {
        if (startPos != lastStart || endPos != lastEnd)
        {
            lastStart = startPos;
            lastEnd = endPos;
            editObjects = objects.extendedDescendingSubMap(startPos, endPos);
        }
        return editObjects;
    }
    public NavigableMap<Integer, ArrayList<HitObject>> getEditObjects()
    {
        return editObjects;
    }
    public NavigableMap<Integer, ArrayList<HitObject>> getSubMap(int startPos, int endPos)
    {
        return objects.descendingSubMap(startPos, true, endPos, true);
    }
    public NavigableMap<Integer, Snap> getActiveSnaps(int startPos, int endPos)
    {
        return divisor.getSnaps(startPos, endPos);
    }
    public TreeMap<Integer, Snap> getAllSnaps()
    {
        return divisor.getSnaps();
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
        int currentPos;
        Iterator<Map.Entry<Integer, ArrayList<TimingPoint>>> timing = null, effect = null;
        Map.Entry<Integer, ArrayList<TimingPoint>> nextTiming = null, nextEffect = null;
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
                        currentPos = 0;
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
                                    fullMapInfo.sliderTickRate = Integer.parseInt(line.substring(15).trim());
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
                        break;
                    case 6: //HitObjects
                        HitObject h = HitObject.create(line);
                        currentPos = h.pos;

                        int lastTimingPos = -1;
                        int lastEffectPos = -1;
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
                        }
                        if (lastEffectPos < lastTimingPos)
                        {
                            svRate = fullMapInfo.sliderMultiplier; //return to base sv
                            volume = newVolume;
                        }

                        if (h.type == HitObject.HitType.SLIDER)
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

        StringBuilder sb = new StringBuilder("\n\n[TimingPoints]\n");

        Iterator<Map.Entry<Integer, ArrayList<TimingPoint>>> timing, effect;
        timing = timingPoints.entrySet().iterator();
        effect = effectPoints.entrySet().iterator();

        Map.Entry<Integer, ArrayList<TimingPoint>> nextTiming = null, nextEffect = null;

        if (timing.hasNext())
            nextTiming = timing.next();

        if (effect.hasNext())
            nextEffect = effect.next();

        while (nextTiming != null || nextEffect != null)
        {
            if (nextTiming == null)
            {
                for (TimingPoint t : nextEffect.getValue())
                    sb.append(t.toString()).append('\n');

                if (effect.hasNext())
                    nextEffect = effect.next();
                else
                    nextEffect = null;
            }
            else if (nextEffect == null || (nextTiming.getKey() <= nextEffect.getKey()))
            {
                for (TimingPoint t : nextTiming.getValue())
                    sb.append(t.toString()).append('\n');

                if (timing.hasNext())
                    nextTiming = timing.next();
                else
                    nextTiming = null;
            }
            else //next effect is not null and is before next timing
            {
                for (TimingPoint t : nextEffect.getValue())
                    sb.append(t.toString()).append('\n');

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
        int currentPos;
        Iterator<Map.Entry<Integer, ArrayList<TimingPoint>>> timing, effect;
        Map.Entry<Integer, ArrayList<TimingPoint>> nextTiming = null, nextEffect = null;
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



        StringBuilder sb = new StringBuilder("\n\n[HitObjects]\n");



        for (Map.Entry<Integer, ArrayList<HitObject>> stacked : objects.entrySet())
        {
            currentPos = stacked.getKey();

            int lastTimingPos = -1;
            int lastEffectPos = -1;

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
                sb.append(h.toString(currentBPM, svRate)).append('\n');
        }

        return sb.toString();
    }

    public String getName()
    {
        return fullMapInfo.difficultyName;
    }
}
