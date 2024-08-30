package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.editor.views.EffectView;
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

import static alchyr.taikoedit.core.input.BindingGroup.shift;

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
                    renderPreview = hold == null;
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
        /*else if (hold != null) {
            hold.render(sb, sr);
        }*/
    }

    @Override
    public MouseHoldObject click(MapView view, float x, float y, int button, int modifiers) {
        if (button == Input.Buttons.LEFT && renderPreview && previewView.equals(view))
        {
            //Generate an inherited copy of the closest previous timing point
            Map.Entry<Long, ArrayList<TimingPoint>> lastEffect = view.map.effectPoints.floorEntry(placementObject.getPos());
            Map.Entry<Long, ArrayList<TimingPoint>> lastTiming = view.map.timingPoints.floorEntry(placementObject.getPos());

            if (lastTiming == null && lastEffect == null)
            {
                //No previous point, just use default values
                TimingPoint p = ((TimingPoint) placementObject.shiftedCopy(placementObject.getPos())).inherit();
                p.kiai = BindingGroup.shift();

                return hold = new SvAdjustHold(view, Gdx.input.getY(), p);
            }
            else if (lastEffect == null) {
                lastEffect = lastTiming;
            }
            else if (lastTiming != null) {
                if (lastTiming.getKey() > lastEffect.getKey())
                    lastEffect = lastTiming;
            }

            TimingPoint p = ((TimingPoint) lastEffect.getValue().get(lastEffect.getValue().size() - 1).shiftedCopy(placementObject.getPos())).inherit();
            if (BindingGroup.shift()) {
                p.kiai = !p.kiai;
            }

            renderPreview = false;

            return hold = new SvAdjustHold(view, Gdx.input.getY(), p);
        }

        return null;
    }

    @Override
    public boolean instantUse(MapView view) {
        if (hold != null)
            return false;

        long time = Math.round(view.getTimeFromPosition(SettingsMaster.getMiddleX()));
        Snap closest = view.getClosestSnap(time, MAX_SNAP_OFFSET * 2);

        if (closest != null)
        {
            time = closest.pos;
        }

        //Generate an inherited copy of the closest previous timing point
        Map.Entry<Long, ArrayList<TimingPoint>> lastEffect = view.map.effectPoints.floorEntry(time);
        Map.Entry<Long, ArrayList<TimingPoint>> lastTiming = view.map.timingPoints.floorEntry(time);

        if (lastTiming == null && lastEffect == null)
        {
            //No previous point, just use default values
            TimingPoint p = ((TimingPoint) placementObject.shiftedCopy(time)).inherit();
            p.kiai = BindingGroup.shift();

            view.map.registerAndPerformAddObject("Add Green Line", p, view.replaceTest);
            return true;
        }
        else if (lastEffect == null) {
            lastEffect = lastTiming;
        }
        else if (lastTiming != null) {
            if (lastTiming.getKey() > lastEffect.getKey())
                lastEffect = lastTiming;
        }


        TimingPoint p = ((TimingPoint) lastEffect.getValue().get(lastEffect.getValue().size() - 1).shiftedCopy(time)).inherit();
        if (BindingGroup.shift()) {
            p.kiai = !p.kiai;
        }

        view.map.registerAndPerformAddObject("Add Green Line", p, view.replaceTest);
        return true;
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
        private static final float MIN_DRAG_DIST = 10;

        private final MapView placingView;
        private final TimingPoint adjusting;
        private final double initialValue;
        private double totalVerticalOffset;
        private int lastY;

        private boolean dragging = false;

        public SvAdjustHold(MapView view, int placementY, TimingPoint point)
        {
            super(null, null);

            placingView = view;
            adjusting = point;
            initialValue = adjusting.getValue();
            lastY = placementY;

            totalVerticalOffset = 0;

            onRelease = (x, y)->{
                //register object placement
                this.placingView.map.registerAndPerformAddObject("Add Green Line", adjusting, placingView.replaceTest);

                hold = null;
            };
        }

        private void cancel() {
            //placingView.map.effectPoints.removeObject(adjusting);
            //placingView.map.allPoints.removeObject(adjusting);
        }

        @Override
        public void update(float elapsed) {
            if (!(placingView instanceof EffectView)) return;

            placingView.displayAdditionalObject(adjusting);
            ((EffectView) placingView).setFocus(adjusting);

            double verticalChange = (Gdx.input.getY() - lastY);
            //Find closest snap to this new offset

            if (!dragging && (verticalChange > MIN_DRAG_DIST || verticalChange < -MIN_DRAG_DIST)) {
                dragging = true;
            }

            verticalChange *= (shift() ? 0.01f : 0.05f);

            if (dragging) {
                adjusting.setValue(Math.max(TimingPoint.MIN_SV, initialValue - (totalVerticalOffset / 20.0)));

                totalVerticalOffset += verticalChange; //Track separately every time so holding shift can adjust just new input
                lastY = Gdx.input.getY();
                ((EffectView) placingView).recheckSvLimits();
                //Do not check for removed points here for simpler check.
                //Proper update will occur on release.
                //placingView.map.updateLines(Collections.singleton(new Pair<>(adjusting.getPos(), Collections.singletonList(adjusting))), null, false);
            }
        }

        /*public void render(SpriteBatch sb, ShapeRenderer sr) {
            placingView.renderObject(adjusting, sb, sr);
            if (placingView instanceof EffectView) {
                int stackX = (int) (SettingsMaster.getMiddleX() + (adjusting.getPos() - placingView.preciseTime) * viewScale + 4);
                ((EffectView) placingView).renderLabel(sb, EffectView.twoDecimal.format(adjusting.value), stackX, placingView.bottom + EffectView.TOP_VALUE_Y);
            }
        }*/
    }
}