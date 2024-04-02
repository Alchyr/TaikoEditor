package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.editor.changes.KiaiChange;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.editor.views.EffectView;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.GeneralUtils;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.util.structures.PositionalObject;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.*;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class KiaiTool extends EditorTool {
    private static final Color enableKiaiColor = new Color(0.7f, 0.7f, 0.7f, 0.35f); //when hovering non-kiai area, render gray
    private static final Color disableKiaiColor = new Color(240.0f/255.0f, 164.0f/255.0f, 66.0f/255.0f, 0.4f); //when hovering kiai area, render orange

    private final Texture pix = assetMaster.get("ui:pixel");

    private static KiaiTool tool;
    private EffectView previewView;
    private boolean renderPreview = false;
    private long previewPos = 0;

    private KiaiTool() {
        super("Kiai");
    }

    public static KiaiTool get() {
        if (tool == null)
            tool = new KiaiTool();
        return tool;
    }

    @Override
    public void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed) {
        float y = SettingsMaster.gameY();

        renderPreview = false;
        previewView = null;

        if (y > viewsTop || y < viewsBottom)
            return;

        for (EditorBeatmap m : activeMaps)
        {
            ViewSet v = views.get(m);

            if (v.containsY(y))
            {
                MapView hovered = v.getView(y);
                if (hovered instanceof EffectView)
                {
                    renderPreview = true;
                    previewPos = (long) hovered.getTimeFromPosition(Gdx.input.getX());
                    previewView = (EffectView) hovered;
                }
                return;
            }
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        if (renderPreview)
        {
            boolean kiai, first = true;
            Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> pointIterator = previewView.map.allPoints.descendingSubMap(Long.MIN_VALUE, true, previewPos, true).entrySet().iterator();
            TimingPoint next;
            long start = previewView.getPositionFromTime(previewPos), end = start;
            if (pointIterator.hasNext()) {
                next = GeneralUtils.listLast(pointIterator.next().getValue());
                kiai = next.kiai;

                while (start > 0 && next != null) {
                    if (!next.selected && !first)
                        break; //can't go into this section at all

                    first = false;
                    start = previewView.getPositionFromTime(next.getPos());

                    next = pointIterator.hasNext() ? GeneralUtils.listLast(pointIterator.next().getValue()) : null;
                }
            }
            else {
                return; //can't change lines if there's no lines to change before a point
            }

            pointIterator = previewView.map.allPoints.subMap(previewPos, false, Long.MAX_VALUE, true).entrySet().iterator();
            if (pointIterator.hasNext()) {
                next = GeneralUtils.listLast(pointIterator.next().getValue());
                while (end < SettingsMaster.getWidth() && next != null) {
                    end = previewView.getPositionFromTime(next.getPos());
                    if (!next.selected) {
                        break;
                    }
                    next = pointIterator.hasNext() ? GeneralUtils.listLast(pointIterator.next().getValue()) : null;
                }
            }
            else {
                end = SettingsMaster.getWidth();
            }

            if (end > start) {
                sb.setColor(kiai ? disableKiaiColor : enableKiaiColor);
                sb.draw(pix, start, previewView.bottom + (previewView.height / 2) - EffectView.SV_AREA, end - start, EffectView.SV_AREA * 2);
            }
        }
    }

    @Override
    public MouseHoldObject click(MapView view, float x, float y, int button, int modifiers) {
        //Place an object at current previewed placement position
        //For sliders/spinners, will need to track current start position using update, and next click will finish placement or cancel (if it's a right click)
        if (button == Input.Buttons.LEFT && renderPreview && previewView.equals(view))
        {
            boolean kiai, first = true;
            List<TimingPoint> swapping;
            TimingPoint main;

            Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> pointIterator = previewView.map.allPoints.descendingSubMap(Long.MIN_VALUE, true, previewPos, true).entrySet().iterator();
            Map.Entry<Long, ArrayList<TimingPoint>> next;

            if (pointIterator.hasNext()) {
                next = pointIterator.next();
                kiai = GeneralUtils.listLast(next.getValue()).kiai;
                swapping = new ArrayList<>();

                while (next != null) {
                    main = GeneralUtils.listLast(next.getValue());
                    if (!main.selected && !first)
                        break; //can't go into this section at all

                    first = false;
                    swapping.addAll(next.getValue());

                    next = pointIterator.hasNext() ? pointIterator.next() : null;
                }
            }
            else {
                return null;
            }

            pointIterator = previewView.map.allPoints.subMap(previewPos, false, Long.MAX_VALUE, true).entrySet().iterator();
            if (pointIterator.hasNext()) {
                next = pointIterator.next();
                while (next != null) {
                    main = GeneralUtils.listLast(next.getValue());
                    if (!main.selected) { //Nope
                        break;
                    }

                    swapping.addAll(next.getValue());
                    next = pointIterator.hasNext() ? pointIterator.next() : null;
                }
            }

            previewView.map.registerChange(new KiaiChange(previewView.map, swapping, !kiai).perform());
            return MouseHoldObject.nothing;
        }

        return null;
    }

    @Override
    public boolean instantUse(MapView view) {
        if (view.hasSelection()) { //Type is already checked by supportsView
            boolean toKiai = false;
            List<TimingPoint> swapping = new ArrayList<>();

            for (Map.Entry<Long, ArrayList<PositionalObject>> stack : view.getSelection().entrySet()) {
                for (PositionalObject p : stack.getValue()) {
                    if (!((TimingPoint) p).kiai)
                        toKiai = true;
                    swapping.add((TimingPoint) p);
                }
            }

            if (!swapping.isEmpty()) {
                view.map.registerChange(new KiaiChange(view.map, swapping, toKiai).perform());
            }
        }
        else {
            double time = view.getTimeFromPosition(SettingsMaster.getMiddleX());
            Map.Entry<Long, ArrayList<TimingPoint>> stack = view.map.allPoints.floorEntry((long) time);

            if (stack != null) {
                view.map.registerChange(new KiaiChange(view.map, new ArrayList<>(stack.getValue()), !GeneralUtils.listLast(stack.getValue()).kiai).perform());
            }
        }
        return true;
    }

    @Override
    public void cancel() {
        previewView = null;
        renderPreview = false;
    }

    @Override
    public boolean supportsView(MapView view) {
        return view.type == MapView.ViewType.EFFECT_VIEW;
    }
}
