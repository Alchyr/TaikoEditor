package alchyr.taikoedit.maps;

import com.badlogic.gdx.graphics.g2d.BitmapFont;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class Mapset {
    public String key; //Map folder

    public boolean sameSong; //Usually true, but will be false in some multi-mp3 beatmapsets. Should result in some features being disabled.

    protected String songFile = "";
    protected String creator = "";
    protected String title = "";
    protected String artist = "";
    protected String background = "";

    private String shortCreator, shortTitle, shortArtist;

    private final ArrayList<MapInfo> maps;
    //TODO: Reduce amount of irrelevant information stored in MapInfo constantly taking up ram

    public Mapset(String key, File[] mapFiles)
    {
        this.key = key;

        this.maps = new ArrayList<>();
        sameSong = true;

        for (File map : mapFiles)
        {
            MapInfo info = new MapInfo(map, this);
            if (info.mode == 1)
            {
                maps.add(info);

                if (songFile.isEmpty())
                    songFile = info.songFile;
                else if (!songFile.equals(info.songFile))
                    sameSong = false;
            }
        }
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

        int len = artist.length() - 3;

        do
        {
            shortArtist = artist.substring(0, len) + "...";
            width = textRenderer.getWidth(shortArtist);

            --len;
            if (artist.charAt(len - 1) == ' ')
                --len;
        } while (width >= limit);

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

        int len = title.length() - 3;

        do
        {
            shortTitle = title.substring(0, len) + "...";
            width = textRenderer.getWidth(shortTitle);

            --len;
            if (title.charAt(len - 1) == ' ')
                --len;
        } while (width >= limit);

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


        int len = creator.length() - 3;

        do
        {
            shortCreator = creator.substring(0, len) + "...";
            width = textRenderer.getWidth(shortCreator);

            --len;
            if (creator.charAt(len - 1) == ' ')
                --len;
        } while (width >= limit);

        return shortCreator;
    }

    public String getSongFile() {
        return songFile;
    }

    public boolean isEmpty()
    {
        return maps.isEmpty();
    }
}
