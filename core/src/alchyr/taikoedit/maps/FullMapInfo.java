package alchyr.taikoedit.maps;

import alchyr.taikoedit.util.assets.FileHelper;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

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
    public List<Integer> bookmarks = new ArrayList<>();
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
    public int sliderTickRate = 1; //Usually 1

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
        return "osu file format v14\n" +
            "\n" +
            "[General]\n" +
            "AudioFilename: " + getSongFile() + "\n" +
            "AudioLeadIn: " + audioLeadIn + "\n" +
            "PreviewTime: " + previewTime + "\n" +
            "Countdown: " + (countdown ? 1 : 0) + "\n" +
            "SampleSet: " + sampleSet + "\n" +
            "StackLeniency: " + stackLeniency + "\n" +
            "Mode: 1\n" +
            "LetterboxInBreaks: " + (letterboxInBreaks ? 1 : 0) + "\n" +
            "WidescreenStoryboard: " + (widescreenStoryboard ? 1 : 0) + "\n" +
            "\n" +
            "[Editor]\n" +
            "DistanceSpacing: " + distanceSpacing + "\n" +
            "BeatDivisor: " + beatDivisor + "\n" +
            "GridSize: " + gridSize + "\n" +
            "TimelineZoom: " + timelineZoom + "\n" +
            "\n" +
            "[Metadata]\n" +
            "Title:" + title + "\n" +
            "TitleUnicode:" + titleUnicode + "\n" +
            "Artist:" + artist + "\n" +
            "ArtistUnicode:" + artistUnicode + "\n" +
            "Creator:" + creator + "\n" +
            "Version:" + difficultyName + "\n" +
            "Source:" + source + "\n" +
            "Tags:" + tagText() + "\n" +
            "BeatmapID:" + beatmapID + "\n" +
            "BeatmapSetID:" + beatmapSetID + "\n" +
            "\n" +
            "[Difficulty]\n" +
            "HPDrainRate:" + df.format(hp) + "\n" +
            "CircleSize:" + df.format(cs) + "\n" +
            "OverallDifficulty:" + df.format(od) + "\n" +
            "ApproachRate:" + df.format(ar) + "\n" +
            "SliderMultiplier:" + sliderMultiplier + "\n" +
            "SliderTickRate:" + sliderTickRate + "\n" +
            "\n" +
            "[Events]\n" +
            "//Background and Video events\n" +
                eventFormat(backgroundEvents) +
            "//Break Periods\n" +
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

    private String eventFormat(List<String[]> events)
    {
        StringBuilder returnVal = new StringBuilder();
        int i;
        for (String[] event : events)
        {
            for (i = 0; i < event.length - 1; ++i)
                returnVal.append(event[i]).append(',');
            returnVal.append(event[i]).append('\n');
        }
        return returnVal.toString();
    }

    private String fullStoryboard()
    {
        StringBuilder returnVal = new StringBuilder();
        for (String event : fullStoryboard)
        {
            returnVal.append(event).append('\n');
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
