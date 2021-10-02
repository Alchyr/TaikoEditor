package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.input.sub.TextInputReceiver;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.MapChange;
import alchyr.taikoedit.editor.changes.ValueSetChange;
import alchyr.taikoedit.editor.tools.*;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.GeneralUtils;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;
import com.badlogic.gdx.Gdx;
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

public class SvView extends MapView implements TextInputReceiver {
    //Kiai, sv
    //SV can be represented using a line graph?
    //Drag line graph to quickly adjust sv?
    //Select a section to do effects like interpolation (smooth line) or something similar?

    public static final int HEIGHT = 300;
    public static final int SV_AREA = (HEIGHT - 80) / 2;

    private static final int SV_LABEL_BOTTOM = 245;
    private static final int SV_LABEL_TOP = 265;

    //Selection
    private static final int SELECTION_DIST = 30;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.7f);
    private static final Color lineColor = new Color(0.35f, 0.35f, 0.38f, 0.4f);

    private SortedMap<Long, Snap> activeSnaps;

    //Positions
    private int baseMidY;

    //Offset positions
    private int midY;


    //SV Graph
    private static final int SMOOTH_GRAPH_DISTANCE = 300; //Further apart than this, no smooth graph
    private static final int LABEL_SPACING = 36;
    private final DecimalFormat svFormat = new DecimalFormat("0.000x");
    private double peakSV, minSV;
    private String peakSVText, minSVText;
    private boolean renderLabels = true; //TODO: Add way to disable labels. Toggle button on overlay?

    //Sv values
    private final BitmapFont font;

    //Hitsounds of map
    private double lastSounded;

    public SvView(EditorLayer parent, EditorBeatmap beatmap) {
        super(ViewType.EFFECT_VIEW, parent, beatmap, HEIGHT);

        addOverlayButton(new ImageButton(assetMaster.get("editor:exit"), assetMaster.get("editor:exith"), this::close).setAction("Close View"));

        font = assetMaster.getFont("aller small");

        allowVerticalDrag = true;

        lastSounded = 0;
        peakSV = 1.3;
        minSV = 0.75;
        recheckSvLimits();
    }

    public void close(int button)
    {
        //TODO: Add save check
        if (button == Input.Buttons.LEFT)
        {
            parent.removeView(this);
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
    public double getTimeFromPosition(int x) {
        return getTimeFromPosition(x, SettingsMaster.getMiddle());
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> prep(long pos) {
        //return graph points
        return map.getEditEffectPoints(pos - EditorLayer.viewTime, pos + EditorLayer.viewTime);
    }

    @Override
    public void update(double exactPos, long msPos) {
        super.update(exactPos, msPos);
        activeSnaps = map.getActiveSnaps(time - EditorLayer.viewTime, time + EditorLayer.viewTime);

        blipTimer -= Gdx.graphics.getDeltaTime();
        if (blipTimer <= 0) {
            blipTimer = 0.4f;
            renderBlip = !renderBlip;
        }

        if (adjustMode == AdjustMode.ACTIVE && !parent.tools.getCurrentTool().equals(toolset.getDefaultTool())) {
            endAdjust();
        }
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
            t = points.get(0);

            if (!map.effectPoints.containsKey(t.pos)) {
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

        renderGraph(sb, sr);
        textRenderer.setFont(font).renderText(sb, Color.WHITE, peakSVText, 0, midY + SV_AREA + 10);
        textRenderer.setFont(font).renderText(sb, Color.WHITE, minSVText, 0, midY - SV_AREA - 1);

        super.renderOverlay(sb, sr);
    }

    private void renderGraph(SpriteBatch sb, ShapeRenderer sr) {
        if (map.timingPoints.isEmpty() && map.effectPoints.isEmpty())
            return;

        //Returns from first line before visible area, to first line after visible area
        Iterator<ArrayList<TimingPoint>> effectPointIterator = map.getEditEffectPoints().values().iterator();
        Iterator<ArrayList<TimingPoint>> timingPointIterator = map.getVisibleTimingPoints((long) time - EditorLayer.viewTime, (long) time + EditorLayer.viewTime).values().iterator();

        //*********************GRAPH*********************
        float graphPosition = 0, newGraphPosition; //if new position not same, draw graph line. If new position is same, just pretend this line doesn't exist, unless the next line is different.
        TimingPoint effect = null, timing = null, lastPoint = null;
        double maxValue = peakSV - 1, minValue = 1 - minSV;

        if (effectPointIterator.hasNext())
            effect = effectPointIterator.next().get(0);
        if (timingPointIterator.hasNext()) {
            timing = timingPointIterator.next().get(0);
        }

        sb.end();
        sr.begin(ShapeRenderer.ShapeType.Line);

        sr.setColor(green);

        //Render graph for last point
        if (timing != null && (effect == null || timing.pos > effect.pos)) { //Rendering the last point of the map
            int drawPos = getPositionFromTime(timing.pos, SettingsMaster.getMiddle());
            if (drawPos < SettingsMaster.getWidth() && atEnd(timing.pos)) //Last point is a timing point. position 0.
            {
                sr.line(drawPos, midY,
                        SettingsMaster.getWidth(), midY);
            }
        }
        else if (effect != null) {
            int drawPos = getPositionFromTime(effect.pos, SettingsMaster.getMiddle());
            if (drawPos < SettingsMaster.getWidth() && atEnd(effect.pos)) //Last point is a timing point. position 0.
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
            if (timing.pos > effect.pos) { //Timing next
                newGraphPosition = 0;

                if (lastPoint != null) {
                    if (lastPoint.pos - timing.pos >= SMOOTH_GRAPH_DISTANCE) //If they're too far apart, don't show a gradual change as it's kind of inaccurate.
                    {
                        sr.line(getPositionFromTime(timing.pos, SettingsMaster.getMiddle()), midY + newGraphPosition,
                                getPositionFromTime(lastPoint.pos, SettingsMaster.getMiddle()), midY + newGraphPosition);
                    } else {
                        sr.line(getPositionFromTime(timing.pos, SettingsMaster.getMiddle()), midY + newGraphPosition,
                                getPositionFromTime(lastPoint.pos, SettingsMaster.getMiddle()), midY + graphPosition);
                    }
                }
                lastPoint = timing;
                graphPosition = newGraphPosition;

                if (timingPointIterator.hasNext()) {
                    timing = timingPointIterator.next().get(0);
                }
                else {
                    timing = null;
                }
            }
            else if (effect.pos > timing.pos) {
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
                    if (lastPoint.pos - effect.pos >= SMOOTH_GRAPH_DISTANCE) //If they're too far apart, don't show a gradual change as it's kind of inaccurate.
                    {
                        sr.line(getPositionFromTime(effect.pos, SettingsMaster.getMiddle()), midY + newGraphPosition,
                                getPositionFromTime(lastPoint.pos, SettingsMaster.getMiddle()), midY + newGraphPosition);
                    } else {
                        sr.line(getPositionFromTime(effect.pos, SettingsMaster.getMiddle()), midY + newGraphPosition,
                                getPositionFromTime(lastPoint.pos, SettingsMaster.getMiddle()), midY + graphPosition);
                    }
                }
                lastPoint = effect;
                graphPosition = newGraphPosition;

                if (effectPointIterator.hasNext()) {
                    effect = effectPointIterator.next().get(0);
                }
                else {
                    effect = null;
                }
            }
            else {
                if (effect.value >= 1)
                {
                    newGraphPosition = SV_AREA * (float) ((effect.value - 1) / maxValue);
                }
                else
                {
                    newGraphPosition = -SV_AREA * (float) ((1 - effect.value) / minValue);
                }

                if (lastPoint != null) {
                    if (lastPoint.pos - effect.pos >= SMOOTH_GRAPH_DISTANCE) //If they're too far apart, don't show a gradual change as it's kind of inaccurate.
                    {
                        sr.line(getPositionFromTime(effect.pos, SettingsMaster.getMiddle()), midY + newGraphPosition,
                                getPositionFromTime(lastPoint.pos, SettingsMaster.getMiddle()), midY + newGraphPosition);
                    } else {
                        sr.line(getPositionFromTime(effect.pos, SettingsMaster.getMiddle()), midY + newGraphPosition,
                                getPositionFromTime(lastPoint.pos, SettingsMaster.getMiddle()), midY + graphPosition);
                    }
                }
                lastPoint = effect;
                graphPosition = newGraphPosition;

                if (effectPointIterator.hasNext())
                    effect = effectPointIterator.next().get(0);
                else {
                    effect = null;
                }

                if (timingPointIterator.hasNext())
                    timing = timingPointIterator.next().get(0);
                else
                    timing = null;
            }
        }
        //Just sv left
        while (effect != null) {
            if (effect.value >= 1)
            {
                newGraphPosition = SV_AREA * (float) ((effect.value - 1) / maxValue);
            }
            else
            {
                newGraphPosition = -SV_AREA * (float) ((1 - effect.value) / minValue);
            }

            if (lastPoint != null) {
                if (lastPoint.pos - effect.pos >= SMOOTH_GRAPH_DISTANCE) //If they're too far apart, don't show a gradual change as it's kind of inaccurate.
                {
                    sr.line(getPositionFromTime(effect.pos, SettingsMaster.getMiddle()), midY + newGraphPosition,
                            getPositionFromTime(lastPoint.pos, SettingsMaster.getMiddle()), midY + newGraphPosition);
                } else {
                    sr.line(getPositionFromTime(effect.pos, SettingsMaster.getMiddle()), midY + newGraphPosition,
                            getPositionFromTime(lastPoint.pos, SettingsMaster.getMiddle()), midY + graphPosition);
                }
            }
            lastPoint = effect;
            graphPosition = newGraphPosition;

            if (effectPointIterator.hasNext()) {
                effect = effectPointIterator.next().get(0);
            }
            else {
                effect = null;
            }
        }
        //Just timing points left
        while (timing != null) {
            newGraphPosition = 0;

            if (lastPoint != null) {
                if (lastPoint.pos - timing.pos >= SMOOTH_GRAPH_DISTANCE) //If they're too far apart, don't show a gradual change as it's kind of inaccurate.
                {
                    sr.line(getPositionFromTime(timing.pos, SettingsMaster.getMiddle()), midY + newGraphPosition,
                            getPositionFromTime(lastPoint.pos, SettingsMaster.getMiddle()), midY + newGraphPosition);
                } else {
                    sr.line(getPositionFromTime(timing.pos, SettingsMaster.getMiddle()), midY + newGraphPosition,
                            getPositionFromTime(lastPoint.pos, SettingsMaster.getMiddle()), midY + graphPosition);
                }
            }
            lastPoint = timing;
            graphPosition = newGraphPosition;

            if (timingPointIterator.hasNext()) {
                timing = timingPointIterator.next().get(0);
            }
            else {
                timing = null;
            }
        }

        //Iteration goes in reverse order, so the last point checked is the earliest point
        if (lastPoint != null && atStart(lastPoint.pos))
        {
            //sv at start of map before first timing point is fixed at 1x
            sr.line(0, midY,
                    getPositionFromTime(lastPoint.pos, SettingsMaster.getMiddle()), midY);
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

            renderLabels(sb, focus);
        }
    }

    private void renderLabels(SpriteBatch sb, TimingPoint adjust) {
        long lastRenderable = adjust == null ? 0 : adjust.pos + (long)(LABEL_SPACING / viewScale);
        double svLabelSpacing = 0;
        double bpmLabelSpacing = 0;

        TimingPoint effect = null, timing = null, lastPoint = null;

        Iterator<ArrayList<TimingPoint>> effectPointIterator = map.getEditEffectPoints().values().iterator();
        Iterator<ArrayList<TimingPoint>> timingPointIterator = map.getVisibleTimingPoints((int) time - EditorLayer.viewTime, (int) time + EditorLayer.viewTime).values().iterator();

        if (effectPointIterator.hasNext())
            effect = effectPointIterator.next().get(0);
        if (timingPointIterator.hasNext()) {
            timing = timingPointIterator.next().get(0);
        }

        while (effect != null && timing != null) { //SV+TIMING
            //Timing labels don't care about selected effect point
            if (timing.pos > effect.pos) { //Timing next
                if (lastPoint == null) { //First point.
                    renderLabel(sb, bpmFormat.format(timing.getBPM()),
                            (int) (SettingsMaster.getMiddle() + (timing.pos - time) * viewScale + 4), bottom + 50);
                    renderLabel(sb, twoDecimal.format(1),
                            (int) (SettingsMaster.getMiddle() + (effect.pos - time) * viewScale + 4), bottom + 259);
                    bpmLabelSpacing = LABEL_SPACING;
                    svLabelSpacing = LABEL_SPACING;
                }
                else {
                    bpmLabelSpacing -= (lastPoint.pos - timing.pos) * viewScale;
                    svLabelSpacing -= (lastPoint.pos - timing.pos) * viewScale;

                    if (bpmLabelSpacing <= 0)
                    {
                        renderLabel(sb, bpmFormat.format(timing.getBPM()),
                                (int) (SettingsMaster.getMiddle() + (timing.pos - time) * viewScale + 4), bottom + 50);
                        bpmLabelSpacing = LABEL_SPACING;
                    }
                    if (svLabelSpacing <= 0)
                    {
                        renderLabel(sb, twoDecimal.format(1),
                                (int) (SettingsMaster.getMiddle() + (timing.pos - time) * viewScale + 4), bottom + 259);
                        svLabelSpacing = LABEL_SPACING;
                    }
                }
                lastPoint = timing;

                if (timingPointIterator.hasNext()) {
                    timing = timingPointIterator.next().get(0);
                }
                else {
                    timing = null;
                }
            }
            else if (effect.pos > timing.pos) {
                if (adjust != null && effect.pos <= lastRenderable) { //selected object special case
                    if (effect.equals(adjust)) {
                        renderAdjustableLabel(sb, effect,
                                (int) (SettingsMaster.getMiddle() + (effect.pos - time) * viewScale + 4), bottom + 259);
                        svLabelSpacing = LABEL_SPACING;
                        adjust = null;
                    }
                }
                else if (lastPoint == null) {
                    renderLabel(sb, twoDecimal.format(effect.value),
                            (int) (SettingsMaster.getMiddle() + (effect.pos - time) * viewScale + 4), bottom + 259);
                    svLabelSpacing = LABEL_SPACING;
                }
                else {
                    bpmLabelSpacing -= (lastPoint.pos - effect.pos) * viewScale;
                    svLabelSpacing -= (lastPoint.pos - effect.pos) * viewScale;
                    if (svLabelSpacing <= 0)
                    {
                        renderLabel(sb, twoDecimal.format(effect.value),
                                (int) (SettingsMaster.getMiddle() + (effect.pos - time) * viewScale + 4), bottom + 259);
                        svLabelSpacing = LABEL_SPACING;
                    }
                }
                lastPoint = effect;

                if (effectPointIterator.hasNext()) {
                    effect = effectPointIterator.next().get(0);
                }
                else {
                    effect = null;
                }
            }
            else { //effect and timing point stacked
                if (adjust != null && effect.pos <= lastRenderable) {
                    //timing point, handled normally
                    if (lastPoint == null)
                        renderLabel(sb, bpmFormat.format(timing.getBPM()),
                                (int) (SettingsMaster.getMiddle() + (timing.pos - time) * viewScale + 4), bottom + 50);
                    else {
                        bpmLabelSpacing -= (lastPoint.pos - effect.pos) * viewScale;
                        if (bpmLabelSpacing <= 0) {
                            renderLabel(sb, bpmFormat.format(timing.getBPM()),
                                    (int) (SettingsMaster.getMiddle() + (timing.pos - time) * viewScale + 4), bottom + 50);
                            bpmLabelSpacing = LABEL_SPACING;
                        }
                    }

                    //effect point
                    if (effect.equals(adjust)) {
                        renderAdjustableLabel(sb, effect,
                                (int) (SettingsMaster.getMiddle() + (effect.pos - time) * viewScale + 4), bottom + 259);
                        svLabelSpacing = LABEL_SPACING;
                        adjust = null;
                    }
                }
                else if (lastPoint == null) {
                    renderLabel(sb, bpmFormat.format(timing.getBPM()),
                            (int) (SettingsMaster.getMiddle() + (timing.pos - time) * viewScale + 4), bottom + 50);
                    renderLabel(sb, twoDecimal.format(effect.value),
                            (int) (SettingsMaster.getMiddle() + (effect.pos - time) * viewScale + 4), bottom + 259);
                    svLabelSpacing = LABEL_SPACING;
                    bpmLabelSpacing = LABEL_SPACING;
                }
                else {
                    bpmLabelSpacing -= (lastPoint.pos - effect.pos) * viewScale;
                    svLabelSpacing -= (lastPoint.pos - effect.pos) * viewScale;
                    if (bpmLabelSpacing <= 0) {
                        renderLabel(sb, bpmFormat.format(timing.getBPM()),
                                (int) (SettingsMaster.getMiddle() + (timing.pos - time) * viewScale + 4), bottom + 50);
                        bpmLabelSpacing = LABEL_SPACING;
                    }
                    if (svLabelSpacing <= 0)
                    {
                        renderLabel(sb, twoDecimal.format(effect.value),
                                (int) (SettingsMaster.getMiddle() + (effect.pos - time) * viewScale + 4), bottom + 259);
                        svLabelSpacing = LABEL_SPACING;
                    }
                }
                lastPoint = effect;

                if (effectPointIterator.hasNext())
                    effect = effectPointIterator.next().get(0);
                else
                    effect = null;

                if (timingPointIterator.hasNext())
                    timing = timingPointIterator.next().get(0);
                else
                    timing = null;
            }
        }
        //Just sv left
        while (effect != null) {
            if (adjust != null && effect.pos <= lastRenderable) {
                if (effect.equals(adjust)) {
                    renderAdjustableLabel(sb, effect,
                            (int) (SettingsMaster.getMiddle() + (effect.pos - time) * viewScale + 4), bottom + 259);
                    svLabelSpacing = LABEL_SPACING;
                    adjust = null;
                }
            }
            else if (lastPoint == null) {
                renderLabel(sb, twoDecimal.format(effect.value),
                        (int) (SettingsMaster.getMiddle() + (effect.pos - time) * viewScale + 4), bottom + 259);
                svLabelSpacing = LABEL_SPACING;
            }
            else {
                bpmLabelSpacing -= (lastPoint.pos - effect.pos) * viewScale;
                svLabelSpacing -= (lastPoint.pos - effect.pos) * viewScale;
                if (svLabelSpacing <= 0)
                {
                    renderLabel(sb, twoDecimal.format(effect.value),
                            (int) (SettingsMaster.getMiddle() + (effect.pos - time) * viewScale + 4), bottom + 259);
                    svLabelSpacing = LABEL_SPACING;
                }
            }
            lastPoint = effect;

            if (effectPointIterator.hasNext()) {
                effect = effectPointIterator.next().get(0);
            }
            else {
                effect = null;
            }
        }
        //Just timing points left
        while (timing != null) {
            if (lastPoint == null) {
                renderLabel(sb, bpmFormat.format(timing.getBPM()),
                        (int) (SettingsMaster.getMiddle() + (timing.pos - time) * viewScale + 4), bottom + 50);
                renderLabel(sb, twoDecimal.format(1),
                        (int) (SettingsMaster.getMiddle() + (timing.pos - time) * viewScale + 4), bottom + 259);
                bpmLabelSpacing = LABEL_SPACING;
                svLabelSpacing = LABEL_SPACING;
            }
            else {
                bpmLabelSpacing -= (lastPoint.pos - timing.pos) * viewScale;
                svLabelSpacing -= (lastPoint.pos - timing.pos) * viewScale;

                if (bpmLabelSpacing <= 0)
                {
                    renderLabel(sb, bpmFormat.format(timing.getBPM()),
                            (int) (SettingsMaster.getMiddle() + (timing.pos - time) * viewScale + 4), bottom + 50);
                    bpmLabelSpacing = LABEL_SPACING;
                }
                if (svLabelSpacing <= 0)
                {
                    renderLabel(sb, twoDecimal.format(1),
                            (int) (SettingsMaster.getMiddle() + (timing.pos - time) * viewScale + 4), bottom + 259);
                    svLabelSpacing = LABEL_SPACING;
                }
            }
            lastPoint = timing;

            if (timingPointIterator.hasNext()) {
                timing = timingPointIterator.next().get(0);
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
        textRenderer.setFont(font).renderText(sb, Color.WHITE, twoDecimal.format(p.value), x, y);
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
        if (map.timingPoints.containsKey(o.pos) && o instanceof TimingPoint) {
            ((TimingPoint) o).renderYellow(sb, sr, time, viewScale, SettingsMaster.getMiddle(), bottom, alpha);
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
    public void registerValueChange(double totalChange) {
        if (selectedObjects != null)
        {
            PositionalObjectTreeMap<PositionalObject> movementCopy = new PositionalObjectTreeMap<>();
            movementCopy.addAll(selectedObjects); //use addAll to make a copy without sharing any references other than the positionalobjects themselves
            this.map.registerValueChange(movementCopy, totalChange);
            this.map.updateEffectPoints(movementCopy, movementCopy);
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
    public PositionalObject clickObject(int x, int y) {
        //Check if y location is on sv label area
        //If so, allow wider x area for clicking.
        endAdjust();

        NavigableMap<Long, ArrayList<TimingPoint>> selectable = map.getEditEffectPoints();
        if (selectable == null || y < bottom || y > top)
            return null;

        double time = getTimeFromPosition(x);

        if (y > bottom + SV_LABEL_BOTTOM && y < bottom + SV_LABEL_TOP) {
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
    public boolean clickedEnd(PositionalObject o, int x) {
        return false;
    }

    public void testNewLimit(double newLimit)
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

    private static final Toolset toolset = new Toolset(SelectionTool.get(), GreenLineTool.get(), SVFunctionTool.get());
    public static Toolset getToolset()
    {
        return toolset;
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
        textInput = precise.format(p.value);
        blipOffsetX = textRenderer.setFont(font).getWidth(textInput) + BLIP_BUFFER;
        renderBlip = true;
        blipTimer = 0.4f;
        changed = false;
        EditorLayer.processor.setTextReceiver(this);
    }
    private void endAdjust() {
        if (adjustMode == AdjustMode.ACTIVE && adjustPoint != null && changed && selectedObjects != null) {
            try {
                double nSv = Double.parseDouble(textInput);
                //parent.showText("Set new SV: " + nSv);

                map.registerChange(new ValueSetChange(map, selectedObjects, nSv).perform());

                if (nSv <= 0) {
                    parent.showText("Invalid value entered.");
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
                ((c == ',' || c == '.') && GeneralUtils.charCount(textInput, '.') < 1) ||
                ((c == 'e' || c == 'E') && GeneralUtils.charCount(textInput, 'E') < 1);
    }

    @Override
    public int getCharLimit() {
        return 12;
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

    private static final DecimalFormat twoDecimal = new DecimalFormat("##0.##x");
    private static final DecimalFormat bpmFormat = new DecimalFormat("##0.## BPM");
    private static final DecimalFormat precise = new DecimalFormat("##0.0##");

    private static final Color red = Color.RED.cpy();
    private static final Color green = new Color(0.25f, 0.75f, 0.0f, 1.0f);
    private static final Color yellow = new Color(0.8f, 0.8f, 0.0f, 1.0f);

    private static final Color selection = new Color(1.0f, 0.6f, 0.0f, 1.0f);
}
