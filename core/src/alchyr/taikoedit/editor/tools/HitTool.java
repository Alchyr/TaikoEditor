package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.RimChange;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.hitobjects.Hit;
import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.util.structures.PositionalObject;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static alchyr.taikoedit.core.layers.EditorLayer.finisherLock;

public class HitTool extends EditorTool {
    private static final int MAX_SNAP_OFFSET = 40;
    protected static final Color previewColor = new Color(1.0f, 1.0f, 1.0f, 0.6f);
    private static HitTool don, kat;

    private final boolean isRim;

    private boolean renderPreview;
    private MapView previewView;
    private HitObject placementObject;

    private HitTool(boolean isRim)
    {
        super(isRim ? "Kat" : "Don");
        this.isRim = isRim;
    }

    public static HitTool don()
    {
        if (don == null)
        {
            don = new HitTool(false);
            don.placementObject = new Hit(0, don.isRim);
        }

        return don;
    }
    public static HitTool kat()
    {
        if (kat == null)
        {
            kat = new HitTool(true);
            kat.placementObject = new Hit(0, kat.isRim);
        }

        return kat;
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

            if (v.containsY(y))
            {
                MapView hovered = v.getView(y);
                if (this.supportsView(hovered))
                {
                    if (BindingGroup.alt()) {
                        previewView = hovered;
                        renderPreview = true;
                        placementObject.setPos(hovered.getTimeFromPosition(Gdx.input.getX()));
                    }
                    else {
                        Snap closest = hovered.getClosestSnap(hovered.getTimeFromPosition(Gdx.input.getX()), MAX_SNAP_OFFSET);

                        if (closest != null)
                        {
                            previewView = hovered;
                            renderPreview = true;
                            placementObject.setPos(closest.pos);
                        }
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
            view.map.addObject(placementObject, view.replaceTest);

            placementObject = new Hit(0, isRim);
            renderPreview = false;
            return MouseHoldObject.nothing;
        }

        return null;
    }

    @Override
    public boolean instantUse(MapView view) {
        if (view.hasSelection()) { //Type is already checked by supportsView
            List<Hit> hits = new ArrayList<>();

            for (ArrayList<PositionalObject> stack : view.getSelection().values()) {
                for (PositionalObject h : stack) {
                    if (h instanceof Hit && ((Hit) h).isRim() ^ isRim) {
                        hits.add((Hit) h);
                    }
                }
            }

            view.map.registerChange(new RimChange(view.map, hits, isRim).perform());
            return false; //Don't swap off of selection tool when modifying selected objects
        }
        else {
            long time = Math.round(view.getTimeFromPosition(SettingsMaster.getMiddleX()));
            Snap closest = view.getClosestSnap(time, MAX_SNAP_OFFSET * 2);

            if (closest != null)
            {
                time = closest.pos;
            }

            view.map.addObject(new Hit(time, isRim, finisherLock ^ BindingGroup.shift()), view.replaceTest);
            return true;
        }
    }

    @Override
    public void cancel() {

    }

    @Override
    public boolean supportsView(MapView view) {
        return view.type == MapView.ViewType.OBJECT_VIEW || view.type == MapView.ViewType.GIMMICK_VIEW;
    }
}