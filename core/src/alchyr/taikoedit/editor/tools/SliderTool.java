package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.hitobjects.Slider;
import alchyr.taikoedit.core.input.MouseHoldObject;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.HashMap;
import java.util.List;

import static alchyr.taikoedit.core.layers.EditorLayer.finisherLock;

public class SliderTool extends EditorTool {
    private static final int MAX_SNAP_OFFSET = 40;
    private static final int MAX_PLACEMENT_OFFSET = 1000;
    private static final int BASE_PLACEMENT_DURATION = 100;
    protected static final Color previewColor = new Color(1.0f, 1.0f, 1.0f, 0.6f);
    private static SliderTool tool;

    private MapView currentlyPlacing;
    private boolean isPlacing;
    private boolean renderPreview;
    private MapView previewView;
    private Slider placementObject;

    private SliderTool()
    {
        super("Slider");
        isPlacing = false;
    }

    public static SliderTool get()
    {
        if (tool == null)
        {
            tool = new SliderTool();
            tool.placementObject = new Slider(0, 0);
        }

        return tool;
    }

    @Override
    public void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed) {
        if (isPlacing)
        {
            double time = currentlyPlacing.getTimeFromPosition(Gdx.input.getX());
            Snap closest = currentlyPlacing.getClosestSnap(time, MAX_PLACEMENT_OFFSET);

            if (closest != null && closest.pos > placementObject.getPos())
            {
                placementObject.setDuration((int) closest.pos - placementObject.getPos());
            }
            else if (time > placementObject.getPos())
            {
                placementObject.setDuration((int) time - placementObject.getPos());
            }
        }
        else
        {
            float y = SettingsMaster.gameY();

            renderPreview = false;
            placementObject.setDuration(0);
            placementObject.setIsFinish(finisherLock ^ Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT));

            if (y > viewsTop || y < viewsBottom)
                return;

            for (EditorBeatmap m : activeMaps)
            {
                ViewSet v = views.get(m);

                if (v.containsY(y))
                {
                    MapView hovered = v.getView(y);
                    if (this.supportsView(hovered)) {
                        Snap closest = hovered.getClosestSnap(hovered.getTimeFromPosition(Gdx.input.getX()), MAX_SNAP_OFFSET);

                        if (closest != null) {
                            previewView = hovered;
                            renderPreview = true;
                            placementObject.setPos((int) closest.pos);
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
                currentlyPlacing.map.addObject(placementObject);

                placementObject = new Slider(0, 0);
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