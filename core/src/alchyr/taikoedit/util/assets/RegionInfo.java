package alchyr.taikoedit.util.assets;

//For atlasregions
public class RegionInfo {
    public String texture;
    public int x, y, width, height;

    public RegionInfo(String texture, String[] params)
    {
        this.texture = texture;
        x = Integer.parseInt(params[0]);
        y = Integer.parseInt(params[1]);
        width = Integer.parseInt(params[2]);
        height = Integer.parseInt(params[3]);
    }
}
