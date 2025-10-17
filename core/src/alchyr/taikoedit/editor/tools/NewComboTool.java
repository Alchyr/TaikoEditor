package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.editor.changes.NewComboChange;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
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

public class NewComboTool extends EditorTool {
    private static final Color ncHoverColor = new Color(0.1f, 1.0f, 0.6f, 1.0f);
    private static final Color unncHoverColor = new Color(0.88f, 0.05f, 0.6f, 0.8f);

    private static NewComboTool tool;
    private MapView previewView;
    private HitObject previewObject = null;

    private NewComboTool() {
        super("New Combo");
    }

    public static NewComboTool get() {
        if (tool == null)
            tool = new NewComboTool();
        return tool;
    }

    @Override
    public void activate() {
        HitObject.showNc = true;
    }

    @Override
    public void onSelected(EditorLayer source) {
        HitObject.showNc = true;
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

            if (hovered.type != MapView.ViewType.OBJECT_VIEW) return;

            MapObject obj = hovered.getObjectAt(Gdx.input.getX(), y);
            if (obj == null) return;

            ArrayList<HitObject> stack = hovered.map.objects.get(obj.getPos());
            if (stack == null) return;

            for (HitObject hitObj : stack) {
                if (hitObj.type != HitObject.HitObjectType.SPINNER) {
                    previewObject = hitObj;
                    previewView = hovered;
                }
            }
            return;
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        if (previewObject != null)
        {
            Color c = previewObject.newCombo ? unncHoverColor : ncHoverColor;

            previewObject.renderSelectionColored(sb, sr, previewView.preciseTime, EditorLayer.viewScale, SettingsMaster.getMiddleX(), previewView.bottom + (float) previewView.height / 2, c);
        }
    }

    @Override
    public MouseHoldObject click(MapView view, float x, float y, int button, int modifiers) {
        if (button == Input.Buttons.LEFT && previewObject != null && previewView.equals(view))
        {
            boolean toNc = !previewObject.newCombo;
            List<HitObject> changed = new ArrayList<>();

            if (previewObject.selected) {
                MapObjectTreeMap<MapObject> selected = previewView.getSelection();

                //iterate and render on all uninherited points
                selected.forEachObject((obj)->{
                    if (obj instanceof HitObject && ((HitObject) obj).type != HitObject.HitObjectType.SPINNER) {
                        changed.add((HitObject) obj);
                    }
                });
            }
            else {
                changed.add(previewObject);
            }

            previewView.map.registerChange(new NewComboChange(previewView.map, changed, toNc).preDo());
            return MouseHoldObject.nothing;
        }

        return null;
    }

    @Override
    public boolean instantUse(MapView view) {
        if (view.hasSelection()) { //Type is already checked by supportsView
            boolean toNc = false;
            List<HitObject> swapping = new ArrayList<>();

            for (Map.Entry<Long, ArrayList<MapObject>> stack : view.getSelection().entrySet()) {
                for (MapObject p : stack.getValue()) {
                    if (((HitObject) p).type == HitObject.HitObjectType.SPINNER) continue;

                    if (!((HitObject) p).newCombo)
                        toNc = true;
                    swapping.add((HitObject) p);
                }
            }

            if (!swapping.isEmpty()) {
                view.map.registerChange(new NewComboChange(view.map, swapping, toNc).preDo());
            }
        }
        else {
            MapObject obj = view.getObjectAt(SettingsMaster.getMiddleX(), SettingsMaster.gameY());

            if (obj instanceof HitObject && ((HitObject) obj).type != HitObject.HitObjectType.SPINNER) {
                List<HitObject> changed = new ArrayList<>();
                changed.add((HitObject) obj);
                view.map.registerChange(new NewComboChange(view.map, changed, !((HitObject) obj).newCombo).preDo());
            }
        }
        return true;
    }

    @Override
    public void cancel() {
        previewView = null;
        previewObject = null;
        HitObject.showNc = false;
    }

    @Override
    public boolean supportsView(MapView view) {
        return view.type == MapView.ViewType.OBJECT_VIEW;
    }
}
