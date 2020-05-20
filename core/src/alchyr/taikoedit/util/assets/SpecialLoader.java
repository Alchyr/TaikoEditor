package alchyr.taikoedit.util.assets;

import com.badlogic.gdx.assets.AssetManager;

public interface SpecialLoader {
    void load(String name, AssetManager manager, AssetInfo info, String[] params);
    void unload(String name, AssetInfo info, String[] params);
}
