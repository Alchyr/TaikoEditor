package alchyr.taikoedit.editor.views;

import alchyr.diffcalc.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.MapChange;
import alchyr.taikoedit.editor.tools.*;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.util.EditorTime;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.text.DecimalFormat;
import java.util.*;

import static alchyr.taikoedit.TaikoEditor.*;
import static alchyr.taikoedit.core.layers.EditorLayer.viewScale;

public class DifficultyView extends MapView {
    public static final int HEIGHT = 350;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.7f);

    private static final int midPos = SettingsMaster.getWidth() / 2;

    //Base position values
    private int baseTextY = 0;

    //Offset
    private int textY = 0;

    private double lastSounded;

    private final Map<HitObject, TaikoDifficultyHitObject> difficultyInfo;

    public DifficultyView(EditorLayer parent, EditorBeatmap beatmap, Map<HitObject, TaikoDifficultyHitObject> difficultyInfo) {
        super(ViewType.DIFFICULTY_VIEW, parent, beatmap, HEIGHT);
        lastSounded = 0;

        this.difficultyInfo = difficultyInfo;

        addOverlayButton(new ImageButton(assetMaster.get("editor:exit"), assetMaster.get("editor:exith")).setClick(this::close).setAction("Close View"));
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

        baseTextY = this.y + 50;

        return this.y;
    }

    public void setOffset(int offset)
    {
        super.setOffset(offset);

        textY = baseTextY + yOffset;
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
    public void renderBase(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, bottom, SettingsMaster.getWidth(), height);
    }

    private final DecimalFormat df = new DecimalFormat("#0.##", osuSafe);

    @Override
    public void renderObject(PositionalObject o, SpriteBatch sb, ShapeRenderer sr, float alpha) {
        textRenderer.renderText(sb, Color.WHITE, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).BASE_COLOR_DEBUG), SettingsMaster.getMiddle() + (float) (o.getPos() - time) * viewScale, textY + 250);
        textRenderer.renderText(sb, Color.WHITE, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).SWAP_BONUS_DEBUG), SettingsMaster.getMiddle() + (float) (o.getPos() - time) * viewScale, textY + 200);
        textRenderer.renderText(sb, Color.WHITE, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).RHYTHM_BONUS_DEBUG), SettingsMaster.getMiddle() + (float) (o.getPos() - time) * viewScale, textY + 150);
        textRenderer.renderText(sb, Color.WHITE, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).COMBINED_DEBUG), SettingsMaster.getMiddle() + (float) (o.getPos() - time) * viewScale, textY + 100);
        textRenderer.renderText(sb, Color.WHITE, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).BURST_BASE), SettingsMaster.getMiddle() + (float) (o.getPos() - time) * viewScale, textY + 50);
        textRenderer.renderText(sb, Color.WHITE, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).BURST_DEBUG), SettingsMaster.getMiddle() + (float) (o.getPos() - time) * viewScale, textY);
    }
    @Override
    public void renderSelection(PositionalObject o, SpriteBatch sb, ShapeRenderer sr) {
        //o.renderSelection(sb, sr, time, viewScale, SettingsMaster.getMiddle(), objectY);
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
        NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> source = map.getEditObjects((long) time - EditorLayer.viewTime, (long) time + EditorLayer.viewTime);

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

    public PositionalObject clickObject(float x, float y)
    {
        return null;
    }

    @Override
    public boolean clickedEnd(PositionalObject o, float x) {
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
    public double getTimeFromPosition(float x) {
        return getTimeFromPosition(x, SettingsMaster.getMiddle());
    }

    private static final Toolset toolset = new Toolset(SelectionTool.get());
    public Toolset getToolset()
    {
        return toolset;
    }
}