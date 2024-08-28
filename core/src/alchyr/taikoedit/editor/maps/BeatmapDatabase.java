package alchyr.taikoedit.editor.maps;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.util.GeneralUtils;
import alchyr.taikoedit.util.Sync;
import alchyr.taikoedit.util.interfaces.functional.VoidMethod;
import alchyr.taikoedit.util.structures.CharacterTreeMap;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class BeatmapDatabase {
    private static final Logger logger = LogManager.getLogger("BeatmapDatabase");
    public static boolean updating = false;
    public static AtomicInteger updateCount = new AtomicInteger(0);

    private Thread saveThread;

    private static final String DATABASE_VER = "0";


    public static float progress = 0;
    public static int mapCount = 0;

    public Map<String, Mapset> mapsets = new HashMap<>();

    private final CharacterTreeMap<Mapset> indexedMapsets = new CharacterTreeMap<>();

    public BeatmapDatabase(File songsFolder, VoidMethod receiveLoadCompletion)
    {
        boolean delayed = receiveLoadCompletion != null;
        //First, try to load already generated map database

        try
        {
            logger.info("Loading Songs folder: " + songsFolder.getPath());
            long loadStart = System.nanoTime();

            progress = 0;
            mapCount = 0;

            File database = getDatabaseFile();
            indexedMapsets.clear();

            if (database.exists())
            {
                logger.info("Existing map data found. Attempting to load.");
                try {
                    HashMap<String, Mapset> oldData = loadDatabase(database, delayed);

                    if (oldData != null) {
                        if (delayed) {
                            mapsets = oldData;

                            Thread mapUpdating = new Thread(() -> updateData(oldData, songsFolder, (sets)->{
                                TaikoEditor.onMain(()->{
                                    indexedMapsets.clear();
                                    mapsets = sets;
                                    for (Mapset mapset : sets.values()) {
                                        index(mapset);
                                    }
                                    receiveLoadCompletion.run();

                                    saveDatabase();
                                });
                            }));
                            mapUpdating.setName("Maps Updating");
                            mapUpdating.setDaemon(true);
                            mapUpdating.setPriority(7);
                            mapUpdating.start();

                            return;
                        }

                        logger.info("Updating data.");
                        long updateStart = System.nanoTime();

                        updateData(oldData, songsFolder, (sets)->{
                            mapsets = sets;
                            for (Mapset mapset : sets.values()) {
                                index(mapset);
                            }
                        });
                        saveDatabase();
                        updateStart = System.nanoTime() - updateStart;
                        loadStart = System.nanoTime() - loadStart;
                        logger.debug("Updating database: " + (1.0 * updateStart / Sync.NANOS_IN_SECOND) + " seconds.");
                        logger.debug("Total load: " + (1.0 * loadStart / Sync.NANOS_IN_SECOND) + " seconds.");
                        return;
                    }
                    else {
                        indexedMapsets.clear();
                        logger.info("Failed to load database.");
                    }
                }
                catch (Exception e) {
                    logger.info("Failed to load database.");
                    GeneralUtils.logStackTrace(logger, e);
                }
            }

            logger.info("Loading new data.");
            long loadNewStart = System.nanoTime();
            loadData(songsFolder);
            saveDatabase();
            loadNewStart = System.nanoTime() - loadNewStart;
            loadStart = System.nanoTime() - loadStart;
            logger.debug("Loading new database: " + (1.0 * loadNewStart / Sync.NANOS_IN_SECOND) + " seconds.");
            logger.debug("Total load: " + (1.0 * loadStart / Sync.NANOS_IN_SECOND) + " seconds.");
        }
        catch (Exception e)
        {
            logger.error("Failed to generate map data.");
            e.printStackTrace();
        }
    }

    public void save() {
        if (saveThread == null) {
            saveThread = new Thread(()->{
                saveDatabase();
                saveThread = null;
            });
            saveThread.setName("TaikoEditor Save");
            saveThread.setDaemon(true);
            saveThread.start();
        }
        else {
            logger.info("Attempted to save while save already in progress.");
        }
    }

    private void loadData(File songsFolder) {
        Path songsFolderPath = songsFolder.toPath();
        Collection<Path> subFolders = new ConcurrentLinkedQueue<>();

        try (DirectoryStream<Path> songFolders = Files.newDirectoryStream(songsFolderPath)) {
            Collection<Path> finalSubFolders = subFolders;
            mapsets = StreamSupport.stream(songFolders.spliterator(), true)
                    .map((folder)->readFolder(folder, finalSubFolders))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toConcurrentMap((set)->set.key, (set)->set));

            logger.info("Checking subfolders of folders with no maps");
            while (!subFolders.isEmpty()) {
                Collection<Path> current = subFolders;
                subFolders = new ArrayList<>();

                for (Path sub : current) {
                    Mapset set = readFolder(sub, subFolders);
                    if (set != null) {
                        mapsets.put(set.key, set);
                    }
                }
            }


            int completeCount = 0;
            progress = 0;
            int total = mapsets.size();

            logger.info("Finished collecting mapsets, starting indexing");

            for (Mapset mapset : mapsets.values()) {
                index(mapset);
                ++completeCount;
                progress = (float) completeCount / total;
            }

            logger.info("Successfully loaded Songs folder.");
            progress = 1;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private void updateData(HashMap<String, Mapset> oldData, File songsFolder, Consumer<Map<String, Mapset>> completion) {
        //Similar to loadData, but first checks old data map.
        //If old data with matching filename exists, it will be used.
        //If filename has no match, it will be loaded normally.
        updating = true;

        Path songsFolderPath = songsFolder.toPath();
        Collection<Path> subFolders = new ConcurrentLinkedQueue<>();

        int max = oldData.size();
        updateCount.set(0);


        Map<String, Mapset> updatingSets;

        try (DirectoryStream<Path> songFolders = Files.newDirectoryStream(songsFolderPath)) {
            Collection<Path> finalSubFolders = subFolders;

            //parallel is definitely faster than direct iteration (tested)
            //However, main burden is the updateFolder method.
            updatingSets = StreamSupport.stream(songFolders.spliterator(), true)
                    .map((folder)->{
                        progress = updateCount.incrementAndGet() / (float) max;
                        return updateFolder(oldData, folder, finalSubFolders);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toConcurrentMap((set)->set.key, (set)->set));

            logger.info("Checking subfolders of folders with no maps");
            while (!subFolders.isEmpty()) {
                Collection<Path> current = subFolders;
                subFolders = new ArrayList<>();

                for (Path sub : current) {
                    Mapset set = updateFolder(oldData, sub, subFolders);
                    if (set != null) {
                        updatingSets.put(set.key, set);
                    }
                }
            }

            logger.info("Finished collecting mapsets");

            completion.accept(updatingSets);
            logger.info("Successfully loaded Songs folder.");
            progress = 1;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            updating = false;
        }
    }

    private Mapset readFolder(Path folder, Collection<Path> subFolders) {
        ++mapCount;

        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
            List<Path> folders = new ArrayList<>();
            List<Path> maps = new ArrayList<>();

            for (Path file : files) {
                if (file.toString().endsWith(".osu")) {
                    maps.add(file);
                }
                else {
                    folders.add(file);
                }
            }

            if (!maps.isEmpty()) {
                Mapset set = new Mapset(folder.toFile(), maps);

                if (!set.isEmpty()) {
                    return set;
                }
            }
            else {
                for (Path maybeFolder : folders) {
                    if (Files.isDirectory(maybeFolder)) {
                        subFolders.add(maybeFolder);
                    }
                }
            }
        }
        catch (NotDirectoryException ignored) {

        }
        catch (Exception e) {
            logger.info("Error occurred reading folder " + folder);
            logger.error(e.getStackTrace());
            e.printStackTrace();
        }
        return null;
    }

    private Mapset updateFolder(HashMap<String, Mapset> oldData, Path folder, Collection<Path> subFolders) {
        Mapset old = oldData.get(folder.toString());
        if (old == null) {
            //logger.info("Set not found in database: " + folder.getAbsolutePath());
            return readFolder(folder, subFolders);
        }

        ++mapCount;

        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
            boolean hasMap = false;
            List<Path> folders = new ArrayList<>();

            List<MapInfo> confirmed = new ArrayList<>();

            outer:
            for (Path file : files) {
                if (file.toString().endsWith(".osu")) {
                    hasMap = true;

                    File mapFile = file.toFile();

                    for (MapInfo info : old.getMaps()) {
                        if (info.getMapFile().equals(mapFile)) {
                            confirmed.add(info);
                            continue outer; //This file is all good, move on to the next one.
                        }
                    }

                    //This file doesn't exist in the old data. Have to load it.
                    MapInfo info = new MapInfo(mapFile, old);
                    if (info.getMode() == 1)
                    {
                        confirmed.add(info);
                        if (!old.getSongFile().equals(info.getSongFile()))
                            old.sameSong = false;
                        logger.info("Found added difficulty: " + info.getDifficultyName());
                    }
                }
                else {
                    folders.add(file);
                }
            }

            old.setMaps(confirmed);
            if (!old.isEmpty())
            {
                return old;
            }

            if (!hasMap) {
                for (Path maybeFolder : folders) {
                    if (Files.isDirectory(maybeFolder)) {
                        subFolders.add(maybeFolder);
                    }
                }
            }
        }
        catch (NotDirectoryException ignored) {

        }
        catch (Exception e) {
            logger.info("Error occurred reading folder " + folder);
            logger.error(e.getStackTrace());
            e.printStackTrace();
        }
        return null;
    }

    private void saveDatabase() {
        logger.info("Saving database.");
        File f = getDatabaseFile();
        Writer writer = null;
        Json json = new Json(JsonWriter.OutputType.json);
        try {
            writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(f.toPath()), StandardCharsets.UTF_8));
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
                    if (!m.sameSong) {
                        json.writeValue("songFile", map.getSongFile());
                    }
                    json.writeValue("background", map.getBackground());
                    //no need to write mode, it can be assumed to be 1
                    json.writeValue("name", map.getDifficultyName());
                    json.writeObjectEnd();
                }
                json.writeArrayEnd();
                json.writeObjectEnd();
            }
            json.writeArrayEnd();
            logger.info("Database saved.");
        }
        catch (Exception e) {
            logger.error("Failed to save map database.");
            GeneralUtils.logStackTrace(logger, e);
        }
        finally {
            StreamUtils.closeQuietly(writer);
        }
    }
    private HashMap<String, Mapset> loadDatabase(File f, boolean immediatelyIndex) throws Exception {
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

            String folder, songFile;
            boolean sameSong;
            File setDirectory, mapFile;
            ArrayList<MapInfo> maps;
            Mapset set;

            while (data != null) {
                try {
                    folder = data.getString("key");
                    songFile = data.getString("songFile");
                    sameSong = data.getBoolean("sameSong");
                    FileHandle songFileHandle = Gdx.files.absolute(songFile);
                    if (!songFileHandle.exists()) {
                        logger.info("Song file \"" + songFile + "\" does not exist. Map will be reloaded.");
                        data = data.next();
                        continue;
                    }

                    setDirectory = new File(folder);
                    if (setDirectory.exists() && setDirectory.isDirectory()) {
                        maps = new ArrayList<>();

                        JsonValue map = data.get("maps");
                        if (map != null)
                            map = map.child(); //enter array
                        while (map != null) {
                            mapFile = new File(setDirectory, map.getString("mapFile"));
                            if (mapFile.exists()) {
                                maps.add(new MapInfo(mapFile, sameSong ? songFile : map.getString("songFile"), map.getString("background"), map.getString("name")));
                            }
                            map = map.next();
                        }

                        if (maps.isEmpty()) {
                            logger.info("Folder \"" + folder + "\" contains no maps.");
                        }
                        else {
                            set = new Mapset(setDirectory, maps, sameSong, songFile, data.getString("creator"), data.getString("title"), data.getString("artist"), data.getString("background"));
                            processed.put(set.key, set);
                            index(set);
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

            logger.info("Database loaded.");
            return processed;
        }
        return null;
    }
    private File getDatabaseFile() {
        return new File("mapdata.json");
    }

    public void index(Mapset set) {
        indexedMapsets.put(set.getCreator().toLowerCase().split(" "), set, 1.5f); //mappers get bonus weight in the search.
        indexedMapsets.put(set.getArtist().toLowerCase().split(" "), set, 1.0f);
        indexedMapsets.put(set.getTitle().toLowerCase().split(" "), set, 1.0f);
    }
    public List<Mapset> search(String[] terms) {
        return indexedMapsets.search(terms, 0.3f);
    }
}
