package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.hitobjects.Slider;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.HashMap;
import java.util.List;

import static alchyr.taikoedit.core.layers.EditorLayer.finisherLock;

public class FakeSliderTool extends EditorTool {
    private static final int MAX_SNAP_OFFSET = 40;
    protected static final Color previewColor = new Color(1.0f, 1.0f, 1.0f, 0.6f);
    private static FakeSliderTool tool;

    private boolean renderPreview;
    private MapView previewView;
    private HitObject placementObject;

    private FakeSliderTool()
    {
        super("Fake Slider");
    }

    public static FakeSliderTool get()
    {
        if (tool == null)
        {
            tool = new FakeSliderTool();
            tool.placementObject = new Slider(0, -.01);
        }

        return tool;
    }

    @Override
    public boolean overrideViewClick() {
        return true;
    }

    @Override
    public void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed) {
        float y = SettingsMaster.gameY();

        renderPreview = false;
        placementObject.setIsFinish(finisherLock ^ BindingGroup.shift());

        if (y > viewsTop || y < viewsBottom)
            return;

        for (EditorBeatmap m : activeMaps)
        {
            ViewSet v = views.get(m);

            if (v == null) continue;

            if (v.containsY(y))
            {
                MapView hovered = v.getView(y);
                if (this.supportsView(hovered))
                {
                    Snap closest = hovered.getClosestSnap(hovered.getTimeFromPosition(Gdx.input.getX()), MAX_SNAP_OFFSET);

                    if (closest != null)
                    {
                        previewView = hovered;
                        renderPreview = true;
                        placementObject.setPos(closest.pos);
                    }
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
    public MouseHoldObject click(MapView view, float x, float y, int button, int modifiers) {
        //Place an object at current previewed placement position
        //For sliders/spinners, will need to track current start position using update, and next click will finish placement or cancel (if it's a right click)
        if (button == Input.Buttons.LEFT && renderPreview && previewView.equals(view))
        {
            view.map.registerAndPerformAddObject("Add Fake Slider", placementObject, view.replaceTest);

            placementObject = new Slider(0, -.01);
            renderPreview = false;
            return MouseHoldObject.nothing;
        }

        return null;
    }

    @Override
    public boolean instantUse(MapView view) {
        long time = Math.round(view.getTimeFromPosition(SettingsMaster.getMiddleX()));
        Snap closest = view.getClosestSnap(time, MAX_SNAP_OFFSET * 2);

        if (closest != null)
        {
            time = closest.pos;
        }

        Slider s = new Slider(time, -.01);
        if (finisherLock ^ Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
            s.setIsFinish(true);
        }

        view.map.registerAndPerformAddObject("Add Fake Slider", s, view.replaceTest);
        return true;
    }

    @Override
    public void cancel() {

    }

    @Override
    public boolean supportsView(MapView view) {
        return view.type == MapView.ViewType.GIMMICK_VIEW;
    }
}