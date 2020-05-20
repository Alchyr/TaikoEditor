package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.maps.components.HitObject;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.NavigableMap;

public abstract class MapView {
    EditorLayer parent;
    public EditorBeatmap map;
    public int type; //Views of the same time should use the same set of objects in the same order

    protected int y;
    protected int height;

    public MapView(int viewType, EditorLayer parent, EditorBeatmap beatmap, int height)
    {
        this.type = viewType;
        this.parent = parent;
        this.map = beatmap;

        this.y = 0;
        this.height = height;
    }

    public int setPos(int y)
    {
        this.y = y;

        return y - height;
    }

    public abstract void update(int pos);
    public abstract void renderBase(SpriteBatch sb, ShapeRenderer sr);
    public abstract void renderObject(HitObject o, SpriteBatch sb, ShapeRenderer sr);

    //Ensure map is ready for rendering. Exact details will depend on the view.
    public abstract NavigableMap<Integer, ArrayList<HitObject>> prep(EditorBeatmap map);
}
