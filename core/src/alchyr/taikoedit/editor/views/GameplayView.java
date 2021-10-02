package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.MapChange;
import alchyr.taikoedit.editor.tools.*;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;

import java.util.*;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.core.layers.EditorLayer.viewScale;


/* TODO LIST
 *
 * On map change, update necessary data
 */
public class GameplayView extends MapView {
    //Objects that should be rendered are all those where given a current time, their start time is before it and their end time is after it.
    private TreeMap<Long, ArrayList<HitObject>> startTimes; //A sorted set based on object start times
    private TreeMap<Long, ArrayList<HitObject>> endTimes; //A sorted set based on object end times
    private HashMap<HitObject, Long> oldEndTimes; //for getting end times of objects to remove them when they're updated.
    //Start times do not need a similar list since they are stored on objects and will only be updated in this class.

    TreeMap<Long, Snap> barlineStartTimes; //Sorted maps for purpose of determining which snaps to render.
    TreeMap<Long, Snap> barlineEndTimes;
    HashMap<Snap, Long> barlineStartMap; //The same snaps are used in every gameplay view, so they have to be tracked outside of the snaps themselves.

    public static final int HEIGHT = 150;
    public static final int HALF_HEIGHT = HEIGHT / 2;
    private static final int HIT_AREA_SIZE = 100;
    private static final int HIT_AREA_HALF = HIT_AREA_SIZE / 2;

    private static final float VISIBLE_LENGTH = SettingsMaster.getWidth() * 1.5f;

    private static final int HIT_AREA_X = 200;
    private static final int HIT_AREA_DRAW_X = HIT_AREA_X - HIT_AREA_HALF;

    private static final float SCROLL_SPEED_SCALE = 240;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.9f); //more opacity than other types of view
    private static final Color hitAreaColor = new Color(1.0f, 1.0f, 1.0f, 0.6f);
    protected Texture hitArea = assetMaster.get("editor:hitarea");

    //Base position values
    private int baseObjectY = 0;

    //Offset
    private int objectY = 0;
    private int hitAreaY = 0;

    public GameplayView(EditorLayer parent, EditorBeatmap beatmap) {
        super(MapView.ViewType.GAMEPLAY_VIEW, parent, beatmap, HEIGHT);
        lastSounded = 0;

        addOverlayButton(new ImageButton(assetMaster.get("editor:exit"), assetMaster.get("editor:exith"), this::close).setAction("Close View"));

        startTimes = new TreeMap<>();
        endTimes = new TreeMap<>();
        oldEndTimes = new HashMap<>();

        /*objectStartTimes = HashBiMap.create(beatmap.objects.size());
        objectEndTimes = HashBiMap.create(beatmap.objects.size());*/

        barlineStartTimes = new TreeMap<>();
        barlineEndTimes = new TreeMap<>();
        barlineStartMap = new HashMap<>();

        calculateTimes();
    }


    private void calculateTimes() {
        Iterator<Map.Entry<Long, ArrayList<HitObject>>> objectIterator = map.objects.entrySet().iterator();
        Iterator<Map.Entry<Long, Snap>> snapIterator = map.getBarlineSnaps().entrySet().iterator();

        //Sv tracking variables
        long lastTimingPos = -1, lastEffectPos = -1, startTime;
        Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> timing = null, effect = null;
        Map.Entry<Long, Snap> nextSnap = null;
        Map.Entry<Long, ArrayList<HitObject>> stack = null;
        Map.Entry<Long, ArrayList<TimingPoint>> nextTiming = null, nextEffect = null;
        float baseSV = map.getBaseSV();
        double svRate = baseSV, currentBPM;

        timing = map.timingPoints.entrySet().iterator();
        effect = map.effectPoints.entrySet().iterator();

        if (timing.hasNext()) //First timing point
        {
            nextTiming = timing.next();
            currentBPM = nextTiming.getValue().get(0).value;
            if (timing.hasNext())
                nextTiming = timing.next();
            else
                nextTiming = null; //Only one timing point.
        }
        else
        {
            //what the fuck why are there no timing points >:(
            currentBPM = 120; //This is what osu uses as default so it's what I'm gonna use. Though really, there shouldn't be any objects if there's no timing points.
        }

        if (effect.hasNext())
            nextEffect = effect.next(); //First SV doesn't apply until the first timing point is reached.

        if (objectIterator.hasNext())
            stack = objectIterator.next();
        if (snapIterator.hasNext())
            nextSnap = snapIterator.next();

        while (stack != null || nextSnap != null) {
            if (nextSnap != null && (stack == null || nextSnap.getKey() <= stack.getKey())) {
                //next is a barline
                while (nextTiming != null && nextTiming.getKey() <= nextSnap.getKey())// + 1)
                {
                    currentBPM = nextTiming.getValue().get(nextTiming.getValue().size() - 1).value;
                    lastTimingPos = nextTiming.getKey();
                    svRate = baseSV; //return to base sv

                    if (timing.hasNext())
                        nextTiming = timing.next();
                    else
                        nextTiming = null;
                }
                while (nextEffect != null && nextEffect.getKey() <= nextSnap.getKey())// + 1)
                {
                    lastEffectPos = nextEffect.getKey();
                    svRate = baseSV * nextEffect.getValue().get(nextEffect.getValue().size() - 1).value;

                    if (effect.hasNext())
                        nextEffect = effect.next();
                    else
                        nextEffect = null;
                }
                if (lastEffectPos < lastTimingPos)
                {
                    svRate = baseSV; //return to base sv
                }

                //Calculate start and end times

                if (svRate <= 0) { //0 bpm dumb
                    barlineStartMap.put(nextSnap.getValue(), Long.MIN_VALUE);
                }
                else {
                    //currentBPM - ms gap between beats. High value = lower bpm = lower speed = higher duration
                    //svRate - multiplier of scroll speed

                    //speed in pixels per ms - about 320 * svRate / currentBPM
                    //visible pixels / pixels per ms = ms duration
                    startTime = nextSnap.getKey() - (long) (VISIBLE_LENGTH / (SCROLL_SPEED_SCALE * svRate / currentBPM));

                    if (startTime == nextSnap.getKey()) //some absurdly high sv value that makes it instant could result in division by 0
                        startTime -= 1;

                    barlineStartMap.put(nextSnap.getValue(), startTime);
                }
                //for convenience, barlines will just vanish at their time
                barlineStartTimes.put(barlineStartMap.get(nextSnap.getValue()), nextSnap.getValue());

                if (snapIterator.hasNext())
                    nextSnap = snapIterator.next();
                else
                    nextSnap = null;
            }
            else { //next is objects
                while (nextTiming != null && nextTiming.getKey() <= stack.getKey())// + 1)
                {
                    currentBPM = nextTiming.getValue().get(nextTiming.getValue().size() - 1).value;
                    lastTimingPos = nextTiming.getKey();
                    svRate = baseSV; //return to base sv

                    if (timing.hasNext())
                        nextTiming = timing.next();
                    else
                        nextTiming = null;
                }
                while (nextEffect != null && nextEffect.getKey() <= stack.getKey())// + 1)
                {
                    lastEffectPos = nextEffect.getKey();
                    svRate = baseSV * nextEffect.getValue().get(nextEffect.getValue().size() - 1).value;

                    if (effect.hasNext())
                        nextEffect = effect.next();
                    else
                        nextEffect = null;
                }
                if (lastEffectPos < lastTimingPos)
                {
                    svRate = baseSV; //return to base sv
                }

                //Calculate start and end times

                if (svRate <= 0) { //0 bpm dumb
                    startTime = Long.MIN_VALUE;
                }
                else {
                    //currentBPM - ms gap between beats. High value = lower bpm = lower speed = higher duration
                    //svRate - multiplier of scroll speed

                    //speed in pixels per ms - about 320 * svRate / currentBPM
                    //visible pixels / pixels per ms = ms duration
                    startTime = stack.getKey() - (long) (VISIBLE_LENGTH / (SCROLL_SPEED_SCALE * svRate / currentBPM));

                    if (startTime == stack.getKey()) //some absurdly high sv value that makes it instant could result in division by 0
                        startTime -= 1;
                }

                if (!startTimes.containsKey(startTime)) {
                    startTimes.put(startTime, new ArrayList<>());
                }

                for (HitObject h : stack.getValue()) {
                    h.gameplayStart = startTime;

                    startTimes.get(h.gameplayStart).add(h);
                    endTimes.compute(h.getEndPos(), (k, v) -> {
                        if (v == null) {
                            ArrayList<HitObject> list = new ArrayList<>();
                            list.add(h);
                            return list;
                        }
                        else {
                            v.add(h);
                            return v;
                        }
                    });
                    oldEndTimes.put(h, h.getEndPos());
                }

                if (objectIterator.hasNext())
                    stack = objectIterator.next();
                else
                    stack = null;
            }
        }
    }


    private double lastSounded; //purely for audio in primaryUpdate
    @Override
    public void primaryUpdate(boolean isPlaying) {
        if (isPrimary && isPlaying && lastSounded < time) //might have skipped backwards
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
    public void update(double exactPos, long msPos) {
        super.update(exactPos, msPos);
    }

    @Override
    public void renderBase(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, bottom, SettingsMaster.getWidth(), height);

        sb.setColor(hitAreaColor);
        sb.draw(hitArea, HIT_AREA_DRAW_X, hitAreaY);

        //Divisors.
        for (Snap s : barlines)
        {
            s.render(sb, sr, s.pos, viewScale, //Very Beautiful type casting here for the purpose of limiting positioning to whole values, to mimic rendering of game. This makes certain barline gimmicks work.
                    HIT_AREA_X + Interpolation.linear.apply(VISIBLE_LENGTH, 0, (float)((double)((long)time - barlineStartMap.get(s)) / ((long)s.pos - barlineStartMap.get(s)))), bottom,
                    HEIGHT);
        }
    }

    @Override
    public void renderObject(PositionalObject o, SpriteBatch sb, ShapeRenderer sr, float alpha) {
        if (!(o instanceof HitObject))
            return;

        HitObject h = (HitObject) o;
        //Calculate position based on start and end time?

        //% progress based on time, relative to start and end time:
        //time = end time, progress is 1
        //time = start time, progress is 0
        //remaining time * speed = distance from hit zone
        //end time is always pos, the start time of the object.
        h.gameplayRender(sb, sr, viewScale, HIT_AREA_X, Interpolation.linear.apply(VISIBLE_LENGTH, 0, (float) ((time - h.gameplayStart) / (h.pos - h.gameplayStart))), objectY, alpha);
    }
    @Override
    public void renderSelection(PositionalObject o, SpriteBatch sb, ShapeRenderer sr) {
    }

    PositionalObjectTreeMap<PositionalObject> visibleObjects = new PositionalObjectTreeMap<>();
    long lastPos = Long.MIN_VALUE;
    private final ArrayList<Snap> barlines = new ArrayList<>();
    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> prep(long pos) {
        Iterator<Map.Entry<Long, ArrayList<PositionalObject>>> objectIterator = visibleObjects.entrySet().iterator();
        Map.Entry<Long, ArrayList<PositionalObject>> stack;

        if (lastPos < pos) { //moved forward. Can check just a few.
            //Remove expired objects
            while (objectIterator.hasNext()) {
                stack = objectIterator.next();

                if (((HitObject) stack.getValue().get(0)).getEndPos() < pos) {
                    objectIterator.remove();
                }
            }
            barlines.removeIf((snap)->snap.pos < pos);

            for (ArrayList<HitObject> hits : startTimes.subMap(lastPos, false, pos, true).values()) {
                for (HitObject h : hits)
                    if (h.getEndPos() >= pos)
                        visibleObjects.add(h);
            }
            for (Snap s : barlineStartTimes.subMap(lastPos, false, pos, true).values()) {
                if (s.pos >= pos)
                    barlines.add(s);
            }
        }
        else if (pos < lastPos) {
            //Moved backwards.
            while (objectIterator.hasNext()) {
                stack = objectIterator.next();

                if (((HitObject) stack.getValue().get(0)).gameplayStart > pos) { //objects that aren't active yet
                    objectIterator.remove();
                }
            }
            barlines.removeIf((snap)->barlineStartMap.get(snap) > pos);

            for (ArrayList<HitObject> hits : endTimes.subMap(pos, true, lastPos, false).values()) {
                for (HitObject h : hits)
                    if (h.gameplayStart <= pos)
                        visibleObjects.add(h);
            }
            for (Snap s : map.getBarlineSnaps().subMap(pos, true, lastPos, false).values()) {
                if (barlineStartMap.get(s) <= pos)
                    barlines.add(s);
            }
        }

        lastPos = pos;
        return visibleObjects.descendingMap(); //descending version to ensure reverse rendering order for correct overlapping
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

    public void close(int button)
    {
        if (button == Input.Buttons.LEFT)
        {
            parent.removeView(this);
        }
    }

    @Override
    public int setPos(int y) {
        super.setPos(y);

        baseObjectY = this.y + HALF_HEIGHT;

        return this.y;
    }

    public void setOffset(int offset)
    {
        super.setOffset(offset);

        objectY = baseObjectY + yOffset;
        hitAreaY = baseObjectY + yOffset - HIT_AREA_HALF;
    }

    @Override
    public float getBasePosition() {
        return HIT_AREA_X;
    }

    @Override
    public void dispose() {
        super.dispose();
    }




    /* Irrelevant Methods */

    @Override
    public void selectAll() {

    }

    @Override
    public boolean hasSelection() {
        return false;
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> getVisibleRange(long start, long end) {
        return null; //only used by Selection tool, which isn't supported on gameplay view.
    }

    @Override
    public String getSelectionString() {
        return "";
    }

    @Override
    public void addSelectionRange(long startTime, long endTime) {

    }

    @Override
    public PositionalObject clickObject(int x, int y) {
        return null;
    }

    @Override
    public boolean clickedEnd(PositionalObject o, int x) {
        return false;
    }

    @Override
    public PositionalObjectTreeMap<?> getEditMap() {
        return map.objects;
    }

    @Override
    public boolean noSnaps() {
        return map.getCurrentSnaps().isEmpty();
    }

    @Override //will never do anything since it's based on clickObject, which always returns null
    public void deleteObject(PositionalObject o) {
        this.map.delete(MapChange.ChangeType.OBJECTS, o);
    }

    @Override
    public void pasteObjects(PositionalObjectTreeMap<PositionalObject> copyObjects) {

    }

    @Override
    public void reverse() {
        if (!hasSelection())
            return;

        this.map.reverse(MapChange.ChangeType.OBJECTS, true, selectedObjects);
        refreshSelection();
    }

    @Override
    public void deleteSelection() {
        if (selectedObjects != null)
        {
            this.map.delete(MapChange.ChangeType.OBJECTS, selectedObjects);
            clearSelection();
        }
    }

    @Override
    public void registerMove(long totalMovement) {
        if (selectedObjects != null)
        {
            PositionalObjectTreeMap<PositionalObject> movementCopy = new PositionalObjectTreeMap<>();
            movementCopy.addAll(selectedObjects); //use addAll to make a copy without sharing any references other than the positionalobjects themselves
            this.map.registerMovement(MapChange.ChangeType.OBJECTS, movementCopy, totalMovement);
        }
    }

    @Override
    public double getTimeFromPosition(int x) {
        return getTimeFromPosition(x, HIT_AREA_X);
    }

    private static final Toolset toolset = new Toolset();
    public static Toolset getToolset()
    {
        return toolset;
    }
}
