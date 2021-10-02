package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.layers.sub.SvFunctionLayer;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.LineAddition;
import alchyr.taikoedit.editor.views.SvView;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.management.bindings.BindingGroup;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.PreviewLine;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.input.MouseHoldObject;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.*;
import java.util.function.Function;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class SVFunctionTool extends EditorTool {
    private static final int MAX_SNAP_OFFSET = 40;
    protected static final Color previewColor = new Color(1.0f, 1.0f, 1.0f, 0.6f);
    private static final Color selectionColor = new Color(0.85f, 0.85f, 0.9f, 0.2f);

    private static SVFunctionTool tool;

    private final Texture pix = assetMaster.get("ui:pixel");

    private EditorLayer source;
    private MapView previewView;
    private boolean renderPreview;

    private PreviewLine start, end;

    private int state = 0;

    SvFunctionLayer functionLayer = null;


    private SVFunctionTool()
    {
        super("SV Function");

        start = new PreviewLine(0);
        end = new PreviewLine(0);
    }

    public static SVFunctionTool get()
    {
        if (tool == null)
        {
            tool = new SVFunctionTool();
        }

        return tool;
    }

    @Override
    public void onSelected(EditorLayer source) {
        source.showText("Select the starting position.");
        state = 0;

        this.source = source;
    }

    @Override
    public void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed) {
        int y;
        switch (state)
        {
            case 0:
                y = SettingsMaster.getHeight() - Gdx.input.getY();

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
                            double time = hovered.getTimeFromPosition(Gdx.input.getX());
                            Snap closest = hovered.getClosestSnap(time, MAX_SNAP_OFFSET);

                            previewView = hovered;
                            renderPreview = true;
                            if (closest == null || BindingGroup.alt()) { //Just go to cursor position
                                start.setPosition((int) time);
                            }
                            else { //Snap to closest snap
                                start.setPosition((int) closest.pos);
                            }
                        }
                        return;
                    }
                }
                break;
            case 1:
                y = SettingsMaster.getHeight() - Gdx.input.getY();

                renderPreview = false;

                if (y > viewsTop || y < viewsBottom)
                    return;

                if (previewView != null)
                {
                    double time = previewView.getTimeFromPosition(Gdx.input.getX());
                    Snap closest = previewView.getClosestSnap(time, MAX_SNAP_OFFSET * 2);

                    renderPreview = true;

                    if (closest == null || BindingGroup.alt())
                    {
                        end.setPosition((int) time);
                    }
                    else
                    {
                        end.setPosition((int) closest.pos);
                    }
                }
                break;
            case 2:
                if (functionLayer == null) {
                    state = 0;
                }
                else {
                    if (functionLayer.result != null) {
                        SvFunctionLayer.SvFunctionProperties result = functionLayer.result;

                        applyFunction(result.isv, result.fsv, result.svBarlines, result.relativeLast, result.function);

                        functionLayer.result = null;
                    }
                    if (!functionLayer.active) {
                        functionLayer = null;
                        state = 0;
                    }
                }
                break;
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        switch (state)
        {
            case 0:
                if (renderPreview)
                {
                    previewView.renderObject(start, sb, sr, previewColor.a);
                }
                break;
            case 1:
                previewView.renderObject(start, sb, sr, previewColor.a);
                if (renderPreview)
                {
                    previewView.renderObject(end, sb, sr, previewColor.a);

                    int startPosition = previewView.getPositionFromTime(start.pos, SettingsMaster.getMiddle());
                    int endPosition = previewView.getPositionFromTime(end.pos, SettingsMaster.getMiddle());

                    if (startPosition < endPosition)
                    {
                        sb.setColor(selectionColor);
                        sb.draw(pix, startPosition, previewView.bottom, endPosition - startPosition, previewView.top - previewView.bottom);
                    }
                    else if (startPosition > endPosition)
                    {
                        sb.setColor(selectionColor);
                        sb.draw(pix, endPosition, previewView.bottom, startPosition - endPosition, previewView.top - previewView.bottom);
                    }
                }
                break;
        }
    }

    @Override
    public boolean consumesRightClick() {
        return state > 0;
    }

    @Override
    public MouseHoldObject click(MapView view, int x, int y, int button, int modifiers) {
        if (button == Input.Buttons.LEFT && renderPreview && previewView.equals(view)) {
            switch (state) {
                case 0:
                    state = 1;

                    if (source != null)
                        source.showText("Select the end position.");
                    break;
                case 1:
                    if (end.pos <= start.pos) {
                        state = 0;
                    }
                    else {
                        state = 2;
                        //applyFunction(exp);
                        //TODO: Open a screen that allows you to set all the details.
                        if (EditorLayer.music.isPlaying())
                            EditorLayer.music.pause();

                        functionLayer = new SvFunctionLayer(svAtTime(start.pos), svAtTime(end.pos));
                        TaikoEditor.addLayer(functionLayer);

                        EditorLayer.processor.clearInput();
                        if (EditorLayer.processor.mouseHold != null)
                        {
                            EditorLayer.processor.mouseHold.onRelease(Gdx.input.getX(), SettingsMaster.getHeight() - Gdx.input.getY());
                            EditorLayer.processor.mouseHold = null;
                        }
                    }
                    break;
            }
        } else if (button == Input.Buttons.RIGHT) {
            if (state > 0) {
                state = 0;
            }
        }

        return null;
    }

    @Override
    public void cancel() {
        state = 0;
    }

    @Override
    public boolean supportsView(MapView view) {
        return view.type == MapView.ViewType.EFFECT_VIEW;
    }

    private double svAtTime(long time) {
        Map.Entry<Long, ArrayList<TimingPoint>> baseSv = previewView.map.effectPoints.floorEntry(time);
        Map.Entry<Long, ArrayList<TimingPoint>> baseTiming = previewView.map.effectPoints.floorEntry(time);
        if (baseSv == null) {
            return 1;
        }
        else if (baseTiming == null) {
            //wtf green line in map with no red lines
            return baseSv.getValue().get(baseSv.getValue().size() - 1).value;
        }
        else {
            ArrayList<TimingPoint> pointList = baseSv.getValue();
            TimingPoint effect = pointList.get(pointList.size() - 1);
            pointList = baseTiming.getValue();
            TimingPoint timing = pointList.get(pointList.size() - 1);

            if (timing.pos > effect.pos) {
                return 1;
            }
            else {
                return effect.value;
            }
        }
    }

    //parameters: double initial sv, double final sv, boolean svBarlines, boolean relativeLast
    public void applyFunction(double isv, double fsv, boolean svBarlines, boolean relativeLast, Function<Double, Double> f)
    {
        //boolean svBarlines = true;
        //boolean relativeLast = true; //if false: final sv is relative to starting bpm. If true, it's relative to ending bpm.

        //isv = 1.0; //Initial SV
        //fsv = 1.71; //Final SV

        if (state == 2)
        {
            //prep
            double dsv, dist;
            EditorBeatmap map = previewView.map;

            long start = this.start.pos, end = this.end.pos;

            if (start == end)
            {
                return;
            }
            if (start > end)
            {
                long temp = start;
                start = end;
                end = temp;
            }

            Map.Entry<Long, ArrayList<TimingPoint>> firstTimingEntry = map.timingPoints.floorEntry(start);

            if (firstTimingEntry == null)
            {
                firstTimingEntry = map.timingPoints.ceilingEntry(start);
                if (firstTimingEntry == null)
                    return; //wtf no timing points
            }

            TimingPoint firstTiming = firstTimingEntry.getValue().get(firstTimingEntry.getValue().size() - 1);

            dist = end - start;

            //Find all positions where green lines are needed
            HashSet<Long> positions = new HashSet<>(map.getSubMap(start, end).keySet());

            if (svBarlines)
            {
                for (Snap s : map.getSnaps(1))
                {
                    if (s.divisor == 0 && s.pos >= start && s.pos <= end)
                        positions.add((long) s.pos);
                }
            }

            //Adjust final sv if it is relative to the ending bpm and not the initial bpm
            if (relativeLast)
            {
                Map.Entry<Long, ArrayList<TimingPoint>> lastTiming = map.timingPoints.floorEntry(end);

                if (lastTiming == null)
                    return; //wtf no timing points

                float ratio = (float) (lastTiming.getValue().get(lastTiming.getValue().size() - 1).getBPM() / firstTiming.getBPM());

                fsv = fsv * ratio;
            }

            //Generate sv
            dsv = fsv - isv; //delta (change) in SV

            PositionalObjectTreeMap<PositionalObject> generated = new PositionalObjectTreeMap<>();

            for (long pos : positions)
            {
                Map.Entry<Long, ArrayList<TimingPoint>> lastEffect = map.effectPoints.floorEntry(pos);
                Map.Entry<Long, ArrayList<TimingPoint>> lastTiming = map.timingPoints.floorEntry(pos);

                if (lastTiming == null)
                {
                    lastTiming = map.timingPoints.ceilingEntry(pos);
                    if (lastTiming == null)
                        return;
                }

                if (lastEffect == null || lastTiming.getKey() > lastEffect.getKey())
                {
                    lastEffect = lastTiming;
                }

                //SV is based on the initial timing point. If the timing is different, the sv also must be adjusted.
                float ratio = (float) (firstTiming.getBPM() / lastTiming.getValue().get(lastTiming.getValue().size() - 1).getBPM());

                //Generate an inherited copy of the closest previous timing point
                TimingPoint p = ((TimingPoint) lastEffect.getValue().get(lastEffect.getValue().size() - 1).shiftedCopy(pos)).inherit();

                p.setValue((isv + (dsv * f.apply((pos - start) / dist))) * ratio);

                generated.add(p);
            }

            map.registerChange(new LineAddition(map, generated).perform());
            //map.effectPoints.add(p);
        }
    }
}