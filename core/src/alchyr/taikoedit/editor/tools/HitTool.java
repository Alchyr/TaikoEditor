package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.maps.components.HitObject;
import alchyr.taikoedit.maps.components.hitobjects.Hit;
import alchyr.taikoedit.util.input.KeyHoldManager;
import alchyr.taikoedit.util.input.MouseHoldObject;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.HashMap;
import java.util.List;

import static alchyr.taikoedit.core.layers.EditorLayer.finisherLock;

public class HitTool extends EditorTool {
    private static final int MAX_SNAP_OFFSET = 40;
    protected static final Color previewColor = new Color(1.0f, 1.0f, 1.0f, 0.6f);
    private static HitTool don, kat;

    private final boolean isRim;

    private boolean renderPreview;
    private MapView previewView;
    private HitObject placementObject;

    private HitTool(boolean isRim)
    {
        super(isRim ? "Kat" : "Don");
        this.isRim = isRim;
    }

    public static HitTool don()
    {
        if (don == null)
        {
            don = new HitTool(false);
            don.placementObject = new Hit(0, don.isRim);
        }

        return don;
    }
    public static HitTool kat()
    {
        if (kat == null)
        {
            kat = new HitTool(true);
            kat.placementObject = new Hit(0, kat.isRim);
        }

        return kat;
    }

    @Override
    public void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed) {
        int y = SettingsMaster.getHeight() - Gdx.input.getY();

        renderPreview = false;
        placementObject.finish = finisherLock ^ Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);

        if (y > viewsTop || y < viewsBottom)
            return;

        for (EditorBeatmap m : activeMaps)
        {
            ViewSet v = views.get(m);

            if (v.containsY(y))
            {
                MapView hovered = v.getView(y);
                Snap closest = hovered.getClosestSnap(hovered.getTimeFromPosition(Gdx.input.getX()), MAX_SNAP_OFFSET);

                if (closest != null)
                {
                    previewView = hovered;
                    renderPreview = true;
                    placementObject.setPosition(closest.pos);
                }
                return;
            }
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        if (renderPreview)
        {
            previewView.renderObject(placementObject, sb, sr, previewColor.a);
        }
    }

    @Override
    public MouseHoldObject click(MapView view, int x, int y, int button, KeyHoldManager keyHolds) {
        //Place an object at current previewed placement position
        //For sliders/spinners, will need to track current start position using update, and next click will finish placement or cancel (if it's a right click)
        if (button == Input.Buttons.LEFT && renderPreview && previewView.equals(view))
        {
            view.map.addObject(placementObject);

            placementObject = new Hit(0, isRim);
            renderPreview = false;
        }

        return null;
    }

    @Override
    public void cancel() {

    }
}