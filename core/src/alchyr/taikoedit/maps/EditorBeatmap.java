package alchyr.taikoedit.maps;

import alchyr.taikoedit.editor.BeatDivisors;
import alchyr.taikoedit.editor.DivisorOptions;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.maps.components.HitObject;
import alchyr.taikoedit.maps.components.TimingPoint;
import alchyr.taikoedit.util.assets.FileHelper;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;

import java.util.*;

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
            editObjects = objects.descendingSubMap(startPos, true, endPos, true);
        }
        return editObjects;
    }
    public SortedSet<Snap> getActiveSnaps(int startPos, int endPos)
    {
        return divisor.getSnaps(startPos, endPos);
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

        int section = -1,
                eventSection = -1;
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

            if (section == 4 && line.startsWith("//"))
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
                        if (line.startsWith("BeatmapID"))
                        {
                            fullMapInfo.beatmapID = Integer.parseInt(line.substring(10).trim());
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
                        objects.add(h);
                        break;
                }
            }
        }
    }
}
