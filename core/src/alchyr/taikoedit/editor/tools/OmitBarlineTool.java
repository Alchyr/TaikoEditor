package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.editor.changes.OmitBarlineChange;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.editor.views.EffectView;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OmitBarlineTool extends EditorTool {
    private static final Color omitHoverColor = new Color(0.2f, 0.35f, 0.7f, 0.8f);
    private static final Color unomitHoverColor = new Color(0.8f, 0.8f, 0.8f, 0.8f);

    private static OmitBarlineTool tool;
    private EffectView previewView;
    private TimingPoint previewObject = null;

    private OmitBarlineTool() {
        super("Omit Barlines");
    }

    public static OmitBarlineTool get() {
        if (tool == null)
            tool = new OmitBarlineTool();
        return tool;
    }

    @Override
    public void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed) {
        float y = SettingsMaster.gameY();

        previewObject = null;
        previewView = null;

        if (y > viewsTop || y < viewsBottom)
            return;

        for (EditorBeatmap m : activeMaps)
        {
            ViewSet v = views.get(m);

            if (v == null) continue;

            if (!v.containsY(y)) continue;

            MapView hovered = v.getView(y);

            if (!(hovered instanceof EffectView)) return;

            MapObject obj = hovered.getObjectAt(Gdx.input.getX(), y);
            if (obj == null) return;

            ArrayList<TimingPoint> stack = hovered.map.timingPoints.get(obj.getPos());
            if (stack == null) return;

            for (TimingPoint point : stack) {
                if (point.uninherited) {
                    previewObject = point;
                    previewView = (EffectView) hovered;
                }
            }
            return;
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        if (previewObject != null)
        {
            Color c = previewObject.omitted ? unomitHoverColor : omitHoverColor;
            if (previewObject.selected) {
                MapObjectTreeMap<MapObject> selected = previewView.getSelection();

                //iterate and render on all uninherited points
                selected.forEachObject((point)->{
                    if (point instanceof TimingPoint && ((TimingPoint) point).uninherited) {
                        ((TimingPoint) point).renderSelection(sb, sr, previewView.preciseTime, EditorLayer.viewScale, SettingsMaster.getMiddleX(), previewView.bottom, c);
                    }
                });
            }
            else {
                previewObject.renderSelection(sb, sr, previewView.preciseTime, EditorLayer.viewScale, SettingsMaster.getMiddleX(), previewView.bottom, c);
            }
        }
    }

    @Override
    public MouseHoldObject click(MapView view, float x, float y, int button, int modifiers) {
        if (button == Input.Buttons.LEFT && previewObject != null && previewView.equals(view))
        {
            boolean toOmitted = !previewObject.omitted;
            List<TimingPoint> changed = new ArrayList<>();

            if (previewObject.selected) {
                MapObjectTreeMap<MapObject> selected = previewView.getSelection();

                //iterate and render on all uninherited points
                selected.forEachObject((point)->{
                    if (point instanceof TimingPoint && ((TimingPoint) point).uninherited) {
                        changed.add((TimingPoint) point);
                    }
                });
            }
            else {
                changed.add(previewObject);
            }

            previewView.map.registerChange(new OmitBarlineChange(previewView.map, changed, toOmitted).preDo());
            return MouseHoldObject.nothing;
        }

        return null;
    }

    @Override
    public boolean instantUse(MapView view) {
        if (view.hasSelection()) { //Type is already checked by supportsView
            boolean toOmitted = false;
            List<TimingPoint> swapping = new ArrayList<>();

            for (Map.Entry<Long, ArrayList<MapObject>> stack : view.getSelection().entrySet()) {
                for (MapObject p : stack.getValue()) {
                    if (!((TimingPoint) p).uninherited) continue; //skip green lines

                    if (!((TimingPoint) p).omitted)
                        toOmitted = true;
                    swapping.add((TimingPoint) p);
                }
            }

            if (!swapping.isEmpty()) {
                view.map.registerChange(new OmitBarlineChange(view.map, swapping, toOmitted).preDo());
            }
        }
        else {
            MapObject obj = view.getObjectAt(SettingsMaster.getMiddleX(), SettingsMaster.gameY());

            if (obj instanceof TimingPoint && ((TimingPoint) obj).uninherited) {
                List<TimingPoint> changed = new ArrayList<>();
                changed.add((TimingPoint) obj);
                view.map.registerChange(new OmitBarlineChange(view.map, changed, !((TimingPoint) obj).omitted).preDo());
            }
        }
        return true;
    }

    @Override
    public void cancel() {
        previewView = null;
        previewObject = null;
    }

    @Override
    public boolean supportsView(MapView view) {
        return view.type == MapView.ViewType.EFFECT_VIEW;
    }
}
