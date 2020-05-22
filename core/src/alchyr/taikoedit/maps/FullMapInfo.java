package alchyr.taikoedit.maps;

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
    public String creator; //Loaded from mapset
    public String artist;
    public String title;

    public int beatmapID = 0; //Everything else *should* be shared between difficulties.

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
        this.creator = set.creator;
        this.artist = set.artist;
        this.title = set.title;
        this.difficultyName = base.difficultyName;
    }
}
