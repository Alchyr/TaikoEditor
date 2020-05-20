package alchyr.taikoedit.management;


import alchyr.taikoedit.util.assets.AssetLists;
import alchyr.taikoedit.util.assets.SpecialLoader;
import alchyr.taikoedit.util.assets.loaders.OsuBackgroundLoader;
import alchyr.taikoedit.util.structures.Pair;
import alchyr.taikoedit.util.assets.RegionInfo;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Json;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

//Manages the AssetManager :)
public class AssetMaster {
    private AssetLists assetLists;

    public HashMap<String, String> loadedAssets = new HashMap<>();

    public ArrayList<Pair<String, RegionInfo>> loadingRegions = new ArrayList<>();
    public HashMap<String, TextureAtlas.AtlasRegion> loadedRegions = new HashMap<>();
    public HashMap<String, ArrayList<String>> textureRegions = new HashMap<>(); //for unloading regions. Texture file and region names

    public HashMap<String, SpecialLoader> specialLoaders = new HashMap<>();

    public HashMap<String, BitmapFont> loadedFonts = new HashMap<>(); //fonts are stored separately. They should never be unloaded.

    private final AssetManager m;

    public void loadAssetLists(String filePath)
    {
        String data = Gdx.files.internal(filePath).readString(String.valueOf(StandardCharsets.UTF_8));

        Json json = new Json();

        AssetLists loadedLists = json.fromJson(AssetLists.class, data);
        assetLists.addLists(loadedLists);

        editorLogger.info("Loaded additional asset lists successfully. " + loadedLists.count() + " lists loaded.");
    }
    public void loadAssetLists()
    {
        String data = Gdx.files.internal("taikoedit/data/AssetLists.json").readString(String.valueOf(StandardCharsets.UTF_8));

        Json json = new Json();

        assetLists = json.fromJson(AssetLists.class, data);

        editorLogger.info("Loaded asset lists successfully. " + assetLists.count() + " lists loaded.");
    }
    public void addSpecialLoaders()
    {
        specialLoaders.clear();

        specialLoaders.put(OsuBackgroundLoader.KEY, new OsuBackgroundLoader());
    }


    public AssetMaster()
    {
        m = new AssetManager();
    }

    public boolean update()
    {
        if (m.update())
        {
            Texture t;
            for (Pair<String, RegionInfo> info : loadingRegions)
            {
                t = m.get(info.b.texture);

                loadedRegions.put(info.a, new TextureAtlas.AtlasRegion(t, info.b.x, info.b.y, info.b.width, info.b.height));

                if (!textureRegions.containsKey(info.b.texture))
                    textureRegions.put(info.b.texture, new ArrayList<>());

                textureRegions.get(info.b.texture).add(info.a);
            }

            return true;
        }
        return false;
    }
    public float getProgress()
    {
        return m.getProgress();
    }

    @SuppressWarnings("unchecked")
    public <T> T get (String name) {
        name = name.toLowerCase();
        if (name.startsWith("font:"))
        {
            return (T) getFont(name);
        }
        if (loadedAssets.containsKey(name))
            return m.get(loadedAssets.get(name));

        if (!m.contains(name))
            editorLogger.error("Attempted to use \"" + name + "\" while it had not been loaded!");
        return m.get(name);
    }

    public TextureAtlas.AtlasRegion getRegion(String name)
    {
        name = name.toLowerCase();

        if (loadedRegions.containsKey(name))
            return loadedRegions.get(name);

        editorLogger.error("Attempted to use AtlasRegion \"" + name + "\" while it had not been loaded or is still loading!");

        return null;
    }

    public BitmapFont getFont(String name) {
        name = name.toLowerCase();
        if (!name.contains(":"))
            name = "font:" + name;

        if (loadedFonts.containsKey(name))
            return loadedFonts.get(name);

        if (m.isLoaded(loadedAssets.get(name)))
        {
            loadedFonts.put(name, m.get(loadedAssets.get(name)));
            return loadedFonts.get(name);
        }
        else if (m.contains(loadedAssets.get(name)))
        {
            editorLogger.error("Attempted to access font " + name + " while it is still loading!");
            return null;
        }

        if (!m.contains(name))
            editorLogger.error("Attempted to use font \"" + name + "\" while it had not been loaded!");

        return m.get(name); //last resort
    }

    public void load(String key, String filename, Class<?> type)
    {
        loadedAssets.put(key, filename);
        m.load(filename, type);
    }

    public void loadList(String assetList)
    {
        assetLists.loadList(assetList, m);
    }

    public void unload(String name)
    {
        if (loadedAssets.containsKey(name))
        {
            m.unload(loadedAssets.remove(name));
        }
        else
        {
            if (loadedAssets.containsValue(name))
            {
                String remove = null;
                for (String s : loadedAssets.keySet())
                {
                    if (loadedAssets.get(s).equals(name))
                    {
                        remove = s;
                        break;
                    }
                }
                if (remove != null)
                    loadedAssets.remove(remove);
            }
            m.unload(name);
        }
    }

    public void unloadList(String assetList)
    {
        assetLists.unloadList(assetList, this);
    }

    public void clear() //does not clear fonts.
    {
        m.clear();
        loadedAssets.clear();
        loadedRegions.clear();
    }

    public void dispose()
    {
        m.dispose();
        loadedAssets.clear();
        loadedRegions.clear();

        for (Map.Entry<String, BitmapFont> fonts : loadedFonts.entrySet())
        {
            fonts.getValue().dispose();
        }
        loadedFonts.clear();
    }
}
