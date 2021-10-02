package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.MapChange;
import alchyr.taikoedit.editor.tools.*;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.ILongObject;
import alchyr.taikoedit.util.EditorTime;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.*;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.editorLogger;
import static alchyr.taikoedit.core.layers.EditorLayer.viewScale;

public class ObjectView extends MapView {
    public static final int HEIGHT = 200;
    public static final int MEDIUM_HEIGHT = 30;
    public static final int SMALL_HEIGHT = 15;

    //Selection
    private static final int MAX_SELECTION_DIST = 70;
    private static final int SMALL_SELECTION_DIST = 45;
    private static final int MAX_SELECTION_OFFSET = HEIGHT / 2 - MAX_SELECTION_DIST;
    private static final int SMALL_SELECTION_OFFSET = HEIGHT / 2 - SMALL_SELECTION_DIST;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.7f);

    //Base position values
    private int baseObjectY = 0;
    //private int baseTopBigY = 0;

    //Offset
    private int objectY = 0;
    //private int topBigY = 0;

    private double lastSounded;

    private SortedMap<Long, Snap> activeSnaps;

    public ObjectView(EditorLayer parent, EditorBeatmap beatmap) {
        super(ViewType.OBJECT_VIEW, parent, beatmap, HEIGHT);
        lastSounded = 0;

        addOverlayButton(new ImageButton(assetMaster.get("editor:exit"), assetMaster.get("editor:exith"), this::close).setAction("Close View"));
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
        if (isPrimary && isPlaying && lastSounded < time) //might have skipped backwards
        {
            //Play only the most recently passed HitObject list. This avoids spamming a bunch of sounds if fps is too low.
            /*Map.Entry<Integer, ArrayList<HitObject>> entry = map.getEditObjects().higherEntry(currentPos);
            if (entry != null && entry.getKey() > lastSounded)
            {
                for (HitObject o : entry.getValue())
                    o.playSound();
            }*/

            //To play ALL hitobjects passed.
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
        activeSnaps = map.getActiveSnaps(time - EditorLayer.viewTime, time + EditorLayer.viewTime);
    }

    @Override
    public void renderBase(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, bottom, SettingsMaster.getWidth(), height);

        //Divisors.
        for (Snap s : activeSnaps.values())
        {
            s.render(sb, sr, time, viewScale, SettingsMaster.getMiddle(), bottom, HEIGHT);
        }

        //Replace Fat White Midpoint with something a little less obnoxious
        //Small triangle on top and bottom like my skin? It would look nice tessellated as well, I think.
        /*sb.setColor(Color.WHITE);
        sb.draw(pix, midPos, bottom, 2, BIG_HEIGHT);
        sb.draw(pix, midPos, topBigY, 2, BIG_HEIGHT);*/
    }

    @Override
    public void renderObject(PositionalObject o, SpriteBatch sb, ShapeRenderer sr, float alpha) {
        o.render(sb, sr, time, viewScale, SettingsMaster.getMiddle(), objectY, alpha);
    }
    @Override
    public void renderSelection(PositionalObject o, SpriteBatch sb, ShapeRenderer sr) {
        o.renderSelection(sb, sr, time, viewScale, SettingsMaster.getMiddle(), objectY);
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> prep(long pos) {
        return map.getEditObjects(pos - EditorLayer.viewTime, pos + EditorLayer.viewTime);
    }

    @Override
    public void selectAll() {
        clearSelection();

        selectedObjects = new PositionalObjectTreeMap<>();

        selectedObjects.addAll(map.objects);

        for (ArrayList<? extends PositionalObject> stuff : selectedObjects.values())
            for (PositionalObject o : stuff)
                o.selected = true;
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> getVisibleRange(long start, long end) {
        NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> source = map.getEditObjects((int) time - EditorLayer.viewTime, (int) time + EditorLayer.viewTime);

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
            Iterator<Map.Entry<Long, ArrayList<PositionalObject>>> selectionObjects = selectedObjects.entrySet().iterator();

            Map.Entry<Long, ArrayList<HitObject>> currentList = null;
            Map.Entry<Long, ArrayList<PositionalObject>> selectedObjectList = null;

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

        return new EditorTime((int) time) + " - ";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addSelectionRange(long startTime, long endTime)
    {
        if (startTime == endTime)
            return;

        PositionalObjectTreeMap<PositionalObject> newSelection;

        if (selectedObjects == null)
        {
            newSelection = new PositionalObjectTreeMap<>();
            if (startTime > endTime)
                newSelection.addAll(map.getSubMap(endTime, startTime));
            else
                newSelection.addAll(map.getSubMap(startTime, endTime));

            selectedObjects = newSelection;
            for (ArrayList<PositionalObject> stuff : selectedObjects.values())
                for (PositionalObject o : stuff)
                    o.selected = true;
        }
        else
        {
            NavigableMap<Long, ArrayList<HitObject>> newSelected;

            if (startTime > endTime)
                newSelected = map.getSubMap(endTime, startTime);
            else
                newSelected =  map.getSubMap(startTime, endTime);

            selectedObjects.addAll(newSelected);

            for (ArrayList<? extends PositionalObject> stuff : newSelected.values())
                for (PositionalObject o : stuff)
                    o.selected = true;
        }
    }

    public PositionalObject clickObject(int x, int y)
    {
        NavigableMap<Long, ArrayList<HitObject>> selectable = map.getEditObjects();
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

        //Check the closer objects first.
        if (lowerDist < higherDist)
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
                        boolean finish = selectableObjects.get(i).type == HitObject.HitObjectType.SPINNER || selectableObjects.get(i).finish;
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
                    if (selectableObjects.get(i).finish)
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
                if (selectableObjects.isEmpty() || higherDist > MAX_SELECTION_DIST)
                {
                    //Nothing to do here, just skip checking higher object.
                }
                else if (higherDist > SMALL_SELECTION_DIST || y < bottom + SMALL_SELECTION_OFFSET || y > top - SMALL_SELECTION_OFFSET)
                {
                    //Can only select a finisher.
                    for (int i = selectableObjects.size() - 1; i >= 0; --i)
                    {
                        if (selectableObjects.get(i).finish)
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
            }
        }
        else if (higherDist < lowerDist)
        {
            ArrayList<HitObject> selectableObjects = higher.getValue();
            if (selectableObjects.isEmpty() || higherDist > MAX_SELECTION_DIST)
            {
                //Nothing to do here, just skip checking higher object.
            }
            else if (higherDist > SMALL_SELECTION_DIST || y < bottom + SMALL_SELECTION_OFFSET || y > top - SMALL_SELECTION_OFFSET)
            {
                //Can only select a finisher.
                for (int i = selectableObjects.size() - 1; i >= 0; --i)
                {
                    if (selectableObjects.get(i).finish)
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
                            boolean finish = selectableObjects.get(i).type == HitObject.HitObjectType.SPINNER || selectableObjects.get(i).finish;
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
                        if (selectableObjects.get(i).finish)
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
    public boolean clickedEnd(PositionalObject o, int x) {
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
    public void dispose() {
        super.dispose();

        activeSnaps = null;
    }

    @Override
    public PositionalObjectTreeMap<?> getEditMap() {
        return map.objects;
    }

    @Override
    public void deleteObject(PositionalObject o) {
        this.map.delete(MapChange.ChangeType.OBJECTS, o);
    }

    @Override
    public void pasteObjects(PositionalObjectTreeMap<PositionalObject> copyObjects) {
        //This should overwrite existing objects.

        //Make copies of the hitobjects (add a copy() method to the HitObject class) and shift their position appropriately
        //Find closest (1 ms before or after limit) snap (of any existing snap, not just the active one) and put objects at that position

        long offset, targetPos;

        Snap closest = getClosestSnap(time, 250);
        offset = closest == null ? (int) time : (int) closest.pos;
        offset -= copyObjects.firstKey();

        PositionalObjectTreeMap<PositionalObject> placementCopy = new PositionalObjectTreeMap<>();
        TreeMap<Long, Snap> snaps = map.getAllSnaps();

        for (Map.Entry<Long, ArrayList<PositionalObject>> entry : copyObjects.entrySet())
        {
            targetPos = entry.getKey() + offset;

            closest = snaps.get(targetPos);
            if (closest == null)
                closest = snaps.get(targetPos + 1);
            if (closest == null)
                closest = snaps.get(targetPos - 1);
            if (closest != null)
                targetPos = (int) closest.pos;

            for (PositionalObject o : entry.getValue())
            {
                placementCopy.add(o.shiftedCopy(targetPos));
            }
        }

        this.map.paste(placementCopy);
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
        return getTimeFromPosition(x, SettingsMaster.getMiddle());
    }

    private static final Toolset toolset = new Toolset(SelectionTool.get(), HitTool.don(), HitTool.kat(), SliderTool.get(), SpinnerTool.get());
    public static Toolset getToolset()
    {
        return toolset;
    }
}
