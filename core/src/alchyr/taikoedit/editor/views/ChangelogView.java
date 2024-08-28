package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.changes.MapChange;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.tools.SelectionTool;
import alchyr.taikoedit.editor.tools.Toolset;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.EditorTime;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;

import static alchyr.taikoedit.TaikoEditor.*;

public class ChangelogView extends MapView {
    public static final String ID = "changelog";
    @Override
    public String typeString() {
        return ID;
    }

    public static final int HEIGHT = 50;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.7f);

    private static final int midPos = SettingsMaster.getWidth() / 2;

    //Base position values
    private int baseTextY = 0;

    //Offset
    private int textY = 0;

    private double lastSounded;

    public ChangelogView(EditorLayer parent, EditorBeatmap beatmap) {
        super(ViewType.CHANGELOG_VIEW, parent, beatmap, HEIGHT);
        lastSounded = 0;

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

        baseTextY = this.y + HEIGHT / 2;

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
    public void update(double exactPos, long msPos, float elapsed, boolean canHover) {
        super.update(exactPos, msPos, elapsed, canHover);

        //if changelog index or branch is not the same as it was last update: update display buttons (buttons will "jump" to selected state)
    }

    @Override
    public MouseHoldObject click(float x, float y, int button) {
        return super.click(x, y, button);
    }

    @Override
    public void renderBase(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, bottom, SettingsMaster.getWidth(), height);

        MapChange change = map.lastChange();
        if (change != null) {
            textRenderer.renderTextCentered(sb, change.getName(), SettingsMaster.getMiddleX(), textY);
        }
    }

    private final DecimalFormat df = new DecimalFormat("#0.##", osuDecimalFormat);

    @Override
    public void renderObject(MapObject o, SpriteBatch sb, ShapeRenderer sr, float alpha) {
    }
    @Override
    public void renderSelection(MapObject o, SpriteBatch sb, ShapeRenderer sr) {
        //o.renderSelection(sb, sr, time, viewScale, SettingsMaster.getMiddleX(), objectY);
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends MapObject>> prep() {
        return map.getEditObjects(time - EditorLayer.viewTime, time + EditorLayer.viewTime);
    }

    @Override
    public void selectAll() {
        clearSelection();

        selectedObjects = new MapObjectTreeMap<>();

        selectedObjects.addAll(map.objects);

        for (ArrayList<? extends MapObject> stuff : selectedObjects.values())
            for (MapObject o : stuff)
                o.selected = true;
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

        MapObjectTreeMap<MapObject> newSelection;

        if (selectedObjects == null)
        {
            newSelection = new MapObjectTreeMap<>();
            if (startTime > endTime)
                newSelection.addAll(map.getSubMap(endTime, startTime));
            else
                newSelection.addAll(map.getSubMap(startTime, endTime));

            selectedObjects = newSelection;
            for (ArrayList<MapObject> stuff : selectedObjects.values())
                for (MapObject o : stuff)
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

            for (ArrayList<? extends MapObject> stuff : newSelected.values())
                for (MapObject o : stuff)
                    o.selected = true;
        }
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
    public void dispose() {
        super.dispose();
    }

    @Override
    public void updateSelectionPositions() {
        MapObjectTreeMap<MapObject> selected = getSelection();
        map.objects.removeAll(selected);
        map.objects.addAll(selected);
        refreshSelection();
    }

    @Override
    public void deleteObject(MapObject o) {
        this.map.registerAndPerformDelete(o);
    }

    @Override
    public void pasteObjects(MapObjectTreeMap<MapObject> copyObjects) {
    }

    @Override
    public void reverse() {
    }

    @Override
    public void deleteSelection() {
        if (selectedObjects != null)
        {
            this.map.registerAndPerformDelete(selectedObjects);
            clearSelection();
        }
    }

    @Override
    public void registerMove(long totalMovement) {
        if (selectedObjects != null && totalMovement != 0)
        {
            MapObjectTreeMap<MapObject> movementCopy = new MapObjectTreeMap<>();
            movementCopy.addAll(selectedObjects); //use addAll to make a copy without sharing any references other than the positionalobjects themselves
            this.map.registerAndPerformObjectMovement(movementCopy, totalMovement);
        }
    }

    private static final Toolset toolset = new Toolset(SelectionTool.get());
    public Toolset getToolset()
    {
        return toolset;
    }
}