package alchyr.taikoedit.management.assets.loaders;

import alchyr.taikoedit.management.AssetMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.management.assets.AssetInfo;
import alchyr.taikoedit.management.assets.FileHelper;
import alchyr.taikoedit.management.assets.SpecialLoader;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.management.assets.AssetLists.mipmaps;

public class OsuBackgroundLoader implements SpecialLoader {
    public static final String KEY = "osu_background";

    public static ArrayList<String> loadedBackgrounds = new ArrayList<>();

    @Override
    public void load(String name, AssetMaster manager, AssetInfo info, String[] params) {
        File f = new File(FileHelper.concat(SettingsMaster.osuFolder, "Data", "bg"));

        if (f.isDirectory())
        {
            File[] files = f.listFiles(FileHelper::isImage);

            if (files != null)
            {
                int i = MathUtils.random(files.length - 1);
                if (i >= 0 && i < files.length) {
                    File img = files[i];
                    if (img.exists()) {
                        manager.load(img.getAbsolutePath(), Texture.class, mipmaps);
                        assetMaster.loadedAssets.put(img.getAbsolutePath().toLowerCase(Locale.ROOT), FileHelper.gdxSeparator(img.getAbsolutePath()));

                        loadedBackgrounds.add(img.getAbsolutePath().toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
    }

    @Override
    public void unload(String name, AssetInfo info, String[] params) {
        for (String s : loadedBackgrounds) {
            assetMaster.unload(s); //unloadAbs
        }

        loadedBackgrounds.clear();
    }
}
