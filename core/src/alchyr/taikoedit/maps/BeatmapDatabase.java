package alchyr.taikoedit.maps;

import alchyr.taikoedit.util.structures.CharacterTreeMap;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;

public class BeatmapDatabase {
    private static final Logger logger = LogManager.getLogger("BeatmapDatabase");

    public HashMap<String, Mapset> mapsets;
    public ArrayList<String> keys;

    private CharacterTreeMap<Mapset> indexedMapsets;

    public BeatmapDatabase(File songsFolder)
    {
        //First, try to load already generated map database

        try
        {
            FileHandle mapData = Gdx.files.local("maps");

            mapsets = new HashMap<>();
            keys = new ArrayList<>();
            indexedMapsets = new CharacterTreeMap<>();

            if (mapData.exists())
            {

            }
            else
            {
                File[] songFolders = songsFolder.listFiles(File::isDirectory);

                if (songFolders != null)
                {
                    for (File songFolder : songFolders)
                    {
                        File[] maps = songFolder.listFiles((f)->f.getName().endsWith(".osu"));

                        if (maps != null && maps.length > 0) //There are maps in this folder.
                        {
                            Mapset set = new Mapset(songFolder.getName(), maps);

                            if (!set.isEmpty())
                            {
                                keys.add(set.key);
                                mapsets.put(set.key, set);
                                indexedMapsets.put(set.getArtist().toLowerCase().split(" "), set);
                                indexedMapsets.put(set.getCreator().toLowerCase().split(" "), set);
                                indexedMapsets.put(set.getTitle().toLowerCase().split(" "), set);
                            }
                        }
                    }

                    logger.info("Successfully loaded Songs folder.");
                }
                else
                {
                    logger.error("Failed to read Song folder. Incorrect osu! folder path provided?");
                }
            }
        }
        catch (Exception e)
        {
            logger.error("Failed to generate map data.");
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
