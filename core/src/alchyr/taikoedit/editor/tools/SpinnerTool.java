package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.hitobjects.Spinner;
import alchyr.taikoedit.core.input.MouseHoldObject;
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
    public boolean overrideViewClick() {
        return true;
    }

    @Override
    public void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed) {
        if (isPlacing)
        {
            double time = currentlyPlacing.getTimeFromPosition(Gdx.input.getX());
            Snap closest = currentlyPlacing.getClosestSnap(time, MAX_PLACEMENT_OFFSET);

            if (closest != null && closest.pos > placementObject.getPos())
            {
                placementObject.setDuration(closest.pos - placementObject.getPos());
            }
            else if (time > placementObject.getPos())
            {
                placementObject.setDuration((long) time - placementObject.getPos());
            }
        }
        else
        {
            float y = SettingsMaster.gameY();

            renderPreview = false;
            placementObject.setDuration(0);
            //placementObject.finish = finisherLock ^ Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);

            if (y > viewsTop || y < viewsBottom)
                return;

            for (EditorBeatmap m : activeMaps)
            {
                ViewSet v = views.get(m);

                if (v == null) continue;

                if (v.containsY(y))
                {
                    MapView hovered = v.getView(y);
                    if (this.supportsView(hovered)) {
                        Snap closest = hovered.getClosestSnap(hovered.getTimeFromPosition(Gdx.input.getX()), MAX_SNAP_OFFSET);

                        if (closest != null) {
                            previewView = hovered;
                            renderPreview = true;
                            placementObject.setPos(closest.pos);
                        }
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
    public boolean consumesRightClick() {
        return isPlacing;
    }

    @Override
    public MouseHoldObject click(MapView view, float x, float y, int button, int modifiers) {
        //Place an object at current previewed placement position
        //For sliders/spinners, will need to track current start position using update, and next click will finish placement or cancel (if it's a right click)
        if (isPlacing)
        {
            if (button == Input.Buttons.LEFT)
            {
                currentlyPlacing.map.registerAndPerformAddObject("Add Spinner", placementObject, currentlyPlacing.replaceTest);

                placementObject = new Spinner(0, 0);
                renderPreview = false;
                isPlacing = false;
                currentlyPlacing = null;
            }
            else
            {
                cancel();
            }
            return MouseHoldObject.nothing;
        }
        else if (button == Input.Buttons.LEFT && renderPreview && previewView.equals(view))
        {
            currentlyPlacing = view;
            isPlacing = true;
            placementObject.setDuration(BASE_PLACEMENT_DURATION);
            return MouseHoldObject.nothing;
        }

        return null;
    }

    @Override
    public boolean supportsView(MapView view) {
        return view.type == MapView.ViewType.OBJECT_VIEW;
    }
}