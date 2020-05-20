package alchyr.taikoedit.management;

import alchyr.taikoedit.maps.BeatmapDatabase;
import alchyr.taikoedit.maps.Mapset;
import alchyr.taikoedit.util.assets.FileHelper;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class MapMaster {
    public static BeatmapDatabase mapDatabase;

    public static void load()
    {
        mapDatabase = new BeatmapDatabase(new File(FileHelper.concat(SettingsMaster.osuFolder, "Songs")));
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
