package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.maps.components.hitobjects.Spinner;
import alchyr.taikoedit.util.input.KeyHoldManager;
import alchyr.taikoedit.util.input.MouseHoldObject;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.HashMap;
import java.util.List;

public class SpinnerTool extends EditorTool {
    private static final int MAX_SNAP_OFFSET = 40;
    private static final int MAX_PLACEMENT_OFFSET = 1000;
    private static final int BASE_PLACEMENT_DURATION = 100;
    protected static final Color previewColor = new Color(1.0f, 1.0f, 1.0f, 0.6f);
    private static SpinnerTool tool;

    private MapView currentlyPlacing;
    private boolean isPlacing;
    private boolean renderPreview;
    private MapView previewView;
    private Spinner placementObject;

    private SpinnerTool()
    {
        super("Spinner");
        isPlacing = false;
    }

    public static SpinnerTool get()
    {
        if (tool == null)
        {
            tool = new SpinnerTool();
            tool.placementObject = new Spinner(0, 0);
        }

        return tool;
    }

    @Override
    public void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed) {
        if (isPlacing)
        {
            int time = currentlyPlacing.getTimeFromPosition(Gdx.input.getX());
            Snap closest = currentlyPlacing.getClosestSnap(time, MAX_PLACEMENT_OFFSET);

            if (closest != null && closest.pos > placementObject.pos)
            {
                placementObject.setDuration(closest.pos - placementObject.pos);
            }
            else if (time > placementObject.pos)
            {
                placementObject.setDuration(time - placementObject.pos);
            }
        }
        else
        {
            int y = SettingsMaster.getHeight() - Gdx.input.getY();

            renderPreview = false;
            placementObject.setDuration(0);
            //placementObject.finish = finisherLock ^ Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);

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
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        if (isPlacing)
        {
            currentlyPlacing.renderObject(placementObject, sb, sr);
        }
        if (renderPreview)
        {
            previewView.renderObject(placementObject, sb, sr, previewColor.a);
        }
    }

    @Override
    public void cancel() {
        currentlyPlacing = null;
        isPlacing = false;
        placementObject.setDuration(0);
    }

    @Override
    public MouseHoldObject click(MapView view, int x, int y, int button, KeyHoldManager keyHolds) {
        //Place an object at current previewed placement position
        //For sliders/spinners, will need to track current start position using update, and next click will finish placement or cancel (if it's a right click)
        if (isPlacing)
        {
            if (button == Input.Buttons.LEFT)
            {
                currentlyPlacing.map.addObject(placementObject);

                placementObject = new Spinner(0, 0);
                renderPreview = false;
                isPlacing = false;
                currentlyPlacing = null;
            }
            else
            {
                cancel();
            }
        }
        else if (button == Input.Buttons.LEFT && renderPreview && previewView.equals(view))
        {
            currentlyPlacing = view;
            isPlacing = true;
            placementObject.setDuration(BASE_PLACEMENT_DURATION);
        }

        return null;
    }
}