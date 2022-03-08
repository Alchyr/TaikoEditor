package alchyr.taikoedit.editor.maps;

import alchyr.taikoedit.util.GeneralUtils;
import alchyr.taikoedit.util.structures.CharacterTreeMap;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.StringBuilder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BeatmapDatabase {
    private static final Logger logger = LogManager.getLogger("BeatmapDatabase");
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ArrayList<Future<?>> activeTasks = new ArrayList<>();

    private static final String DATABASE_VER = "0";


    public static float progress = 0;

    public HashMap<String, Mapset> mapsets;

    private CharacterTreeMap<Mapset> indexedMapsets;

    public BeatmapDatabase(File songsFolder)
    {
        //First, try to load already generated map database

        try
        {
            logger.info("Loading Songs folder: " + songsFolder.getPath());

            File database = getDatabaseFile();

            mapsets = new HashMap<>();
            indexedMapsets = new CharacterTreeMap<>();

            if (database.exists())
            {
                logger.info("Existing map data found. Attempting to load.");
                try {
                    HashMap<String, Mapset> oldData = loadDatabase(database);
                    if (oldData != null) {
                        logger.info("Updating data.");
                        updateData(oldData, songsFolder);
                        saveDatabase();
                        return;
                    }
                }
                catch (Exception e) {
                    logger.info("Failed to load database.");
                    GeneralUtils.logStackTrace(logger, e);
                }
            }

            logger.info("Loading new data.");
            loadData(songsFolder);
            saveDatabase();
        }
        catch (Exception e)
        {
            logger.error("Failed to generate map data.");
            e.printStackTrace();
        }
    }

    private static final int TASK_LIMIT = 16, STAGE_LIMIT = TASK_LIMIT - 1;
    private static final List<List<File>> subLists = new ArrayList<>();
    private static final List<List<Mapset>> processed = new ArrayList<>();
    private int activeCount = 0;
    private int completeCount = 0;
    private final Object loadWaiter = new Object();
    private void loadData(File songsFolder) {
        File[] songFolders = songsFolder.listFiles(File::isDirectory);
        activeCount = 0;
        completeCount = 0;

        if (songFolders != null)
        {
            subLists.clear();
            processed.clear();
            for (int i = 0; i < TASK_LIMIT; ++i) {
                subLists.add(new ArrayList<>());
                processed.add(new ArrayList<>());
            }

            synchronized (loadWaiter) {
                try {
                    int step = songFolders.length / TASK_LIMIT, stage = 0, limit = step;
                    //Divide folders into 32 sub-sections and assign a task for each section
                    for (int i = 0; i < songFolders.length; ++i) {
                        if (stage < STAGE_LIMIT && i > limit) {
                            int index = stage;
                            activeTasks.add(executor.submit(()->{
                                for (File f : subLists.get(index))
                                    readFolder(f, index, true);

                                --activeCount;
                                if (activeCount <= 0)
                                    loadWaiter.notify();
                            }));
                            ++activeCount;

                            ++stage;
                            limit += step;
                            progress = (float) completeCount / songFolders.length; //update progress during startup
                        }
                        subLists.get(stage).add(songFolders[i]);
                    }
                    int index = stage;
                    activeTasks.add(executor.submit(()->{
                        for (File f : subLists.get(index))
                            readFolder(f, index, true);

                        --activeCount;
                        if (activeCount <= 0)
                            loadWaiter.notify();
                    }));
                    ++activeCount;

                    while (!activeTasks.isEmpty()) {
                        progress = (float) completeCount / songFolders.length;
                        activeTasks.removeIf(Future::isDone);
                        if (!activeTasks.isEmpty())
                            loadWaiter.wait(250);
                    }
                }
                catch (InterruptedException ignored) {

                }
            }

            executor.shutdownNow();

            logger.info("Read mapset data. Adding tracking data.");

            completeCount = 0;
            progress = 0;
            for (List<Mapset> mapsetList : processed) {
                for (Mapset set : mapsetList) {
                    mapsets.put(set.key, set);
                    index(set);
                }
                ++completeCount;
                progress = (float) completeCount / processed.size();
            }

            logger.info("Successfully loaded Songs folder.");

            //Save extra map information
        }
        else
        {
            executor.shutdownNow();
            logger.error("Failed to read Song folder. Incorrect osu! folder path provided?");
        }
        subLists.clear();
        processed.clear();
    }
    private void updateData(HashMap<String, Mapset> oldData, File songsFolder) {
        //Similar to loadData, but first checks old data map.
        //If old data with matching filename exists, it will be used.
        //If filename has no match, it will be loaded normally.
        File[] songFolders = songsFolder.listFiles(File::isDirectory);
        activeCount = 0;
        completeCount = 0;

        if (songFolders != null)
        {
            subLists.clear();
            processed.clear();
            for (int i = 0; i < TASK_LIMIT; ++i) {
                subLists.add(new ArrayList<>());
                processed.add(new ArrayList<>());
            }

            synchronized (loadWaiter) {
                try {
                    int step = songFolders.length / TASK_LIMIT, stage = 0, limit = step;
                    //Divide folders into 32 sub-sections and assign a task for each section
                    for (int i = 0; i < songFolders.length; ++i) {
                        if (stage < STAGE_LIMIT && i > limit) {
                            int index = stage;
                            activeTasks.add(executor.submit(()->{
                                for (File f : subLists.get(index))
                                    updateFolder(oldData, f, index, true);

                                --activeCount;
                                if (activeCount <= 0)
                                    loadWaiter.notify();
                            }));
                            ++activeCount;

                            ++stage;
                            limit += step;
                            progress = (float) completeCount / songFolders.length; //update progress during startup
                        }

                        subLists.get(stage).add(songFolders[i]);
                    }
                    int index = stage;
                    activeTasks.add(executor.submit(()->{
                        for (File f : subLists.get(index))
                            updateFolder(oldData, f, index, true);

                        --activeCount;
                        if (activeCount <= 0)
                            loadWaiter.notify();
                    }));
                    ++activeCount;

                    while (!activeTasks.isEmpty()) {
                        progress = (float) completeCount / songFolders.length;
                        activeTasks.removeIf(Future::isDone);
                        if (!activeTasks.isEmpty())
                            loadWaiter.wait(250);
                    }
                }
                catch (InterruptedException ignored) {

                }
            }
            executor.shutdownNow();

            logger.info("Read mapset data. Adding tracking data.");

            completeCount = 0;
            progress = 0;
            for (List<Mapset> mapsetList : processed) {
                for (Mapset set : mapsetList) {
                    mapsets.put(set.key, set);
                    index(set);
                }
                ++completeCount;
                progress = (float) completeCount / processed.size();
            }

            logger.info("Successfully loaded Songs folder.");

            //Save extra map information
        }
        else
        {
            executor.shutdownNow();
            logger.error("Failed to read Song folder. Incorrect osu! folder path provided?");
        }
        subLists.clear();
        processed.clear();
    }

    private void readFolder(File folder, int storeIndex, boolean count) {
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
                //logger.info("Mapset: " + folder.getName());
                Mapset set = new Mapset(folder);

                if (!set.isEmpty())
                {
                    processed.get(storeIndex).add(set);
                    logger.info("Mapset " + folder.getName() + " has " + set.getMaps().size() + " taiko difficulties.");
                }
                else
                {
                    logger.info("Mapset " + folder.getName() + " has no taiko maps.");
                }
            }
            else
            {
                logger.info("Folder " + folder.getName() + " has no maps. Checking subfolders.");

                for (File f : folders)
                    readFolder(f, storeIndex, false);
            }
        }
        catch (Exception e) {
            logger.info("Error occurred reading folder " + folder.getPath());
            logger.error(e.getStackTrace());
            e.printStackTrace();
        }
        finally {
            if (count) {
                ++completeCount;
            }
        }
    }
    //Overload will call update first on subfolders rather than immediately trying to read them.
    private void readFolder(HashMap<String, Mapset> oldData, File folder, int storeIndex, boolean count) {
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
                //logger.info("Mapset: " + folder.getName());
                Mapset set = new Mapset(folder);

                if (!set.isEmpty())
                {
                    processed.get(storeIndex).add(set);
                    logger.info("Mapset " + folder.getName() + " has " + set.getMaps().size() + " taiko difficulties.");
                }
                else
                {
                    logger.info("Mapset " + folder.getName() + " has no taiko maps.");
                }
            }
            else
            {
                logger.info("Folder " + folder.getName() + " has no maps. Checking subfolders.");

                for (File f : folders)
                    updateFolder(oldData, f, storeIndex, false);
            }
        }
        catch (Exception e) {
            logger.info("Error occurred reading folder " + folder.getPath());
            logger.error(e.getStackTrace());
            e.printStackTrace();
        }
        finally {
            if (count) {
                ++completeCount;
            }
        }
    }
    private void updateFolder(HashMap<String, Mapset> oldData, File folder, int storeIndex, boolean count) {
        Mapset old = oldData.get(folder.getAbsolutePath());
        if (old == null) {
            logger.info("Set not found in database: " + folder.getAbsolutePath());
            readFolder(oldData, folder, storeIndex, count);
            return;
        }

        try {
            boolean hasMap = false;
            List<File> folders = new ArrayList<>();

            List<MapInfo> confirmed = new ArrayList<>(), unconfirmed = old.getMaps();

            File[] all = folder.listFiles();
            if (all != null) {
                outer:
                for (File f : all) {
                    if (!hasMap && f.isDirectory()) {
                        folders.add(f);
                    }
                    else if (f.isFile() && f.getPath().endsWith(".osu")) {
                        hasMap = true;

                        for (MapInfo info : unconfirmed) {
                            if (info.getMapFile().equals(f)) {
                                confirmed.add(info);
                                continue outer; //This file is all good, move on to the next one.
                            }
                        }

                        //This file doesn't exist in the old data. Have to load it.
                        MapInfo info = new MapInfo(f, old);
                        if (info.getMode() == 1)
                        {
                            confirmed.add(info);
                            if (!old.getSongFile().equals(info.getSongFile()))
                                old.sameSong = false;
                            logger.info("Found added difficulty: " + info.getDifficultyName());
                        }
                    }
                }

                old.setMaps(confirmed);
                if (!old.isEmpty())
                {
                    processed.get(storeIndex).add(old);
                }
            }


            if (!hasMap) {
                //logger.info("\t - Folder has no map. Checking subfolders.");

                for (File f : folders)
                    updateFolder(oldData, f, storeIndex, false);
            }
        }
        catch (Exception e) {
            logger.info("Error occurred reading folder " + folder.getPath());
            logger.error(e.getStackTrace());
            e.printStackTrace();
        }
        finally {
            if (count) {
                ++completeCount;
            }
        }
    }

    private void saveDatabase() {
        logger.info("Saving database.");
        File f = getDatabaseFile();
        Writer writer = null;
        Json json = new Json(JsonWriter.OutputType.json);
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
            json.setWriter(writer);

            json.writeArrayStart();
            json.writeObjectStart();
            json.writeValue("ver", DATABASE_VER);
            json.writeObjectEnd();
            for (Map.Entry<String, Mapset> mapsetEntry : mapsets.entrySet()) {
                Mapset m = mapsetEntry.getValue();
                json.writeObjectStart();
                json.writeValue("key", m.key);
                json.writeValue("sameSong", m.sameSong);
                json.writeValue("songFile", m.songFile);
                json.writeValue("creator", m.creator);
                json.writeValue("title", m.title);
                json.writeValue("artist", m.artist);
                json.writeValue("background", m.background);
                json.writeArrayStart("maps");
                for (MapInfo map : m.getMaps()) {
                    json.writeObjectStart();
                    json.writeValue("mapFile", map.getMapFile().getName());
                    json.writeValue("songFile", map.getSongFile());
                    json.writeValue("background", map.getBackground());
                    //no need to write mode, it can be assumed to be 1
                    json.writeValue("name", map.getDifficultyName());
                    json.writeObjectEnd();
                }
                json.writeArrayEnd();
                json.writeObjectEnd();
            }
            json.writeArrayEnd();
        }
        catch (Exception e) {
            logger.error("Failed to save map database.");
            GeneralUtils.logStackTrace(logger, e);
        }
        finally {
            StreamUtils.closeQuietly(writer);
        }
    }
    private HashMap<String, Mapset> loadDatabase(File f) throws Exception {
        if (f.isFile() && f.canRead())
        {
            FileInputStream in = new FileInputStream(f);
            JsonValue data = new JsonReader().parse(in); //Closes stream

            if (data == null) {
                logger.error("No data found in database.");
                return null;
            }
            data = data.child();
            if (data == null) {
                logger.info("No data found in database.");
                return null;
            }
            String ver = data.getString("ver");
            if (!DATABASE_VER.equals(ver)) {
                logger.info("Database version doesn't match. Reloading maps.");
                return null;
            }
            data = data.next();

            HashMap<String, Mapset> processed = new HashMap<>();

            String folder;
            File setDirectory, mapFile;
            ArrayList<MapInfo> maps;
            Mapset set;

            while (data != null) {
                try {
                    folder = data.getString("key");
                    setDirectory = new File(folder);
                    if (setDirectory.exists() && setDirectory.isDirectory()) {
                        maps = new ArrayList<>();

                        JsonValue map = data.get("maps");
                        if (map != null)
                            map = map.child(); //enter array
                        while (map != null) {
                            mapFile = new File(setDirectory, map.getString("mapFile"));
                            if (mapFile.exists()) {
                                maps.add(new MapInfo(mapFile, map.getString("songFile"), map.getString("background"), map.getString("name")));
                            }
                            map = map.next();
                        }

                        if (maps.isEmpty()) {
                            logger.info("Folder \"" + folder + "\" contains no maps.");
                        }
                        else {
                            set = new Mapset(setDirectory, maps, data.getBoolean("sameSong"), data.getString("songFile"), data.getString("creator"), data.getString("title"), data.getString("artist"), data.getString("background"));
                            processed.put(set.key, set);
                        }
                    }
                    else {
                        logger.info("Folder \"" + folder + "\" does not exist.");
                    }
                }
                catch (Exception e) {
                    logger.error("Error occurred while processing database.");
                    GeneralUtils.logStackTrace(logger, e);
                }

                data = data.next();
            }

            return processed;
        }
        return null;
    }
    private File getDatabaseFile() {
        return new File("mapdata.json");
    }

    private void index(Mapset set) {
        indexedMapsets.put(set.getCreator().toLowerCase().split(" "), set, 1.5f); //mappers get bonus weight in the search.
        indexedMapsets.put(set.getArtist().toLowerCase().split(" "), set, 1.0f);
        indexedMapsets.put(set.getTitle().toLowerCase().split(" "), set, 1.0f);
    }
    public List<Mapset> search(String[] terms) {
        return indexedMapsets.search(terms, 0.3f);
    }
}
