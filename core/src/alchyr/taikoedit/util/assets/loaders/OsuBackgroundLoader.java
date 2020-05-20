package alchyr.taikoedit.util.assets.loaders;

import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.assets.AssetInfo;
import alchyr.taikoedit.util.assets.FileHelper;
import alchyr.taikoedit.util.assets.SpecialLoader;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.util.assets.AssetLists.mipmaps;

public class OsuBackgroundLoader implements SpecialLoader {
    public static final String KEY = "osu_background";

    public static ArrayList<String> loadedBackgrounds = new ArrayList<>();

    @Override
    public void load(String name, AssetManager manager, AssetInfo info, String[] params) {
        File f = new File(FileHelper.concat(SettingsMaster.osuFolder, "Data", "bg"));

        if (f.isDirectory())
        {
            File[] files = f.listFiles(FileHelper::isImage);

            if (files != null)
            {
                for (File img : files)
                {
                    manager.load(img.getAbsolutePath(), Texture.class, mipmaps);
                    assetMaster.loadedAssets.put(img.getAbsolutePath().toLowerCase(), FileHelper.gdxSeparator(img.getAbsolutePath()));

                    loadedBackgrounds.add(img.getAbsolutePath().toLowerCase());
                }
            }
        }
    }

    @Override
    public void unload(String name, AssetInfo info, String[] params) {
        File f = new File(FileHelper.concat(SettingsMaster.osuFolder, "Data", "bg"));

        if (f.isDirectory())
        {
            File[] files = f.listFiles(FileHelper::isImage);

            if (files != null)
                for (File img : files)
                    assetMaster.unload(img.getAbsolutePath().toLowerCase());
        }

        loadedBackgrounds.clear();
    }
}
