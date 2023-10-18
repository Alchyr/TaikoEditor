package alchyr.taikoedit.editor.maps;

import alchyr.taikoedit.util.structures.Pair;

import java.io.File;
import java.text.DecimalFormat;
import java.util.*;

import static alchyr.taikoedit.TaikoEditor.osuSafe;

public class FullMapInfo {
    private Mapset parent;
    private MapInfo base;

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
    List<Pair<Long, Long>> breakPeriods = new ArrayList<>(); //For changing
    List<String> fullStoryboard = new ArrayList<>(); //Should not be modified in this program

    public FullMapInfo(Mapset set, MapInfo base)
    {
        this.parent = set;
        this.base = base;
    }

    public FullMapInfo(FullMapInfo base, String diffName) {
        this.parent = base.parent;

        this.audioLeadIn = base.audioLeadIn;
        this.previewTime = base.previewTime;
        this.countdown = base.countdown;
        this.sampleSet = base.sampleSet;
        this.stackLeniency = base.stackLeniency;
        this.letterboxInBreaks = base.letterboxInBreaks;
        this.widescreenStoryboard = base.widescreenStoryboard;

        this.od = base.od;
        this.hp = base.hp;
        this.cs = base.cs;
        this.ar = base.ar;

        this.bookmarks.addAll(base.bookmarks);
        this.distanceSpacing = base.distanceSpacing;
        this.beatDivisor = base.beatDivisor;
        this.gridSize = base.gridSize;
        this.timelineZoom = base.timelineZoom;

        this.creator = base.creator;
        this.artist = base.artist;
        this.artistUnicode = base.artistUnicode;
        this.title = base.title;
        this.titleUnicode = base.titleUnicode;
        this.source = base.source;
        this.tags = new String[base.tags.length];
        System.arraycopy(base.tags, 0, tags, 0, tags.length);

        this.beatmapSetID = base.beatmapSetID;
        this.sliderTickRate = base.sliderTickRate;

        this.backgroundEvents.addAll(base.backgroundEvents);
        for (Pair<Long, Long> breakPeriod : base.breakPeriods)
            this.breakPeriods.add(new Pair<>(breakPeriod.a, breakPeriod.b));
        this.fullStoryboard.addAll(base.fullStoryboard);

        File f = new File(base.getMapFile().getParentFile(), generateFilename(diffName));
        this.base = new MapInfo(base, f, diffName);
    }

    public File getMapFile() {
        return base.getMapFile();
    }
    public void setMapFile(File newFile) {
        base.setMapFile(newFile);
    }
    public String getSongFile() {
        return base.getSongFile();
    }
    public String getBackground() {
        return base.getBackground();
    }

    public void setBackground(String path, String name) {
        base.setBackground(path);
        if (backgroundEvents.isEmpty()) {
            backgroundEvents.add(new String[] {
                    "0",
                    "0",
                    '\"' + name + '\"',
                    "0",
                    "0"
            });
        }
        else {
            backgroundEvents.set(0, new String[] {
                    "0",
                    "0",
                    '\"' + name + '\"',
                    "0",
                    "0"
            });
        }
    }
    public int getMode() {
        return base.getMode();
    }
    public String getDifficultyName() {
        return base.getDifficultyName();
    }
    public void setDifficultyName(String name) {
        base.setDifficultyName(name);
    }

    public File generateMapFile() {
        return new File(getMapFile().getParentFile(), generateFilename());
    }
    public String generateFilename(String diffName) {
        String name = artist + " - " + title + " (" + creator + ") [" + diffName + "].osu";
        return name.replaceAll("[\\\\/:*?\"<>|]", "");
    }
    public String generateFilename() {
        String name = artist + " - " + title + " (" + creator + ") [" + getDifficultyName() + "].osu";
        return name.replaceAll("[\\\\/:*?\"<>|]", "");
    }
    public String generateTJAFilename() {
        String name = artist + "-" + title + ".tja";
        return name.replaceAll("[\\\\/:*?\"<>| ]", "");
    }

    private final StringBuilder saveBuilder = new StringBuilder();
    @Override
    public String toString() {
        DecimalFormat df = new DecimalFormat("#0.#", osuSafe);
        return "osu file format v14\r\n" +
            "\r\n" +
            "[General]\r\n" +
            "AudioFilename: " + getSongFileForSave() + "\r\n" +
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
            "Version:" + base.getDifficultyName() + "\r\n" +
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
                breaks() +
                fullStoryboard();
    }

    public String tagText()
    {
        saveBuilder.setLength(0);
        int i;
        for (i = 0; i < tags.length - 1; ++i)
            saveBuilder.append(tags[i]).append(' ');
        saveBuilder.append(tags[i]);

        return saveBuilder.toString();
    }

    private String bookmarkString()
    {
        if (bookmarks.isEmpty())
            return "";
        saveBuilder.setLength(0);
        saveBuilder.append("Bookmarks: ");
        int count = 0;
        for (int i : bookmarks)
        {
            saveBuilder.append(i);
            if (count++ < bookmarks.size() - 1)
                saveBuilder.append(',');
        }
        return saveBuilder.append("\r\n").toString();
    }

    private String eventFormat(List<String[]> events)
    {
        saveBuilder.setLength(0);
        int i;
        for (String[] event : events)
        {
            for (i = 0; i < event.length - 1; ++i)
                saveBuilder.append(event[i]).append(',');
            saveBuilder.append(event[i]).append("\r\n");
        }
        return saveBuilder.toString();
    }
    private String breaks() {
        saveBuilder.setLength(0);
        for (Pair<Long, Long> breakPeriod : breakPeriods) {
            saveBuilder.append("2,").append(breakPeriod.a).append(",").append(breakPeriod.b).append("\r\n");
        }
        return saveBuilder.toString();
    }

    private String fullStoryboard()
    {
        saveBuilder.setLength(0);
        for (String event : fullStoryboard)
        {
            saveBuilder.append(event).append("\r\n");
        }
        return saveBuilder.toString();
    }

    private String getSongFileForSave()
    {
        String songFile = base.getSongFile();
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

    public MapInfo getInfo() {
        return base;
    }
    public boolean is(MapInfo info) {
        return base.equals(info);
    }
}
