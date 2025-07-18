package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.editor.tools.Toolset;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.EditorTime;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.core.layers.EditorLayer.viewScale;
import static alchyr.taikoedit.management.assets.skins.Skins.currentSkin;


/* TODO LIST
 *
 * Spinners should fade in, not stay permanently
 */
public class GameplayView extends MapView {
    public static final String ID = "gmp";
    @Override
    public String typeString() {
        return ID;
    }


    private final ReentrantLock calculationLock = new ReentrantLock();

    //Objects that should be rendered are all those where given a current time, their start time is before it and their end time is after it.
    private final TreeMap<Long, ArrayList<HitObject>> startTimes; //A sorted set based on object start times
    private final TreeMap<Long, ArrayList<HitObject>> endTimes; //A sorted set based on object end times
    private final TreeMap<Long, Float> svMap; //using for scaling sliders
    //private HashMap<HitObject, Long> oldEndTimes; //for getting end times of objects to remove them when they're updated.
    //Start times do not need a similar list since they are stored on objects and will only be updated in this class.

    TreeMap<Long, Snap> barlineStartTimes; //Sorted snaps for purpose of determining which barlines to render.
    TreeMap<Long, Snap> barlineEndTimes;
    HashMap<Snap, Long> barlineStartMap; //The same snaps are used in every gameplay view, so they have to be tracked outside of the snaps themselves (since different difficulties could have different sv on their barlines)

    public static final int HEIGHT = 150;
    public static final int HALF_HEIGHT = HEIGHT / 2;
    private static final int HIT_AREA_SIZE = 100;
    private static final int HIT_AREA_HALF = HIT_AREA_SIZE / 2;

    private static final float VISIBLE_LENGTH = SettingsMaster.getWidth() * 1.5f;

    private static final int HIT_AREA_X = 200;
    private static final int HIT_AREA_DRAW_X = HIT_AREA_X + 1;

    private static final float SCROLL_SPEED_SCALE = 245;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.9f); //more opacity than other types of view

    //Base position values
    private int baseObjectY = 0;

    //Offset
    private int objectY = 0;

    private boolean autoRefresh = true;

    public GameplayView(EditorLayer parent, EditorBeatmap beatmap) {
        super(MapView.ViewType.GAMEPLAY_VIEW, parent, beatmap, HEIGHT);
        lastSounded = 0;

        addOverlayButton(new ImageButton(assetMaster.get("editor:exit"), assetMaster.get("editor:exith")).setClick(this::close).setAction("Close View"));
        addOverlayButton(new ImageButton(assetMaster.get("editor:refresh"), assetMaster.get("editor:refreshh")).setClick(()->TaikoEditor.onMain(this::calculateTimes)).setAction("Refresh"));

        Texture aron = assetMaster.get("editor:arefreshon");
        Texture aronh = assetMaster.get("editor:arefreshonh");
        Texture aroff = assetMaster.get("editor:arefreshoff");
        Texture aroffh = assetMaster.get("editor:arefreshoffh");
        ImageButton autoRefreshButton = new ImageButton(aron, aronh).setAction("Disable Auto Refresh");
        autoRefreshButton.setClick(()->{
            if (autoRefreshButton.action.equals("Disable Auto Refresh")) {
                autoRefreshButton.setTextures(aroff, aroffh);
                autoRefreshButton.setAction("Enable Auto Refresh");
                this.autoRefresh = false;
            }
            else {
                autoRefreshButton.setTextures(aron, aronh);
                autoRefreshButton.setAction("Disable Auto Refresh");
                this.autoRefresh = true;
            }
        });
        addOverlayButton(autoRefreshButton);
        addLockPositionButton();

        startTimes = new TreeMap<>();
        endTimes = new TreeMap<>();
        svMap = new TreeMap<>();
        //oldEndTimes = new HashMap<>();

        /*objectStartTimes = HashBiMap.create(beatmap.objects.size());
        objectEndTimes = HashBiMap.create(beatmap.objects.size());*/

        barlineStartTimes = new TreeMap<>();
        barlineEndTimes = new TreeMap<>();
        barlineStartMap = new HashMap<>();

        calculateTimes();
    }

    public boolean autoRefresh() {
        return autoRefresh;
    }

    public void calculateTimes() {
        calculationLock.lock();
        {
            startTimes.clear();
            endTimes.clear();
            svMap.clear();
            barlineStartTimes.clear();
            barlineEndTimes.clear();
            barlineStartMap.clear();

            boolean reprep = !visibleObjects.isEmpty() || !barlines.isEmpty();
            visibleObjects.clear();
            barlines.clear();
            lastPos = Long.MIN_VALUE;

            Iterator<Map.Entry<Long, ArrayList<HitObject>>> objectIterator = map.objects.entrySet().iterator();
            Iterator<Map.Entry<Long, Snap>> snapIterator = map.getBarlineSnaps().entrySet().iterator();

            //Sv tracking variables
            long lastTimingPos = Long.MIN_VALUE, lastEffectPos = Long.MIN_VALUE, startTime;
            Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> timing, effect;
            Map.Entry<Long, Snap> nextSnap = null;
            Map.Entry<Long, ArrayList<HitObject>> stack = null;
            Map.Entry<Long, ArrayList<TimingPoint>> nextTiming = null, nextEffect = null;
            float baseSV = map.getBaseSV();
            double svRate = baseSV, currentBPM;

            TimingPoint temp;

            timing = map.timingPoints.entrySet().iterator();
            effect = map.effectPoints.entrySet().iterator();

            if (timing.hasNext()) //First timing point
            {
                nextTiming = timing.next();
                currentBPM = nextTiming.getValue().get(0).value;
                svMap.put(Long.MIN_VALUE, (float) (SCROLL_SPEED_SCALE * baseSV / currentBPM));
                if (timing.hasNext())
                    nextTiming = timing.next();
                else
                    nextTiming = null; //Only one timing point.
            } else {
                //what the fuck why are there no timing points >:(
                currentBPM = 120; //This is what osu uses as default so it's what I'm gonna use. Though really, there shouldn't be any objects if there's no timing points.
            }

            if (effect.hasNext())
                nextEffect = effect.next(); //First SV doesn't apply until the first timing point is reached because game Dumb.

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
                        svMap.put(lastTimingPos, (float) (SCROLL_SPEED_SCALE * svRate / currentBPM));

                        if (timing.hasNext())
                            nextTiming = timing.next();
                        else
                            nextTiming = null;
                    }
                    while (nextEffect != null && nextEffect.getKey() <= nextSnap.getKey())// + 1)
                    {
                        lastEffectPos = nextEffect.getKey();
                        temp = nextEffect.getValue().get(nextEffect.getValue().size() - 1);
                        svRate = baseSV * temp.value;
                        svMap.put(lastEffectPos, (float) (SCROLL_SPEED_SCALE * svRate / currentBPM));

                        if (effect.hasNext())
                            nextEffect = effect.next();
                        else
                            nextEffect = null;
                    }
                    if (lastEffectPos < lastTimingPos) {
                        svRate = baseSV; //return to base sv
                    }

                    //Calculate start and end times

                    if (svRate <= 0) { //0 bpm dumb
                        barlineStartMap.put(nextSnap.getValue(), Long.MIN_VALUE);
                    } else {
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
                } else { //next is objects
                    while (nextTiming != null && nextTiming.getKey() <= stack.getKey())// + 1)
                    {
                        currentBPM = nextTiming.getValue().get(nextTiming.getValue().size() - 1).value;
                        lastTimingPos = nextTiming.getKey();
                        svRate = baseSV; //return to base sv
                        svMap.put(lastTimingPos, (float) (SCROLL_SPEED_SCALE * svRate / currentBPM));

                        if (timing.hasNext())
                            nextTiming = timing.next();
                        else
                            nextTiming = null;
                    }
                    while (nextEffect != null && nextEffect.getKey() <= stack.getKey())// + 1)
                    {
                        lastEffectPos = nextEffect.getKey();
                        temp = nextEffect.getValue().get(nextEffect.getValue().size() - 1);
                        svRate = baseSV * temp.value;
                        svMap.put(lastEffectPos, (float) (SCROLL_SPEED_SCALE * svRate / currentBPM));

                        if (effect.hasNext())
                            nextEffect = effect.next();
                        else
                            nextEffect = null;
                    }
                    if (lastEffectPos < lastTimingPos) {
                        svRate = baseSV; //return to base sv
                    }

                    //Calculate start and end times

                    if (svRate <= 0) { //0 bpm dumb
                        startTime = Long.MIN_VALUE;
                    } else {
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
                            } else {
                                v.add(h);
                                return v;
                            }
                        });
                        //oldEndTimes.put(h, h.getEndPos());
                    }

                    if (objectIterator.hasNext())
                        stack = objectIterator.next();
                    else
                        stack = null;
                }
            }

            if (reprep) {
                prep(); //prevent flicker when clearing visual info for 1 frame
            }
        }
        calculationLock.unlock();
    }


    private long lastSounded; //purely for audio in primaryUpdate
    @Override
    public void primaryUpdate(boolean isPlaying) {
        if (isPrimary && lockOffset == 0 && isPlaying && lastSounded < time && time - lastSounded < 25) //might have skipped backwards, make sure didn't skip too far
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

        sb.setColor(currentSkin.gameplayHitAreaColor);
        currentSkin.hitArea.renderC(sb, sr, HIT_AREA_DRAW_X, objectY, currentSkin.normalScale, currentSkin.gameplayHitAreaColor);

        //Divisors.
        for (Snap s : barlines)
        {
            s.render(sb, sr, s.pos, viewScale, //Very Beautiful type casting here for the purpose of limiting positioning to whole values, to mimic rendering of game. This makes certain barline gimmicks work.
                    HIT_AREA_X + Interpolation.linear.apply(VISIBLE_LENGTH, 0, (float)((double)(time - barlineStartMap.get(s)) / (s.pos - barlineStartMap.get(s)))), bottom,
                    HEIGHT);
        }
    }

    @Override
    public void renderObject(MapObject o, SpriteBatch sb, ShapeRenderer sr, float alpha) {
        if (!(o instanceof HitObject))
            return;

        calculationLock.lock();

        HitObject h = (HitObject) o;
        //Calculate position based on start and end time?

        //% progress based on time, relative to start and end time:
        //time = end time, progress is 1
        //time = start time, progress is 0
        //remaining time * speed = distance from hit zone
        //end time is always pos, the start time of the object.
        if (h.type == HitObject.HitObjectType.SPINNER && h.getGameplayEndPos() - h.gameplayStart > 5000)
            //Dunno osu's logic to decide when to fade spinners in, this is merely an approximation for convenience
            alpha *= 1 - MathUtils.clamp(((h.getPos() - preciseTime) - 1000) / 500.0, 0.0, 1.0);

        h.gameplayRender(sb, sr, svMap.floorEntry(h.getPos()).getValue(), HIT_AREA_X, Interpolation.linear.apply(VISIBLE_LENGTH, 0, (float) ((preciseTime - h.gameplayStart) / (h.getPos() - h.gameplayStart))), objectY, alpha);

        calculationLock.unlock();
    }
    @Override
    public void renderSelection(MapObject o, SpriteBatch sb, ShapeRenderer sr) {
    }

    MapObjectTreeMap<MapObject> visibleObjects = new MapObjectTreeMap<>();
    long lastPos = Long.MIN_VALUE;
    private final ArrayList<Snap> barlines = new ArrayList<>();
    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends MapObject>> prep() {
        calculationLock.lock();

        Iterator<Map.Entry<Long, ArrayList<MapObject>>> objectIterator = visibleObjects.entrySet().iterator();
        Map.Entry<Long, ArrayList<MapObject>> stack;

        if (lastPos < time) { //moved forward. Can check just a few.
            //Remove expired objects
            while (objectIterator.hasNext()) {
                stack = objectIterator.next();

                if (((HitObject) stack.getValue().get(0)).getGameplayEndPos() < time) {
                    objectIterator.remove();
                }
            }
            barlines.removeIf((snap)->snap.pos+(snap.pos-barlineStartMap.getOrDefault(snap, 0L)) / 2 < time);

            for (ArrayList<HitObject> hits : startTimes.subMap(lastPos, false, time, true).values()) {
                for (HitObject h : hits)
                    if (h.getEndPos() >= time)
                        visibleObjects.add(h);
            }
            for (Snap s : barlineStartTimes.subMap(lastPos, false, time, true).values()) {
                if (s.pos >= time)
                    barlines.add(s);
            }
        }
        else if (time < lastPos) {
            //Moved backwards.
            while (objectIterator.hasNext()) {
                stack = objectIterator.next();

                if (((HitObject) stack.getValue().get(0)).gameplayStart > time) { //objects that aren't active yet
                    objectIterator.remove();
                }
            }
            barlines.removeIf((snap)->barlineStartMap.get(snap) > time);

            for (ArrayList<HitObject> hits : endTimes.subMap(time, true, lastPos, false).values()) {
                for (HitObject h : hits)
                    if (h.gameplayStart <= time)
                        visibleObjects.add(h);
            }
            for (Snap s : map.getBarlineSnaps().subMap(time, true, lastPos, false).values()) {
                Long startTime = barlineStartMap.get(s);
                if (startTime != null && startTime <= time)
                    barlines.add(s);
            }
        }

        calculationLock.unlock();

        lastPos = time;
        return visibleObjects.descendingMap(); //descending version to ensure reverse rendering order for correct overlapping
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
    }

    @Override
    public int getBasePosition() {
        return HIT_AREA_X;
    }

    @Override
    public void dispose() {
        super.dispose();
    }




    /* Irrelevant Methods */

    @Override
    public void selectAll() {
        clearSelection();
        selectObjects(map.objects);
    }

    @Override
    public boolean hasSelection() {
        return false;
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends MapObject>> getVisibleRange(long start, long end) {
        return null; //only used by Selection tool, which isn't supported on gameplay view.
    }

    @Override
    public String getSelectionString() {
        if (hasSelection())
        {
            MapObjectTreeMap<MapObject> selectedObjects = getSelection();

            StringBuilder sb = new StringBuilder(new EditorTime(selectedObjects.firstKey()).toString()).append(" (");

            int comboCount = 0;
            boolean foundStart = false;

            NavigableMap<Long, ArrayList<HitObject>> precedingObjects = map.objects.descendingSubMap(selectedObjects.firstKey(), true);

            //Find initial combo count
            for (Map.Entry<Long, ArrayList<HitObject>> entry : precedingObjects.entrySet())
            {
                for (HitObject h : entry.getValue())
                {
                    if (foundStart)
                    {
                        ++comboCount;
                        if (h.newCombo)
                            break;
                    }
                    else
                    {
                        if (h.equals(selectedObjects.firstEntry().getValue().get(0)))
                        {
                            foundStart = true;
                            comboCount = 0;
                            if (h.newCombo)
                                break;
                        }
                    }
                }
            }

            Iterator<Map.Entry<Long, ArrayList<HitObject>>> allObjects = map.objects.subMap(selectedObjects.firstKey(), true, selectedObjects.lastKey(), true).entrySet().iterator();
            Iterator<Map.Entry<Long, ArrayList<MapObject>>> selectionObjects = selectedObjects.entrySet().iterator();

            Map.Entry<Long, ArrayList<HitObject>> currentList = null;
            Map.Entry<Long, ArrayList<MapObject>> selectedObjectList = null;

            if (allObjects.hasNext())
                currentList = allObjects.next();

            if (selectionObjects.hasNext())
                selectedObjectList = selectionObjects.next();

            while (currentList != null && selectedObjectList != null)
            {
                if (currentList.getKey().equals(selectedObjectList.getKey())) //Position of lists match.
                {
                    for (HitObject h : currentList.getValue()) //For each object in map. Have to go through all of them to track combo count.
                    {
                        ++comboCount;
                        if (h.newCombo)
                            comboCount = 1;

                        if (selectedObjectList.getValue().contains(h)) //This is a selected object, so add it to text
                        {
                            sb.append(comboCount).append(",");
                        }
                    }
                    //This part of selected objects is done, move to next list in selected objects.
                    if (selectionObjects.hasNext())
                        selectedObjectList = selectionObjects.next();
                    else
                        selectedObjectList = null;
                }
                else //This list has no selected objects, just track combo.
                {
                    for (HitObject h : currentList.getValue())
                    {
                        if (h.newCombo)
                        {
                            comboCount = 1;
                        }
                        ++comboCount;
                    }
                }

                //Move to next list in map.
                if (allObjects.hasNext())
                    currentList = allObjects.next();
                else
                    currentList = null;
            }

            //All done.
            sb.deleteCharAt(sb.length() - 1).append(") - ");

            return sb.toString();
        }

        return new EditorTime(time) + " - ";
    }

    @Override
    public void addSelectionRange(long startTime, long endTime) {
        if (startTime == endTime)
            return;

        MapObjectTreeMap<MapObject> newSelection = new MapObjectTreeMap<>();
        if (startTime > endTime)
            newSelection.addAll(map.getSubMap(endTime, startTime));
        else
            newSelection.addAll(map.getSubMap(startTime, endTime));

        selectObjects(newSelection);
    }

    @Override
    public MapObject getObjectAt(float x, float y) {
        return null;
    }

    @Override
    public boolean clickedEnd(MapObject o, float x) {
        return false;
    }

    @Override
    public void pasteObjects(ViewType copyType, MapObjectTreeMap<MapObject> copyObjects) {

    }

    @Override
    public void reverse() {
        if (!hasSelection())
            return;

        this.map.registerReverse(true, getSelection(true));
    }
    @Override
    public void invert() {
    }

    @Override
    public void deleteSelection() {
        MapObjectTreeMap<MapObject> selection = getSelection(true);
        if (selection != null)
        {
            this.map.registerAndPerformDelete(selection);
            clearSelection();
        }
    }

    @Override
    public void registerMove(long totalMovement) {
        MapObjectTreeMap<MapObject> selection = getSelection(true);
        if (selection != null && totalMovement != 0)
        {
            this.map.registerAndPerformObjectMovement(selection, totalMovement);
        }
    }

    @Override
    public double getTimeFromPosition(float x) {
        return getTimeFromPosition(x, HIT_AREA_X);
    }

    private static final Toolset toolset = new Toolset();
    public Toolset getToolset()
    {
        return toolset;
    }
}
