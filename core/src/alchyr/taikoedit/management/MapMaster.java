package alchyr.taikoedit.management;

import alchyr.taikoedit.editor.maps.BeatmapDatabase;
import alchyr.taikoedit.editor.maps.Mapset;
import alchyr.taikoedit.management.assets.FileHelper;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class MapMaster {
    public static BeatmapDatabase mapDatabase;
    public static boolean loading = false;

    public static void load()
    {
        if (!loading) {
            try
            {
                loading = true;
                BeatmapDatabase.progress = 0;
                mapDatabase = null;
                System.gc();
                mapDatabase = new BeatmapDatabase(new File(FileHelper.concat(SettingsMaster.osuFolder, "Songs")));
            }
            catch (Exception e)
            {
                editorLogger.info("Unexpected error occurred while loading beatmaps.");
                e.printStackTrace();
            }
            finally {
                loading = false;
            }
        }
    }

    public static float getProgress()
    {
        return BeatmapDatabase.progress;
    }

    //Add tag searching?
    //Would mean adding tags to set information.
    //For search, use multiple search fields?
    //Would add precision, but I don't think it's really necessary, since someone searching should know what they're looking for already.
    //So, just one search field is fine.
    public static List<Mapset> search(String searchText)
    {
        if (mapDatabase != null)
        {
            String[] terms = searchText.toLowerCase().split(" ");

            return mapDatabase.search(terms);
        }

        return Collections.emptyList();
    }
}
