package alchyr.taikoedit.util.assets;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

public class AssetInfo implements Json.Serializable {
    public String getAssetName(String listName) {
        return listName + ":" + assetName;
    }

    public String getFileName() {
        return fileName;
    }

    public String getType() {
        return type;
    }

    public String getParams() {return params; }

    private String assetName;
    private String fileName;
    private String type;
    private String params;

    public AssetInfo()
    {}

    public AssetInfo(String assetName, String fileName, String type)
    {
        this.assetName = assetName.toLowerCase();
        this.fileName = fileName;
        this.type = type.toLowerCase();
        params = null;
    }
    public AssetInfo(String assetName, String fileName, String type, String params)
    {
        this.assetName = assetName.toLowerCase();
        this.fileName = fileName;
        this.type = type.toLowerCase();
        this.params = params.toLowerCase();
    }

    @Override
    public void read(Json json, JsonValue jsonData) {
        assetName = jsonData.getString("name").toLowerCase();
        fileName = jsonData.getString("file");
        type = jsonData.getString("class").toLowerCase();
        params = jsonData.has("params") ? jsonData.getString("params").toLowerCase() : null;
    }

    @Override
    public void write(Json json) {
        json.writeValue("name", fileName);
        json.writeValue("file", fileName);
        json.writeValue("class", type);
        if (params != null)
            json.writeValue("params", params);
    }
}
