package alchyr.taikoedit.editor.maps;

import java.io.File;
import java.text.DecimalFormat;
import java.util.*;

public class FullMapInfo extends MapInfo {
    //[General]
    public int audioLeadIn = 0;
    public int previewTime = -1;
    public boolean countdown = false;
    public String sampleSet = "Normal";
    public String stackLeniency = "0.7"; //Save as string since it doesn't need to be modified as a float
    public boolean letterboxInBreaks = false;
    public boolean widescreenStoryboard = false;

    //[Editor]
    public NavigableSet<Integer> bookmarks = new TreeSet<>();
    public String distanceSpacing = "0.8"; //similar to stackLeniency, irrelevant
    public int beatDivisor = 4;
    public String gridSize = "32"; //irrelevant
    public String timelineZoom = "1"; //irrelevant

    //[Metadata]
    public String creator = ""; //Loaded from mapset
    public String artist;
    public String artistUnicode = "";
    public String title;
    public String titleUnicode = "";
    public String source = "";
    public String[] tags = new String[] {};

    public int beatmapID = 0; //Everything else *should* be shared between difficulties.
    public int beatmapSetID = -1;

    //[Difficulty]
    public float hp = 5;
    public float cs = 5; //Mostly irrelevant
    public float od = 5;
    public float ar = 5; //Mostly irrelevant
    public float sliderMultiplier = 1.4f; //Usually 1.4
    public float sliderTickRate = 1; //Usually 1

    //[Events]
    List<String[]> backgroundEvents = new ArrayList<>(); //For changing
    List<String[]> breakPeriods = new ArrayList<>(); //For changing
    List<String> fullStoryboard = new ArrayList<>(); //Should not be modified in this program

    public FullMapInfo(Mapset set, MapInfo base)
    {
        this.mapFile = base.mapFile;
        this.songFile = base.songFile;
        this.background = base.background;

        this.mode = base.mode;
        this.difficultyName = base.difficultyName;
    }

    @Override
    public String toString() {
        DecimalFormat df = new DecimalFormat("#0.#");
        return "osu file format v14\r\n" +
            "\r\n" +
            "[General]\r\n" +
            "AudioFilename: " + getSongFile() + "\r\n" +
            "AudioLeadIn: " + audioLeadIn + "\r\n" +
            "PreviewTime: " + previewTime + "\r\n" +
            "Countdown: " + (countdown ? 1 : 0) + "\r\n" +
            "SampleSet: " + sampleSet + "\r\n" +
            "StackLeniency: " + stackLeniency + "\r\n" +
            "Mode: 1\r\n" +
            "LetterboxInBreaks: " + (letterboxInBreaks ? 1 : 0) + "\r\n" +
            "WidescreenStoryboard: " + (widescreenStoryboard ? 1 : 0) + "\r\n" +
            "\r\n" +
            "[Editor]\r\n" +
            bookmarkString() +
            "DistanceSpacing: " + distanceSpacing + "\r\n" +
            "BeatDivisor: " + beatDivisor + "\r\n" +
            "GridSize: " + gridSize + "\r\n" +
            "TimelineZoom: " + timelineZoom + "\r\n" +
            "\r\n" +
            "[Metadata]\r\n" +
            "Title:" + title + "\r\n" +
            "TitleUnicode:" + titleUnicode + "\r\n" +
            "Artist:" + artist + "\r\n" +
            "ArtistUnicode:" + artistUnicode + "\r\n" +
            "Creator:" + creator + "\r\n" +
            "Version:" + difficultyName + "\r\n" +
            "Source:" + source + "\r\n" +
            "Tags:" + tagText() + "\r\n" +
            "BeatmapID:" + beatmapID + "\r\n" +
            "BeatmapSetID:" + beatmapSetID + "\r\n" +
            "\r\n" +
            "[Difficulty]\r\n" +
            "HPDrainRate:" + df.format(hp) + "\r\n" +
            "CircleSize:" + df.format(cs) + "\r\n" +
            "OverallDifficulty:" + df.format(od) + "\r\n" +
            "ApproachRate:" + df.format(ar) + "\r\n" +
            "SliderMultiplier:" + sliderMultiplier + "\r\n" +
            "SliderTickRate:" + df.format(sliderTickRate) + "\r\n" +
            "\r\n" +
            "[Events]\r\n" +
            "//Background and Video events\r\n" +
                eventFormat(backgroundEvents) +
            "//Break Periods\r\n" +
                eventFormat(breakPeriods) +
                fullStoryboard();
    }

    private String tagText()
    {
        StringBuilder returnVal = new StringBuilder();
        int i;
        for (i = 0; i < tags.length - 1; ++i)
            returnVal.append(tags[i]).append(' ');
        returnVal.append(tags[i]);

        return returnVal.toString();
    }

    private String bookmarkString()
    {
        if (bookmarks.isEmpty())
            return "";
        StringBuilder returnVal = new StringBuilder("Bookmarks: ");
        int count = 0;
        for (int i : bookmarks)
        {
            returnVal.append(i);
            if (count++ < bookmarks.size() - 1)
                returnVal.append(',');
        }
        return returnVal.append("\r\n").toString();
    }

    private String eventFormat(List<String[]> events)
    {
        StringBuilder returnVal = new StringBuilder();
        int i;
        for (String[] event : events)
        {
            for (i = 0; i < event.length - 1; ++i)
                returnVal.append(event[i]).append(',');
            returnVal.append(event[i]).append("\r\n");
        }
        return returnVal.toString();
    }

    private String fullStoryboard()
    {
        StringBuilder returnVal = new StringBuilder();
        for (String event : fullStoryboard)
        {
            returnVal.append(event).append("\r\n");
        }
        return returnVal.toString();
    }

    private String getSongFile()
    {
        if (songFile.contains(File.separator))
        {
            if (songFile.endsWith(File.separator))
            {
                if (songFile.substring(0, songFile.length() - 1).contains(File.separator))
                    return songFile.substring(0, songFile.length() - 1).substring(songFile.lastIndexOf(File.separator) + 1);
                else
                    return songFile;
            }
            return songFile.substring(songFile.lastIndexOf(File.separator) + 1);
        }
        else
        {
            return songFile;
        }
    }
}
