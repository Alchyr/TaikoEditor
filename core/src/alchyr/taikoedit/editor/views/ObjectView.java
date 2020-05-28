package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.Deletion;
import alchyr.taikoedit.editor.changes.MapChange;
import alchyr.taikoedit.editor.tools.*;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.maps.components.HitObject;
import alchyr.taikoedit.maps.components.ILongObject;
import alchyr.taikoedit.maps.components.hitobjects.Slider;
import alchyr.taikoedit.maps.components.hitobjects.Spinner;
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
    private static final int BIG_HEIGHT = 50;
    public static final int MEDIUM_HEIGHT = 30;
    public static final int SMALL_HEIGHT = 15;

    //Selection
    private static final int MAX_SELECTION_DIST = 70;
    private static final int SMALL_SELECTION_DIST = 45;
    private static final int MAX_SELECTION_OFFSET = HEIGHT / 2 - MAX_SELECTION_DIST;
    private static final int SMALL_SELECTION_OFFSET = HEIGHT / 2 - SMALL_SELECTION_DIST;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.7f);

    private static final int midPos = SettingsMaster.getWidth() / 2;

    //Base position values
    private int baseObjectY = 0;
    private int baseTopBigY = 0;

    //Offset
    private int objectY = 0;
    private int topBigY = 0;

    private int lastSounded;

    private SortedMap<Integer, Snap> activeSnaps;

    public ObjectView(EditorLayer parent, EditorBeatmap beatmap) {
        super(ViewType.OBJECT_VIEW, parent, beatmap, HEIGHT);
        lastSounded = 0;

        addOverlayButton(new ImageButton(assetMaster.get("editor:exit"), assetMaster.get("editor:exith"), this::close));
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

        baseObjectY = this.y + 100;
        baseTopBigY = this.y + HEIGHT - BIG_HEIGHT;

        return this.y;
    }

    public void setOffset(int offset)
    {
        super.setOffset(offset);

        objectY = baseObjectY + yOffset;
        topBigY = baseTopBigY + yOffset;
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
    public void update(float exactPos, int msPos) {
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
            s.render(sb, sr, time, viewScale, SettingsMaster.getMiddle(), bottom);
        }

        //Replace Fat White Midpoint with something a little less obnoxious
        //Small triangle on top and bottom like my skin? It would look nice tessellated as well, I think.
        sb.setColor(Color.WHITE);
        sb.draw(pix, midPos, bottom, 2, BIG_HEIGHT);
        sb.draw(pix, midPos, topBigY, 2, BIG_HEIGHT);
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
    public NavigableMap<Integer, ? extends ArrayList<? extends PositionalObject>> prep(int pos) {
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
    public NavigableMap<Integer, ? extends ArrayList<? extends PositionalObject>> getVisisbleRange(int start, int end) {
        NavigableMap<Integer, ? extends ArrayList<? extends PositionalObject>> source = map.getEditObjects(time - EditorLayer.viewTime, time + EditorLayer.viewTime);

        if (source.isEmpty())
            return null;

        start = Math.max(start, source.lastKey());
        end = Math.min(end, source.firstKey());

        if (start >= end)
            return null;

        return source.subMap(end, true, start, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addSelectionRange(int startTime, int endTime)
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
            NavigableMap<Integer, ArrayList<HitObject>> newSelected;

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
        NavigableMap<Integer, ArrayList<HitObject>> selectable = map.getEditObjects();
        if (selectable == null || y < bottom + MAX_SELECTION_OFFSET || y > topY - MAX_SELECTION_OFFSET)
            return null;

        int time = getTimeFromPosition(x);
        Map.Entry<Integer, ArrayList<HitObject>> lower = selectable.higherEntry(time); //These are reversed because editObjects is a descending map.
        Map.Entry<Integer, ArrayList<HitObject>> higher = selectable.lowerEntry(time);
        int higherDist = 0, lowerDist = 0;
        //boolean isLower = true;

        if (lower == null && higher == null)
            return null;
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
            ArrayList<HitObject> selectableObjects = lower.getValue();
            if (selectableObjects.isEmpty())
            {
                editorLogger.error("WTF? Empty arraylist of objects in object map.");
            }
            else if (lowerDist > MAX_SELECTION_DIST)
            {
                boolean needsFinish = y < bottom + SMALL_SELECTION_OFFSET || y > topY - SMALL_SELECTION_OFFSET;

                for (int i = selectableObjects.size() - 1; i >= 0; --i)
                {
                    if (selectableObjects.get(i) instanceof ILongObject)
                    {
                        boolean finish = selectableObjects.get(i).type == HitObject.HitType.SPINNER || selectableObjects.get(i).finish;
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
            else if (lowerDist > SMALL_SELECTION_DIST || y < bottom + SMALL_SELECTION_OFFSET || y > topY - SMALL_SELECTION_OFFSET)
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
                else if (higherDist > SMALL_SELECTION_DIST || y < bottom + SMALL_SELECTION_OFFSET || y > topY - SMALL_SELECTION_OFFSET)
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
            else if (higherDist > SMALL_SELECTION_DIST || y < bottom + SMALL_SELECTION_OFFSET || y > topY - SMALL_SELECTION_OFFSET)
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
                    boolean needsFinish = y < bottom + SMALL_SELECTION_OFFSET || y > topY - SMALL_SELECTION_OFFSET;

                    for (int i = selectableObjects.size() - 1; i >= 0; --i)
                    {
                        if (selectableObjects.get(i) instanceof ILongObject)
                        {
                            boolean finish = selectableObjects.get(i).type == HitObject.HitType.SPINNER || selectableObjects.get(i).finish;
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
                else if (lowerDist > SMALL_SELECTION_DIST || y < bottom + SMALL_SELECTION_OFFSET || y > topY - SMALL_SELECTION_OFFSET)
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
    public PositionalObject clickSelection(int x, int y) {
        PositionalObject o = clickObject(x, y);

        if (o != null)
        {
            if (o.selected)
            {
                deselect(o);
            }
            else
            {
                select(o);
            }
        }

        return o;
    }

    @Override
    public boolean clickedEnd(PositionalObject o, int x) {
        if (o instanceof ILongObject)
        {
            ILongObject obj = (ILongObject) o;

            int time = getTimeFromPosition(x);

            int dist;
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
        Map.Entry<Integer, Snap> next = map.getAllSnaps().higherEntry(EditorLayer.music.isPlaying() ? time - 250 : time);
        if (next == null)
            return null;
        if (next.getKey() - time < 2)
        {
            next = map.getAllSnaps().higherEntry(next.getKey());
            if (next == null)
                return null;
        }
        return next.getValue();
    }

    @Override
    public Snap getPreviousSnap() {
        Map.Entry<Integer, Snap> previous = map.getAllSnaps().lowerEntry(EditorLayer.music.isPlaying() ? time - 250 : time);
        if (previous == null)
            return null;
        if (time - previous.getKey() < 2)
        {
            previous = map.getAllSnaps().lowerEntry(previous.getKey());
            if (previous == null)
                return null;
        }
        return previous.getValue();
    }

    @Override
    public Snap getClosestSnap(int time, int limit) {
        if (map.getAllSnaps().containsKey(time))
            return map.getAllSnaps().get(time);

        Map.Entry<Integer, Snap> lower, higher;
        lower = map.getAllSnaps().lowerEntry(time);
        higher = map.getAllSnaps().higherEntry(time);

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
            int lowerDist = time - lower.getKey(), higherDist = higher.getKey() - time;
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
        return map.getAllSnaps().isEmpty();
    }

    @Override
    public void dispose() {
        super.dispose();

        activeSnaps = null;
    }

    @Override
    public void deleteObject(PositionalObject o) {
        this.map.delete(MapChange.ChangeType.OBJECTS, o);
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
    public void registerMove(int totalMovement) {
        if (selectedObjects != null)
        {
            PositionalObjectTreeMap<PositionalObject> movementCopy = new PositionalObjectTreeMap<>();
            movementCopy.addAll(selectedObjects); //use addAll to make a copy without sharing any references other than the positionalobjects themselves
            this.map.registerMovedObjects(movementCopy, totalMovement);
        }
    }

    @Override
    public int getTimeFromPosition(int x) {
        return getTimeFromPosition(x, SettingsMaster.getMiddle());
    }

    private static final Toolset toolset = new Toolset(SelectionTool.get(), HitTool.don(), HitTool.kat(), SliderTool.get(), SpinnerTool.get());
    public static Toolset getToolset()
    {
        return toolset;
    }
}
