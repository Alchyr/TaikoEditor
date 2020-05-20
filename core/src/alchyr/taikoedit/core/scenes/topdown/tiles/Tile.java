package alchyr.taikoedit.core.scenes.topdown.tiles;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class Tile {
    public static final int SIZE = 32;

    private TextureAtlas.AtlasRegion region;
    private boolean isWalkable;

    public boolean render;

    //t = new Tile("tiles:stone1", true);

    public Tile(TextureAtlas.AtlasRegion img, boolean isWalkable)
    {
        this.region = img;
        this.isWalkable = isWalkable;
        this.render = true;
    }
    public Tile(String img, boolean isWalkable)
    {
        this(assetMaster.getRegion(img), isWalkable);
    }

    public boolean walkable()
    {
        return isWalkable;
    }

    public void render(SpriteBatch sb, float x, float y)
    {
        if (render)
        {
            sb.draw(region, x, y);
        }
    }

    public Tile getTile() //most tiles can simply use a single instance. If tiles have more complex logic, then this method can be overriden to create a new instance.
    {
        return this;
    }
}
