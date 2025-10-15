package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.audio.Waveform;
import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.core.input.sub.TextInputReceiver;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.MapObjectChange;
import alchyr.taikoedit.editor.changes.ValueSetChange;
import alchyr.taikoedit.editor.changes.VolumeSetChange;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.editor.tools.*;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.GeneralUtils;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;
import alchyr.taikoedit.util.structures.MultiMergeIterator;
import alchyr.taikoedit.util.structures.Pair;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Function;

import static alchyr.taikoedit.TaikoEditor.*;
import static alchyr.taikoedit.core.layers.EditorLayer.viewScale;

public class EffectView extends MapView implements TextInputReceiver {
    public static final String ID = "eff";
    @Override
    public String typeString() {
        return ID;
    }

    //Kiai, sv
    //SV can be represented using a line graph?
    //Drag line graph to quickly adjust sv?
    //Select a section to do effects like interpolation (smooth line) or something similar?

    public static final int HEIGHT = 240;
    private static final int MARGIN = 40;
    public static final int SV_AREA = (HEIGHT - (MARGIN * 2)) / 2;
    private static final int BOTTOM_VALUE_Y = MARGIN + 10;
    public static final int TOP_VALUE_Y = HEIGHT - (MARGIN + 1);

    //For clicking value of a timing point
    private static final int VALUE_LABEL_TOP = TOP_VALUE_Y + 2;
    private static final int VALUE_LABEL_BOTTOM = TOP_VALUE_Y - 12;
    private static final int BPM_LABEL_TOP = BOTTOM_VALUE_Y + 2;
    private static final int BPM_LABEL_BOTTOM = BOTTOM_VALUE_Y - 12;

    //Selection
    private static final int SELECTION_DIST = 10, ADJUST_DIST = 30;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.7f);
    private static final Color lineColor = new Color(0.35f, 0.35f, 0.38f, 0.4f);
    private static final Color kiai = new Color(240.0f/255.0f, 164.0f/255.0f, 66.0f/255.0f, 1.0f);

    private SortedMap<Long, Snap> activeSnaps;

    private enum AdjustMode{
        NONE,
        POSSIBLE,
        ACTIVE
    }
    private AdjustMode adjustMode = AdjustMode.NONE;
    private TimingPoint adjustPoint = null, focus = null;

    //Positions
    private int baseMidY;

    //Offset positions
    private int midY;

    public boolean mode = true; //true = sv, false = volume

    public boolean effectPointsEnabled = true; //TODO: Add button to disable.
    public boolean timingEnabled = false;

    //SV Graph
    private static final int SMOOTH_GRAPH_DISTANCE = 300; //Further apart than this, no smooth graph
    private static final int LABEL_SPACING = 36;
    private static final DecimalFormat svFormat = new DecimalFormat("0.000x", osuDecimalFormat);
    public double peakSV, minSV;
    private String peakSVText, minSVText;
    private boolean renderLabels = true; //TODO: Add way to disable labels. Toggle button on overlay?

    //Waveform Graph
    private static final Color waveformColor = new Color(81f/255, 68f/255, 1f, 1f);
    private Waveform waveform = null;
    private int waveformMode = -1; //-1 = none, 0 = normal, 1 = absolute value
    private static final int MAX_WAVEFORM_MODE = 1;

    //Sv values
    private final BitmapFont font;

    //Hitsounds of map
    private long lastSounded;

    public EffectView(EditorLayer parent, EditorBeatmap beatmap) {
        super(ViewType.EFFECT_VIEW, parent, beatmap, HEIGHT);

        addOverlayButton(new ImageButton(assetMaster.get("editor:exit"), assetMaster.get("editor:exith")).setClick(this::close).setAction("Close View"));
        addOverlayButton(new ImageButton(assetMaster.get("editor:mode"), assetMaster.get("editor:modeh")).setClick(this::swapMode).setAction("Swap Modes"));
        addOverlayButton(new ImageButton(assetMaster.get("editor:graph"), assetMaster.get("editor:graphh")).setClick(this::waveformMode).setAction("Waveform Mode"));
        addOverlayButton(new ImageButton(assetMaster.get("editor:timing"), assetMaster.get("editor:timingh")).setClick(this::swapTimingEnabled).setAction("Edit Timing"));
        addOverlayButton(new ImageButton(assetMaster.get("editor:label"), assetMaster.get("editor:labelh")).setClick(this::toggleLabels).setAction("Show Labels"));
        addLockPositionButton();

        font = assetMaster.getFont("base:aller small");

        lastSounded = 0;
        minSV = 0.75;
        peakSV = 1.3;
        recheckSvLimits();

        updateToolset();
    }

    public void close(int button)
    {
        if (button == Input.Buttons.LEFT)
        {
            parent.removeView(this);
        }
    }

    public void swapMode(int button)
    {
        EditorTool t = parent.tools.getCurrentTool();
        if (t != null)
            t.cancel();
        endAdjust();

        mode = !mode;
        updateToolset();

        SelectionTool.get().effectMode = mode;
    }

    @Override
    public boolean allowVerticalDrag() {
        return noRedlines() && (effectPointsEnabled || !mode); //not adjusting a red line, volume mode or effect points exist
    }

    private boolean noRedlines() {
        if (!hasSelection())
            return true;

        for (ArrayList<MapObject> stack : getSelection().values()) {
            for (MapObject o : stack) {
                if (o instanceof TimingPoint && ((TimingPoint) o).uninherited)
                    return false;
            }
        }
        return true;
    }

    public void swapTimingEnabled(int button)
    {
        timingEnabled = !timingEnabled;
        parent.showText(timingEnabled ? "Timing editing enabled." : "Timing editing disabled.");
        clearSelection();
        updateToolset();
    }

    public void toggleLabels(int button)
    {
        renderLabels = !renderLabels;
        parent.showText(renderLabels ? "Labels enabled." : "Labels disabled.");
    }

    public void waveformMode(int button)
    {
        if (waveform != null && waveformMode == MAX_WAVEFORM_MODE) {
            waveform = null;
            waveformMode = -1;
        }
        else {
            music.getWaveform((w)->waveform = w, (e)->{
                parent.showText("Failed to generate waveform: " + e.getMessage());
            });
            ++waveformMode;
            if (waveform == null) {
                parent.showText("Generating waveform...");
            }
        }
    }

    @Override
    public int setPos(int y) {
        super.setPos(y);

        baseMidY = this.y + HEIGHT / 2;

        return this.y;
    }

    public void setOffset(int offset)
    {
        super.setOffset(offset);

        midY = baseMidY + yOffset;
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends MapObject>> prep() {
        //return graph points
        if (!timingEnabled && !effectPointsEnabled) {
            return Collections.emptyNavigableMap();
        }
        if (!timingEnabled) {
            return map.getEditEffectPoints(time - EditorLayer.viewTime, time + EditorLayer.viewTime);
        }
        else if (!effectPointsEnabled) {
            return map.getEditTimingPoints(time - EditorLayer.viewTime, time + EditorLayer.viewTime);
        }
        else {
            return map.getEditPoints(time - EditorLayer.viewTime, time + EditorLayer.viewTime);
        }
    }

    public void setFocus(TimingPoint point) {
        if (adjustPoint == null)
            focus = point;
    }

    @Override
    public void update(double exactPos, long msPos, float elapsed, boolean canHover) {
        super.update(exactPos, msPos, elapsed, canHover);
        activeSnaps = map.getActiveSnaps(preciseTime - EditorLayer.viewTime, preciseTime + EditorLayer.viewTime);

        blipTimer -= elapsed;
        if (blipTimer <= 0) {
            blipTimer = 0.4f;
            renderBlip = !renderBlip;
        }

        if (adjustMode == AdjustMode.ACTIVE && !parent.tools.getCurrentTool().equals(getToolset().getDefaultTool())) {
            endAdjust();
        }

        focus = null;

        if (adjustPoint != null) {
            if (!adjustPoint.testHidden())
                focus = adjustPoint;
        }
        else if (hasSelection()) {
            MapObjectTreeMap<MapObject> selection = getSelection();
            if (selection.count() == 1) {
                ArrayList<MapObject> s = selection.firstEntry().getValue();

                if (!s.isEmpty()) {
                    MapObject o = s.get(0);

                    if (o instanceof TimingPoint && !o.testHidden()) {
                        focus = (TimingPoint) o;
                    }
                }
            }
        }
    }

    @Override
    public void primary() {
        super.primary();
        SelectionTool.get().effectMode = mode;
    }

    @Override
    public void notPrimary() {
        super.notPrimary();
        endAdjust();
    }

    @Override
    public void primaryUpdate(boolean isPlaying) {
        if (isPrimary && lockOffset == 0 && isPlaying && lastSounded < time && time - lastSounded < 25
                && parent.getViewSet(map).contains((o)->o.type == ViewType.OBJECT_VIEW))
        {
            for (ArrayList<HitObject> objects : map.objects.subMap(lastSounded, false, time, true).values())
            {
                for (HitObject o : objects)
                {
                    o.playSound();
                }
            }
        }
        lastSounded = time;
    }

    @Override
    public void renderBase(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, bottom, SettingsMaster.getWidth(), height);
        sb.setColor(lineColor);
        sb.draw(pix, 0, midY, SettingsMaster.getWidth(), 1);
        sb.draw(pix, 0, midY + SV_AREA, SettingsMaster.getWidth(), 1);
        sb.draw(pix, 0, midY - SV_AREA, SettingsMaster.getWidth(), 1);

        if (waveform != null) {
            renderWaveform(sb, sr);
        }

        if (!timingEnabled) { //If timing editing disabled, timing points are rendered as part of base.
            TimingPoint t;
            for (ArrayList<TimingPoint> points : map.getEditTimingPoints(time - EditorLayer.viewTime, time + EditorLayer.viewTime).values()) {
                t = GeneralUtils.listLast(points);
                renderObject(t, sb, sr, 1);
            }
        }
    }

    @Override
    public void renderOverlay(SpriteBatch sb, ShapeRenderer sr) {
        //Snaps go on top of the lines for this one.
        for (Snap s : activeSnaps.values())
        {
            s.halfRender(sb, sr, preciseTime, viewScale, SettingsMaster.getMiddleX(), bottom, 40);
        }

        if (mode) {
            //sv
            renderSVGraph(sb, sr);
            textRenderer.setFont(font).renderText(sb, peakSVText, 0, midY + SV_AREA + 10, Color.WHITE);
            textRenderer.renderText(sb, minSVText, 0, midY - SV_AREA - 1);
        }
        else {
            //volume
            renderVolumeGraph(sb, sr);
            textRenderer.setFont(font).renderText(sb, "100", 0, midY + SV_AREA + 10, Color.WHITE);
            textRenderer.renderText(sb, "0", 0, midY - SV_AREA - 1);
        }

        if (renderLabels) {
            if (mode) {
                renderValueLabels(sb, focus);
            }
            else {
                renderVolumeLabels(sb, focus);
            }
        }

        super.renderOverlay(sb, sr);
    }

    private static float svGraphPos(TimingPoint p, double maxValue, double minValue) { //FIX GRAPH NOT WORKING RIGHT ON TEMP DISPLAY OBJECTS. And not updating limits when doing up/down drag.
        if (p.uninherited) { //red line
            return 0;
        }
        else { //green line
            if (p.value >= 1)
            {
                return SV_AREA * (float) ((p.value - 1) / maxValue);
            }
            else
            {
                return -SV_AREA * (float) ((1 - p.value) / minValue);
            }
        }
    }
    private void renderSVGraph(SpriteBatch sb, ShapeRenderer sr) {
        if (map.allPoints.isEmpty())
            return;

        /*if (!additionalDisplayObjects().isEmpty()) {
            StringBuilder tempSb = new StringBuilder("MapPoints: ");
            map.getEditPoints(time - EditorLayer.viewTime, time + EditorLayer.viewTime).forEach((key, value) -> tempSb.append(key).append("|"));
            editorLogger.info(tempSb);
            tempSb.setLength(0);
            tempSb.append("AddPoints: ");
            additionalDisplayObjects().descendingMap().forEach((key, value) -> tempSb.append(key).append("|"));
            editorLogger.info(tempSb);
            tempSb.setLength(0);
            tempSb.append("Combined:  ");

            MultiMergeIterator<Long, TimingPoint, ArrayList<TimingPoint>> pointIterator = new MultiMergeIterator<>(ArrayList::new, reverseLongComparator);
            pointIterator.addFilter(MapObject::testHidden); //remove hidden objects

            pointIterator.addIterator(map.getEditPoints(time - EditorLayer.viewTime, time + EditorLayer.viewTime).entrySet().iterator(), Map.Entry::getKey, (list, entry)->list.addAll(entry.getValue()));
            pointIterator.addIterator(additionalDisplayObjects().descendingMap().entrySet().iterator(), Map.Entry::getKey, (list, entry)->entry.getValue().forEach((obj)->list.add((TimingPoint) obj)), 1);

            pointIterator.forEachRemaining((o)->{
                tempSb.append(pointIterator.getLastKey());
                if (!o.isEmpty()) {
                    tempSb.append("/").append(o.get(0).getPos());
                }
                else {
                    tempSb.append("EMPTY");
                }
                tempSb.append("|");
            });
            editorLogger.info(tempSb);
        }*/

        //Returns from first line before visible area, to first line after visible area
        MultiMergeIterator<Long, TimingPoint, ArrayList<TimingPoint>> pointIterator = new MultiMergeIterator<>(ArrayList::new, reverseLongComparator);
        pointIterator.addFilter(MapObject::testHidden); //remove hidden objects

        pointIterator.addIterator(map.getEditPoints(time - EditorLayer.viewTime, time + EditorLayer.viewTime).entrySet().iterator(), Map.Entry::getKey, (list, entry)->list.addAll(entry.getValue()));
        pointIterator.addIterator(additionalDisplayObjects().descendingMap().entrySet().iterator(), Map.Entry::getKey, (list, entry)->entry.getValue().forEach((obj)->list.add((TimingPoint) obj)), 1);

        //*********************GRAPH*********************
        float graphPosition = 0, newGraphPosition; //if new position not same, draw graph line. If new position is same, just pretend this line doesn't exist, unless the next line is different.
        TimingPoint current, lastPoint;
        double maxValue = peakSV - 1, minValue = 1 - minSV;

        current = GeneralUtils.iterateListsUntilNull(pointIterator);

        sb.end();
        sr.begin(ShapeRenderer.ShapeType.Line);

        sr.setColor(green);

        //Render graph for last point
        if (current != null) {
            int drawPos = getPositionFromTime(current.getPos());
            graphPosition = svGraphPos(current, maxValue, minValue);
            if (drawPos < SettingsMaster.getWidth() && atEnd(current.getPos())) //This is the last point in map, render after line to end
            {
                sr.setColor(current.kiai ? kiai : green);

                sr.line(drawPos, midY + graphPosition,
                        SettingsMaster.getWidth(), midY + graphPosition);
            }
        }

        //SV+TIMING
        lastPoint = current;
        current = GeneralUtils.iterateListsUntilNull(pointIterator);

        while (current != null) {
            newGraphPosition = svGraphPos(current, maxValue, minValue);
            sr.setColor(current.kiai ? kiai : green);

            if (lastPoint.getPos() - current.getPos() >= SMOOTH_GRAPH_DISTANCE) //If they're too far apart, don't show a gradual change as it's kind of inaccurate.
            {
                sr.line(Math.max(0, getPositionFromTime(current.getPos())), midY + newGraphPosition,
                        Math.min(SettingsMaster.getWidth(), getPositionFromTime(lastPoint.getPos())), midY + newGraphPosition);
            } else {
                sr.line(getPositionFromTime(current.getPos()), midY + newGraphPosition,
                        getPositionFromTime(lastPoint.getPos()), midY + graphPosition);
            }

            graphPosition = newGraphPosition;
            lastPoint = current;
            current = GeneralUtils.iterateListsUntilNull(pointIterator);
        }

        //Iteration goes in reverse order, so the last point checked is the earliest point
        if (lastPoint != null && atStart(lastPoint.getPos()))
        {
            //sv at start of map before first timing point is fixed at 1x
            int pos = getPositionFromTime(lastPoint.getPos());
            if (pos > 0)
                sr.line(0, midY, pos, midY);
        }

        sr.end();
        sb.begin();
    }

    private static float volumeGraphPos(TimingPoint p) {
        return SV_AREA * ((p.volume - 50) / 50.0f);
    }
    private void renderVolumeGraph(SpriteBatch sb, ShapeRenderer sr) {
        if (map.allPoints.isEmpty())
            return;

        //Returns from first line before visible area, to first line after visible area
        MultiMergeIterator<Long, TimingPoint, ArrayList<TimingPoint>> pointIterator = new MultiMergeIterator<>(ArrayList::new, reverseLongComparator);
        pointIterator.addFilter(MapObject::testHidden); //remove hidden objects

        pointIterator.addIterator(map.getEditPoints(time - EditorLayer.viewTime, time + EditorLayer.viewTime).entrySet().iterator(), Map.Entry::getKey, (list, entry)->list.addAll(entry.getValue()));
        pointIterator.addIterator(additionalDisplayObjects().descendingMap().entrySet().iterator(), Map.Entry::getKey, (list, entry)->entry.getValue().forEach((obj)->list.add((TimingPoint) obj)), 1);

        //*********************GRAPH*********************
        float graphPosition = 0;
        TimingPoint current, lastPoint = null;
        //double  = 5, minValue = 1 - minSV;

        current = GeneralUtils.iterateListsUntilNull(pointIterator);

        sb.end();
        sr.begin(ShapeRenderer.ShapeType.Line);

        sr.setColor(yellow);

        //Render graph for last point
        if (current != null) {
            int drawPos = getPositionFromTime(current.getPos());
            graphPosition = volumeGraphPos(current);
            if (drawPos < SettingsMaster.getWidth() && atEnd(current.getPos())) //This is the last point in map, render after line to end
            {
                sr.line(drawPos, midY + graphPosition,
                        SettingsMaster.getWidth(), midY + graphPosition);
            }
        }

        //SV+TIMING
        lastPoint = current;
        current = GeneralUtils.iterateListsUntilNull(pointIterator);

        while (current != null) {
            graphPosition = volumeGraphPos(current);
            sr.setColor(current.kiai ? kiai : yellow);

            sr.line(getPositionFromTime(current.getPos()), midY + graphPosition,
                    getPositionFromTime(lastPoint.getPos()), midY + graphPosition);

            lastPoint = current;
            current = GeneralUtils.iterateListsUntilNull(pointIterator);
        }

        //Iteration goes in reverse order, so the last point checked is the earliest point
        if (lastPoint != null && atStart(lastPoint.getPos()))
        {
            //sv at start of map before first timing point is fixed at 1x
            graphPosition = volumeGraphPos(lastPoint);
            int pos = getPositionFromTime(lastPoint.getPos());
            if (pos > 0) {
                sr.setColor(yellow);
                sr.line(0, midY + graphPosition, pos, midY + graphPosition);
            }
        }

        sr.end();
        sb.begin();
    }

    private void renderWaveform(SpriteBatch sb, ShapeRenderer sr) {
        if (waveform == null || waveform.isEmpty())
            return;

        sb.end();
        sr.begin(ShapeRenderer.ShapeType.Filled);

        sr.setColor(waveformColor);

        int stepRounding = (int) Math.ceil(1 / viewScale);
        if (stepRounding % 2 == 0)
            stepRounding += 1;

        float offset = (music.activeOffset * 1000) + SettingsMaster.waveformOffset;
        int startTime = (int) (getTimeFromPosition(0) - offset);
        int endTime = (int) (getTimeFromPosition(SettingsMaster.getWidth()) - offset);

        startTime = (startTime / stepRounding) * stepRounding;
        endTime += stepRounding;

        SortedMap<Integer, Pair<Float, Float>> values = waveform.getSection(startTime, endTime);

        //Process "steps", combining steps with the same min/max into "chunks"
        //startTime: startTime of last chunk. lastMin/lastMax: min/max of last chunk.
        //nextPos: next step position. "current" position is nextPos - stepRounding

        //At the end of each "step": if min/max are the same as the previous, just increase size of chunk (increment nextPos)
        //If min/max are different from previous, draw previous chunk and start new chunk.

        int nextPos = startTime + stepRounding;
        float max = 0, min = 0, lastMin = 0, lastMax = 0;
        for (Map.Entry<Integer, Pair<Float, Float>> entry : values.entrySet()) {
            if (entry.getKey() < nextPos) { //This entry is in the current "step", track it and move on
                min = Math.min(min, entry.getValue().a);
                max = Math.max(max, entry.getValue().b);
            }
            else while (entry.getKey() >= nextPos) { //This entry is outside of the current "step". Process stuff.
                if (min != lastMin || max != lastMax) {
                    //This completed step is not the same as the "last" step
                    //So, show the last step, unless it had "no" data
                    if (lastMin != 0 || lastMax != 0) {
                        int x = getPositionFromTime(startTime + offset);
                        int end = getPositionFromTime(nextPos - stepRounding + offset);
                        //Render values obtained
                        switch (waveformMode) {
                            case 0:
                                sr.rect(x, midY + (SV_AREA * lastMin), end - x, SV_AREA * (lastMax - lastMin));
                                break;
                            case 1:
                                sr.rect(x, midY - SV_AREA, end - x, SV_AREA * (lastMax - lastMin));
                                break;
                        }
                    }

                    //Create new chunk
                    startTime = nextPos - stepRounding;
                    lastMin = min; //Store values of last step
                    lastMax = max;
                }

                //New step, set values. Testing previous is only necessary if entry is "within" current step.
                min = entry.getValue().a;
                max = entry.getValue().b;
                nextPos += stepRounding;
            }
        }
        //Render last two chunks
        if (lastMin != 0 || lastMax != 0) {
            int x = getPositionFromTime(startTime + offset);
            int end = getPositionFromTime(nextPos - stepRounding + offset);
            //Render values obtained
            switch (waveformMode) {
                case 0:
                    sr.rect(x, midY + (SV_AREA * lastMin), end - x, SV_AREA * (lastMax - lastMin));
                    break;
                case 1:
                    sr.rect(x, midY - SV_AREA, end - x, SV_AREA * (lastMax - lastMin));
                    break;
            }
        }
        if (min != 0 || max != 0) {
            int x = getPositionFromTime(nextPos + offset);
            if (x < SettingsMaster.getWidth()) {
                //Render values obtained
                switch (waveformMode) {
                    case 0:
                        sr.rect(x, midY + (SV_AREA * lastMin), SettingsMaster.getWidth() - x, SV_AREA * (lastMax - lastMin));
                        break;
                    case 1:
                        sr.rect(x, midY - SV_AREA, SettingsMaster.getWidth() - x, SV_AREA * (lastMax - lastMin));
                        break;
                }
            }
        }

        sr.end();
        sb.begin();
    }

    private void renderValueLabels(SpriteBatch sb, TimingPoint focusPoint) {
        long lastRenderable = focusPoint == null ? 0 : focusPoint.getPos() + (long)(LABEL_SPACING / viewScale);
        double svLabelSpacing = 0;
        double bpmLabelSpacing = 0;

        MultiMergeIterator<Long, TimingPoint, ArrayList<TimingPoint>> pointIterator = new MultiMergeIterator<>(ArrayList::new, 3, reverseLongComparator);
        pointIterator.addFilter(MapObject::testHidden); //remove hidden objects

        pointIterator.addIterator(map.getEditPoints(time - EditorLayer.viewTime, time + EditorLayer.viewTime).entrySet().iterator(), Map.Entry::getKey, (list, entry)->list.addAll(entry.getValue()));
        pointIterator.addIterator(additionalDisplayObjects().descendingMap().entrySet().iterator(), Map.Entry::getKey, (list, entry)->entry.getValue().forEach((obj)->list.add((TimingPoint) obj)), 1);

        if (!pointIterator.hasNext())
            return;

        ArrayList<TimingPoint> stack, previous = null;
        long key, previousKey;
        stack = pointIterator.next();
        previousKey = key = pointIterator.getLastKey();

        TimingPoint effect, timing;

        while (stack != null) {
            if (focusPoint != null) {
                if (key < lastRenderable - (long)(LABEL_SPACING / viewScale))
                    focusPoint = null; //Passed the focus point somehow, clear it
            }

            timing = null;
            effect = null;

            int stackX = (int) (SettingsMaster.getMiddleX() + (key - preciseTime) * viewScale + 4);

            if (previous != null) {
                bpmLabelSpacing -= (previousKey - key) * viewScale;
                svLabelSpacing -= (previousKey - key) * viewScale;
            }

            if (focusPoint != null && key <= lastRenderable) { //Adjust point not null and within range of it.
                for (TimingPoint point : stack) {
                    if (point.uninherited) {
                        //if adjustment point is not red (so this doesn't matter), or this is the adjustment point
                        if (!focusPoint.uninherited || focusPoint.equals(point)) timing = point;
                    }
                    else {
                        //if this is the adjustment point
                        if (focusPoint.uninherited || focusPoint.equals(point)) effect = point;
                    }
                }


                if (effect != null && svLabelSpacing <= 0) {
                    if (effect.equals(focusPoint)) {
                        renderAdjustableLabel(sb, effect, stackX, bottom + TOP_VALUE_Y);
                        focusPoint = null;
                    }
                    else {
                        renderLabel(sb, twoDecimal.format(effect.value), stackX, bottom + TOP_VALUE_Y);
                    }
                    svLabelSpacing = LABEL_SPACING;
                }

                if (timing != null && bpmLabelSpacing <= 0) {
                    if (timing.equals(focusPoint)) {
                        renderAdjustableLabel(sb, timing, stackX, bottom + BOTTOM_VALUE_Y);
                        focusPoint = null;

                        //If svLabelSpacing <= 0, effect must be null. Render a label, the adjust point isn't sv.
                        if (svLabelSpacing <= 0) {
                            renderLabel(sb, twoDecimal.format(1), stackX, bottom + TOP_VALUE_Y);
                            svLabelSpacing = LABEL_SPACING;
                        }
                    }
                    else {
                        renderLabel(sb, bpmFormat.format(timing.getBPM()), stackX, bottom + BOTTOM_VALUE_Y);
                        //For a non-adjust point timing point to be rendered, the adjust point is an effect point.
                        //Do not render an effect point position unless effect is not null, which is handled.
                    }
                    bpmLabelSpacing = LABEL_SPACING;
                }
            }
            else {
                for (int i = stack.size() - 1; i >= 0 && (timing == null || effect == null); --i) {
                    TimingPoint point = stack.get(i);
                    if (point.uninherited && timing == null)
                        timing = point;
                    else if (!point.uninherited && effect == null)
                        effect = point;
                }

                if (effect != null && svLabelSpacing <= 0)
                {
                    renderLabel(sb, twoDecimal.format(effect.value), stackX, bottom + TOP_VALUE_Y);
                    svLabelSpacing = LABEL_SPACING;
                }

                if (timing != null && bpmLabelSpacing <= 0)
                {
                    renderLabel(sb, bpmFormat.format(timing.getBPM()),
                            stackX, bottom + BOTTOM_VALUE_Y);
                    bpmLabelSpacing = LABEL_SPACING;

                    if (svLabelSpacing <= 0)
                    {
                        renderLabel(sb, twoDecimal.format(1), stackX, bottom + TOP_VALUE_Y);
                        svLabelSpacing = LABEL_SPACING;
                    }
                }
            }

            previous = stack;
            previousKey = key;

            if (pointIterator.hasNext()) {
                stack = pointIterator.next();
                key = pointIterator.getLastKey();
            }
            else {
                stack = null;
            }
        }
    }

    private void renderVolumeLabels(SpriteBatch sb, TimingPoint adjust) {
        long lastRenderable = adjust == null ? 0 : adjust.getPos() + (long)(LABEL_SPACING / viewScale);
        double volumeLabelSpacing = 0;
        double bpmLabelSpacing = 0;

        MultiMergeIterator<Long, TimingPoint, ArrayList<TimingPoint>> pointIterator = new MultiMergeIterator<>(ArrayList::new, 3, reverseLongComparator);
        pointIterator.addFilter(MapObject::testHidden); //remove hidden objects

        pointIterator.addIterator(map.getEditPoints(time - EditorLayer.viewTime, time + EditorLayer.viewTime).entrySet().iterator(), Map.Entry::getKey, (list, entry)->list.addAll(entry.getValue()));
        pointIterator.addIterator(additionalDisplayObjects().descendingMap().entrySet().iterator(), Map.Entry::getKey, (list, entry)->entry.getValue().forEach((obj)->list.add((TimingPoint) obj)), 1);
        
        if (!pointIterator.hasNext())
            return;

        ArrayList<TimingPoint> stack, previous = null;
        long key, previousKey;
        stack = pointIterator.next();
        previousKey = key = pointIterator.getLastKey();

        TimingPoint effect, timing;

        while (stack != null) {
            if (adjust != null) {
                if (key < lastRenderable - (long)(LABEL_SPACING / viewScale))
                    adjust = null; //Passed the adjust point somehow, clear it.
            }

            timing = null;
            effect = null;

            int stackX = (int) (SettingsMaster.getMiddleX() + (key - preciseTime) * viewScale + 4);

            if (previous != null) {
                bpmLabelSpacing -= (previousKey - key) * viewScale;
                volumeLabelSpacing -= (previousKey - key) * viewScale;
            }

            if (adjust != null && key <= lastRenderable) { //Adjust point not null and within range of it.
                for (TimingPoint point : stack) {
                    if (point.uninherited) {
                        //if adjustment point is not red (so this doesn't matter), or this is the adjustment point
                        if (!adjust.uninherited || adjust.equals(point)) timing = point;
                    }
                    else {
                        //if this is the adjustment point
                        if (adjust.equals(point)) effect = point;
                    }
                }

                if (timing != null && bpmLabelSpacing <= 0) {
                    renderLabel(sb, bpmFormat.format(timing.getBPM()), stackX, bottom + BOTTOM_VALUE_Y);
                    bpmLabelSpacing = LABEL_SPACING;
                }
                if (effect != null || adjust.equals(timing)) {
                    renderAdjustableLabel(sb, adjust, stackX, bottom + TOP_VALUE_Y);
                    volumeLabelSpacing = LABEL_SPACING;
                    adjust = null;
                }
            }
            else {
                for (int i = stack.size() - 1; i >= 0 && (timing == null || effect == null); --i) {
                    TimingPoint point = stack.get(i);
                    if (point.uninherited && timing == null)
                        timing = point;
                    else if (!point.uninherited && effect == null)
                        effect = point;
                }

                if (timing != null && bpmLabelSpacing <= 0)
                {
                    renderLabel(sb, bpmFormat.format(timing.getBPM()),
                            stackX, bottom + BOTTOM_VALUE_Y);
                    bpmLabelSpacing = LABEL_SPACING;

                    if (effect == null && volumeLabelSpacing <= 0)
                    {
                        renderLabel(sb, volume.format(timing.volume), stackX, bottom + TOP_VALUE_Y);
                        volumeLabelSpacing = LABEL_SPACING;
                    }
                }

                if (effect != null && volumeLabelSpacing <= 0)
                {
                    renderLabel(sb, volume.format(effect.volume), stackX, bottom + TOP_VALUE_Y);
                    volumeLabelSpacing = LABEL_SPACING;
                }
            }

            previous = stack;
            previousKey = key;

            if (pointIterator.hasNext()) {
                stack = pointIterator.next();
                key = pointIterator.getLastKey();
            }
            else {
                stack = null;
            }
        }
    }

    public void renderLabel(SpriteBatch sb, String text, int x, int y) {
        textRenderer.setFont(font).renderText(sb, text, x, y, Color.WHITE);
    }
    private void renderAdjustableLabel(SpriteBatch sb, TimingPoint p, int x, int y) {
        if (adjustMode == AdjustMode.ACTIVE && p.equals(adjustPoint)) {
            textRenderer.setFont(font).renderText(sb, textInput, x, y, Color.WHITE);

            if (renderBlip) {
                sb.draw(pix, x + blipOffsetX, y - 10, BLIP_WIDTH, BLIP_HEIGHT);
            }
            return;
        }
        if (p.uninherited) {
            textRenderer.setFont(font).renderText(sb, mode ? bpmFormat.format(p.getBPM()) : volume.format(p.volume), x, y, Color.WHITE);
        }
        else {
            textRenderer.setFont(font).renderText(sb, mode ? twoDecimal.format(p.value) : volume.format(p.volume), x, y, Color.WHITE);
        }
    }

    //if time is at/more extreme than the start/end of map
    private boolean atStart(long time) {
        if (map.effectPoints.isEmpty() && map.timingPoints.isEmpty())
            return false;
        else if (map.effectPoints.isEmpty()) {
            return time <= map.timingPoints.firstKey();
        }
        else if (map.timingPoints.isEmpty()) {
            return time <= map.effectPoints.firstKey();
        }
        return time <= Math.min(map.timingPoints.firstKey(), map.effectPoints.firstKey());
    }
    private boolean atEnd(long time) {
        if (map.effectPoints.isEmpty() && map.timingPoints.isEmpty())
            return false;
        else if (map.effectPoints.isEmpty()) {
            return time >= map.timingPoints.lastKey();
        }
        else if (map.timingPoints.isEmpty()) {
            return time >= map.effectPoints.lastKey();
        }
        return time >= Math.min(map.timingPoints.lastKey(), map.effectPoints.lastKey());
    }

    @Override
    public void renderStack(ArrayList<? extends MapObject> objects, SpriteBatch sb, ShapeRenderer sr) {
        if (objects.isEmpty())
            return;

        MapObject selected = null, mainObj = GeneralUtils.listLast(objects);
        int type = 0;
        TimingPoint maybeOmitted = null;
        for (MapObject o : objects) {
            if (o.selected)
                selected = o;

            if (((TimingPoint) o).uninherited) {
                type |= 1;
                maybeOmitted = (TimingPoint) o;
            }
            else {
                type |= 2;
            }
        }
        if (!timingEnabled && map.timingPoints.containsKey(mainObj.getPos())) {
            type |= 1;
        }
        switch (type) {
            case 1:
                ((TimingPoint) mainObj).renderColored(sb, sr, preciseTime, viewScale, SettingsMaster.getMiddleX(), bottom, red, 1);
                break;
            case 3:
                ((TimingPoint) mainObj).renderColored(sb, sr, preciseTime, viewScale, SettingsMaster.getMiddleX(), bottom, yellow, 1);
                break;
            default:
                ((TimingPoint) mainObj).renderColored(sb, sr, preciseTime, viewScale, SettingsMaster.getMiddleX(), bottom, green, 1);
                break;
        }
        if (maybeOmitted != null) {
            maybeOmitted.renderOmittedMark(sb, SettingsMaster.getMiddleX(), bottom, preciseTime, viewScale, 1);
        }
        if (selected != null)
            renderSelection(selected, sb, sr);
    }

    @Override
    public void renderObject(MapObject o, SpriteBatch sb, ShapeRenderer sr, float alpha) {
        if (o instanceof TimingPoint) {
            if (((TimingPoint) o).uninherited) { //red line rendered through this method = red
                ((TimingPoint) o).renderColored(sb, sr, preciseTime, viewScale, SettingsMaster.getMiddleX(), bottom, red, alpha);
            }
            else if (map.timingPoints.containsKey(o.getPos())) { //A red line exists at this point, rendering a green line = yellow
                ((TimingPoint) o).renderColored(sb, sr, preciseTime, viewScale, SettingsMaster.getMiddleX(), bottom, yellow, alpha);
            }
            else { //Let the line decide its own color. Should be green.
                o.render(sb, sr, preciseTime, viewScale, SettingsMaster.getMiddleX(), bottom, alpha);
            }
        }
        else {
            o.render(sb, sr, preciseTime, viewScale, SettingsMaster.getMiddleX(), bottom, alpha);
        }
    }

    @Override
    public void renderSelection(MapObject o, SpriteBatch sb, ShapeRenderer sr) {
        o.renderSelection(sb, sr, preciseTime, viewScale, SettingsMaster.getMiddleX(), bottom);
    }

    @Override
    public void pasteObjects(ViewType copyType, MapObjectTreeMap<MapObject> copyObjects) {
        endAdjust();
        long offset, targetPos;

        offset = time - copyObjects.firstKey();

        MapObjectTreeMap<MapObject> placementCopy = new MapObjectTreeMap<>();

        if (copyType == type) {
            Map<MapObject, MapObject> copyToOriginal = new HashMap<>();

            for (Map.Entry<Long, ArrayList<MapObject>> entry : copyObjects.entrySet())
            {
                targetPos = entry.getKey() + offset;
                for (MapObject o : entry.getValue())
                {
                    MapObject cpy = o.shiftedCopy(targetPos);
                    placementCopy.add(cpy);
                    copyToOriginal.put(cpy, o);
                }
            }

            this.map.registerAndPerformAddObjects("Paste Lines", placementCopy, copyToOriginal, replaceTest);
        }
        else {
            for (Map.Entry<Long, ArrayList<MapObject>> entry : copyObjects.entrySet())
            {
                targetPos = entry.getKey() + offset;

                Map.Entry<Long, ArrayList<TimingPoint>> stack = map.allPoints.floorEntry(targetPos);

                MapObject asLine = stack == null ? new TimingPoint(targetPos) : ((TimingPoint) GeneralUtils.listLast(stack.getValue()).shiftedCopy(targetPos)).inherit();
                placementCopy.add(asLine);
            }
            map.registerAndPerformAddObjects("Paste ? As Lines", placementCopy, null, replaceTest);
        }
    }

    @Override
    public void reverse() {
        if (!hasSelection())
            return;

        endAdjust();
        this.map.registerReverse(false, getSelection());
    }

    @Override
    public void invert() {
        if (!hasSelection())
            return;

        MapObjectTreeMap<MapObject> toInvert = getSelection(true);
        MapObjectTreeMap<MapObject> replacement = new MapObjectTreeMap<>();


        //Generate an inherited copy of the closest previous timing point
        Map.Entry<Long, ArrayList<TimingPoint>> lastEffectStack = map.effectPoints.floorEntry(toInvert.firstKey());
        Map.Entry<Long, ArrayList<TimingPoint>> lastTimingStack = map.timingPoints.floorEntry(toInvert.firstKey());

        if (lastEffectStack == null) {
            lastEffectStack = lastTimingStack;
        }
        else if (lastTimingStack != null) {
            if (lastTimingStack.getKey() > lastEffectStack.getKey())
                lastEffectStack = lastTimingStack;
        }

        TimingPoint lastEffect = lastEffectStack == null ? new TimingPoint(0) : GeneralUtils.listLast(lastEffectStack.getValue());
        TimingPoint lastTiming = lastTimingStack == null ? new TimingPoint(0).uninherit() : GeneralUtils.listLast(lastTimingStack.getValue());

        for (Map.Entry<Long, ArrayList<MapObject>> stack : toInvert.entrySet()) {
            for (MapObject obj : stack.getValue()) {
                if (obj instanceof TimingPoint) {
                    if (((TimingPoint) obj).uninherited) { //red line -> green line
                        lastEffect = ((TimingPoint) lastEffect.shiftedCopy(obj.getPos())).inherit();
                        replacement.add(lastEffect);
                    }
                    else { //green line -> red line
                        lastTiming = (TimingPoint) lastTiming.shiftedCopy(obj.getPos());
                        lastEffect = lastTiming;
                        replacement.add(lastTiming);
                    }
                }
            }
        }

        map.registerChange(new MapObjectChange(map, "Invert Lines", toInvert, replacement).preDo());
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends MapObject>> getVisibleRange(long start, long end) {
        NavigableMap<Long, ? extends ArrayList<? extends MapObject>> source = prep();

        if (source.isEmpty())
            return null;

        start = Math.max(start, source.lastKey());
        end = Math.min(end, source.firstKey());

        if (start >= end)
            return null;

        return source.subMap(end, true, start, true);
    }

    @Override
    public String getSelectionString() {
        return "";
    }

    @Override
    public void updateVerticalDrag(MapObject dragObject, MapObjectTreeMap<MapObject> copyObjects, HashMap<MapObject, MapObject> copyMap, double adjustment) {
        //copyMap is original -> copy
        if (mode) { //sv mode
            double svAdjust = dragObject.getValue() - (adjustment / 20.0); //calculate basic adjustment on original object
            if (!BindingGroup.alt()) { //round if not holding alt
                svAdjust = Math.round(svAdjust * 100.0) / 100.0; //ew
            }
            svAdjust -= dragObject.getValue(); //convert from "new value" to "difference"

            for (Map.Entry<MapObject, MapObject> copyPair : copyMap.entrySet()) {
                if (copyPair.getValue() instanceof TimingPoint && !(((TimingPoint) copyPair.getValue()).uninherited)) {
                    double newVal = Math.max(TimingPoint.MIN_SV, copyPair.getKey().getValue() + svAdjust);
                    copyPair.getValue().setValue(newVal);
                }
            }
        }
        else {
            for (Map.Entry<MapObject, MapObject> copyPair : copyMap.entrySet()) {
                if (copyPair.getValue() instanceof TimingPoint && !(((TimingPoint) copyPair.getValue()).uninherited)) {
                    copyPair.getValue().setVolume(MathUtils.clamp((int) (copyPair.getKey().getVolume() - Math.round(adjustment * 1.5)), 1, 100));
                }
            }
        }

        //Proper update will occur on release.
        map.updateLines(getSelection().entrySet(), null, false);
    }

    @Override
    public void deleteObject(MapObject o) {
        endAdjust();
        super.deleteObject(o);
    }
    @Override
    public void deleteSelection() {
        MapObjectTreeMap<MapObject> selection = getSelection(true);
        if (selection != null)
        {
            endAdjust();
            this.map.registerAndPerformDelete(selection);
            clearSelection();
        }
    }
    @Override
    public void registerMove(long totalMovement) {
        if (hasSelection())
        {
            map.regenerateDivisor();
            if (totalMovement != 0) {
                this.map.registerAndPerformObjectMovement(getSelection(true), totalMovement);
            }
        }
    }
    @Override
    public void registerValueChange(HashMap<MapObject, MapObject> copyMap) {
        if (hasSelection())
        {
            Map<MapObject, Double> newValueMap = new HashMap<>();
            MapObjectTreeMap<MapObject> adjustCopy = new MapObjectTreeMap<>();

            getSelection().forEachObject((obj)->{
                MapObject cpy = copyMap.get(obj);
                if (cpy != null && obj instanceof TimingPoint) {
                    newValueMap.put(obj, cpy.getValue());
                    adjustCopy.add(obj);
                }
            });
            this.map.registerAndPerformValueChange(adjustCopy, newValueMap);
        }
    }
    public void registerVolumeChange(HashMap<MapObject, MapObject> copyMap) {
        if (hasSelection())
        {
            Map<Long, Integer> newVolumeMap = new HashMap<>();

            MapObjectTreeMap<MapObject> adjustCopy = new MapObjectTreeMap<>();
            getSelection().forEachObject((obj)->{
                MapObject cpy = copyMap.get(obj);
                if (cpy != null && obj instanceof TimingPoint) {
                    newVolumeMap.put(obj.getPos(), cpy.getVolume());
                    //((TimingPoint) obj).tempSetVolume(cpy.getVolume());
                }
                adjustCopy.add(obj);
            }); //Change to registerAndPerformVolumeChange

            this.map.registerAndPerformVolumeChange(adjustCopy, newVolumeMap);
        }
    }

    public void fuckSelection() {
        if (hasSelection())
        {
            float variance = 0.01f; //1% variance

            Map<MapObject, Double> newValueMap = new HashMap<>();
            MapObjectTreeMap<MapObject> adjustCopy = new MapObjectTreeMap<>();

            getSelection().forEachObject((obj)->{
                if (!((TimingPoint) obj).uninherited) {
                    newValueMap.put(obj, obj.getValue() * (1 + (variance * (Math.random() - 0.5f) * 2.0f)));
                    adjustCopy.add(obj);
                }
            });

            map.registerAndPerformValueChange(adjustCopy, newValueMap);
        }
    }

    @Override
    public void select(MapObject p) {
        super.select(p);

        if (adjustMode == AdjustMode.ACTIVE && adjustPoint != null) {
            endAdjust();
        }
    }

    @Override
    public void selectAll() {
        endAdjust();
        clearSelection();

        if (timingEnabled && effectPointsEnabled)
            selectObjects(map.allPoints);
        else if (effectPointsEnabled)
            selectObjects(map.effectPoints);
        else if (timingEnabled)
            selectObjects(map.timingPoints);
    }

    @Override
    public void addSelectionRange(long startTime, long endTime) {
        endAdjust();
        if (startTime == endTime)
            return;

        MapObjectTreeMap<TimingPoint> src;
        if (timingEnabled && effectPointsEnabled)
            src = map.allPoints;
        else if (timingEnabled)
            src = map.timingPoints;
        else
            src = map.effectPoints;

        MapObjectTreeMap<MapObject> newSelection = new MapObjectTreeMap<>();
        if (startTime > endTime)
            newSelection.addAll(src.descendingSubMap(endTime, true, startTime, true));
        else
            newSelection.addAll(src.descendingSubMap(startTime, true, endTime, true));

        selectObjects(newSelection);
    }

    @Override
    public void movingObjects() {
        endAdjust();
    }
    @Override
    public void dragRelease() {
        if (adjustMode == AdjustMode.POSSIBLE && hasSelection() && adjustPoint != null) {
            startAdjust(adjustPoint, false);
        }
        else {
            endAdjust();
        }
    }

    @Override
    public MapObject clickObject(float x, float y) {
        endAdjust();
        return getObjectAt(x, y);
    }
    public MapObject getObjectAt(float x, float y) {
        //Check if y location is on sv label area
        //If so, allow wider x area for clicking.
        NavigableMap<Long, ? extends ArrayList<? extends MapObject>> selectable = prep();
        if (selectable == null || y < bottom || y > top)
            return null;

        double time = getTimeFromPosition(x);

        boolean lowerSelection = (y > bottom + BPM_LABEL_BOTTOM && y < bottom + BPM_LABEL_TOP),
                higherSelection = (y > bottom + VALUE_LABEL_BOTTOM && y < bottom + VALUE_LABEL_TOP);
        if (lowerSelection || higherSelection) {
            adjustMode = AdjustMode.POSSIBLE;
        }

        if (selectable.containsKey((long) time)) {
            ArrayList<? extends MapObject> selectableObjects = selectable.get((long) time);
            if (selectableObjects.isEmpty())
            {
                editorLogger.error("WTF? Empty arraylist of objects in object map.");
            }
            else
            {
                return adjustPoint = (TimingPoint) selectableObjects.get(selectableObjects.size() - 1);
            }
        }

        Map.Entry<Long, ? extends ArrayList<? extends MapObject>> lower = selectable.higherEntry((long) time);
        Map.Entry<Long, ? extends ArrayList<? extends MapObject>> higher = selectable.lowerEntry((long) time);
        double higherDist, lowerDist;
        double adjustLimit = adjustMode == AdjustMode.POSSIBLE ? ADJUST_DIST : SELECTION_DIST;

        if (lower == null && higher == null)
            return adjustPoint = null;
        else if (lower == null)
        {
            higherDist = higher.getKey() - time;
            lowerDist = Integer.MAX_VALUE;
        }
        else if (higher == null)
        {
            lowerDist = time - lower.getKey();
            higherDist = Integer.MAX_VALUE;
        }
        else
        {
            //Neither are null. Determine which one is closer.
            lowerDist = time - lower.getKey();
            higherDist = higher.getKey() - time;
        }

        lowerDist *= viewScale;
        higherDist *= viewScale;

        //Check the closer objects first.
        if (lower != null && lowerDist < higherDist)
        {
            TimingPoint point = (TimingPoint) GeneralUtils.listLast(lower.getValue());
            //lower distance is within selection range.
            if (point != null &&
                (lowerDist < SELECTION_DIST ||
                    (lowerDist < adjustLimit &&
                        ((point.uninherited && lowerSelection) || (!point.uninherited && higherSelection))
                    )
                )
            ) {
                return adjustPoint = point;
            }
            //No need to check higher; if lower is not in a possibly greater range and higher one is farther,
            //it is definitely not in range
        }
        else if (higher != null && higherDist < lowerDist)
        {
            if (higherDist < SELECTION_DIST) {
                return adjustPoint = (TimingPoint) GeneralUtils.listLast(higher.getValue());
            }

            if (lower != null)
            {
                TimingPoint point = (TimingPoint) GeneralUtils.listLast(lower.getValue());
                if (point != null && lowerDist < adjustLimit &&
                        ((point.uninherited && lowerSelection) ||
                        (!point.uninherited && higherSelection))) {
                    return adjustPoint = point;
                }
            }
        }
        return adjustPoint = null;
    }

    @Override
    public boolean clickedEnd(MapObject o, float x) {
        return false;
    }

    @Override
    public boolean rightClick(float x, float y) {
        MapObject close = clickObject(x, y);

        if (close != null) {
            if (close.selected && hasSelection()) {
                if (adjustMode == AdjustMode.POSSIBLE && close instanceof TimingPoint) {
                    startAdjust((TimingPoint) close, true);
                }
                else {
                    deleteSelection();
                    clearSelection();
                }
            }
            else {
                if (adjustMode == AdjustMode.POSSIBLE && close instanceof TimingPoint) {
                    clearSelection();
                    select(close);
                    startAdjust((TimingPoint) close, true);
                }
                else {
                    deleteObject(close);
                    clearSelection();
                }
            }
            return true;
        }
        return false;
    }

    public void testNewSvLimit(double newLimit)
    {
        if (newLimit > peakSV)
        {
            peakSV = newLimit;
            peakSVText = svFormat.format(peakSV);
        }
        else if (newLimit < minSV)
        {
            minSV = newLimit;
            minSVText = svFormat.format(newLimit);
        }
    }

    public void recheckSvLimits()
    {
        peakSV = 1.3;
        minSV = 0.75;
        for (ArrayList<TimingPoint> points : map.effectPoints.values())
        {
            for (TimingPoint p : points)
            {
                if (p.value > peakSV)
                    peakSV = p.value;
                if (p.value < minSV)
                    minSV = p.value;
            }
        }
        for (ArrayList<MapObject> points : additionalDisplayObjects().values())
        {
            for (MapObject o : points)
            {
                if (!(o instanceof TimingPoint)) continue;

                TimingPoint p = (TimingPoint) o;

                if (!p.uninherited) {
                    if (p.value > peakSV)
                        peakSV = p.value;
                    if (p.value < minSV)
                        minSV = p.value;
                }
            }
        }
        peakSVText = svFormat.format(peakSV);
        minSVText = svFormat.format(minSV);
    }

    private final Toolset tools = new Toolset();
    public Toolset getToolset()
    {
        return tools;
    }
    private void updateToolset() {
        tools.clear();
        tools.addTool(SelectionTool.get());

        if (timingEnabled)
            tools.addTool(RedLineTool.get());

        if (effectPointsEnabled)
            tools.addTool(GreenLineTool.get());

        if (effectPointsEnabled)
            tools.addTool(mode ? SVFunctionTool.get() : VolumeFunctionTool.get());

        if (effectPointsEnabled || timingEnabled)
            tools.addTool(KiaiTool.get());

        if (timingEnabled)
            tools.addTool(OmitBarlineTool.get());

        if (parent != null && parent.tools != null)
            parent.tools.changeToolset(this);
    }

    //text input
    private String textInput = "";
    private static final float BLIP_BUFFER = 2;
    private static final float BLIP_WIDTH = 2;
    private static final float BLIP_HEIGHT = 11;
    private float blipOffsetX = 0;
    private boolean renderBlip;
    private float blipTimer = 0;

    private boolean changed = false;

    private void startAdjust(TimingPoint p, boolean clearValue) {
        adjustPoint = p;
        adjustMode = AdjustMode.ACTIVE;
        textInput = clearValue ? "" : (mode ? p.valueText(precise) : volume.format(p.volume));
        blipOffsetX = textRenderer.setFont(font).getWidth(textInput) + BLIP_BUFFER;
        renderBlip = true;
        blipTimer = 0.4f;
        changed = false;
        EditorLayer.processor.setTextReceiver(this);
    }
    private void endAdjust() {
        if (adjustMode == AdjustMode.ACTIVE && adjustPoint != null && changed && hasSelection()) {
            try {
                MapObjectTreeMap<MapObject> changeObjects = getSelection();
                if (mode) {
                    double newValue = Double.parseDouble(textInput);
                    if (adjustPoint.uninherited)
                        newValue = 60000 / newValue;

                    if (newValue <= 0) {
                        parent.showText("Invalid value entered.");
                    }
                    else {
                        MapObjectTreeMap<MapObject> adjustCopy = new MapObjectTreeMap<>();
                        adjustCopy.addAll(changeObjects);
                        //Only keep points of the same uninherited state
                        adjustCopy.removeIf((p)->(!(p instanceof TimingPoint) || (((TimingPoint) p).uninherited != adjustPoint.uninherited)));
                        map.registerChange(new ValueSetChange(map, adjustCopy, newValue).preDo());
                    }
                }
                else {
                    int nVol = Integer.parseInt(textInput);

                    if (nVol < 1 || nVol > 100) {
                        parent.showText("Invalid value entered.");
                    }
                    else {
                        map.registerChange(new VolumeSetChange(map, map.getStackedObjects(changeObjects, map.allPoints), nVol).preDo());
                    }
                }
            }
            catch (Exception e) {
                parent.showText("Failed to parse new value.");
            }
        }

        EditorLayer.processor.disableTextReceiver(this);
        adjustMode = AdjustMode.NONE;
        adjustPoint = null;
    }

    @Override
    public boolean acceptCharacter(char c) {
        return (c == 8) || (c == '\r') || (c == '\n') ||
                (c >= '0' && c <= '9') ||
                (mode && (c == ',' || c == '.') && GeneralUtils.charCount(textInput, '.') < 1) ||
                (mode && (c == 'e' || c == 'E') && GeneralUtils.charCount(textInput, 'E') < 1);
    }

    @Override
    public int getCharLimit() {
        return mode || (adjustPoint != null && adjustPoint.uninherited) ? 12 : 3;
    }

    @Override
    public String getInitialText() {
        return textInput;
    }

    @Override
    public void setText(String newText) {
        changed = true;
        this.textInput = newText.replaceAll(",", ".").replaceAll("e", "E");
        blipOffsetX = textRenderer.setFont(font).getWidth(textInput) + BLIP_BUFFER;
    }

    @Override
    public boolean blockInput(int key) {
        return (key > Input.Keys.NUM_0 && key <= Input.Keys.NUM_9) ||
                (key > Input.Keys.NUMPAD_0 && key <= Input.Keys.NUMPAD_9) ||
                key == Input.Keys.PERIOD || key == Input.Keys.COMMA ||
                key == Input.Keys.BACKSPACE;
    }

    @Override
    public BitmapFont getFont() {
        return font;
    }

    @Override
    public Function<String, Boolean> onPressEnter() {
        return this::onEnter;
    }
    public boolean onEnter(String text) {
        onMain(this::endAdjust);
        return false;
    }

    public static final DecimalFormat twoDecimal = new DecimalFormat("##0.##x", osuDecimalFormat);
    private static final DecimalFormat bpmFormat = new DecimalFormat("##0.## BPM", osuDecimalFormat);
    private static final DecimalFormat precise = new DecimalFormat("##0.0##", osuDecimalFormat);
    private static final DecimalFormat volume = new DecimalFormat("##0", osuDecimalFormat);

    private static final Color red = Color.RED.cpy();
    private static final Color green = new Color(0.25f, 0.75f, 0.0f, 1.0f);
    private static final Color yellow = new Color(0.8f, 0.8f, 0.0f, 1.0f);
}
