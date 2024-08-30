package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.BreakAdjust;
import alchyr.taikoedit.editor.changes.BreakRemoval;
import alchyr.taikoedit.editor.changes.RepositionChange;
import alchyr.taikoedit.editor.maps.BreakInfo;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.ILongObject;
import alchyr.taikoedit.editor.tools.*;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.EditorTime;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.*;

import static alchyr.taikoedit.TaikoEditor.*;
import static alchyr.taikoedit.core.layers.EditorLayer.viewScale;

public class ObjectView extends MapView {
    public static final String ID = "obj";
    @Override
    public String typeString() {
        return ID;
    }

    public static final int HEIGHT = 150;
    public static final int MEDIUM_HEIGHT = 22;
    public static final int SMALL_HEIGHT = 14;

    //Selection
    private static final int MAX_SELECTION_DIST = 62;
    private static final int SMALL_SELECTION_DIST = 42;
    private static final int MAX_SELECTION_OFFSET = HEIGHT / 2 - MAX_SELECTION_DIST;
    private static final int SMALL_SELECTION_OFFSET = HEIGHT / 2 - SMALL_SELECTION_DIST;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.7f);
    private static final Color breakColor = new Color(0.6f, 0.6f, 0.6f, 0.3f);
    private static final Color faintBreakColor = new Color(0.5f, 0.5f, 0.5f, 0.2f);
    private static final Color fakeBreakColor = new Color(0.44f, 0.6f, 0.88f, 0.2f);

    private boolean breaks;

    //Base position values
    private int baseObjectY = 0;
    //private int baseTopBigY = 0;

    //Offset
    private int objectY = 0;
    //private int topBigY = 0;

    private long lastSounded;

    private SortedMap<Long, Snap> activeSnaps = Collections.emptySortedMap();

    public ObjectView(EditorLayer parent, EditorBeatmap beatmap) {
        super(ViewType.OBJECT_VIEW, parent, beatmap, HEIGHT);
        lastSounded = 0;

        breaks = beatmap.autoBreaks;

        addOverlayButton(new ImageButton(assetMaster.get("editor:exit"), assetMaster.get("editor:exith")).setClick(this::close).setAction("Close View"));
        addOverlayButton(new ImageButton(assetMaster.get("editor:breaks"), assetMaster.get("editor:breaksh")).setClick(this::toggleBreaks).setAction("Toggle Break Visibility"));
        addOverlayButton(new ImageButton(assetMaster.get("editor:position"), assetMaster.get("editor:positionh")).setClick(this::reposition).setAction("Reposition Objects"));

        addLockPositionButton();
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

        baseObjectY = this.y + HEIGHT / 2;
        //baseTopBigY = this.y + HEIGHT - BIG_HEIGHT;

        return this.y;
    }

    public void setOffset(int offset)
    {
        super.setOffset(offset);

        objectY = baseObjectY + yOffset;
        //topBigY = baseTopBigY + yOffset;
    }

    @Override
    public void primaryUpdate(boolean isPlaying) {
        if (isPrimary && lockOffset == 0 && isPlaying && lastSounded < time && time - lastSounded < 25) //might have skipped backwards
        {
            //Play only the most recently passed HitObject list. This avoids spamming a bunch of sounds if fps is too low.
            /*Map.Entry<Integer, ArrayList<HitObject>> entry = map.getEditObjects().higherEntry(currentPos);
            if (entry != null && entry.getKey() > lastSounded)
            {
                for (HitObject o : entry.getValue())
                    o.playSound();
            }*/

            //To play ALL hitobjects passed.
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
    public void update(double exactPos, long msPos, float elapsed, boolean canHover) {
        super.update(exactPos, msPos, elapsed, canHover);
        activeSnaps = map.getActiveSnaps(preciseTime - EditorLayer.viewTime, preciseTime + EditorLayer.viewTime);
    }

    @Override
    public void renderBase(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, bottom, SettingsMaster.getWidth(), height);

        //Breaks
        if (breaks) {
            Map.Entry<Long, ArrayList<HitObject>> stack;
            double start, end, breakStart, breakEnd;
            long startTime;
            Color startColor, endColor;
            Optional<HitObject> longest;

            for (BreakInfo breakSection : map.getBreaks()) {
                startColor = endColor = faintBreakColor;

                breakEnd = (breakSection.end - preciseTime) * viewScale + SettingsMaster.getMiddleX();

                stack = map.objects.ceilingEntry(breakSection.end);
                if (stack != null) {
                    //if distance is <= min distance, color is gray
                    //Otherwise, color is blue to denote extended break delay
                    //Do the same for start delay, except end delay is dynamic based on AR.
                    if (stack.getKey() - breakSection.end > map.getBreakEndDelay())
                        endColor = fakeBreakColor;

                    end = (stack.getKey() - preciseTime) * viewScale + SettingsMaster.getMiddleX();
                }
                else { //this is a cheaty break with no closing object.
                    end = SettingsMaster.getWidth();
                }

                if (end < 0)
                    continue;

                breakStart = (breakSection.start - preciseTime) * viewScale + SettingsMaster.getMiddleX();

                stack = map.objects.floorEntry(breakSection.start);
                if (stack != null) {
                    longest = stack.getValue().stream().max(Comparator.comparingLong(HitObject::getEndPos));
                    startTime = longest.map(HitObject::getEndPos).orElse(0L);

                    if (breakSection.start - startTime > 200)
                        startColor = fakeBreakColor;

                    start = (startTime - preciseTime) * viewScale + SettingsMaster.getMiddleX();
                }
                else {
                    start = 0;
                }

                if (start > SettingsMaster.getWidth())
                    break;

                //should render if start is anywhere >0 or end anywhere <width, or if start<0 and end>width?
                if (start <= SettingsMaster.getWidth() && end >= 0) {
                    start = Math.max(start, 0);
                    if (breakStart > 0) {
                        sb.setColor(startColor);
                        sb.draw(pix, (float)start, bottom, (float)(breakStart - start), height);
                    }
                    if (breakEnd <= SettingsMaster.getWidth()) {
                        sb.setColor(endColor);
                        sb.draw(pix, (float)breakEnd, bottom, (float)(end - breakEnd), height);
                    }
                    sb.setColor(breakColor);
                    sb.draw(pix, (float)breakStart, bottom, (float)(breakEnd - breakStart), height);
                }
            }
        }

        //Divisors.
        for (Snap s : activeSnaps.values())
        {
            s.render(sb, sr, preciseTime, viewScale, SettingsMaster.getMiddleX(), bottom, HEIGHT);
        }
    }

    @Override
    public void renderObject(MapObject o, SpriteBatch sb, ShapeRenderer sr, float alpha) {
        o.render(sb, sr, preciseTime, viewScale, SettingsMaster.getMiddleX(), objectY, alpha);
    }
    @Override
    public void renderSelection(MapObject o, SpriteBatch sb, ShapeRenderer sr) {
        o.renderSelection(sb, sr, preciseTime, viewScale, SettingsMaster.getMiddleX(), objectY);
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends MapObject>> prep() {
        return map.getEditObjects(time - EditorLayer.viewTime, time + EditorLayer.viewTime);
    }

    @Override
    public void selectAll() {
        clearSelection();
        selectObjects(map.objects);
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends MapObject>> getVisibleRange(long start, long end) {
        NavigableMap<Long, ? extends ArrayList<? extends MapObject>> source = map.getEditObjects(time - EditorLayer.viewTime, time + EditorLayer.viewTime);

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
    public void addSelectionRange(long startTime, long endTime)
    {
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
    public MouseHoldObject click(float x, float y, int button)
    {
        MouseHoldObject o = super.click(x, y, button);
        if (o != null)
            return o;

        if (breaks) {
            double time = getTimeFromPosition(x);

            Iterator<BreakInfo> breakIterator = map.getBreaks().iterator();
            BreakInfo breakSection;
            long startTime, endTime;
            boolean adjustable;
            Map.Entry<Long, ArrayList<HitObject>> stack;
            Optional<HitObject> longest;

            while (breakIterator.hasNext()) {
                breakSection = breakIterator.next();
                adjustable = true;
                stack = map.objects.ceilingEntry(breakSection.end);
                if (stack != null) {
                    endTime = stack.getKey();
                }
                else { //this is a cheaty break with no closing object.
                    endTime = Long.MAX_VALUE;
                    adjustable = false;
                }

                stack = map.objects.floorEntry(breakSection.start);
                if (stack != null) {
                    longest = stack.getValue().stream().max(Comparator.comparingLong(HitObject::getEndPos));
                    startTime = longest.map(HitObject::getEndPos).orElse(Long.MIN_VALUE);
                }
                else {
                    startTime = Long.MIN_VALUE;
                    adjustable = false;
                }

                if (time <= endTime && time >= startTime) {
                    if (button == Input.Buttons.RIGHT) {
                        //Right clicking anywhere on a break attempts deletion.
                        long len = endTime - startTime;

                        if (len < 5000) {
                            map.registerChange(new BreakRemoval(map, breakSection));
                            breakIterator.remove();
                        }
                        else {
                            parent.showText("Gap is too long to remove break.");
                            return MouseHoldObject.nothing;
                        }
                    }
                    else if (button == Input.Buttons.LEFT && adjustable) {
                        //left clicking on one of the break's "transition" points allows adjustment.
                        double startDist, endDist;

                        startDist = Math.abs(time - breakSection.start) / viewScale;
                        endDist = Math.abs(time - breakSection.end) / viewScale;

                        //overlay is exceedingly unlikely since there's a minimum 650 ms gap between start and end, but in that case end is prioritized
                        if (endDist < 10) {
                            return new BreakAdjustingMouseHoldObject(this, breakSection, false, time, Math.max(breakSection.start + 650, endTime - 5000), endTime - map.getBreakEndDelay());
                        }
                        else if (startDist < 10) {
                            return new BreakAdjustingMouseHoldObject(this, breakSection, true, time, startTime + 200, Math.min(breakSection.end - 650, startTime + 5000));
                        }
                    }
                    return null;
                }
                else if (time < breakSection.start) {
                    return null;
                }
            }
        }

        return null;
    }

    public MapObject getObjectAt(float x, float y)
    {
        NavigableMap<Long, ArrayList<HitObject>> selectable = map.getEditObjects(time - EditorLayer.viewTime, time + EditorLayer.viewTime);
        if (selectable == null || y < bottom + MAX_SELECTION_OFFSET || y > top - MAX_SELECTION_OFFSET)
            return null;

        double time = getTimeFromPosition(x);

        if (selectable.containsKey((long) time)) {
            ArrayList<HitObject> selectableObjects = selectable.get((long) time);
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

        Map.Entry<Long, ArrayList<HitObject>> lower = selectable.higherEntry((long) time); //These are reversed because editObjects is a descending map.
        Map.Entry<Long, ArrayList<HitObject>> higher = selectable.lowerEntry((long) time);
        double higherDist, lowerDist;
        //boolean isLower = true;

        if (lower == null && higher == null)
            return null;
        else if (lower == null)
        {
            higherDist = higher.getKey() - time;
            lowerDist = Double.MAX_VALUE;
        }
        else if (higher == null)
        {
            lowerDist = time - lower.getKey();
            higherDist = Double.MAX_VALUE;
        }
        else
        {
            //Neither are null. Determine which one is closer.
            lowerDist = time - lower.getKey();
            higherDist = higher.getKey() - time;
        }

        higherDist *= viewScale;
        lowerDist *= viewScale;

        //Check the closer objects first.
        if (lower != null && lowerDist < higherDist)
        {
            ArrayList<HitObject> selectableObjects = lower.getValue();
            if (selectableObjects.isEmpty())
            {
                editorLogger.error("WTF? Empty arraylist of objects in object map.");
            }
            else if (lowerDist > MAX_SELECTION_DIST)
            {
                boolean needsFinish = y < bottom + SMALL_SELECTION_OFFSET || y > top - SMALL_SELECTION_OFFSET;

                for (int i = selectableObjects.size() - 1; i >= 0; --i)
                {
                    if (selectableObjects.get(i) instanceof ILongObject)
                    {
                        boolean finish = selectableObjects.get(i).type == HitObject.HitObjectType.SPINNER || selectableObjects.get(i).isFinish();
                        if (finish || !needsFinish)
                        {
                            if (((ILongObject) selectableObjects.get(i)).getEndPos() > time - (finish ? MAX_SELECTION_DIST : SMALL_SELECTION_DIST))
                            {
                                return selectableObjects.get(i);
                            }
                        }
                    }
                }
            }
            else if (lowerDist > SMALL_SELECTION_DIST || y < bottom + SMALL_SELECTION_OFFSET || y > top - SMALL_SELECTION_OFFSET)
            {
                //Can only select a finisher.
                for (int i = selectableObjects.size() - 1; i >= 0; --i)
                {
                    if (selectableObjects.get(i).isFinish())
                    {
                        return selectableObjects.get(i);
                    }
                }
            }
            else //lower distance is within selection range.
            {
                //Select the first object.
                return selectableObjects.get(selectableObjects.size() - 1);
            }

            if (higher != null)
            {
                selectableObjects = higher.getValue();
                if (!selectableObjects.isEmpty() && !(higherDist > MAX_SELECTION_DIST)) {
                    if (higherDist > SMALL_SELECTION_DIST || y < bottom + SMALL_SELECTION_OFFSET || y > top - SMALL_SELECTION_OFFSET)
                    {
                        //Can only select a finisher.
                        for (int i = selectableObjects.size() - 1; i >= 0; --i)
                        {
                            if (selectableObjects.get(i).isFinish())
                            {
                                return selectableObjects.get(i);
                            }
                        }
                    }
                    else
                    {
                        //Select the first object.
                        return selectableObjects.get(selectableObjects.size() - 1);
                    }
                } //else {
                    //Nothing to do here, just skip checking higher object.
               // }
            }
        }
        else if (higher != null && higherDist < lowerDist)
        {
            ArrayList<HitObject> selectableObjects = higher.getValue();
            if (!selectableObjects.isEmpty() && !(higherDist > MAX_SELECTION_DIST)) {
                if (higherDist > SMALL_SELECTION_DIST || y < bottom + SMALL_SELECTION_OFFSET || y > top - SMALL_SELECTION_OFFSET)
                {
                    //Can only select a finisher.
                    for (int i = selectableObjects.size() - 1; i >= 0; --i)
                    {
                        if (selectableObjects.get(i).isFinish())
                        {
                            return selectableObjects.get(i);
                        }
                    }
                }
                else
                {
                    //Select the first object.
                    return selectableObjects.get(selectableObjects.size() - 1);
                }
            } //else {
                //Nothing to do here, just skip checking higher object.
            //}

            if (lower != null)
            {
                selectableObjects = lower.getValue();
                if (selectableObjects.isEmpty())
                {
                    editorLogger.error("WTF? Empty arraylist of objects in object map.");
                }
                else if (lowerDist > MAX_SELECTION_DIST)
                {
                    boolean needsFinish = y < bottom + SMALL_SELECTION_OFFSET || y > top - SMALL_SELECTION_OFFSET;

                    for (int i = selectableObjects.size() - 1; i >= 0; --i)
                    {
                        if (selectableObjects.get(i) instanceof ILongObject)
                        {
                            boolean finish = selectableObjects.get(i).type == HitObject.HitObjectType.SPINNER || selectableObjects.get(i).isFinish();
                            if (finish || !needsFinish)
                            {
                                if (((ILongObject) selectableObjects.get(i)).getEndPos() > time - (finish ? MAX_SELECTION_DIST : SMALL_SELECTION_DIST))
                                {
                                    return selectableObjects.get(i);
                                }
                            }
                        }
                    }
                }
                else if (lowerDist > SMALL_SELECTION_DIST || y < bottom + SMALL_SELECTION_OFFSET || y > top - SMALL_SELECTION_OFFSET)
                {
                    //Can only select a finisher.
                    for (int i = selectableObjects.size() - 1; i >= 0; --i)
                    {
                        if (selectableObjects.get(i).isFinish())
                        {
                            return selectableObjects.get(i);
                        }
                    }
                }
                else //lower distance is within selection range.
                {
                    //Select the first object.
                    return selectableObjects.get(selectableObjects.size() - 1);
                }
            }
        }
        return null;
    }

    @Override
    public boolean clickedEnd(MapObject o, float x) {
        if (o instanceof ILongObject)
        {
            ILongObject obj = (ILongObject) o;

            double time = getTimeFromPosition(x);

            double dist;
            if (time > obj.getEndPos())
            {
                dist = time - obj.getEndPos();
            }
            else
            {
                dist = obj.getEndPos() - time;
            }

            return dist < SMALL_SELECTION_DIST;
        }
        return false;
    }

    @Override
    public void dispose() {
        super.dispose();

        activeSnaps = null;
    }

    private void reposition() {
        if (hasSelection()) {
            map.registerChange(new RepositionChange(map, getSelection(true)).preDo());
            parent.showText("Repositioned selected objects.");
        }
        else {
            map.registerChange(new RepositionChange(map).preDo());
            parent.showText("Repositioned all objects.");
        }
    }

    private void toggleBreaks() {
        if (breaks) {
            breaks = false;
            parent.showText("Breaks hidden.");
        }
        else {
            breaks = true;
            parent.showText("Breaks shown.");
        }
    }

    @Override
    public void pasteObjects(MapObjectTreeMap<MapObject> copyObjects) {
        //This should overwrite existing objects.

        //Make copies of the hitobjects (add a copy() method to the HitObject class) and shift their position appropriately
        //Find closest (1 ms before or after limit) snap (of any existing snap, not just the active one) and put objects at that position

        long offset, targetPos;

        Snap closest = getClosestSnap(preciseTime, 250);
        offset = closest == null ? time : closest.pos;
        offset -= copyObjects.firstKey();

        MapObjectTreeMap<MapObject> placementCopy = new MapObjectTreeMap<>();
        Map<MapObject, MapObject> copyToOriginal = new HashMap<>();
        TreeMap<Long, Snap> snaps = map.getAllSnaps();
        boolean resnap = !BindingGroup.alt();

        for (Map.Entry<Long, ArrayList<MapObject>> entry : copyObjects.entrySet())
        {
            targetPos = entry.getKey() + offset;

            if (resnap) {
                closest = snaps.get(targetPos);
                if (closest == null)
                    closest = snaps.get(targetPos + 1);
                if (closest == null)
                    closest = snaps.get(targetPos - 1);
                if (closest != null)
                    targetPos = closest.pos;
            }

            for (MapObject o : entry.getValue())
            {
                MapObject cpy = o.shiftedCopy(targetPos);
                placementCopy.add(cpy);
                copyToOriginal.put(cpy, o);
            }
        }

        this.map.registerAndPerformAddObjects("Paste Objects", placementCopy, copyToOriginal, replaceTest);
    }

    @Override
    public void reverse() {
        if (!hasSelection())
            return;

        this.map.registerReverse(true, getSelection());
    }

    @Override
    public void deleteSelection() {
        if (!hasSelection())
            return;

        this.map.registerAndPerformDelete(getSelection());
        clearSelection();
    }

    @Override
    public void registerMove(long totalMovement) {
        if (hasSelection() && totalMovement != 0)
        {
            this.map.registerAndPerformObjectMovement(getSelection(true), totalMovement);
        }
    }

    private static final Toolset toolset = new Toolset(SelectionTool.get(), HitTool.don(), HitTool.kat(), SliderTool.get(), SpinnerTool.get());
    public Toolset getToolset()
    {
        return toolset;
    }


    private static class BreakAdjustingMouseHoldObject extends MouseHoldObject {
        ObjectView parent;
        BreakInfo breakSection;
        boolean start; //true, adjusting start. false, adjusting end.
        double initialClick;
        long initialPosition, min, max;
        long lastChange = 0;

        public BreakAdjustingMouseHoldObject(ObjectView parent, BreakInfo breakSection, boolean start, double initialClick, long min, long max) {
            super(null, null);

            this.parent = parent;
            this.breakSection = breakSection;
            this.start = start;

            this.initialClick = initialClick;
            this.initialPosition = start ? breakSection.start : breakSection.end;
            this.min = min;
            this.max = max;

            onRelease = (x, y)->{
                this.parent.map.registerChange(new BreakAdjust(this.parent.map, this.breakSection, this.start, this.initialPosition, this.start ? this.breakSection.start : this.breakSection.end));
            };
        }

        @Override
        public void update(float elapsed) {
            long offsetChange = (long) (parent.getTimeFromPosition(Gdx.input.getX()) - initialClick);

            if (offsetChange == lastChange)
                return;

            Snap closest = parent.getClosestSnap(initialPosition + offsetChange, 500);

            //Just go to cursor position
            if (closest != null && !BindingGroup.alt()) { //Snap to closest snap unless alt is held
                offsetChange = closest.pos - initialPosition;
            }
            else {
                offsetChange = (long) parent.getTimeFromPosition(Gdx.input.getX()) - initialPosition;
            }

            long newPos = initialPosition + offsetChange;
            if (newPos < min)
                newPos = min;
            if (newPos > max)
                newPos = max;

            if (start) {
                breakSection.start = newPos;
            }
            else {
                breakSection.end = newPos;
            }

            //Should move slower than normal selection.
            if (Gdx.input.getX() <= 1)
            {
                music.seekSecond(music.getSecondTime() - elapsed * 2);
            }
            else if (Gdx.input.getX() >= SettingsMaster.getWidth() - 1)
            {
                music.seekSecond(music.getSecondTime() + elapsed * 2);
            }
        }
    }
}
