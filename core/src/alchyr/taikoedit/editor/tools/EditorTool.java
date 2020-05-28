package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.util.input.KeyHoldManager;
import alchyr.taikoedit.util.input.MouseHoldObject;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.HashMap;
import java.util.List;

public abstract class EditorTool {
    public final String name;

    public EditorTool(String name)
    {
        this.name = name;
    }

    public abstract void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed);
    public abstract void render(SpriteBatch sb, ShapeRenderer sr);
    public abstract void cancel();

    public MouseHoldObject click(MapView view, int x, int y, int button, KeyHoldManager keyHolds)
    {
        return null;
    }
}
