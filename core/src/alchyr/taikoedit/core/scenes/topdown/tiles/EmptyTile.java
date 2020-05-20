package alchyr.taikoedit.core.scenes.topdown.tiles;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;

public class EmptyTile extends Tile {
    public EmptyTile()
    {
        super((TextureAtlas.AtlasRegion)null, false);
        render = false;
    }
}
