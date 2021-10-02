package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.*;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public abstract class MapView {
    public enum ViewType {
        OBJECT_VIEW,
        TIMING_VIEW,
        EFFECT_VIEW,
        GAMEPLAY_VIEW,
        DIFFICULTY_VIEW
    }

    // prep -> update -> primaryUpdate for primary view -> rendering


    private static final Color selectionGlow = new Color(0.8f, 0.7f, 0.4f, 1.0f);
    private static final float GLOW_THICKNESS = 2.0f * SettingsMaster.SCALE;
    private static final float SEPARATION_THICKNESS = 1.0f * SettingsMaster.SCALE;

    EditorLayer parent;
    public EditorBeatmap map;
    public final ViewType type; //Views of the same time should use the same set of objects in the same order

    public boolean isPrimary;

    //Position within song.
    protected double time = 0;

    //Base position values
    public int y;

    public int yOffset = 0;
    //Post-Offset values
    public int bottom; //y + offset
    public int top;

    //Un-Offset values
    public int overlayY; //Relative to Bottom.

    public int overlayWidth;

    public int height;
    protected Texture pix = assetMaster.get("ui:pixel");
    //protected Texture overlayEnd = assetMaster.get("editor:overlay end");

    //Selection
    protected PositionalObjectTreeMap<PositionalObject> selectedObjects;
    public boolean allowVerticalDrag = false;

    private final List<ImageButton> overlayButtons;

    public MapView(ViewType viewType, EditorLayer parent, EditorBeatmap beatmap, int height)
    {
        this.type = viewType;
        this.parent = parent;
        this.map = beatmap;

        this.y = 0;
        this.height = height;
        this.overlayY = height - 30;
        this.overlayWidth = 0;

        setOffset(0);

        isPrimary = false;

        overlayButtons = new ArrayList<>();
    }

    protected void addOverlayButton(ImageButton b)
    {
        overlayButtons.add(b);
        overlayWidth += b.getWidth();
    }

    public double getTime()
    {
        return time;
    }
    public abstract double getTimeFromPosition(int x); //milliseconds
    protected double getTimeFromPosition(int x, int offset)
    {
        return (time + (x - offset) / EditorLayer.viewScale);
    }
    public int getPositionFromTime(double time, int offset)
    {
        return (int) ((time - this.time) * EditorLayer.viewScale + offset);
    }
    public float getBasePosition() {
        return SettingsMaster.getMiddle();
    }

    public int setPos(int y)
    {
        this.y = y - height;

        return this.y;
    }

    //If this method returns true, make it the primary view
    public boolean justClicked;
    public boolean click(int x, int y, int pointer, int button)
    {
        justClicked = true;
        if (!isPrimary)
        {
            isPrimary = true;
            return true;
        }
        return false;
    }
    public boolean clickOverlay(int x, int y, int button)
    {
        if (x <= overlayWidth && y >= overlayY)
        {
            for (ImageButton b : overlayButtons)
            {
                if (b.click(x, y, button))
                {
                    return true;
                }
            }
        }
        return false;
    }
    public void notPrimary() {
        isPrimary = false;
    }

    //Prep -> update -> rendering

    //Ensure map is ready for rendering. Exact details will depend on the view.
    public abstract NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> prep(long pos);
    public void setOffset(int offset)
    {
        this.yOffset = offset;

        this.bottom = this.y + offset;
        this.top = this.bottom + height;
    }
    public void update(double exactPos, long msPos)
    {
        time = exactPos * 1000.0f;
        for (ImageButton b : overlayButtons) {
            b.update();
            if (b.hovered) {
                TaikoEditor.hoverText.setText(b.action);
            }
        }
    }
    public abstract void renderBase(SpriteBatch sb, ShapeRenderer sr);
    public void renderObject(PositionalObject o, SpriteBatch sb, ShapeRenderer sr)
    {
        renderObject(o, sb, sr, 1.0f);
    }
    public abstract void renderObject(PositionalObject o, SpriteBatch sb, ShapeRenderer sr, float alpha);
    public abstract void renderSelection(PositionalObject o, SpriteBatch sb, ShapeRenderer sr); //for objects that are not actually selected (while selecting)

    //Update that occurs only if it is the primary view
    public void primaryUpdate(boolean isPlaying)
    {
    }
    //Rendering done to show the currently active MapView.
    public void primaryRender(SpriteBatch sb, ShapeRenderer sr)
    {
        sb.setColor(selectionGlow);
        sb.draw(pix, 0, this.top - SEPARATION_THICKNESS, SettingsMaster.getWidth(), GLOW_THICKNESS);
        sb.draw(pix, 0, this.bottom - SEPARATION_THICKNESS, SettingsMaster.getWidth(), GLOW_THICKNESS);
    }

    public void renderOverlay(SpriteBatch sb, ShapeRenderer sr)
    {
        if (!overlayButtons.isEmpty())
        {
            if (!isPrimary)
            {
                sb.setColor(Color.BLACK);
                sb.draw(pix, 0, this.top - SEPARATION_THICKNESS, SettingsMaster.getWidth(), SEPARATION_THICKNESS);
                sb.draw(pix, 0, this.bottom, SettingsMaster.getWidth(), SEPARATION_THICKNESS);
            }

            sb.setColor(Color.WHITE);
            float y = this.bottom + this.overlayY;
            float x = 0;
            for (ImageButton b : overlayButtons)
            {
                b.render(sb, sr, (int)x, (int)y);
                x += b.getWidth();
            }
        }
    }

    public abstract Snap getPreviousSnap();
    public abstract Snap getNextSnap();
    public abstract Snap getClosestSnap(double time, float limit);
    public abstract boolean noSnaps();

    //Other methods
    public abstract PositionalObjectTreeMap<?> getEditMap(); //Returns the map that should be modified by selection
    public void delete(int x, int y) { //Delete selection if any, otherwise closest object if object is close enough. (Key input.)
        if (hasSelection()) {
            deleteSelection();
            clearSelection();
        }
        else {
            PositionalObject close = clickObject(x, y);

            if (close != null) {
                deleteObject(close);
                clearSelection();
            }
        }
    }
    public void deletePrecise(int x, int y) { //delete clicked object, or entire selection of object is selected (Mouse input.)
        PositionalObject close = clickObject(x, y);

        if (close != null) {
            if (close.selected && hasSelection()) {
                deleteSelection();
            }
            else {
                deleteObject(close);
            }
            clearSelection();
        }
    }
    public abstract void deleteObject(PositionalObject o);
    public abstract void deleteSelection();
    public abstract void registerMove(long totalMovement); //Registers a movement of selected objects with underlying map for undo/redo support
    public void registerValueChange(double totalMovement) { //Registers a modification of selected objects with underlying map for undo/redo support

    }
    public abstract void pasteObjects(PositionalObjectTreeMap<PositionalObject> copyObjects);
    public abstract void reverse();

    //Selection logic
    public abstract NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> getVisibleRange(long start, long end);
    public PositionalObjectTreeMap<PositionalObject> getSelection() {
        return selectedObjects;
    } //Selected objects should be actual objects that will be modified
    public abstract String getSelectionString();

    public boolean hasSelection()
    {
        return selectedObjects != null && !selectedObjects.isEmpty();
    }

    public void clearSelection()
    {
        if (selectedObjects != null)
        {
            for (List<? extends PositionalObject> stuff : selectedObjects.values())
            {
                for (PositionalObject o : stuff)
                    o.selected = false;
            }
            selectedObjects = null;
        }
    }
    public void refreshSelection()
    {
        if (!hasSelection())
            return;

        PositionalObjectTreeMap<PositionalObject> selectionCopy = new PositionalObjectTreeMap<>();
        selectionCopy.addAll(selectedObjects);

        this.selectedObjects = selectionCopy;
    }

    public abstract void selectAll();

    public abstract void addSelectionRange(long startTime, long endTime);

    //Perform Click selection.
    public void dragging() { //method called when dragging begins
    }
    public void clickRelease() { //method called when mouse released without entering a dragging mode
    }
    public abstract PositionalObject clickObject(int x, int y);
    public abstract boolean clickedEnd(PositionalObject o, int x); //assuming this object was returned by clickObject, y should already be confirmed to be in range.
    public void select(PositionalObject p) //Add a single object to selection.
    {
        p.selected = true;
        if (selectedObjects == null)
            selectedObjects = new PositionalObjectTreeMap<>();

        selectedObjects.add(p);
    }
    public void deselect(PositionalObject p)
    {
        p.selected = false;
        if (selectedObjects != null)
        {
            selectedObjects.removeObject(p);
        }
    }

    public void resnap()
    {
        clearSelection();

        PositionalObjectTreeMap<HitObject> resnapped = new PositionalObjectTreeMap<>();
        TreeMap<Long, Snap> allSnaps = map.getAllSnaps();

        for (Map.Entry<Long, ArrayList<HitObject>> objs : map.objects.entrySet())
        {
            if (allSnaps.containsKey(objs.getKey()))
            {
                resnapped.put(objs.getKey(), objs.getValue());
                continue;
            }

            long newSnap = objs.getKey();
            if (allSnaps.containsKey(objs.getKey() + 1))
            {
                newSnap += 1;
            }
            else if (allSnaps.containsKey(objs.getKey() - 1))
            {
                newSnap -= 1;
            }

            if (newSnap != objs.getKey())
            {
                for (HitObject h : objs.getValue())
                {
                    h.setPosition(newSnap);
                }
            }

            resnapped.put(newSnap, objs.getValue());
        }

        map.objects.clear();
        map.objects.addAll(resnapped);
    }


    public void dispose()
    {
        parent = null;
        map = null;
        pix = null; //no need to dispose textures, asset master handles them.
    }
}
