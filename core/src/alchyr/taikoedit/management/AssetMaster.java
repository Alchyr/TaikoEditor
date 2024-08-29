package alchyr.taikoedit.management;


import alchyr.taikoedit.management.assets.RenderComponent;
import alchyr.taikoedit.management.assets.loaders.OsuBackgroundLoader;
import alchyr.taikoedit.util.GeneralUtils;
import alchyr.taikoedit.management.assets.AssetLists;
import alchyr.taikoedit.management.assets.SpecialLoader;
import alchyr.taikoedit.util.structures.Pair;
import alchyr.taikoedit.management.assets.RegionInfo;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetErrorListener;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGeneratorLoader;
import com.badlogic.gdx.graphics.g2d.freetype.FreetypeFontLoader;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

//Manages the AssetManager :)
public class AssetMaster extends AssetManager implements AssetErrorListener {
    private AssetLists assetLists;

    public HashMap<String, String> loadedAssets = new HashMap<>();

    public ArrayList<Pair<String, RegionInfo>> loadingRegions = new ArrayList<>();
    public HashMap<String, TextureAtlas.AtlasRegion> loadedRegions = new HashMap<>();
    public HashMap<String, ArrayList<String>> textureRegions = new HashMap<>(); //for unloading regions. Texture file and region names
    public HashMap<String, RenderComponent<?>> renderComponents = new HashMap<>();

    public HashMap<String, SpecialLoader> specialLoaders = new HashMap<>();

    private boolean doneLoading;

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
        super();
        setErrorListener(this);
        doneLoading = true;

        FileHandleResolver resolver = new InternalFileHandleResolver();
        setLoader(FreeTypeFontGenerator.class, new FreeTypeFontGeneratorLoader(resolver));
        setLoader(BitmapFont.class, ".ttf", new FreetypeFontLoader(resolver));
    }

    public void fastUpdate() {
        try {
            doneLoading = update();
            if (doneLoading && !loadingRegions.isEmpty())
            {
                Texture t;
                for (Pair<String, RegionInfo> info : loadingRegions)
                {
                    t = super.get(info.b.texture);

                    loadedRegions.put(info.a, new TextureAtlas.AtlasRegion(t, info.b.x, info.b.y, info.b.width, info.b.height));

                    if (!textureRegions.containsKey(info.b.texture))
                        textureRegions.put(info.b.texture, new ArrayList<>());

                    textureRegions.get(info.b.texture).add(info.a);
                }

                loadingRegions.clear();
            }
        }
        catch (GdxRuntimeException e) {
            editorLogger.error("Exception occurred loading asset.");
            GeneralUtils.logStackTrace(editorLogger, e);
        }
    }
    public void longUpdate(int time) {
        try {
            doneLoading = update(time);
            if (doneLoading && !loadingRegions.isEmpty())
            {
                Texture t;
                for (Pair<String, RegionInfo> info : loadingRegions)
                {
                    t = super.get(info.b.texture);

                    loadedRegions.put(info.a, new TextureAtlas.AtlasRegion(t, info.b.x, info.b.y, info.b.width, info.b.height));

                    if (!textureRegions.containsKey(info.b.texture))
                        textureRegions.put(info.b.texture, new ArrayList<>());

                    textureRegions.get(info.b.texture).add(info.a);
                }

                loadingRegions.clear();
            }
        }
        catch (GdxRuntimeException e) {
            editorLogger.error("Exception occurred loading asset.");
            GeneralUtils.logStackTrace(editorLogger, e);
        }
    }

    public boolean isDoneLoading() {
        return doneLoading;
    }

    @Override
    public synchronized float getProgress() {
        return super.getProgress();
    }

    public <T> T get(String name) {
        name = name.toLowerCase(Locale.ROOT);
        if (loadedAssets.containsKey(name))
            return super.get(loadedAssets.get(name));

        if (!super.contains(name))
            editorLogger.error("Attempted to use \"" + name + "\" while it had not been loaded!");
        return super.get(name);
    }
    @SuppressWarnings("unchecked")
    public <T> RenderComponent<T> getRenderComponent(String name, Class<T> base) {
        name = name.toLowerCase(Locale.ROOT);
        if (renderComponents.containsKey(name)) {
            return (RenderComponent<T>) renderComponents.get(name);
        }
        else {
            T obj = get(name);
            if (obj != null) {
                RenderComponent<T> component = new RenderComponent<>(obj, base);
                renderComponents.put(name, component);
                return component;
            }
        }
        return null;
    }

    public TextureAtlas.AtlasRegion getRegion(String name)
    {
        name = name.toLowerCase(Locale.ROOT);

        if (loadedRegions.containsKey(name))
            return loadedRegions.get(name);

        editorLogger.error("Attempted to use AtlasRegion \"" + name + "\" while it had not been loaded or is still loading!");

        return null;
    }

    public BitmapFont getFont(String name) {
        name = name.toLowerCase(Locale.ROOT);
        if (!name.contains(":"))
            name = "font:" + name;

        return get(name);
    }

    public void markLoading() {
        doneLoading = false;
    }
    public void load(String key, String filename, Class<?> type)
    {
        filename = filename.replace('\\', '/'); //gdx dumb
        key = key.toLowerCase(Locale.ROOT);
        doneLoading = false;
        loadedAssets.put(key, filename);
        super.load(filename, type);
    }

    public void loadList(String assetList)
    {
        doneLoading = false;
        assetLists.loadList(assetList, this);
    }

    @Override
    public void unload(String name)
    {
        try {
            String lowerName = name.toLowerCase(Locale.ROOT);
            renderComponents.remove(lowerName);
            if (loadedAssets.containsKey(lowerName))
            {
                super.unload(loadedAssets.remove(lowerName));
            }
            else if (loadedAssets.containsKey(name)) {
                super.unload(loadedAssets.remove(name));
            }
            else
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

                if (super.isLoaded(name))
                    super.unload(name);
            }
        }
        catch (GdxRuntimeException e) {
            editorLogger.error(e.getMessage());
            GeneralUtils.logStackTrace(editorLogger, e);
        }
    }
    //case sensitive
    public void unloadAbs(String name)
    {
        renderComponents.remove(name);
        if (loadedAssets.containsKey(name))
        {
            super.unload(loadedAssets.remove(name));
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
            super.unload(name);
        }
    }

    public void unloadList(String assetList)
    {
        assetLists.unloadList(assetList);
    }

    @Override
    public void clear() //does not clear fonts.
    {
        super.clear();
        loadedAssets.clear();
        loadedRegions.clear();
    }

    @Override
    public void dispose()
    {
        super.dispose();
        loadedAssets.clear();
        loadedRegions.clear();
    }

    @Override
    public void error(AssetDescriptor asset, Throwable e) {
        editorLogger.error("Exception occurred loading asset: " + asset);
        GeneralUtils.logStackTrace(editorLogger, e);
    }
}
