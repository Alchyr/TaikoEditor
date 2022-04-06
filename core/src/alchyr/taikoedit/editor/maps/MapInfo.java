package alchyr.taikoedit.editor.maps;

import alchyr.taikoedit.management.assets.FileHelper;

import java.io.File;
import java.util.List;

public class MapInfo {
    private File mapFile;
    private String songFile;
    private String background;

    private int mode = 0;
    private String difficultyName = "";

    //For the future: When loading map info from a database of existing data?
    public MapInfo(File mapFile, String songFile, String background, String name) {
        this.mapFile = mapFile;
        this.songFile = songFile;
        this.background = background;
        this.mode = 1;
        this.difficultyName = name;
    }

    //Creating a new difficulty
    public MapInfo(FullMapInfo base, File mapFile, String newDiffname) {
        this.mapFile = mapFile;
        this.songFile = base.getSongFile();
        this.background = base.getBackground();
        this.mode = 1;
        this.difficultyName = newDiffname;
    }

    //This constructor should be used when loading data for a map from files
    public MapInfo(File map, Mapset owner) {
        mapFile = map;
        background = "";

        List<String> lines = FileHelper.readFileLines(mapFile, "[TimingPoints]");

        if (lines != null) {
            int section = -1;
            //-1 Header
            //0 General
            //1 Editor
            //2 Metadata
            //3 Difficulty
            //4 Events
            //5 TimingPoints
            //6 HitObjects

            read:
            for (String line : lines) {
                if (line.contains(":")) {
                    switch (line.substring(0, line.indexOf(":"))) {
                        case "AudioFilename":
                            songFile = FileHelper.concat(mapFile.getParent(), line.substring(14).trim());
                            break;
                        case "Mode":
                            mode = Integer.parseInt(line.substring(5).trim());
                            if (mode != 1)
                                break read;
                            break;
                        case "Creator":
                            if (owner.creator.isEmpty())
                                owner.creator = line.substring(8).trim();
                            break;
                        case "Title":
                            if (owner.title.isEmpty())
                                owner.title = line.substring(6).trim();
                            break;
                        case "Artist":
                            if (owner.artist.isEmpty())
                                owner.artist = line.substring(7).trim();
                            break;
                        case "Version":
                            difficultyName = line.substring(8).trim();
                            break;
                    }
                } else {
                    if (line.startsWith("[")) {
                        switch (line) {
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
                        }
                    } else if (section == 4 && background.isEmpty() && !line.startsWith("//")) {
                        String[] parts = line.split(",");

                        if (parts.length == 5) {
                            if (parts[2].startsWith("\"") && parts[2].endsWith("\"")) {
                                parts[2] = parts[2].substring(1, parts[2].length() - 1);
                            }
                            if (FileHelper.isImageFilename(parts[2])) {
                                background = FileHelper.concat(mapFile.getParent(), parts[2]);
                                if (owner.background.isEmpty())
                                    owner.background = background;
                                break;
                            }
                        }
                    }
                }
            }
        }
    }


    //Track last modified date to determine if file must be re-read?
    public File getMapFile() {
        return mapFile;
    }

    public void setMapFile(File newFile) {
        this.mapFile = newFile;
    }

    public String getSongFile() {
        return songFile;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String bgFile) {
        this.background = bgFile;
    }

    public int getMode() {
        return mode;
    }

    public String getDifficultyName() {
        return difficultyName;
    }

    public void setDifficultyName(String newName) {
        this.difficultyName = newName;
    }

    @Override
    public String toString() {
        return "MapInfo{" +
                "mapFile=" + mapFile +
                ", songFile='" + songFile + '\'' +
                ", background='" + background + '\'' +
                ", mode=" + mode +
                ", difficultyName='" + difficultyName + '\'' +
                '}';
    }
}
