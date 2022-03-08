/*package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.input.KeyHoldManager;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.util.structures.PositionalObject;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.HashMap;
import java.util.List;

//Just an outline of what a placement tool looks like
public abstract class PlacementTool extends EditorTool {
    private static final int MAX_SNAP_OFFSET = 30;
    protected static final Color previewColor = new Color(1.0f, 1.0f, 1.0f, 0.6f);
    private static PlacementTool tool;

    private PositionalObject placementObject;

    private PlacementTool()
    {
        super("Placement");
    }

    public static PlacementTool get()
    {
        if (tool == null)
            tool = new PlacementTool();

        return tool;
    }

    @Override
    public void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed) {
        int y = SettingsMaster.getHeight() - Gdx.input.getY();

        if (y > viewsTop || y < viewsBottom)
            return;

        for (EditorBeatmap m : activeMaps)
        {
            ViewSet v = views.get(m);

            if (v.containsY(y))
            {
                MapView hovered = v.getView(y);
                Snap closest = hovered.getClosestSnap(hovered.getTimeFromPosition(Gdx.input.getX(), SettingsMaster.getMiddle()), MAX_SNAP_OFFSET);

                if (closest != null)
                {
                    placementObject.setPos(closest.pos);
                }
                return;
            }
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {

    }

    @Override
    public void cancel() {

    }

    @Override
    public MouseHoldObject click(MapView view, int x, int y, int button, KeyHoldManager keyHolds) {
        //Place an object at current previewed placement position
        //For sliders/spinners, will need to track current start position using update, and next click will finish placement or cancel (if it's a right click)
        return null;
    }
}*/
