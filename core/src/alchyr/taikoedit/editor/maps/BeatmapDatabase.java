package alchyr.taikoedit.editor.maps;

import alchyr.taikoedit.util.structures.CharacterTreeMap;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;

public class BeatmapDatabase {
    private static final Logger logger = LogManager.getLogger("BeatmapDatabase");

    public static float progress = 0;

    public HashMap<String, Mapset> mapsets;
    public ArrayList<String> keys;

    private CharacterTreeMap<Mapset> indexedMapsets;

    public BeatmapDatabase(File songsFolder)
    {
        //First, try to load already generated map database

        try
        {
            logger.info("Loading Songs folder: " + songsFolder.getPath());

            FileHandle mapData = Gdx.files.local("maps");

            mapsets = new HashMap<>();
            keys = new ArrayList<>();
            indexedMapsets = new CharacterTreeMap<>();

            if (mapData.exists())
            {
                logger.info("Existing map data found. Ignored because it shouldn't exist, I don't have code for that yet.");
            }

            //else
            //{
                File[] songFolders = songsFolder.listFiles(File::isDirectory);

                if (songFolders != null)
                {
                    int count = 0;
                    for (File songFolder : songFolders)
                    {
                        readFolder(songFolder);

                        ++count;
                        progress = (float) count / songFolders.length;
                    }

                    logger.info("Successfully loaded Songs folder.");

                    //Save extra map information
                }
                else
                {
                    logger.error("Failed to read Song folder. Incorrect osu! folder path provided?");
                }
            //}
        }
        catch (Exception e)
        {
            logger.error("Failed to generate map data.");
            e.printStackTrace();
        }
    }

    private void readFolder(File folder) {
        try {
            boolean hasMap = false;
            List<File> folders = new ArrayList<>();
            File[] all = folder.listFiles();
            if (all != null) {
                for (File f : all) {
                    if (f.isDirectory()) {
                        folders.add(f);
                    }
                    else if (f.isFile() && f.getPath().endsWith(".osu")) {
                        hasMap = true;
                        break;
                    }
                }
            }

            if (hasMap)
            {
                logger.info("Mapset: " + folder.getName());
                Mapset set = new Mapset(folder);

                if (!set.isEmpty())
                {
                    keys.add(set.key);
                    mapsets.put(set.key, set);
                    indexedMapsets.put(set.getArtist().toLowerCase().split(" "), set);
                    indexedMapsets.put(set.getCreator().toLowerCase().split(" "), set);
                    indexedMapsets.put(set.getTitle().toLowerCase().split(" "), set);
                    logger.info("\t - Set has " + set.getMaps().size() + " taiko difficulties.");
                }
                else
                {
                    logger.info("\t - No taiko maps found in this set.");
                }
            }
            else
            {
                logger.info("\t - Folder has no map. Checking subfolders.");

                for (File f : folders)
                    readFolder(f);
            }
        }
        catch (Exception e) {
            logger.info("Error occurred reading folder " + folder.getPath());
            logger.error(e.getStackTrace());
            e.printStackTrace();
        }
    }

    public Set<Mapset> search(String[] terms) {
        Set<Mapset> results = new HashSet<>();

        boolean firstTerm = true;

        for (String term : terms)
        {
            List<Mapset> partialResult = indexedMapsets.find(term.toLowerCase());

            if (firstTerm)
            {
                firstTerm = false;
                results.addAll(partialResult);
            }
            else
            {
                results.retainAll(partialResult);
            }

            if (results.isEmpty())
                break;
        }

        return results;
    }
}
