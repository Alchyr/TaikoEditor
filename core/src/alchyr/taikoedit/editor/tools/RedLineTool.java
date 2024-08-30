package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RedLineTool extends EditorTool {
    private static final int MAX_SNAP_OFFSET = 40;
    protected static final Color previewColor = new Color(1.0f, 1.0f, 1.0f, 0.6f);

    private static RedLineTool tool;

    private boolean renderPreview;
    private MapView previewView;
    private final TimingPoint placementObject;

    private RedLineTool()
    {
        super("Red Line");

        placementObject = new TimingPoint(0);
        placementObject.uninherited = true;
        placementObject.setValue(300);
    }

    public static RedLineTool get()
    {
        if (tool == null)
        {
            tool = new RedLineTool();
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
                    double time = hovered.getTimeFromPosition(Gdx.input.getX());
                    Snap closest = hovered.getClosestSnap(time, MAX_SNAP_OFFSET);

                    previewView = hovered;
                    renderPreview = true;
                    if (closest == null || BindingGroup.alt()) { //Just go to cursor position
                        placementObject.setPos(Math.round(time));
                    }
                    else { //Snap to closest snap
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
        if (button == Input.Buttons.LEFT && renderPreview && previewView.equals(view))
        {
            renderPreview = false;

            //Generate an uninherited copy of the closest previous red line.
            Map.Entry<Long, ArrayList<TimingPoint>> lastTiming = view.map.timingPoints.floorEntry(placementObject.getPos());

            if (lastTiming == null)
            {
                //No previous point, just use default values
                TimingPoint p = ((TimingPoint) placementObject.shiftedCopy(placementObject.getPos())).uninherit();
                p.kiai = BindingGroup.shift();

                view.map.registerAndPerformAddObject("Add Red Line", p, view.replaceTest);
                return null;
            }

            TimingPoint p = ((TimingPoint) lastTiming.getValue().get(lastTiming.getValue().size() - 1).shiftedCopy(placementObject.getPos()));
            if (BindingGroup.shift()) {
                p.kiai = !p.kiai;
            }

            view.map.registerAndPerformAddObject("Add Red Line", p, view.replaceTest);
            return null;
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

        //Generate an inherited copy of the closest previous timing point
        Map.Entry<Long, ArrayList<TimingPoint>> lastTiming = view.map.timingPoints.floorEntry(time);

        if (lastTiming == null)
        {
            //No previous point, just use default values
            TimingPoint p = ((TimingPoint) placementObject.shiftedCopy(time)).uninherit();
            p.kiai = BindingGroup.shift();

            view.map.registerAndPerformAddObject("Add Red Line", p, view.replaceTest);
            return true;
        }

        TimingPoint p = ((TimingPoint) lastTiming.getValue().get(lastTiming.getValue().size() - 1).shiftedCopy(time));
        if (BindingGroup.shift()) {
            p.kiai = !p.kiai;
        }

        view.map.registerAndPerformAddObject("Add Red Line", p, view.replaceTest);
        return true;
    }

    @Override
    public void cancel() {

    }

    @Override
    public boolean supportsView(MapView view) {
        return view.type == MapView.ViewType.EFFECT_VIEW;
    }
}