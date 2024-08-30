package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.layers.sub.VolumeFunctionLayer;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.PreviewLine;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;
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

public class VolumeFunctionTool extends EditorTool {
    private static final int MAX_SNAP_OFFSET = 40;
    protected static final Color previewColor = new Color(1.0f, 1.0f, 1.0f, 0.6f);
    private static final Color selectionColor = new Color(0.85f, 0.85f, 0.9f, 0.2f);

    private static VolumeFunctionTool tool;

    private final Texture pix = assetMaster.get("ui:pixel");

    private EditorLayer source;
    private MapView previewView;
    private boolean renderPreview;

    private volatile boolean seeked = false;

    private final PreviewLine start, end;

    private int state = 0;

    VolumeFunctionLayer functionLayer = null;

    /*
            Generate Lines functions one of two ways.
            If selected objects only is disabled: Generate equidistant points for each volume from start volume to end volume.

            If selected objects only is enabled: Generate a point for each selected object, offset by gen offset of appropriate volume based on object's positions.

            If Adjust Existing is enabled AND based on objects:
            For each position, check if there's a line <= to it that is after the last position and after the previous object.
            If so, adjust that line. If not, generate a new line.

            If generate lines is disabled:
            Adjusting based on objects - For each position, find the following object and adjust sv based on it.
            Not based on objects: Just adjust all existing lines.
     */
    private VolumeFunctionTool()
    {
        super("Volume Function");

        start = new PreviewLine(0);
        end = new PreviewLine(0);
    }

    public static VolumeFunctionTool get()
    {
        if (tool == null)
        {
            tool = new VolumeFunctionTool();
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
                    float mul = BindingGroup.alt() ? 4 : 1;
                    if (music.isPlaying()) {
                        TaikoEditor.onMain(()->{
                            if (!seeked) {
                                seeked = true;
                                music.seekSecond(music.getSecondTime() - mul * Math.max(0.15, Gdx.graphics.getDeltaTime() * 10));
                            }
                        });
                    }
                    else {
                        music.seekSecond(music.getSecondTime() - mul * elapsed * 2);
                    }
                }
                else if (Gdx.input.getX() >= SettingsMaster.getWidth() - 1)
                {
                    music.seekSecond(music.getSecondTime() + (music.isPlaying() ? elapsed * 8 : elapsed * 2) * (BindingGroup.alt() ? 4 : 1));
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
                        VolumeFunctionLayer.VolumeFunctionProperties result = functionLayer.result;

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
        seeked = false;
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

                    int startPosition = previewView.getPositionFromTime(start.getPos());
                    int endPosition = previewView.getPositionFromTime(end.getPos());

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

                        functionLayer = new VolumeFunctionLayer(volumeAtTime(start.getPos()), volumeAtTime(end.getPos()));
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

    private int volumeAtTime(long time) {
        Map.Entry<Long, ArrayList<TimingPoint>> base = previewView.map.allPoints.floorEntry(time);
        if (base == null || base.getValue().isEmpty()) {
            return 100;
        }
        else {
            return base.getValue().get(base.getValue().size() - 1).getVolume();
        }
    }

    public void applyFunction(VolumeFunctionLayer.VolumeFunctionProperties info)
    {
        if (!info.generateLines && !info.adjustExisting)
            return;

        int ivol = info.iVol, fvol = info.fVol; //convenience

        if (state == 2)
        {
            //prep
            double dist;
            int dvol = fvol - ivol;
            EditorBeatmap map = previewView.map;

            long start = this.start.getPos(), end = this.end.getPos();

            if (start == end)
            {
                return;
            }
            if (start > end)
            {
                start += end; //start = a + b
                end += start; //end = b + a + b
                start = end - start; //start = b + a + b - a - b = b
                end -= start * 2; //end = b + a + b - b - b = a
                //I did it, I passed the programming interview
            }

            dist = end - start;

            //Find all positions where green lines are needed
            HashSet<Long> positions = new HashSet<>();

            if (info.generateLines) {
                if (info.selectedOnly) {
                    for (Map.Entry<Long, ArrayList<HitObject>> stack : map.getSubMap(start - 1, end + 1).entrySet()) {
                        if (stack.getValue().stream().anyMatch((h)->h.selected)) {
                            positions.add(stack.getKey());
                        }
                    }
                }
                else {
                    MapObjectTreeMap<MapObject> vol = new MapObjectTreeMap<>();

                    //Generate lines.
                    int steps = Math.abs(dvol) * 4 + 1;
                    double spacing = steps == 1 ? 0 : dist / (steps - 1);
                    long pos;

                    if (info.adjustExisting) {
                        for (Map.Entry<Long, ArrayList<TimingPoint>> basePoint : map.allPoints.subMap(start, end).entrySet()) {
                            TimingPoint p = (TimingPoint) basePoint.getValue().get(basePoint.getValue().size() - 1).shiftedCopy(basePoint.getKey());
                            p.inherit();
                            int v = (int) Math.round(ivol + (dvol * info.function.apply((basePoint.getKey() - start) / dist)));
                            p.setVolume(v);
                            vol.add(p);

                            //Modify volume of red lines. Not undoable.
                            for (TimingPoint point : basePoint.getValue()) {
                                if (point.uninherited) {
                                    point.setVolume(v);
                                }
                            }
                        }
                    }

                    int lastV = -1;
                    for (int i = 0; i < steps; ++i) {
                        pos = start + (long) (spacing * i);

                        Map.Entry<Long, ArrayList<TimingPoint>> basePoint = map.allPoints.floorEntry(pos);
                        if (basePoint == null || basePoint.getValue().isEmpty()) {
                            basePoint = map.allPoints.ceilingEntry(pos);
                            if (basePoint == null || basePoint.getValue().isEmpty())
                                return;
                        }

                        TimingPoint closest = basePoint.getValue().get(basePoint.getValue().size() - 1);

                        if (lastV == -1) lastV = closest.volume;
                        int v = (int) Math.round(ivol + (dvol * info.function.apply((pos - start) / dist)));

                        if (v != lastV) {
                            TimingPoint p = ((TimingPoint) closest.shiftedCopy(pos)).inherit();

                            p.setVolume(v);
                            vol.add(p);

                            lastV = v;
                        }
                    }

                    map.registerAndPerformAddObjects("Volume Function", vol, null, previewView.replaceTest);
                    return;
                }
            }

            MapObjectTreeMap<MapObject> vol = new MapObjectTreeMap<>();

            //SV on existing lines and it has to be based on their own position
            if ((info.adjustExisting && !info.basedOnFollowingObject) || (info.adjustExisting && !info.generateLines)) {
                if (!info.generateLines)
                    positions.add(start);

                for (Map.Entry<Long, ArrayList<TimingPoint>> stack : map.allPoints.subMap(start, true, end, true).entrySet()) {
                    positions.add(stack.getKey());
                }
            }

            ArrayList<Long> sortedPositions = new ArrayList<>(positions);
            sortedPositions.sort(Long::compare);

            if (!info.generateLines && info.basedOnFollowingObject) {
                //This is purely adjusting all existing lines to do volume based on their following object.
                //Positions are the positions of lines to adjust.

                Map<Long, Integer> newVolumeMap = new HashMap<>();

                for (int i = 0; i < sortedPositions.size(); ++i) {
                    long pos = sortedPositions.get(i);

                    Map.Entry<Long, ArrayList<TimingPoint>> stack = map.allPoints.floorEntry(pos);
                    if (stack == null)
                        continue;

                    Long basePos = map.objects.ceilingKey(pos);
                    if (basePos == null || basePos > end || (i < sortedPositions.size() - 1 && basePos >= sortedPositions.get(i + 1))) {
                        basePos = pos;
                    }
                    basePos = MathUtils.clamp(basePos, start, end);

                    newVolumeMap.put(stack.getKey(), (int) Math.round(ivol + (dvol * info.function.apply((basePos - start) / dist))));
                    for (TimingPoint p : stack.getValue()) {
                        vol.add(p);
                    }
                }

                map.registerAndPerformVolumeChange(vol, newVolumeMap);
            }
            else {
                //Normal generation.
                //For each position, if adjust is true, look for the closest recent line. If it's on the same position or there's no object in-between, adjust it.
                //Otherwise just generate a new one.

                Long lastPos = Long.MIN_VALUE;
                int lastV = -1;
                boolean adjust = info.adjustExisting && info.basedOnFollowingObject;
                //if not based on following object, all timing points are treated as their own positions, rather than being "adjusted"

                for (long pos : sortedPositions)
                {
                    Map.Entry<Long, ArrayList<TimingPoint>> basePoint = map.allPoints.floorEntry(pos);

                    if (adjust && basePoint.getKey() > lastPos && basePoint.getKey() <= pos) {
                        //Adjusting and the most recent line is green and it's at least past the last point
                        //and it's not on the target position. If it's on the target position, new line generation will handle it fine.
                        lastPos = map.objects.lowerKey(pos);
                        if (lastPos == null || lastPos < basePoint.getKey()) {
                            //There's no other object between the closest green line and the current position.
                            //Create new timing point on same position with new volume.
                            TimingPoint p = (TimingPoint) basePoint.getValue().get(basePoint.getValue().size() - 1).shiftedCopy(basePoint.getKey());
                            p.inherit();
                            int v = (int) Math.round(ivol + (dvol * info.function.apply((pos - start) / dist)));
                            p.setVolume(v);
                            vol.add(p);
                            lastV = v;

                            lastPos = pos;

                            //For the sake of simplicity, this won't be undoable if it modifies red lines.
                            for (TimingPoint point : basePoint.getValue())  {
                                if (point.uninherited) {
                                    point.setVolume(v);
                                }
                            }
                            continue;
                        }
                    }

                    //Generate an inherited copy of the closest previous timing point
                    long genPos = pos + info.genOffset;

                    TimingPoint closest = basePoint.getValue().get(basePoint.getValue().size() - 1);
                    if (closest.getPos() <= pos && genPos < closest.getPos())
                        genPos = Math.min(pos, closest.getPos() + 1);
                    if (lastV == -1) lastV = closest.getVolume();

                    int v = (int) Math.round(ivol + (dvol * info.function.apply((pos - start) / dist)));

                    if (lastV != v) {
                        TimingPoint p = ((TimingPoint) closest.shiftedCopy(genPos)).inherit();

                        p.setVolume(v);
                        vol.add(p);
                        lastV = v;
                    }

                    lastPos = pos;
                }

                map.registerAndPerformAddObjects("Volume Function", vol, null, previewView.replaceTest);
            }
        }
    }
}