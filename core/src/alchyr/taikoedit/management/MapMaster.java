package alchyr.taikoedit.management;

import alchyr.taikoedit.editor.maps.BeatmapDatabase;
import alchyr.taikoedit.editor.maps.Mapset;
import alchyr.taikoedit.management.assets.FileHelper;
import alchyr.taikoedit.util.interfaces.functional.VoidMethod;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class MapMaster {
    public static BeatmapDatabase mapDatabase;
    public static boolean canTryLoad() {
        return !loading && !BeatmapDatabase.updating;
    }
    public static boolean loading = false;

    public static void load()
    {
        if (canTryLoad()) {
            try
            {
                loading = true;
                BeatmapDatabase.progress = 0;
                mapDatabase = null;
                System.gc();
                mapDatabase = new BeatmapDatabase(new File(FileHelper.concat(SettingsMaster.osuFolder, "Songs")), null);
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

    public static void loadDelayed(VoidMethod receiveLoadCompletion) {
        if (canTryLoad()) {
            try
            {
                loading = true;
                BeatmapDatabase.progress = 0;
                mapDatabase = null;
                System.gc();
                mapDatabase = new BeatmapDatabase(new File(FileHelper.concat(SettingsMaster.osuFolder, "Songs")), receiveLoadCompletion);
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
            return search(searchText.toLowerCase().split(" "));
        }

        return Collections.emptyList();
    }

    //Should be entirely lowercase
    public static List<Mapset> search(String... searchTerms) {
        return mapDatabase.search(searchTerms);
    }
}
