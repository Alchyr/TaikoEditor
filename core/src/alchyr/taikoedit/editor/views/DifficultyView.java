package alchyr.taikoedit.editor.views;

import alchyr.diffcalc.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
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
    public static final String ID = "diff";
    @Override
    public String typeString() {
        return ID;
    }

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
        if (isPrimary && lockOffset == 0 && isPlaying && lastSounded < preciseTime && preciseTime - lastSounded < 25) //might have skipped backwards
        {

            //To play ALL hitobjects passed.
            for (ArrayList<HitObject> objects : map.objects.subMap((long) lastSounded, false, time, true).values())
            {
                for (HitObject o : objects)
                {
                    o.playSound();
                }
            }
        }
        lastSounded = preciseTime;
    }

    @Override
    public void renderBase(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, bottom, SettingsMaster.getWidth(), height);
    }

    private final DecimalFormat df = new DecimalFormat("#0.##", osuDecimalFormat);

    @Override
    public void renderObject(PositionalObject o, SpriteBatch sb, ShapeRenderer sr, float alpha) {
        textRenderer.renderText(sb, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).BASE_COLOR_DEBUG), SettingsMaster.getMiddleX() + (float) (o.getPos() - preciseTime) * viewScale, textY + 250, Color.WHITE);
        textRenderer.renderText(sb, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).SWAP_BONUS_DEBUG), SettingsMaster.getMiddleX() + (float) (o.getPos() - preciseTime) * viewScale, textY + 200);
        textRenderer.renderText(sb, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).RHYTHM_BONUS_DEBUG), SettingsMaster.getMiddleX() + (float) (o.getPos() - preciseTime) * viewScale, textY + 150);
        textRenderer.renderText(sb, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).COMBINED_DEBUG), SettingsMaster.getMiddleX() + (float) (o.getPos() - preciseTime) * viewScale, textY + 100);
        textRenderer.renderText(sb, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).BURST_BASE), SettingsMaster.getMiddleX() + (float) (o.getPos() - preciseTime) * viewScale, textY + 50);
        textRenderer.renderText(sb, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).BURST_DEBUG), SettingsMaster.getMiddleX() + (float) (o.getPos() - preciseTime) * viewScale, textY);
    }
    @Override
    public void renderSelection(PositionalObject o, SpriteBatch sb, ShapeRenderer sr) {
        //o.renderSelection(sb, sr, time, viewScale, SettingsMaster.getMiddleX(), objectY);
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> prep() {
        return map.getEditObjects(time - EditorLayer.viewTime, time + EditorLayer.viewTime);
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
        NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> source = map.getEditObjects(time - EditorLayer.viewTime, time + EditorLayer.viewTime);

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

        return new EditorTime(time) + " - ";
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

            selectedObjects.addAllUnique(newSelected);

            for (ArrayList<? extends PositionalObject> stuff : newSelected.values())
                for (PositionalObject o : stuff)
                    o.selected = true;
        }
    }

    @Override
    public PositionalObject getObjectAt(float x, float y) {
        return null;
    }

    @Override
    public boolean clickedEnd(PositionalObject o, float x) {
        return false;
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    @Override
    public void updatePositions(PositionalObjectTreeMap<PositionalObject> moved) {
        map.objects.removeAll(moved);
        map.objects.addAll(moved);
    }

    @Override
    public void deleteObject(PositionalObject o) {
        this.map.delete(o);
    }

    @Override
    public void pasteObjects(PositionalObjectTreeMap<PositionalObject> copyObjects) {
    }

    @Override
    public void reverse() {
    }

    @Override
    public void deleteSelection() {
        if (selectedObjects != null)
        {
            this.map.delete(selectedObjects);
            clearSelection();
        }
    }

    @Override
    public void registerMove(long totalMovement) {
        if (selectedObjects != null && totalMovement != 0)
        {
            PositionalObjectTreeMap<PositionalObject> movementCopy = new PositionalObjectTreeMap<>();
            movementCopy.addAll(selectedObjects); //use addAll to make a copy without sharing any references other than the positionalobjects themselves
            this.map.registerObjectMovement(movementCopy, totalMovement);
        }
    }

    private static final Toolset toolset = new Toolset(SelectionTool.get());
    public Toolset getToolset()
    {
        return toolset;
    }
}