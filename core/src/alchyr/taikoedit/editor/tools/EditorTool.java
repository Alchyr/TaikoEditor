package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.core.input.MouseHoldObject;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.HashMap;
import java.util.List;

public abstract class EditorTool {
    public final String name;

    public boolean consumesRightClick() {
        return false;
    }
    public boolean overrideViewClick() { return false; }

    public EditorTool(String name)
    {
        this.name = name;
    }

    public abstract void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed);
    public abstract void render(SpriteBatch sb, ShapeRenderer sr);
    public abstract void cancel();

    public void onSelected(EditorLayer source)
    {

    }

    public abstract boolean supportsView(MapView view);

    public MouseHoldObject click(MapView view, float x, float y, int button, int modifiers)
    {
        return null;
    }
    public boolean instantUse(MapView view) {
        //Return value: Whether or not to swap to the tool
        return true;
    }
}
