package alchyr.taikoedit.management;

import alchyr.taikoedit.editor.maps.BeatmapDatabase;
import alchyr.taikoedit.editor.maps.Mapset;
import alchyr.taikoedit.util.assets.FileHelper;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class MapMaster {
    public static BeatmapDatabase mapDatabase;

    public static void load()
    {
        try
        {
            BeatmapDatabase.progress = 0;
            mapDatabase = new BeatmapDatabase(new File(FileHelper.concat(SettingsMaster.osuFolder, "Songs")));
        }
        catch (Exception e)
        {
            editorLogger.info("Unexpected error occured while loading beatmaps.");
            e.printStackTrace();
        }
    }

    public static float getProgress()
    {
        return BeatmapDatabase.progress;
    }

    public static Set<Mapset> search(String searchText)
    {
        if (mapDatabase != null)
        {
            String[] terms = searchText.split(" ");

            return mapDatabase.search(terms);
        }

        return new HashSet<>();
    }
}
