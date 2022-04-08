package alchyr.taikoedit.management.assets.skins;

import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.management.assets.FileHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class Skins {
    public static final List<SkinProvider> skins = new ArrayList<>();

    public static SkinProvider currentSkin = null;
    public static String loadedSkin = "Default";

    public static void load() {
        skins.clear();
        skins.add(currentSkin = new DefaultSkinProvider());

        File f = new File(FileHelper.concat(SettingsMaster.osuFolder, "Skins"));

        if (f.exists() && f.isDirectory()) {
            File[] skinFolders = f.listFiles(File::isDirectory);

            if (skinFolders == null)
                return;

            for (File folder : skinFolders) {
                OsuSkinProvider provider = new OsuSkinProvider(folder);
                if (provider.isValid()) {
                    skins.add(provider);
                }
            }
        }

        skins.sort(Comparator.comparing(SkinProvider::getName));

        for (SkinProvider provider : skins) {
            if (provider.getName().equals(loadedSkin))
                currentSkin = provider;
        }

        editorLogger.info("Found " + skins.size() + " skins.");
    }

    public static void dispose() {
        for (SkinProvider provider : skins) {
            provider.dispose();
        }
    }
}
