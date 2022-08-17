package alchyr.taikoedit.editor.maps;

import com.badlogic.gdx.graphics.g2d.BitmapFont;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class Mapset {
    public String key; //Map folder

    public boolean sameSong; //Usually true, but will be false in some multi-mp3 beatmapsets. Should result in some features being disabled.

    protected String songFile = "";
    protected String creator = "";
    protected String title = "";
    protected String artist = "";
    protected String background = "";

    private String shortCreator = null, shortTitle = null, shortArtist = null, songMeta = null; //temporary things.

    private final File directory;
    private List<MapInfo> maps;

    public Mapset(File directory) // loading new
    {
        this.directory = directory;
        this.key = directory.getAbsolutePath();

        this.maps = new ArrayList<>();
        sameSong = true;

        loadMaps();
    }
    public Mapset(File directory, List<MapInfo> maps, boolean sameSong, String songFile, String creator, String title, String artist, String background) //from data
    {
        this.directory = directory;
        this.key = directory.getAbsolutePath();

        this.maps = maps;
        this.sameSong = sameSong;

        this.songFile = songFile;
        this.creator = creator;
        this.title = title;
        this.artist = artist;
        this.background = background;
    }

    public String getArtist() {
        return artist;
    }
    public String getTitle() {
        return title;
    }
    public String getCreator() {
        return creator;
    }
    public String getBackground() {
        return background;
    }

    public List<MapInfo> getMaps()
    {
        return maps;
    }
    public void setMaps(List<MapInfo> confirmed) {
        maps = confirmed;
        sortMaps();
    }

    //For display, some of these will be too long.
    //Save these to avoid slow process?
    public String getShortArtist(float limit, BitmapFont font) {
        if (shortArtist != null)
        {
            return shortArtist;
        }

        float width = textRenderer.setFont(font).getWidth(artist);

        if (width < limit)
            return artist;

        int len = Math.max(0, artist.length() - 3);

        do
        {
            while (len > 0 && artist.charAt(len - 1) == ' ')
                --len;

            shortArtist = artist.substring(0, len) + "...";
            width = textRenderer.getWidth(shortArtist);

            --len;
        } while (width >= limit && len > 0);

        return shortArtist;
    }
    public String getShortTitle(float limit, BitmapFont font) {
        if (shortTitle != null)
        {
            return shortTitle;
        }

        float width = textRenderer.setFont(font).getWidth(title);

        if (width < limit)
            return title;

        int len = Math.max(0, title.length() - 2);

        do
        {
            while (len > 0 && title.charAt(len - 1) == ' ')
                --len;

            shortTitle = title.substring(0, len) + "...";
            width = textRenderer.getWidth(shortTitle);

            --len;
        } while (width >= limit && len > 0);

        return shortTitle;
    }
    public String getShortCreator(float limit, BitmapFont font) {
        if (shortCreator != null)
        {
            return shortCreator;
        }

        float width = textRenderer.setFont(font).getWidth(creator);

        if (width < limit)
            return creator;


        int len = Math.max(0, creator.length() - 3);

        do
        {
            while (len > 0 && creator.charAt(len - 1) == ' ')
                --len;

            shortCreator = creator.substring(0, len) + "...";
            width = textRenderer.getWidth(shortCreator);

            --len;
        } while (width >= limit && len > 0);

        return shortCreator;
    }

    public String songMeta(BitmapFont font, float limit) {
        if (songMeta != null)
        {
            return songMeta;
        }

        songMeta = artist + " - " + title;
        float width = textRenderer.setFont(font).getWidth(songMeta);

        if (width < limit)
            return songMeta;

        int len = Math.max(0, title.length() - 3);

        do
        {
            while (len > 0 && title.charAt(len - 1) == ' ')
                --len;

            songMeta = artist + " - " + title.substring(0, len) + "...";
            width = textRenderer.getWidth(songMeta);

            --len;
        } while (width >= limit && len > 0);

        if (len == 0) { //could not get short enough by just shortening title
            float artistLimit = limit * 0.4f, titleLimit = limit - artistLimit;

            len = Math.max(0, artist.length() - 3);
            do
            {
                while (len > 0 && artist.charAt(len - 1) == ' ')
                    --len;

                songMeta = artist.substring(0, len) + "... - ";
                width = textRenderer.getWidth(songMeta);

                --len;
            } while (width >= artistLimit && len > 0);

            int titleLen = Math.max(0, title.length() - 3);
            do
            {
                while (titleLen > 0 && title.charAt(titleLen - 1) == ' ')
                    --titleLen;

                songMeta = title.substring(0, titleLen) + "...";
                width = textRenderer.getWidth(songMeta);

                --titleLen;
            } while (width >= titleLimit && titleLen > 0);

            songMeta = artist.substring(0, len) + "... - " + title.substring(0, titleLen) + "...";
        }

        return songMeta;
    }

    public String getSongFile() {
        return songFile;
    }

    public boolean isEmpty()
    {
        return maps.isEmpty();
    }


    public void loadMaps()
    {
        File[] mapFiles = directory.listFiles((f)->f.getPath().endsWith(".osu"));

        if (mapFiles == null)
            return;

        for (File map : mapFiles)
        {
            MapInfo info = new MapInfo(map, this);
            if (info.getMode() == 1)
            {
                maps.add(info);

                if (songFile.isEmpty())
                    songFile = info.getSongFile();
                else if (!songFile.equals(info.getSongFile()))
                    sameSong = false; //i don't handle this very well right now xd
            }
        }

        sortMaps();
    }


    private void sortMaps()
    {
        if (maps.isEmpty())
            return;

        maps.sort(new MapDifficultyComparator());
    }

    public void add(FullMapInfo newBase) {
        maps.add(newBase.getInfo());

        sortMaps();
    }

    private static class MapDifficultyComparator implements Comparator<MapInfo>
    {
        private static int getDifficultyValue(MapInfo info)
        {
            String diff = info.getDifficultyName().toLowerCase(Locale.ROOT);
            if (diff.contains("inner") || diff.contains("ura"))
            {
                return 4;
            }
            else if (diff.contains("oni"))
            {
                return 3;
            }
            else if (diff.contains("muzu"))
            {
                return 2;
            }
            else if (diff.contains("futsuu"))
            {
                return 1;
            }
            else if (diff.contains("kantan"))
            {
                return 0;
            }
            return 5;
        }
        @Override
        public int compare(MapInfo o1, MapInfo o2) {
            return Integer.compare(getDifficultyValue(o2), getDifficultyValue(o1));
        }
    }
}
