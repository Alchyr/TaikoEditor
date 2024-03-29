package alchyr.taikoedit.management;

import alchyr.taikoedit.management.assets.FilePathInfo;
import alchyr.taikoedit.management.localization.LocalizedText;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static alchyr.taikoedit.management.assets.FileHelper.withSeparator;

public class LocalizationMaster {
    private static final Logger logger = LogManager.getLogger("Localization");

    //As pure text doesn't take up too many resources compared to textures, they will not be unloaded when unneeded.
    private static HashMap<String, HashMap<String, LocalizedText>> allText = new HashMap<>();
    private static HashSet<FilePathInfo> loadedFiles = new HashSet<>(); //files to reload when language is changed
    public static final LocalizedText noText = new LocalizedText("");
    //Outer key is json filepath
    //Inner key is the specific key within that file.


    public static LocalizedText getLocalizedText(String groupKey, String entryKey)
    {
        HashMap<String, LocalizedText> fileMap = allText.getOrDefault(groupKey, null);
        if (fileMap != null)
        {
            LocalizedText returnVal = fileMap.getOrDefault(entryKey, null);
            if (returnVal == null)
            {
                logger.error("Entry " + entryKey + " not found in localization group key " + groupKey + ".");
                return noText;
            }

            return returnVal;
        }
        logger.error("Group key " + groupKey + " not found. It might not have been loaded.");
        return null;
    }
    public static Map<String, LocalizedText> getLocalizedGroup(String key) {
        return allText.getOrDefault(key, null);
    }

    public static void loadLocalizationFile(String path, String filename)
    {
        path = withSeparator(path);

        logger.info("Loading localization file: \"" + path + "\"" + " \"" + filename + "\"");

        String tempPath = path + SettingsMaster.getLanguage().name() + File.separator + filename;

        //Test if path exists.
        if (!Gdx.files.internal(tempPath).exists())
        {
            tempPath = withSeparator(path) + SettingsMaster.Language.ENG.name() + File.separator + filename;
        }

        if (Gdx.files.internal(tempPath).exists())
        {
            loadedFiles.add(new FilePathInfo(path, filename));

            Json json = new Json();

            String data = Gdx.files.internal(tempPath).readString(String.valueOf(StandardCharsets.UTF_8));

            LocalizedTextLoader localizedTextLoader = json.fromJson(LocalizedTextLoader.class, data);
            int count = 0;

            for (Map.Entry<String, List<LocalizedText>> localizedTextSet : localizedTextLoader.allText.entrySet()) {
                if (!allText.containsKey(localizedTextSet.getKey()))
                    allText.put(localizedTextSet.getKey(), new HashMap<>());

                HashMap<String, LocalizedText> fileText = allText.get(localizedTextSet.getKey());

                for (LocalizedText text : localizedTextSet.getValue())
                {
                    if (fileText.containsKey(text.key))
                    {
                        fileText.get(text.key).setData(text); //If key already exists, modify existing which will result in objects that reference that localized text having their text changed
                    }
                    else
                    {
                        fileText.put(text.key, text);
                    }
                }
                count += localizedTextSet.getValue().size();
            }
            logger.info("\t" + count + " values loaded successfully.");
        }
        else
        {
            logger.info("\tFile not found.");
        }
    }

    public static void loadDefaultFolder()
    {
        loadLocalizationFile("taikoedit\\localization", "General.json");
        loadLocalizationFile("taikoedit\\localization", "Keys.json");
    }

    //Should be called when language is changed.
    public static void updateLocalization()
    {
        for (FilePathInfo file : loadedFiles)
            loadLocalizationFile(file.path, file.filename);
    }


    private static class LocalizedTextLoader implements Json.Serializable {
        public HashMap<String, List<LocalizedText>> allText = new HashMap<>();
        /*public String name;
        public ArrayList<LocalizedText> allText = new ArrayList<>();*/

        @Override
        public void write(Json json) {

        }

        @Override
        public void read(Json json, JsonValue jsonData) {
            allText.clear();

            JsonValue data = jsonData.child;
            String name;
            List<LocalizedText> setText;

            do {
                name = data.name;
                setText = new ArrayList<>();

                for (JsonValue entry = data.child; entry != null; entry = entry.next)
                {
                    LocalizedText text = new LocalizedText(entry.name);
                    for (JsonValue value = entry.child; value != null; value = value.next)
                    {
                        text.put(value.name, value.asStringArray());
                    }

                    setText.add(text);
                }

                allText.put(name, setText);

                data = data.next();
            } while (data != null);
        }
    }
}