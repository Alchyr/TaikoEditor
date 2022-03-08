package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.input.sub.TextInputReceiver;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.MapChange;
import alchyr.taikoedit.editor.changes.ValueSetChange;
import alchyr.taikoedit.editor.changes.VolumeSetChange;
import alchyr.taikoedit.editor.tools.*;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.GeneralUtils;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Function;

import static alchyr.taikoedit.TaikoEditor.*;
import static alchyr.taikoedit.core.layers.EditorLayer.viewScale;

public class EffectView extends MapView implements TextInputReceiver {
    //Kiai, sv
    //SV can be represented using a line graph?
    //Drag line graph to quickly adjust sv?
    //Select a section to do effects like interpolation (smooth line) or something similar?

    public static final int HEIGHT = 300;
    public static final int SV_AREA = (HEIGHT - 80) / 2;

    private static final int VALUE_LABEL_BOTTOM = 245;
    private static final int VALUE_LABEL_TOP = 265;

    //Selection
    private static final int SELECTION_DIST = 30;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.7f);
    private static final Color lineColor = new Color(0.35f, 0.35f, 0.38f, 0.4f);
    private static final Color kiai = new Color(240.0f/255.0f, 164.0f/255.0f, 66.0f/255.0f, 1.0f);

    private SortedMap<Long, Snap> activeSnaps;

    //Positions
    private int baseMidY;

    //Offset positions
    private int midY;

    public boolean mode = true; //true = sv, false = volume

    //SV Graph
    private static final int SMOOTH_GRAPH_DISTANCE = 300; //Further apart than this, no smooth graph
    private static final int LABEL_SPACING = 36;
    private final DecimalFormat svFormat = new DecimalFormat("0.000x", osuSafe);
    private double peakSV, minSV;
    private String peakSVText, minSVText;
    private boolean renderLabels = true; //TODO: Add way to disable labels. Toggle button on overlay?

    //Sv values
    private final BitmapFont font;

    //Hitsounds of map
    private double lastSounded;

    public EffectView(EditorLayer parent, EditorBeatmap beatmap) {
        super(ViewType.EFFECT_VIEW, parent, beatmap, HEIGHT);

        addOverlayButton(new ImageButton(assetMaster.get("editor:exit"), assetMaster.get("editor:exith")).setClick(this::close).setAction("Close View"));
        addOverlayButton(new ImageButton(assetMaster.get("editor:mode"), assetMaster.get("editor:modeh")).setClick(this::swapMode).setAction("Swap Modes"));

        font = assetMaster.getFont("aller small");

        allowVerticalDrag = true;

        lastSounded = 0;
        peakSV = 1.3;
        minSV = 0.75;
        recheckSvLimits();
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
        SelectionTool.get().effectMode = mode;
        parent.tools.changeToolset(this);
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
    public double getTimeFromPosition(float x) {
        return getTimeFromPosition(x, SettingsMaster.getMiddle());
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> prep(long pos) {
        //return graph points
        return map.getEditEffectPoints(pos - EditorLayer.viewTime, pos + EditorLayer.viewTime);
    }

    @Override
    public void update(double exactPos, long msPos, float elapsed) {
        super.update(exactPos, msPos, elapsed);
        activeSnaps = map.getActiveSnaps(time - EditorLayer.viewTime, time + EditorLayer.viewTime);

        blipTimer -= elapsed;
        if (blipTimer <= 0) {
            blipTimer = 0.4f;
            renderBlip = !renderBlip;
        }

        if (adjustMode == AdjustMode.ACTIVE && !parent.tools.getCurrentTool().equals(getToolset().getDefaultTool())) {
            endAdjust();
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
        if (isPrimary && isPlaying && lastSounded < time && parent.getViewSet(map).contains((o)->o.type == ViewType.OBJECT_VIEW)) //might have skipped backwards
        {
            for (ArrayList<HitObject> objects : map.objects.subMap((long) lastSounded, false, (long) time, true).values())
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

        TimingPoint t;
        for (ArrayList<TimingPoint> points : map.getVisibleTimingPoints((long) time - EditorLayer.viewTime, (long) time + EditorLayer.viewTime).values()) {
            t = GeneralUtils.listLast(points);

            if (!map.effectPoints.containsKey(t.getPos())) {
                renderObject(t, sb, sr, 1);
            }
        }
    }

    @Override
    public void renderOverlay(SpriteBatch sb, ShapeRenderer sr) {
        //Snaps go on top of the lines for this one.
        for (Snap s : activeSnaps.values())
        {
            s.halfRender(sb, sr, time, viewScale, SettingsMaster.getMiddle(), bottom, 40);
        }

        if (mode) {
            //sv
            renderSVGraph(sb, sr);
            textRenderer.setFont(font).renderText(sb, Color.WHITE, peakSVText, 0, midY + SV_AREA + 10);
            textRenderer.renderText(sb, minSVText, 0, midY - SV_AREA - 1);
        }
        else {
            //volume
            renderVolumeGraph(sb, sr);
            textRenderer.setFont(font).renderText(sb, Color.WHITE, "100", 0, midY + SV_AREA + 10);
            textRenderer.renderText(sb, "0", 0, midY - SV_AREA - 1);
        }

        super.renderOverlay(sb, sr);
    }

    private void renderSVGraph(SpriteBatch sb, ShapeRenderer sr) {
        if (map.allPoints.isEmpty())
            return;

        //Returns from first line before visible area, to first line after visible area
        Iterator<ArrayList<TimingPoint>> effectPointIterator = map.getEditEffectPoints().values().iterator();
        Iterator<ArrayList<TimingPoint>> timingPointIterator = map.getVisibleTimingPoints((long) time - EditorLayer.viewTime, (long) time + EditorLayer.viewTime).values().iterator();

        //*********************GRAPH*********************
        float graphPosition = 0, newGraphPosition; //if new position not same, draw graph line. If new position is same, just pretend this line doesn't exist, unless the next line is different.
        TimingPoint effect = null, timing = null, lastPoint = null;
        double maxValue = peakSV - 1, minValue = 1 - minSV;

        if (effectPointIterator.hasNext())
            effect = GeneralUtils.listLast(effectPointIterator.next());
        if (timingPointIterator.hasNext()) {
            timing = GeneralUtils.listLast(timingPointIterator.next());
        }

        sb.end();
        sr.begin(ShapeRenderer.ShapeType.Line);

        sr.setColor(green);

        //Render graph for last point
        if (timing != null && (effect == null || timing.getPos() > effect.getPos())) { //Rendering the last point of the map
            sr.setColor(timing.kiai ? kiai : green);
            int drawPos = getPositionFromTime(timing.getPos(), SettingsMaster.getMiddle());
            if (drawPos < SettingsMaster.getWidth() && atEnd(timing.getPos())) //Last point is a timing point. position 0.
            {
                sr.line(drawPos, midY,
                        SettingsMaster.getWidth(), midY);
            }
        }
        else if (effect != null) {
            sr.setColor(effect.kiai ? kiai : green);
            int drawPos = getPositionFromTime(effect.getPos(), SettingsMaster.getMiddle());
            if (drawPos < SettingsMaster.getWidth() && atEnd(effect.getPos())) //Last point is a timing point. position 0.
            {
                if (effect.value >= 1)
                {
                    newGraphPosition = SV_AREA * (float) ((effect.value - 1) / maxValue);
                }
                else
                {
                    newGraphPosition = -SV_AREA * (float) ((1 - effect.value) / minValue);
                }

                sr.line(drawPos, midY + newGraphPosition,
                        SettingsMaster.getWidth(), midY + newGraphPosition);
            }
        }

        //SV+TIMING
        while (effect != null && timing != null) {
            if (timing.getPos() > effect.getPos()) { //Timing next
                newGraphPosition = 0;
                sr.setColor(timing.kiai ? kiai : green);

                if (lastPoint != null) {
                    if (lastPoint.getPos() - timing.getPos() >= SMOOTH_GRAPH_DISTANCE) //If they're too far apart, don't show a gradual change as it's kind of inaccurate.
                    {
                        sr.line(Math.max(0, getPositionFromTime(timing.getPos(), SettingsMaster.getMiddle())), midY,
                                Math.min(SettingsMaster.getWidth(), getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle())), midY);
                    } else {
                        sr.line(getPositionFromTime(timing.getPos(), SettingsMaster.getMiddle()), midY,
                                getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle()), midY + graphPosition);
                    }
                }
                lastPoint = timing;
                graphPosition = newGraphPosition;

                if (timingPointIterator.hasNext()) {
                    timing = GeneralUtils.listLast(timingPointIterator.next());
                }
                else {
                    timing = null;
                }
            }
            else if (effect.getPos() > timing.getPos()) {
                sr.setColor(effect.kiai ? kiai : green);
                if (effect.value >= 1)
                {
                    newGraphPosition = SV_AREA * (float) ((effect.value - 1) / maxValue);
                }
                else
                {
                    newGraphPosition = -SV_AREA * (float) ((1 - effect.value) / minValue);
                    //newGraphPosition = -SV_AREA * (float) ((effect.value - minSV) / minValue);
                }

                if (lastPoint != null) {
                    if (lastPoint.getPos() - effect.getPos() >= SMOOTH_GRAPH_DISTANCE) //If they're too far apart, don't show a gradual change as it's kind of inaccurate.
                    {
                        sr.line(Math.max(0, getPositionFromTime(effect.getPos(), SettingsMaster.getMiddle())), midY + newGraphPosition,
                                Math.min(SettingsMaster.getWidth(), getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle())), midY + newGraphPosition);
                    } else {
                        sr.line(getPositionFromTime(effect.getPos(), SettingsMaster.getMiddle()), midY + newGraphPosition,
                                getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle()), midY + graphPosition);
                    }
                }
                lastPoint = effect;
                graphPosition = newGraphPosition;

                if (effectPointIterator.hasNext()) {
                    effect = GeneralUtils.listLast(effectPointIterator.next());
                }
                else {
                    effect = null;
                }
            }
            else {
                sr.setColor(effect.kiai ? kiai : green);
                if (effect.value >= 1)
                {
                    newGraphPosition = SV_AREA * (float) ((effect.value - 1) / maxValue);
                }
                else
                {
                    newGraphPosition = -SV_AREA * (float) ((1 - effect.value) / minValue);
                }

                if (lastPoint != null) {
                    if (lastPoint.getPos() - effect.getPos() >= SMOOTH_GRAPH_DISTANCE) //If they're too far apart, don't show a gradual change as it's kind of inaccurate.
                    {
                        sr.line(Math.max(0, getPositionFromTime(effect.getPos(), SettingsMaster.getMiddle())), midY + newGraphPosition,
                                Math.min(SettingsMaster.getWidth(), getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle())), midY + newGraphPosition);
                    } else {
                        sr.line(getPositionFromTime(effect.getPos(), SettingsMaster.getMiddle()), midY + newGraphPosition,
                                getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle()), midY + graphPosition);
                    }
                }
                lastPoint = effect;
                graphPosition = newGraphPosition;

                if (effectPointIterator.hasNext())
                    effect = GeneralUtils.listLast(effectPointIterator.next());
                else {
                    effect = null;
                }

                if (timingPointIterator.hasNext())
                    timing = GeneralUtils.listLast(timingPointIterator.next());
                else
                    timing = null;
            }
        }
        //Just sv left
        while (effect != null) {
            sr.setColor(effect.kiai ? kiai : green);
            if (effect.value >= 1)
            {
                newGraphPosition = SV_AREA * (float) ((effect.value - 1) / maxValue);
            }
            else
            {
                newGraphPosition = -SV_AREA * (float) ((1 - effect.value) / minValue);
            }

            if (lastPoint != null) {
                if (lastPoint.getPos() - effect.getPos() >= SMOOTH_GRAPH_DISTANCE) //If they're too far apart, don't show a gradual change as it's kind of inaccurate.
                {
                    sr.line(Math.max(0, getPositionFromTime(effect.getPos(), SettingsMaster.getMiddle())), midY + newGraphPosition,
                            Math.min(SettingsMaster.getWidth(), getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle())), midY + newGraphPosition);
                } else {
                    sr.line(getPositionFromTime(effect.getPos(), SettingsMaster.getMiddle()), midY + newGraphPosition,
                            getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle()), midY + graphPosition);
                }
            }
            lastPoint = effect;
            graphPosition = newGraphPosition;

            if (effectPointIterator.hasNext()) {
                effect = GeneralUtils.listLast(effectPointIterator.next());
            }
            else {
                effect = null;
            }
        }
        //Just timing points left
        while (timing != null) {
            sr.setColor(timing.kiai ? kiai : green);
            newGraphPosition = 0;

            if (lastPoint != null) {
                if (lastPoint.getPos() - timing.getPos() >= SMOOTH_GRAPH_DISTANCE) //If they're too far apart, don't show a gradual change as it's kind of inaccurate.
                {
                    sr.line(Math.max(0, getPositionFromTime(timing.getPos(), SettingsMaster.getMiddle())), midY,
                            Math.min(SettingsMaster.getWidth(), getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle())), midY);
                } else {
                    sr.line(getPositionFromTime(timing.getPos(), SettingsMaster.getMiddle()), midY + newGraphPosition,
                            getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle()), midY + graphPosition);
                }
            }
            lastPoint = timing;
            graphPosition = newGraphPosition;

            if (timingPointIterator.hasNext()) {
                timing = GeneralUtils.listLast(timingPointIterator.next());
            }
            else {
                timing = null;
            }
        }

        //Iteration goes in reverse order, so the last point checked is the earliest point
        if (lastPoint != null && atStart(lastPoint.getPos()))
        {
            //sv at start of map before first timing point is fixed at 1x
            int pos = getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle());
            if (pos > 0)
                sr.line(0, midY, pos, midY);
        }

        sr.end();
        sb.begin();

        //*********************LABELS*********************
        if (renderLabels) {
            /*switch (adjustMode) {
                case NONE:
                    sb.setColor(Color.RED);
                    break;
                case POSSIBLE:
                    sb.setColor(Color.YELLOW);
                    break;
                case ACTIVE:
                    sb.setColor(Color.GREEN);
                    break;
            }
            sb.draw(pix, 0, bottom + SV_LABEL_BOTTOM, 200, SV_LABEL_TOP - SV_LABEL_BOTTOM);*/

            TimingPoint focus = null;

            if (adjustPoint != null) {
                focus = adjustPoint;
            }
            else if (hasSelection() && selectedObjects.size() == 1) {
                ArrayList<PositionalObject> s = selectedObjects.firstEntry().getValue();

                if (!s.isEmpty()) {
                    PositionalObject o = s.get(s.size() - 1);

                    if (o instanceof TimingPoint) {
                        focus = (TimingPoint) o;
                    }
                }
            }

            renderValueLabels(sb, focus);
        }
    }
    private void renderVolumeGraph(SpriteBatch sb, ShapeRenderer sr) {
        if (map.allPoints.isEmpty())
            return;

        //Returns from first line before visible area, to first line after visible area
        Iterator<ArrayList<TimingPoint>> effectPointIterator = map.getEditEffectPoints().values().iterator();
        Iterator<ArrayList<TimingPoint>> timingPointIterator = map.getVisibleTimingPoints((long) time - EditorLayer.viewTime, (long) time + EditorLayer.viewTime).values().iterator();

        //*********************GRAPH*********************
        float graphPosition = 0;
        TimingPoint effect = null, timing = null, lastPoint = null;
        //double  = 5, minValue = 1 - minSV;

        if (effectPointIterator.hasNext())
            effect = GeneralUtils.listLast(effectPointIterator.next());
        if (timingPointIterator.hasNext()) {
            timing = GeneralUtils.listLast(timingPointIterator.next());
        }

        sb.end();
        sr.begin(ShapeRenderer.ShapeType.Line);

        sr.setColor(yellow);

        //Render graph for last point
        if (timing != null && (effect == null || timing.getPos() > effect.getPos())) { //Rendering the last point of the map
            int drawPos = getPositionFromTime(timing.getPos(), SettingsMaster.getMiddle());
            if (drawPos < SettingsMaster.getWidth() && atEnd(timing.getPos())) //Last point is a timing point. position 0.
            {
                graphPosition = SV_AREA * ((timing.volume - 50) / 50.0f);
                sr.line(drawPos, midY + graphPosition,
                        SettingsMaster.getWidth(), midY + graphPosition);
            }
        }
        else if (effect != null) {
            int drawPos = getPositionFromTime(effect.getPos(), SettingsMaster.getMiddle());
            if (drawPos < SettingsMaster.getWidth() && atEnd(effect.getPos())) //Last point is a timing point. position 0.
            {
                graphPosition = SV_AREA * ((effect.volume - 50) / 50.0f);

                sr.line(drawPos, midY + graphPosition,
                        SettingsMaster.getWidth(), midY + graphPosition);
            }
        }

        //SV+TIMING
        while (effect != null && timing != null) {
            if (timing.getPos() > effect.getPos()) { //Timing next
                graphPosition = SV_AREA * ((timing.volume - 50) / 50.0f);

                if (lastPoint != null) {
                    sr.line(getPositionFromTime(timing.getPos(), SettingsMaster.getMiddle()), midY + graphPosition,
                            getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle()), midY + graphPosition);
                }
                lastPoint = timing;

                if (timingPointIterator.hasNext()) {
                    timing = GeneralUtils.listLast(timingPointIterator.next());
                }
                else {
                    timing = null;
                }
            }
            else if (effect.getPos() > timing.getPos()) {
                graphPosition = SV_AREA * ((effect.volume - 50) / 50.0f);

                if (lastPoint != null) {
                    sr.line(getPositionFromTime(effect.getPos(), SettingsMaster.getMiddle()), midY + graphPosition,
                            getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle()), midY + graphPosition);
                }
                lastPoint = effect;

                if (effectPointIterator.hasNext()) {
                    effect = GeneralUtils.listLast(effectPointIterator.next());
                }
                else {
                    effect = null;
                }
            }
            else {
                graphPosition = SV_AREA * ((effect.volume - 50) / 50.0f);

                if (lastPoint != null) {
                    sr.line(getPositionFromTime(effect.getPos(), SettingsMaster.getMiddle()), midY + graphPosition,
                            getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle()), midY + graphPosition);
                }
                lastPoint = effect;

                if (effectPointIterator.hasNext())
                    effect = GeneralUtils.listLast(effectPointIterator.next());
                else {
                    effect = null;
                }

                if (timingPointIterator.hasNext())
                    timing = GeneralUtils.listLast(timingPointIterator.next());
                else
                    timing = null;
            }
        }
        //Just sv left
        while (effect != null) {
            graphPosition = SV_AREA * ((effect.volume - 50) / 50.0f);

            if (lastPoint != null) {
                sr.line(getPositionFromTime(effect.getPos(), SettingsMaster.getMiddle()), midY + graphPosition,
                        getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle()), midY + graphPosition);
            }
            lastPoint = effect;

            if (effectPointIterator.hasNext()) {
                effect = GeneralUtils.listLast(effectPointIterator.next());
            }
            else {
                effect = null;
            }
        }
        //Just timing points left
        while (timing != null) {
            graphPosition = SV_AREA * ((timing.volume - 50) / 50.0f);

            if (lastPoint != null) {
                sr.line(getPositionFromTime(timing.getPos(), SettingsMaster.getMiddle()), midY + graphPosition,
                        getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle()), midY + graphPosition);
            }
            lastPoint = timing;

            if (timingPointIterator.hasNext()) {
                timing = GeneralUtils.listLast(timingPointIterator.next());
            }
            else {
                timing = null;
            }
        }

        //Iteration goes in reverse order, so the last point checked is the earliest point
        if (lastPoint != null && atStart(lastPoint.getPos()))
        {
            sr.line(0, midY + graphPosition,
                    getPositionFromTime(lastPoint.getPos(), SettingsMaster.getMiddle()), midY + graphPosition);
        }

        sr.end();
        sb.begin();

        //*********************LABELS*********************
        if (renderLabels) {
            TimingPoint focus = null;

            if (adjustPoint != null) {
                focus = adjustPoint;
            }
            else if (hasSelection() && selectedObjects.size() == 1) {
                ArrayList<PositionalObject> s = selectedObjects.firstEntry().getValue();

                if (!s.isEmpty()) {
                    PositionalObject o = s.get(s.size() - 1);

                    if (o instanceof TimingPoint) {
                        focus = (TimingPoint) o;
                    }
                }
            }

            renderVolumeLabels(sb, focus);
        }
    }

    private void renderValueLabels(SpriteBatch sb, TimingPoint adjust) {
        long lastRenderable = adjust == null ? 0 : adjust.getPos() + (long)(LABEL_SPACING / viewScale);
        double svLabelSpacing = 0;
        double bpmLabelSpacing = 0;

        TimingPoint effect = null, timing = null, lastPoint = null;

        Iterator<ArrayList<TimingPoint>> effectPointIterator = map.getEditEffectPoints().values().iterator();
        Iterator<ArrayList<TimingPoint>> timingPointIterator = map.getVisibleTimingPoints((long) time - EditorLayer.viewTime, (long) time + EditorLayer.viewTime).values().iterator();

        if (effectPointIterator.hasNext())
            effect = GeneralUtils.listLast(effectPointIterator.next());
        if (timingPointIterator.hasNext()) {
            timing = GeneralUtils.listLast(timingPointIterator.next());
        }

        while (effect != null && timing != null) { //SV+TIMING
            if (adjust != null) {
                if (Math.max(effect.getPos(), timing.getPos()) < lastRenderable - (long)(LABEL_SPACING / viewScale))
                    adjust = null;
            }
            //Timing labels don't care about selected effect point
            if (timing.getPos() > effect.getPos()) { //Timing next
                if (lastPoint == null) { //First point.
                    renderLabel(sb, bpmFormat.format(timing.getBPM()),
                            (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                    renderLabel(sb, twoDecimal.format(1),
                            (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                    bpmLabelSpacing = LABEL_SPACING;
                    svLabelSpacing = LABEL_SPACING;
                }
                else {
                    bpmLabelSpacing -= (lastPoint.getPos() - timing.getPos()) * viewScale;
                    svLabelSpacing -= (lastPoint.getPos() - timing.getPos()) * viewScale;

                    if (bpmLabelSpacing <= 0)
                    {
                        renderLabel(sb, bpmFormat.format(timing.getBPM()),
                                (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                        bpmLabelSpacing = LABEL_SPACING;
                    }
                    if (svLabelSpacing <= 0)
                    {
                        renderLabel(sb, twoDecimal.format(1),
                                (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 259);
                        svLabelSpacing = LABEL_SPACING;
                    }
                }
                lastPoint = timing;

                if (timingPointIterator.hasNext()) {
                    timing = GeneralUtils.listLast(timingPointIterator.next());
                }
                else {
                    timing = null;
                }
            }
            else if (effect.getPos() > timing.getPos()) {
                if (adjust != null && effect.getPos() <= lastRenderable) { //selected object special case
                    if (effect.equals(adjust)) {
                        renderAdjustableLabel(sb, effect,
                                (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                        svLabelSpacing = LABEL_SPACING;
                        adjust = null;
                    }
                }
                else if (lastPoint == null) {
                    renderLabel(sb, twoDecimal.format(effect.value),
                            (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                    svLabelSpacing = LABEL_SPACING;
                }
                else {
                    bpmLabelSpacing -= (lastPoint.getPos() - effect.getPos()) * viewScale;
                    svLabelSpacing -= (lastPoint.getPos() - effect.getPos()) * viewScale;
                    if (svLabelSpacing <= 0)
                    {
                        renderLabel(sb, twoDecimal.format(effect.value),
                                (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                        svLabelSpacing = LABEL_SPACING;
                    }
                }
                lastPoint = effect;

                if (effectPointIterator.hasNext()) {
                    effect = GeneralUtils.listLast(effectPointIterator.next());
                }
                else {
                    effect = null;
                }
            }
            else { //effect and timing point stacked
                if (adjust != null && effect.getPos() <= lastRenderable) {
                    //timing point, handled normally
                    if (lastPoint == null)
                        renderLabel(sb, bpmFormat.format(timing.getBPM()),
                                (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                    else {
                        bpmLabelSpacing -= (lastPoint.getPos() - effect.getPos()) * viewScale;
                        if (bpmLabelSpacing <= 0) {
                            renderLabel(sb, bpmFormat.format(timing.getBPM()),
                                    (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                            bpmLabelSpacing = LABEL_SPACING;
                        }
                    }

                    //effect point
                    if (effect.equals(adjust)) {
                        renderAdjustableLabel(sb, effect,
                                (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                        svLabelSpacing = LABEL_SPACING;
                        adjust = null;
                    }
                }
                else if (lastPoint == null) {
                    renderLabel(sb, bpmFormat.format(timing.getBPM()),
                            (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                    renderLabel(sb, twoDecimal.format(effect.value),
                            (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                    svLabelSpacing = LABEL_SPACING;
                    bpmLabelSpacing = LABEL_SPACING;
                }
                else {
                    bpmLabelSpacing -= (lastPoint.getPos() - effect.getPos()) * viewScale;
                    svLabelSpacing -= (lastPoint.getPos() - effect.getPos()) * viewScale;
                    if (bpmLabelSpacing <= 0) {
                        renderLabel(sb, bpmFormat.format(timing.getBPM()),
                                (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                        bpmLabelSpacing = LABEL_SPACING;
                    }
                    if (svLabelSpacing <= 0)
                    {
                        renderLabel(sb, twoDecimal.format(effect.value),
                                (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                        svLabelSpacing = LABEL_SPACING;
                    }
                }
                lastPoint = effect;

                if (effectPointIterator.hasNext())
                    effect = GeneralUtils.listLast(effectPointIterator.next());
                else
                    effect = null;

                if (timingPointIterator.hasNext())
                    timing = GeneralUtils.listLast(timingPointIterator.next());
                else
                    timing = null;
            }
        }
        //Just sv left
        while (effect != null) {
            if (adjust != null) {
                if (effect.getPos() < lastRenderable - (long)(LABEL_SPACING / viewScale))
                    adjust = null;
            }
            if (adjust != null && effect.getPos() <= lastRenderable) {
                if (effect.equals(adjust)) {
                    renderAdjustableLabel(sb, effect,
                            (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                    svLabelSpacing = LABEL_SPACING;
                    adjust = null;
                }
            }
            else if (lastPoint == null) {
                renderLabel(sb, twoDecimal.format(effect.value),
                        (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                svLabelSpacing = LABEL_SPACING;
            }
            else {
                bpmLabelSpacing -= (lastPoint.getPos() - effect.getPos()) * viewScale;
                svLabelSpacing -= (lastPoint.getPos() - effect.getPos()) * viewScale;
                if (svLabelSpacing <= 0)
                {
                    renderLabel(sb, twoDecimal.format(effect.value),
                            (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                    svLabelSpacing = LABEL_SPACING;
                }
            }
            lastPoint = effect;

            if (effectPointIterator.hasNext()) {
                effect = GeneralUtils.listLast(effectPointIterator.next());
            }
            else {
                effect = null;
            }
        }
        //Just timing points left
        while (timing != null) {
            if (adjust != null) {
                if (timing.getPos() < lastRenderable - (long)(LABEL_SPACING / viewScale))
                    adjust = null;
            }
            if (lastPoint == null) {
                renderLabel(sb, bpmFormat.format(timing.getBPM()),
                        (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                renderLabel(sb, twoDecimal.format(1),
                        (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 259);
                bpmLabelSpacing = LABEL_SPACING;
                svLabelSpacing = LABEL_SPACING;
            }
            else {
                bpmLabelSpacing -= (lastPoint.getPos() - timing.getPos()) * viewScale;
                svLabelSpacing -= (lastPoint.getPos() - timing.getPos()) * viewScale;

                if (bpmLabelSpacing <= 0)
                {
                    renderLabel(sb, bpmFormat.format(timing.getBPM()),
                            (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                    bpmLabelSpacing = LABEL_SPACING;
                }
                if (svLabelSpacing <= 0)
                {
                    renderLabel(sb, twoDecimal.format(1),
                            (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 259);
                    svLabelSpacing = LABEL_SPACING;
                }
            }
            lastPoint = timing;

            if (timingPointIterator.hasNext()) {
                timing = GeneralUtils.listLast(timingPointIterator.next());
            }
            else {
                timing = null;
            }
        }
    }
    private void renderVolumeLabels(SpriteBatch sb, TimingPoint adjust) {
        long lastRenderable = adjust == null ? 0 : adjust.getPos() + (long)(LABEL_SPACING / viewScale);
        double volumeLabelSpacing = 0;
        double bpmLabelSpacing = 0;

        TimingPoint effect = null, timing = null, lastPoint = null;

        Iterator<ArrayList<TimingPoint>> effectPointIterator = map.getEditEffectPoints().values().iterator();
        Iterator<ArrayList<TimingPoint>> timingPointIterator = map.getVisibleTimingPoints((long) time - EditorLayer.viewTime, (long) time + EditorLayer.viewTime).values().iterator();

        if (effectPointIterator.hasNext())
            effect = GeneralUtils.listLast(effectPointIterator.next());
        if (timingPointIterator.hasNext()) {
            timing = GeneralUtils.listLast(timingPointIterator.next());
        }

        while (effect != null && timing != null) { //SV+TIMING
            if (adjust != null) {
                if (Math.max(effect.getPos(), timing.getPos()) < lastRenderable - (long)(LABEL_SPACING / viewScale))
                    adjust = null;
            }
            //Timing labels don't care about selected effect point
            if (timing.getPos() > effect.getPos()) { //Timing next
                if (lastPoint == null) { //First point.
                    renderLabel(sb, bpmFormat.format(timing.getBPM()),
                            (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                    renderLabel(sb, volume.format(timing.volume),
                            (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                    bpmLabelSpacing = LABEL_SPACING;
                    volumeLabelSpacing = LABEL_SPACING;
                }
                else {
                    bpmLabelSpacing -= (lastPoint.getPos() - timing.getPos()) * viewScale;
                    volumeLabelSpacing -= (lastPoint.getPos() - timing.getPos()) * viewScale;

                    if (bpmLabelSpacing <= 0)
                    {
                        renderLabel(sb, bpmFormat.format(timing.getBPM()),
                                (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                        bpmLabelSpacing = LABEL_SPACING;
                    }
                    if (volumeLabelSpacing <= 0)
                    {
                        renderLabel(sb, volume.format(timing.volume),
                                (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 259);
                        volumeLabelSpacing = LABEL_SPACING;
                    }
                }
                lastPoint = timing;

                if (timingPointIterator.hasNext()) {
                    timing = GeneralUtils.listLast(timingPointIterator.next());
                }
                else {
                    timing = null;
                }
            }
            else if (effect.getPos() > timing.getPos()) {
                if (adjust != null && effect.getPos() <= lastRenderable) { //selected object special case
                    if (effect.equals(adjust)) {
                        renderAdjustableLabel(sb, effect,
                                (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                        volumeLabelSpacing = LABEL_SPACING;
                        adjust = null;
                    }
                }
                else if (lastPoint == null) {
                    renderLabel(sb, volume.format(effect.volume),
                            (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                    volumeLabelSpacing = LABEL_SPACING;
                }
                else {
                    bpmLabelSpacing -= (lastPoint.getPos() - effect.getPos()) * viewScale;
                    volumeLabelSpacing -= (lastPoint.getPos() - effect.getPos()) * viewScale;
                    if (volumeLabelSpacing <= 0)
                    {
                        renderLabel(sb, volume.format(effect.volume),
                                (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                        volumeLabelSpacing = LABEL_SPACING;
                    }
                }
                lastPoint = effect;

                if (effectPointIterator.hasNext()) {
                    effect = GeneralUtils.listLast(effectPointIterator.next());
                }
                else {
                    effect = null;
                }
            }
            else { //effect and timing point stacked
                if (adjust != null && effect.getPos() <= lastRenderable) {
                    //timing point, handled normally
                    if (lastPoint == null)
                        renderLabel(sb, bpmFormat.format(timing.getBPM()),
                                (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                    else {
                        bpmLabelSpacing -= (lastPoint.getPos() - effect.getPos()) * viewScale;
                        if (bpmLabelSpacing <= 0) {
                            renderLabel(sb, bpmFormat.format(timing.getBPM()),
                                    (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                            bpmLabelSpacing = LABEL_SPACING;
                        }
                    }

                    //effect point
                    if (effect.equals(adjust)) {
                        renderAdjustableLabel(sb, effect,
                                (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                        volumeLabelSpacing = LABEL_SPACING;
                        adjust = null;
                    }
                }
                else if (lastPoint == null) {
                    renderLabel(sb, bpmFormat.format(timing.getBPM()),
                            (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                    renderLabel(sb, volume.format(effect.volume),
                            (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                    volumeLabelSpacing = LABEL_SPACING;
                    bpmLabelSpacing = LABEL_SPACING;
                }
                else {
                    bpmLabelSpacing -= (lastPoint.getPos() - effect.getPos()) * viewScale;
                    volumeLabelSpacing -= (lastPoint.getPos() - effect.getPos()) * viewScale;
                    if (bpmLabelSpacing <= 0) {
                        renderLabel(sb, bpmFormat.format(timing.getBPM()),
                                (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                        bpmLabelSpacing = LABEL_SPACING;
                    }
                    if (volumeLabelSpacing <= 0)
                    {
                        renderLabel(sb, volume.format(effect.volume),
                                (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                        volumeLabelSpacing = LABEL_SPACING;
                    }
                }
                lastPoint = effect;

                if (effectPointIterator.hasNext())
                    effect = GeneralUtils.listLast(effectPointIterator.next());
                else
                    effect = null;

                if (timingPointIterator.hasNext())
                    timing = GeneralUtils.listLast(timingPointIterator.next());
                else
                    timing = null;
            }
        }
        //Just sv left
        while (effect != null) {
            if (adjust != null) {
                if (effect.getPos() < lastRenderable - (long)(LABEL_SPACING / viewScale))
                    adjust = null;
            }
            if (adjust != null && effect.getPos() <= lastRenderable) {
                if (effect.equals(adjust)) {
                    renderAdjustableLabel(sb, effect,
                            (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                    volumeLabelSpacing = LABEL_SPACING;
                    adjust = null;
                }
            }
            else if (lastPoint == null) {
                renderLabel(sb, volume.format(effect.volume),
                        (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                volumeLabelSpacing = LABEL_SPACING;
            }
            else {
                bpmLabelSpacing -= (lastPoint.getPos() - effect.getPos()) * viewScale;
                volumeLabelSpacing -= (lastPoint.getPos() - effect.getPos()) * viewScale;
                if (volumeLabelSpacing <= 0)
                {
                    renderLabel(sb, volume.format(effect.volume),
                            (int) (SettingsMaster.getMiddle() + (effect.getPos() - time) * viewScale + 4), bottom + 259);
                    volumeLabelSpacing = LABEL_SPACING;
                }
            }
            lastPoint = effect;

            if (effectPointIterator.hasNext()) {
                effect = GeneralUtils.listLast(effectPointIterator.next());
            }
            else {
                effect = null;
            }
        }
        //Just timing points left
        while (timing != null) {
            if (adjust != null) {
                if (timing.getPos() < lastRenderable - (long)(LABEL_SPACING / viewScale))
                    adjust = null;
            }
            if (lastPoint == null) {
                renderLabel(sb, bpmFormat.format(timing.getBPM()),
                        (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                renderLabel(sb, volume.format(timing.volume),
                        (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 259);
                bpmLabelSpacing = LABEL_SPACING;
                volumeLabelSpacing = LABEL_SPACING;
            }
            else {
                bpmLabelSpacing -= (lastPoint.getPos() - timing.getPos()) * viewScale;
                volumeLabelSpacing -= (lastPoint.getPos() - timing.getPos()) * viewScale;

                if (bpmLabelSpacing <= 0)
                {
                    renderLabel(sb, bpmFormat.format(timing.getBPM()),
                            (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 50);
                    bpmLabelSpacing = LABEL_SPACING;
                }
                if (volumeLabelSpacing <= 0)
                {
                    renderLabel(sb, volume.format(timing.volume),
                            (int) (SettingsMaster.getMiddle() + (timing.getPos() - time) * viewScale + 4), bottom + 259);
                    volumeLabelSpacing = LABEL_SPACING;
                }
            }
            lastPoint = timing;

            if (timingPointIterator.hasNext()) {
                timing = GeneralUtils.listLast(timingPointIterator.next());
            }
            else {
                timing = null;
            }
        }
    }

    private void renderLabel(SpriteBatch sb, String text, int x, int y) {
        textRenderer.setFont(font).renderText(sb, Color.WHITE, text, x, y);
    }
    private void renderAdjustableLabel(SpriteBatch sb, TimingPoint p, int x, int y) {
        if (adjustMode == AdjustMode.ACTIVE && p.equals(adjustPoint)) {
            textRenderer.setFont(font).renderText(sb, Color.WHITE, textInput, x, y);

            if (renderBlip) {
                sb.draw(pix, x + blipOffsetX, y - 10, BLIP_WIDTH, BLIP_HEIGHT);
            }
            return;
        }
        textRenderer.setFont(font).renderText(sb, Color.WHITE, mode ? twoDecimal.format(p.value) : volume.format(p.volume), x, y);
    }


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
    public void renderObject(PositionalObject o, SpriteBatch sb, ShapeRenderer sr, float alpha) {
        if (o instanceof TimingPoint) {
            if (((TimingPoint) o).uninherited) {
                ((TimingPoint) o).renderColored(sb, sr, time, viewScale, SettingsMaster.getMiddle(), bottom, red, alpha);
            }
            else if (map.timingPoints.containsKey(o.getPos())) {
                ((TimingPoint) o).renderColored(sb, sr, time, viewScale, SettingsMaster.getMiddle(), bottom, yellow, alpha);
            }
            else {
                o.render(sb, sr, time, viewScale, SettingsMaster.getMiddle(), bottom, alpha);
            }
        }
        else {
            o.render(sb, sr, time, viewScale, SettingsMaster.getMiddle(), bottom, alpha);
        }
    }

    @Override
    public void renderSelection(PositionalObject o, SpriteBatch sb, ShapeRenderer sr) {
        o.renderSelection(sb, sr, time, viewScale, SettingsMaster.getMiddle(), bottom);
    }

    @Override
    public Snap getNextSnap() {
        Map.Entry<Long, Snap> next = map.getCurrentSnaps().higherEntry(EditorLayer.music.isPlaying() ? (long) time + 250 : (long) time);
        if (next == null)
            return null;
        if (next.getKey() - time < 2)
        {
            next = map.getCurrentSnaps().higherEntry(next.getKey());
            if (next == null)
                return null;
        }
        return next.getValue();
    }

    @Override
    public Snap getPreviousSnap() {
        Map.Entry<Long, Snap> previous = map.getCurrentSnaps().lowerEntry(EditorLayer.music.isPlaying() ? (long) time - 250 : (long) time);
        if (previous == null)
            return null;
        if (time - previous.getKey() < 2)
        {
            previous = map.getCurrentSnaps().lowerEntry(previous.getKey());
            if (previous == null)
                return null;
        }
        return previous.getValue();
    }

    @Override
    public Snap getClosestSnap(double time, float limit) {
        if (map.getCurrentSnaps().containsKey((long) time))
            return map.getCurrentSnaps().get((long) time);

        Map.Entry<Long, Snap> lower, higher;
        lower = map.getCurrentSnaps().lowerEntry((long) time);
        higher = map.getCurrentSnaps().higherEntry((long) time);

        if (lower == null && higher == null)
        {
            return null;
        }
        else if (lower == null)
        {
            if (higher.getKey() - time <= limit)
                return higher.getValue();
        }
        else if (higher == null)
        {
            if (time - lower.getKey() <= limit)
                return lower.getValue();
        }
        else
        {
            double lowerDist = time - lower.getValue().pos, higherDist = higher.getValue().pos - time;
            if (lowerDist <= higherDist)
            {
                if (lowerDist <= limit)
                    return lower.getValue();
            }
            if (higherDist <= limit)
                return higher.getValue();
        }
        return null;
    }

    @Override
    public boolean noSnaps() {
        return map.getCurrentSnaps().isEmpty();
    }


    @Override
    public PositionalObjectTreeMap<?> getEditMap() {
        return map.effectPoints;
    }

    @Override
    public void pasteObjects(PositionalObjectTreeMap<PositionalObject> copyObjects) {
        endAdjust();
        long offset, targetPos;

        offset = (long) time - copyObjects.firstKey();

        PositionalObjectTreeMap<PositionalObject> placementCopy = new PositionalObjectTreeMap<>();

        for (Map.Entry<Long, ArrayList<PositionalObject>> entry : copyObjects.entrySet())
        {
            targetPos = entry.getKey() + offset;
            for (PositionalObject o : entry.getValue())
            {
                placementCopy.add(o.shiftedCopy(targetPos));
            }
        }

        this.map.pasteLines(placementCopy);
    }

    @Override
    public void reverse() {
        if (!hasSelection())
            return;

        endAdjust();
        this.map.reverse(MapChange.ChangeType.EFFECT, false, selectedObjects);
        refreshSelection();
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> getVisibleRange(long start, long end) {
        NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> source = map.getEditEffectPoints((long) time - EditorLayer.viewTime, (long) time + EditorLayer.viewTime);

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
    public void deleteObject(PositionalObject o) {
        endAdjust();
        this.map.delete(MapChange.ChangeType.EFFECT, o);
    }
    @Override
    public void deleteSelection() {
        if (selectedObjects != null)
        {
            endAdjust();
            this.map.delete(MapChange.ChangeType.EFFECT, selectedObjects);
            clearSelection();
        }
    }
    @Override
    public void registerMove(long totalMovement) {
        if (selectedObjects != null)
        {
            PositionalObjectTreeMap<PositionalObject> movementCopy = new PositionalObjectTreeMap<>();
            movementCopy.addAll(selectedObjects); //use addAll to make a copy without sharing any references other than the positionalobjects themselves
            this.map.registerMovement(MapChange.ChangeType.EFFECT, movementCopy, totalMovement);
        }
    }
    @Override
    public void registerValueChange() {
        if (selectedObjects != null)
        {
            PositionalObjectTreeMap<PositionalObject> adjustCopy = new PositionalObjectTreeMap<>();
            adjustCopy.addAll(selectedObjects); //use addAll to make a copy without sharing any references other than the positionalobjects themselves
            this.map.registerValueChange(adjustCopy);
            this.map.updateSv();
        }
    }
    public void registerVolumeChange() {
        if (selectedObjects != null)
        {
            PositionalObjectTreeMap<PositionalObject> adjusted = new PositionalObjectTreeMap<>();
            adjusted.addAll(selectedObjects); //use addAll to make a copy without sharing any references other than the positionalobjects themselves
            this.map.registerVolumeChange(adjusted, getStackedSelection());
            this.map.updateEffectPoints(adjusted.entrySet(), null); //for just a volume check, this is good enough
        }
    }

    public void fuckSelection() {
        if (selectedObjects != null)
        {
            float variance = 0.01f; //1% variance

            for (ArrayList<PositionalObject> points : selectedObjects.values())
            {
                for (PositionalObject t : points)
                {
                    TimingPoint point = (TimingPoint) t;

                    point.value += point.value * (variance * (Math.random() - 0.5f) * 2.0f);
                }
            }
        }

        minSV = 0.5;
        peakSV = 1.5;
        recheckSvLimits();
    }

    @Override
    public void select(PositionalObject p) {
        super.select(p);

        if (selectedObjects.size() != 1)
            endAdjust();
    }

    @Override
    public void selectAll() {
        endAdjust();
        clearSelection();

        selectedObjects = new PositionalObjectTreeMap<>();

        selectedObjects.addAll(map.effectPoints);

        for (ArrayList<? extends PositionalObject> stuff : selectedObjects.values())
            for (PositionalObject o : stuff)
                o.selected = true;
    }

    @Override
    public void addSelectionRange(long startTime, long endTime) {
        endAdjust();
        if (startTime == endTime)
            return;

        PositionalObjectTreeMap<PositionalObject> newSelection;

        if (selectedObjects == null)
        {
            newSelection = new PositionalObjectTreeMap<>();
            if (startTime > endTime)
                newSelection.addAll(map.getSubEffectMap(endTime, startTime));
            else
                newSelection.addAll(map.getSubEffectMap(startTime, endTime));

            selectedObjects = newSelection;
            for (ArrayList<PositionalObject> stuff : selectedObjects.values())
                for (PositionalObject o : stuff)
                    o.selected = true;
        }
        else
        {
            NavigableMap<Long, ArrayList<TimingPoint>> newSelected;

            if (startTime > endTime)
                newSelected = map.getSubEffectMap(endTime, startTime);
            else
                newSelected = map.getSubEffectMap(startTime, endTime);

            selectedObjects.addAll(newSelected);

            for (ArrayList<PositionalObject> stuff : selectedObjects.values())
                for (PositionalObject o : stuff)
                    o.selected = true;
        }
    }

    private enum AdjustMode{
        NONE,
        POSSIBLE,
        ACTIVE
    }
    private AdjustMode adjustMode = AdjustMode.NONE;
    private TimingPoint adjustPoint = null;
    @Override
    public void dragging() {
        endAdjust();
    }
    @Override
    public void clickRelease() {
        if (adjustMode == AdjustMode.POSSIBLE && selectedObjects != null && adjustPoint != null) {
            startAdjust(adjustPoint);
        }
        else {
            endAdjust();
        }
    }

    @Override
    public PositionalObject clickObject(float x, float y) {
        //Check if y location is on sv label area
        //If so, allow wider x area for clicking.
        endAdjust();

        NavigableMap<Long, ArrayList<TimingPoint>> selectable = map.getEditEffectPoints();
        if (selectable == null || y < bottom || y > top)
            return null;

        double time = getTimeFromPosition(x);

        if (y > bottom + VALUE_LABEL_BOTTOM && y < bottom + VALUE_LABEL_TOP) {
            adjustMode = AdjustMode.POSSIBLE;
        }

        if (selectable.containsKey((long) time)) {
            ArrayList<TimingPoint> selectableObjects = selectable.get((long) time);
            if (selectableObjects.isEmpty())
            {
                editorLogger.error("WTF? Empty arraylist of objects in object map.");
            }
            else
            {
                //Select the first object.
                return selectableObjects.get(selectableObjects.size() - 1);
            }
        }

        Map.Entry<Long, ArrayList<TimingPoint>> lower = selectable.higherEntry((long) time);
        Map.Entry<Long, ArrayList<TimingPoint>> higher = selectable.lowerEntry((long) time);
        double higherDist, lowerDist;
        double adjustLimit = (adjustMode == AdjustMode.POSSIBLE ? 30 / viewScale : SELECTION_DIST);
        //boolean isLower = true;

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

        //Check the closer objects first.
        if (lowerDist < higherDist)
        {
            ArrayList<TimingPoint> selectableObjects = lower.getValue();
            if (!selectableObjects.isEmpty() && lowerDist < adjustLimit) //lower distance is within selection range.
            {
                //Select the first object.
                return adjustPoint = selectableObjects.get(selectableObjects.size() - 1);
            }

            if (higher != null)
            {
                selectableObjects = higher.getValue();
                if (!selectableObjects.isEmpty() && higherDist < SELECTION_DIST) {
                    //Select the first object.
                    return adjustPoint = selectableObjects.get(selectableObjects.size() - 1);
                }
            }
        }
        else if (higherDist < lowerDist)
        {
            ArrayList<TimingPoint> selectableObjects = higher.getValue();
            if (!selectableObjects.isEmpty() && higherDist < SELECTION_DIST) {
                //Select the first object.
                return adjustPoint = selectableObjects.get(selectableObjects.size() - 1);
            }

            if (lower != null)
            {
                selectableObjects = lower.getValue();
                if (!selectableObjects.isEmpty() && lowerDist < adjustLimit) {
                    //Select the first object.
                    return adjustPoint = selectableObjects.get(selectableObjects.size() - 1);
                }
                //lower distance is within selection range.


            }
        }
        return adjustPoint = null;
    }

    @Override
    public boolean clickedEnd(PositionalObject o, float x) {
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
        peakSVText = svFormat.format(peakSV);
        minSVText = svFormat.format(minSV);
    }

    private static final Toolset svToolset = new Toolset(SelectionTool.get(), GreenLineTool.get(), SVFunctionTool.get(), KiaiTool.get());
    private static final Toolset volumeToolset = new Toolset(SelectionTool.get(), GreenLineTool.get(), KiaiTool.get());
    public Toolset getToolset()
    {
        return mode ? svToolset : volumeToolset;
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

    private void startAdjust(TimingPoint p) {
        adjustPoint = p;
        adjustMode = AdjustMode.ACTIVE;
        textInput = mode ? precise.format(p.value) : volume.format(p.volume);
        blipOffsetX = textRenderer.setFont(font).getWidth(textInput) + BLIP_BUFFER;
        renderBlip = true;
        blipTimer = 0.4f;
        changed = false;
        EditorLayer.processor.setTextReceiver(this);
    }
    private void endAdjust() {
        if (adjustMode == AdjustMode.ACTIVE && adjustPoint != null && changed && selectedObjects != null) {
            try {
                if (mode) {
                    double nSv = Double.parseDouble(textInput);
                    //parent.showText("Set new SV: " + nSv);

                    if (nSv <= 0) {
                        parent.showText("Invalid value entered.");
                    }
                    else {
                        PositionalObjectTreeMap<PositionalObject> adjustCopy = new PositionalObjectTreeMap<>();
                        adjustCopy.addAll(selectedObjects);
                        map.registerChange(new ValueSetChange(map, adjustCopy, nSv).perform());
                    }
                }
                else {
                    int nVol = Integer.parseInt(textInput);

                    if (nVol < 1 || nVol > 100) {
                        parent.showText("Invalid value entered.");
                    }
                    else {
                        map.registerChange(new VolumeSetChange(map, getStackedSelection(), nVol).perform());
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

    private PositionalObjectTreeMap<PositionalObject> getStackedSelection() {
        PositionalObjectTreeMap<PositionalObject> allStacked = new PositionalObjectTreeMap<>();
        ArrayList<TimingPoint> stack;
        for (Long k : selectedObjects.keySet()) {
            stack = map.timingPoints.get(k);
            if (stack != null)
                for (PositionalObject o : stack)
                    allStacked.add(o);

            stack = map.effectPoints.get(k);
            if (stack != null)
                for (PositionalObject o : stack)
                    allStacked.add(o);
        }

        return allStacked;
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
        return mode ? 12 : 3;
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
        endAdjust();
        return false;
    }

    private static final DecimalFormat twoDecimal = new DecimalFormat("##0.##x", osuSafe);
    private static final DecimalFormat bpmFormat = new DecimalFormat("##0.## BPM", osuSafe);
    private static final DecimalFormat precise = new DecimalFormat("##0.0##", osuSafe);
    private static final DecimalFormat volume = new DecimalFormat("##0", osuSafe);

    private static final Color red = Color.RED.cpy();
    private static final Color green = new Color(0.25f, 0.75f, 0.0f, 1.0f);
    private static final Color yellow = new Color(0.8f, 0.8f, 0.0f, 1.0f);

    private static final Color selection = new Color(1.0f, 0.6f, 0.0f, 1.0f);
}
