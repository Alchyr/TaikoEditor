package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.layers.sub.SvFunctionLayer;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.LineAddition;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.PreviewLine;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.util.*;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.music;

public class SVFunctionTool extends EditorTool {
    private static final int MAX_SNAP_OFFSET = 40;
    protected static final Color previewColor = new Color(1.0f, 1.0f, 1.0f, 0.6f);
    private static final Color selectionColor = new Color(0.85f, 0.85f, 0.9f, 0.2f);

    private static SVFunctionTool tool;

    private final Texture pix = assetMaster.get("ui:pixel");

    private EditorLayer source;
    private MapView previewView;
    private boolean renderPreview;

    private final PreviewLine start, end;

    private int state = 0;

    SvFunctionLayer functionLayer = null;

    /*
        Adjust input layout:
            - Generate Lines (default on) generates lines on snappings which will replace existing lines
                - Objects (default on)
                    - Selected Objects Only (default off)
                - Barlines (default on)
            - Adjust Existing (default on)
                - Base on Objects (default on)

                Generate Lines functions one of two ways.
                If Adjust Existing is disabled or it's enabled but not based on objects: generate a line on every single position.

                If Adjust Existing is enabled AND based on objects:
                For each position, check if there's a line <= to it that is after the last position and after the previous object.
                If so, adjust that line. If not, generate a new line.

                If generate lines is disabled:
                Adjusting based on objects - For each position, find the following object and adjust sv based on it.
                Not based on objects: Just adjust all existing lines.
     */
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
        float y;
        switch (state)
        {
            case 0:
                y = SettingsMaster.gameY();

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
                                start.setPos((long) time);
                            }
                            else { //Snap to closest snap
                                start.setPos(closest.pos);
                            }
                        }
                        return;
                    }
                }
                break;
            case 1:
                y = SettingsMaster.gameY();

                renderPreview = false;

                if (y > viewsTop || y < viewsBottom)
                    return;

                if (Gdx.input.getX() <= 1)
                {
                    music.seekSecond(music.getSecondTime() - (music.isPlaying() ? 0.2 : elapsed * 4));
                }
                else if (Gdx.input.getX() >= SettingsMaster.getWidth() - 1)
                {
                    music.seekSecond(music.getSecondTime() + (music.isPlaying() ? 0.2 : elapsed * 4));
                }

                if (previewView != null)
                {
                    double time = previewView.getTimeFromPosition(Gdx.input.getX());
                    Snap closest = previewView.getClosestSnap(time, MAX_SNAP_OFFSET * 2);

                    renderPreview = true;

                    if (closest == null || BindingGroup.alt())
                    {
                        end.setPos((long) time);
                    }
                    else
                    {
                        end.setPos(closest.pos);
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

                        applyFunction(result);

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

                    int startPosition = previewView.getPositionFromTime(start.getPos(), SettingsMaster.getMiddle());
                    int endPosition = previewView.getPositionFromTime(end.getPos(), SettingsMaster.getMiddle());

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
    public MouseHoldObject click(MapView view, float x, float y, int button, int modifiers) {
        if (button == Input.Buttons.LEFT && renderPreview && previewView.equals(view)) {
            switch (state) {
                case 0:
                    state = 1;

                    if (source != null)
                        source.showText("Select the end position.");
                    break;
                case 1:
                    if (end.getPos() <= start.getPos()) {
                        state = 0;
                    }
                    else {
                        state = 2;
                        source.clean();

                        functionLayer = new SvFunctionLayer(svAtTime(start.getPos()), svAtTime(end.getPos()));
                        TaikoEditor.addLayer(functionLayer);

                        EditorLayer.processor.releaseInput(true);
                    }
                    break;
            }
            return MouseHoldObject.nothing;
        } else if (button == Input.Buttons.RIGHT) {
            if (state > 0) {
                state = 0;
                return MouseHoldObject.nothing;
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
        Map.Entry<Long, ArrayList<TimingPoint>> baseTiming = previewView.map.timingPoints.floorEntry(time);
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

            if (timing.getPos() > effect.getPos()) {
                return 1;
            }
            else {
                return effect.value;
            }
        }
    }

    public void applyFunction(SvFunctionLayer.SvFunctionProperties info)
    {
        if (!info.generateLines && !info.adjustExisting)
            return;

        double fsv = info.fsv, isv = info.isv;

        if (state == 2)
        {
            //prep
            double dsv, dist;
            EditorBeatmap map = previewView.map;

            long start = this.start.getPos(), end = this.end.getPos();

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
            HashSet<Long> positions = new HashSet<>();

            if (info.generateLines) {
                if (info.svObjects) {
                    for (Map.Entry<Long, ArrayList<HitObject>> stack : map.getSubMap(start - 1, end + 1).entrySet()) {
                        if (!info.selectedOnly || stack.getValue().stream().anyMatch((h)->h.selected)) {
                            positions.add(stack.getKey());
                        }
                    }
                }

                //barlines
                if (info.svBarlines)
                {
                    for (Snap s : map.getSnaps(1))
                    {
                        if (s.divisor == 0 && s.pos >= start && s.pos <= end) {
                            positions.add(s.pos);
                        }
                    }
                }
            }

            //SV on existing lines and it has to be based on their own position
            if ((info.adjustExisting && !info.basedOnFollowingObject) || (info.adjustExisting && !info.generateLines)) {
                if (!info.generateLines)
                    positions.add(start);

                for (Map.Entry<Long, ArrayList<TimingPoint>> stack : map.effectPoints.subMap(start, true, end, true).entrySet()) {
                    positions.add(stack.getKey());
                }
            }


            //Adjust final sv if it is relative to the ending bpm and not the initial bpm
            if (info.relativeLast)
            {
                Map.Entry<Long, ArrayList<TimingPoint>> lastTiming = map.timingPoints.floorEntry(end);

                if (lastTiming == null)
                    return; //wtf no timing points

                float ratio = (float) (lastTiming.getValue().get(lastTiming.getValue().size() - 1).getBPM() / firstTiming.getBPM());

                fsv = fsv * ratio;
            }

            //Generate sv
            dsv = fsv - isv; //delta (change) in SV

            PositionalObjectTreeMap<PositionalObject> sv = new PositionalObjectTreeMap<>();

            ArrayList<Long> sortedPositions = new ArrayList<>(positions);
            sortedPositions.sort(Long::compare);

            if (!info.generateLines && info.basedOnFollowingObject) {
                //This is purely adjusting all existing lines to do sv based on their following object.
                //Positions are the positions of lines to adjust.

                for (long pos : sortedPositions)
                {
                    Map.Entry<Long, ArrayList<TimingPoint>> basePoint = map.allPoints.floorEntry(pos);
                    if (basePoint == null)
                        continue;

                    TimingPoint adjust = basePoint.getValue().get(basePoint.getValue().size() - 1);
                    if (adjust.uninherited)
                        continue;

                    Long basePos = map.objects.ceilingKey(pos);
                    if (basePos == null || basePos > end) {
                        basePos = pos;
                    }

                    Map.Entry<Long, ArrayList<TimingPoint>> lastTiming = map.timingPoints.floorEntry(basePos);
                    if (lastTiming == null)
                    {
                        lastTiming = map.timingPoints.ceilingEntry(basePos);
                        if (lastTiming == null)
                            return;
                    }

                    //SV is based on the initial timing point. If the timing is different, the sv also must be adjusted.
                    float ratio = (float) (firstTiming.getBPM() / lastTiming.getValue().get(lastTiming.getValue().size() - 1).getBPM());

                    basePos = MathUtils.clamp(basePos, start, end);

                    adjust.tempSet((isv + (dsv * info.function.apply((basePos - start) / dist))) * ratio);
                    sv.add(adjust);
                }

                map.registerValueChange(sv);
                map.updateSv();
            }
            else {
                //Normal generation.
                //For each position, if adjust is true, look for the closest recent line. If it's on the same position or there's no object in-between, adjust it.
                //Otherwise just generate a new one.

                Long lastPos = Long.MIN_VALUE;
                boolean adjust = info.adjustExisting && info.basedOnFollowingObject;

                for (long pos : sortedPositions)
                {
                    Map.Entry<Long, ArrayList<TimingPoint>> basePoint = map.allPoints.floorEntry(pos);
                    Map.Entry<Long, ArrayList<TimingPoint>> lastTiming = map.timingPoints.floorEntry(pos);

                    if (lastTiming == null)
                    {
                        lastTiming = map.timingPoints.ceilingEntry(pos);
                        if (lastTiming == null)
                            return;
                    }

                    if (basePoint == null)
                    {
                        basePoint = lastTiming;
                    }

                    TimingPoint closest = basePoint.getValue().get(basePoint.getValue().size() - 1);

                    //SV is based on the initial timing point. If the timing is different, the sv also must be adjusted.
                    float ratio = (float) (firstTiming.getBPM() / lastTiming.getValue().get(lastTiming.getValue().size() - 1).getBPM());

                    if (adjust && !closest.uninherited && closest.getPos() > lastPos && closest.getPos() < pos) {
                        //Adjusting and the most recent line is green and it's at least past the last point
                        //and it's not on the target position. If it's on the target position, new line generation will handle it fine.
                        lastPos = map.objects.lowerKey(pos);
                        if (lastPos == null || lastPos < closest.getPos()) {
                            //There's no other object between the closest green line and the current position.
                            TimingPoint p = new TimingPoint(closest);
                            p.setValue((isv + (dsv * info.function.apply((pos - start) / dist))) * ratio);
                            sv.add(p);

                            lastPos = pos;
                            continue;
                        }
                    }

                    //Generate an inherited copy of the closest previous timing point
                    TimingPoint p = ((TimingPoint) closest.shiftedCopy(pos + info.genOffset)).inherit();

                    pos = MathUtils.clamp(pos, start, end);

                    p.setValue((isv + (dsv * info.function.apply((pos - start) / dist))) * ratio);

                    sv.add(p);

                    lastPos = pos;
                }

                map.registerChange(new LineAddition(map, sv).perform());
            }
        }
    }
}