package alchyr.taikoedit.util.assets;

import alchyr.taikoedit.management.AssetMaster;

public interface SpecialLoader {
    void load(String name, AssetMaster manager, AssetInfo info, String[] params);
    void unload(String name, AssetInfo info, String[] params);
}
