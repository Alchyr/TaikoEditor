package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.LineAddition;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.input.MouseHoldObject;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static alchyr.taikoedit.management.bindings.BindingGroup.shift;

public class GreenLineTool extends EditorTool {
    private static final int MAX_SNAP_OFFSET = 40;
    protected static final Color previewColor = new Color(1.0f, 1.0f, 1.0f, 0.6f);

    private static GreenLineTool tool;

    private boolean renderPreview;
    private MapView previewView;
    private final TimingPoint placementObject;

    private GreenLineTool()
    {
        super("Green Line");

        placementObject = new TimingPoint(0);
    }

    public static GreenLineTool get()
    {
        if (tool == null)
        {
            tool = new GreenLineTool();
        }

        return tool;
    }

    @Override
    public void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed) {
        int y = SettingsMaster.getHeight() - Gdx.input.getY();

        renderPreview = false;

        if (y > viewsTop || y < viewsBottom)
            return;

        for (EditorBeatmap m : activeMaps)
        {
            ViewSet v = views.get(m);

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
                        placementObject.setPosition((long) closest.pos);
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
    public MouseHoldObject click(MapView view, int x, int y, int button, int modifiers) {
        if (button == Input.Buttons.LEFT && renderPreview && previewView.equals(view))
        {
            //Generate an inherited copy of the closest previous timing point
            Map.Entry<Long, ArrayList<TimingPoint>> lastEffect = view.map.effectPoints.floorEntry(placementObject.pos);
            Map.Entry<Long, ArrayList<TimingPoint>> lastTiming = view.map.timingPoints.floorEntry(placementObject.pos);

            if (lastTiming == null && lastEffect == null)
            {
                //No previous point, just use default values
                TimingPoint p = ((TimingPoint) placementObject.shiftedCopy(placementObject.pos)).inherit();

                view.map.effectPoints.add(p);

                return hold = new SvAdjustHold(view, Gdx.input.getY(), p);
            }
            else if (lastEffect == null) {
                lastEffect = lastTiming;
            }

            TimingPoint p = ((TimingPoint) lastEffect.getValue().get(lastEffect.getValue().size() - 1).shiftedCopy(placementObject.pos)).inherit();

            renderPreview = false;

            view.map.effectPoints.add(p);

            return hold = new SvAdjustHold(view, Gdx.input.getY(), p);
        }

        return null;
    }

    @Override
    public void instantUse(MapView view) {
        double time = view.getTimeFromPosition(SettingsMaster.getMiddle());
        Snap closest = view.getClosestSnap(time, MAX_SNAP_OFFSET * 2);

        if (closest != null)
        {
            time = closest.pos;
        }

        //Generate an inherited copy of the closest previous timing point
        Map.Entry<Long, ArrayList<TimingPoint>> lastEffect = view.map.effectPoints.floorEntry((long) time);
        Map.Entry<Long, ArrayList<TimingPoint>> lastTiming = view.map.timingPoints.floorEntry((long) time);

        if (lastTiming == null && lastEffect == null)
        {
            //No previous point, just use default values
            TimingPoint p = ((TimingPoint) placementObject.shiftedCopy((long) time)).inherit();

            view.map.registerChange(new LineAddition(view.map, p).perform());
            return;
        }
        else if (lastEffect == null) {
            lastEffect = lastTiming;
        }

        TimingPoint p = ((TimingPoint) lastEffect.getValue().get(lastEffect.getValue().size() - 1).shiftedCopy((long) time)).inherit();

        view.map.registerChange(new LineAddition(view.map, p).perform());
    }

    @Override
    public void cancel() {
        if (hold != null) {
            hold.cancel();
            EditorLayer.processor.cancelMouseHold(hold);
            hold = null;
        }
    }

    @Override
    public boolean supportsView(MapView view) {
        return view.type == MapView.ViewType.EFFECT_VIEW;
    }

    private SvAdjustHold hold = null;

    private class SvAdjustHold extends MouseHoldObject
    {
        private static final float MIN_DRAG_DIST = 5;

        private MapView placingView;
        private TimingPoint adjusting;
        private double totalVerticalOffset;
        private int lastY;

        private boolean dragging = false;

        public SvAdjustHold(MapView view, int placementY, TimingPoint point)
        {
            super(null, null);

            placingView = view;
            adjusting = point;
            lastY = placementY;

            totalVerticalOffset = 0;
        }

        @Override
        public boolean onRelease(int x, int y) {
            //register object placement
            adjusting.registerChange();

            placingView.map.registerChange(new LineAddition(placingView.map, adjusting));

            hold = null;

            return true;
        }

        private void cancel() {
            placingView.map.effectPoints.removeObject(adjusting);
        }

        @Override
        public void update(float elapsed) {
            double verticalChange = (Gdx.input.getY() - lastY);
            //Find closest snap to this new offset

            if (!dragging && (verticalChange > MIN_DRAG_DIST || verticalChange < -MIN_DRAG_DIST)) {
                dragging = true;
            }

            verticalChange *= (shift() ? 0.05f : 0.5f);

            if (dragging) {
                adjusting.tempModification(totalVerticalOffset);

                totalVerticalOffset += verticalChange; //Track separately every time so holding shift can adjust just new input
                lastY = Gdx.input.getY();
            }
        }
    }
}