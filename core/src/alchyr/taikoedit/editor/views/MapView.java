package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.changes.MapObjectChange;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.tools.Toolset;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.text.DecimalFormat;
import java.util.*;
import java.util.function.BiFunction;

import static alchyr.taikoedit.TaikoEditor.*;

public abstract class MapView {
    public enum ViewType {
        OBJECT_VIEW,
        EFFECT_VIEW,
        GAMEPLAY_VIEW,
        DIFFICULTY_VIEW,
        CHANGELOG_VIEW
    }
    public abstract String typeString();
    public static MapView fromTypeString(String typeString, EditorLayer editor, EditorBeatmap map) {
        switch (typeString) {
            case ObjectView.ID:
                return new ObjectView(editor, map);
            case EffectView.ID:
                return new EffectView(editor, map);
            case GameplayView.ID:
                return new GameplayView(editor, map);
            case GimmickView.ID:
                return new GimmickView(editor, map);
            case DifficultyView.ID: //No
            default:
                return null;
        }
    }

    // prep -> update -> primaryUpdate for primary view -> rendering


    private static final Color selectionGlow = new Color(0.8f, 0.7f, 0.4f, 1.0f);
    private static final float GLOW_THICKNESS = Math.max(2, 2.0f * SettingsMaster.SCALE);
    private static final float SEPARATION_THICKNESS = Math.max(1, 1.0f * SettingsMaster.SCALE);
    public static final Comparator<Long> reverseLongComparator = Comparator.reverseOrder();

    EditorLayer parent;
    public EditorBeatmap map;
    public final ViewType type; //Views of the same time should use the same set of objects in the same order
    //Note for future people looking at this: No longer relevant as any view can be placed in a unique position, so each view always handles their objects individually.
    //This makes the ViewType enum useless and inconvenient, but I'm too lazy to change it now.

    protected boolean isPrimary;

    //Position within song in milliseconds
    public double preciseTime = 0;
    protected long time = 0;
    protected boolean positionLocked = false;
    protected double lockOffset = 0;

    private static final DecimalFormat svFormat = new DecimalFormat("0", osuDecimalFormat);
    private final BitmapFont offsetFont = assetMaster.getFont("aller medium");


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

    private static final BiFunction<MapObject, MapObject, Boolean> defaultReplace = (placed, existing)->true;
    public BiFunction<MapObject, MapObject, Boolean> replaceTest = defaultReplace;

    //Selection
    private MapObjectTreeMap<MapObject> selectedObjects;
    private MapObjectTreeMap<MapObject> lastReturnedSelection;
    private int lastSelectionMapState = Integer.MIN_VALUE;


    private final MapObjectTreeMap<MapObject> additionalDisplayObjects = new MapObjectTreeMap<>();

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

    protected void removeOverlayButton(ImageButton b) {
        if (overlayButtons.remove(b)) {
            overlayWidth -= b.getWidth();
        }
    }

    protected void addLockPositionButton() {
        Texture unlocked = assetMaster.get("editor:unlocked");
        Texture unlockedh = assetMaster.get("editor:unlockedh");
        Texture locked = assetMaster.get("editor:locked");
        Texture lockedh = assetMaster.get("editor:lockedh");
        ImageButton lockPositionButton = new ImageButton(unlocked, unlockedh).setAction("Lock");
        lockPositionButton.setClick((key)->{
            if (key == Input.Buttons.RIGHT) {
                lockPositionButton.setTextures(unlocked, unlockedh);
                lockOffset = 0;
                lockPositionButton.setAction("Lock");
                this.positionLocked = false;
            }
            else {
                if (lockPositionButton.action.startsWith("Unlock")) {
                    lockPositionButton.setTextures(unlocked, unlockedh);
                    if (lockOffset != 0)
                        lockPositionButton.setAction("Lock (Right click to reset)");
                    else
                        lockPositionButton.setAction("Lock");

                    this.positionLocked = false;
                }
                else {
                    lockPositionButton.setTextures(locked, lockedh);
                    if (lockOffset != 0)
                        lockPositionButton.setAction("Unlock (Right click to reset)");
                    else
                        lockPositionButton.setAction("Unlock");

                    this.positionLocked = true;
                }
            }
        });
        addOverlayButton(lockPositionButton);
    }

    public double getPreciseTime()
    {
        return preciseTime;
    }

    //time is in milliseconds
    public double getTimeFromPosition(float x) {
        return getTimeFromPosition(x, SettingsMaster.getMiddleX()); //Overriden for views that aren't center based
    }
    protected double getTimeFromPosition(float x, int offset)
    {
        return (getPreciseTime() + (x - offset) / EditorLayer.viewScale);
    }

    public int getPositionFromTime(double time)
    {
        return getPositionFromTime(time, getBasePosition());
    }
    public int getPositionFromTime(double time, int offset)
    {
        return (int) ((time - this.getPreciseTime()) * EditorLayer.viewScale + offset);
    }
    public int getBasePosition() {
        return SettingsMaster.getMiddleX();
    }

    public int setPos(int y)
    {
        this.y = y - height;

        return this.y;
    }

    public boolean allowVerticalDrag() {
        return false;
    }

    //If this method returns true, make it the primary view
    public boolean select()
    {
        if (!isPrimary)
        {
            primary();
            return true;
        }
        return false;
    }
    public MouseHoldObject clickOverlay(float x, float y, int button)
    {
        if (x <= overlayWidth && y >= bottom + overlayY)
        {
            for (ImageButton b : overlayButtons)
            {
                if (b.click(x, y, button))
                {
                    return MouseHoldObject.nothing;
                }
            }
        }
        return null;
    }
    public MouseHoldObject click(float x, float y, int button)
    {
        return null;
    }
    public void primary() {
        isPrimary = true;
    }
    public void notPrimary() {
        isPrimary = false;
    }

    //Prep -> update -> rendering

    //Ensure map is ready for rendering. Exact details will depend on the view.
    public abstract NavigableMap<Long, ? extends ArrayList<? extends MapObject>> prep();
    public MapObjectTreeMap<MapObject> additionalDisplayObjects() {
        return additionalDisplayObjects;
    }
    public void displayAdditionalObject(MapObject object) {
        additionalDisplayObjects.addIfAbsent(object);
    }
    public void displayAdditionalObjects(Map<? extends Long, ? extends ArrayList<? extends MapObject>> map) {
        additionalDisplayObjects.addAllUnique(map);
    }

    public void setOffset(int offset)
    {
        this.yOffset = offset;

        this.bottom = this.y + offset;
        this.top = this.bottom + height;
    }
    public void update(double exactPos, long msPos, float elapsed, boolean canHover)
    {
        if (positionLocked) {
            lockOffset = preciseTime - (exactPos * 1000.0f);
        }
        else {
            preciseTime = (exactPos * 1000.0f) + lockOffset;
            time = msPos + (long) lockOffset;
        }
        for (ImageButton b : overlayButtons) {
            b.update(elapsed);
            if (!canHover)
                b.hovered = false;

            if (b.hovered) {
                TaikoEditor.hoverText.setText(b.action);
            }
        }
    }
    public abstract void renderBase(SpriteBatch sb, ShapeRenderer sr);
    public void renderStack(ArrayList<? extends MapObject> objects, SpriteBatch sb, ShapeRenderer sr) {
        for (MapObject o : objects) {
            renderObject(o, sb, sr);
        }
    }
    public void renderObject(MapObject o, SpriteBatch sb, ShapeRenderer sr)
    {
        renderObject(o, sb, sr, 1.0f);
    }
    public abstract void renderObject(MapObject o, SpriteBatch sb, ShapeRenderer sr, float alpha);
    public abstract void renderSelection(MapObject o, SpriteBatch sb, ShapeRenderer sr); //for objects that are not actually selected (while selecting)

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
        if (lockOffset != 0) {
            sb.setColor(Color.WHITE);
            textRenderer.setFont(offsetFont).renderTextCentered(sb, svFormat.format(lockOffset), SettingsMaster.getMiddleX(), bottom + (height / 2f), Color.WHITE);
        }

        additionalDisplayObjects.clear();
    }

    public Snap getPreviousSnap(long pos) {
        Map.Entry<Long, Snap> previous = map.getCurrentSnaps().lowerEntry(music.isPlaying() ? pos - 250 : pos);
        if (previous == null)
            return null;
        while (pos - previous.getKey() < 2)
        {
            previous = map.getCurrentSnaps().lowerEntry(previous.getKey());
            if (previous == null)
                return null;
        }
        return previous.getValue();
    }

    public Snap getNextSnap(long pos) {
        Map.Entry<Long, Snap> next = map.getCurrentSnaps().higherEntry(music.isPlaying() ? pos + 250 : pos);
        if (next == null)
            return null;
        if (next.getKey() - pos < 2)
        {
            next = map.getCurrentSnaps().higherEntry(next.getKey());
            if (next == null)
                return null;
        }
        return next.getValue();
    }

    public Snap getClosestSnap(double time, float limit) { //time in ms, limit as max ms gap
        long rounded = Math.round(time);
        if (map.getCurrentSnaps().containsKey(rounded))
            return map.getCurrentSnaps().get(rounded);

        Map.Entry<Long, Snap> lower, higher;
        lower = map.getCurrentSnaps().lowerEntry(rounded);
        higher = map.getCurrentSnaps().higherEntry(rounded);

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

    public boolean noSnaps() {
        return map.getCurrentSnaps().isEmpty();
    }

    //Other methods
    public void delete(int x, int y) { //Delete selection if any, otherwise closest object if object is close enough. (Key input.)
        if (hasSelection()) {
            deleteSelection();
            clearSelection();
        }
        else {
            MapObject close = clickObject(x, y);

            if (close != null) {
                deleteObject(close);
                clearSelection();
            }
        }
    }
    public boolean rightClick(float x, float y) { //delete clicked object, or entire selection of object is selected (Mouse input.)
        MapObject close = clickObject(x, y);

        if (close != null) {
            if (close.selected && hasSelection()) {
                deleteSelection();
            }
            else {
                deleteObject(close);
            }
            clearSelection();
            return true;
        }
        return false;
    }

    public void updateVerticalDrag(MapObject dragObject, MapObjectTreeMap<MapObject> copyObjects, HashMap<MapObject, MapObject> copyMap, double totalVerticalOffset) {

    }
    public void deleteObject(MapObject o) {
        this.map.registerAndPerformDelete(o);
    }
    public abstract void deleteSelection();
    public abstract void registerMove(long totalMovement); //Registers a movement of selected objects with underlying map for undo/redo support. May be called with 0 movement.
    public void registerValueChange(HashMap<MapObject, MapObject> copyMap) { //Registers a modification of currently selected objects with underlying map for undo/redo support

    }
    public abstract void pasteObjects(ViewType copyType, MapObjectTreeMap<MapObject> copyObjects);
    public abstract void reverse();
    public abstract void invert();

    //Selection logic
    public abstract NavigableMap<Long, ? extends ArrayList<? extends MapObject>> getVisibleRange(long start, long end);
    public MapObjectTreeMap<MapObject> getSelection() {
        return getSelection(false);
    } //Selected objects should be actual objects that will be modified
    public MapObjectTreeMap<MapObject> getSelection(boolean forceNewMap) {
        if (selectedObjects == null) {
            return lastReturnedSelection = null;
        }
        if (forceNewMap || lastReturnedSelection == null || lastSelectionMapState != map.getStateKey()) {
            lastReturnedSelection = new MapObjectTreeMap<>();
            lastSelectionMapState = map.getStateKey();

            MapObjectTreeMap<? extends MapObject> baseMap = null;
            switch (type) {
                case OBJECT_VIEW:
                case GAMEPLAY_VIEW:
                    baseMap = map.objects;
                    break;
                case EFFECT_VIEW:
                    baseMap = map.allPoints;
                    break;
                case CHANGELOG_VIEW:
                case DIFFICULTY_VIEW:
                    return lastReturnedSelection = null;
            }

            for (ArrayList<MapObject> stack : selectedObjects.values()) {
                for (MapObject obj : stack) {
                    if (baseMap.containsKeyedValue(obj.getPos(), obj)) {
                        lastReturnedSelection.add(obj);
                    }
                }
            }
        }
        lastReturnedSelection.removeIf((obj)->!obj.selected); //selection can be messed with by a different view with same object type
        return lastReturnedSelection.isEmpty() ? null : lastReturnedSelection;
    }
    public abstract String getSelectionString();

    public boolean hasSelection()
    {
        if (selectedObjects == null || selectedObjects.isEmpty()) return false;
        MapObjectTreeMap<MapObject> selected = getSelection();
        return selected != null && !selected.isEmpty();
    }

    public void clearSelection()
    {
        if (selectedObjects != null)
        {
            for (List<? extends MapObject> stuff : selectedObjects.values())
            {
                for (MapObject o : stuff)
                    o.selected = false;
            }
            selectedObjects = null;

            for (MapView view : parent.getViewSet(map).getViews()) {
                if (view != this && view.type == type) {
                    view.clearSelection();
                }
            }
        }
        lastReturnedSelection = null;
    }

    public abstract void selectAll();

    public abstract void addSelectionRange(long startTime, long endTime);

    //Perform Click selection.
    public void movingObjects() { //method called when dragging (horizontal) begins
    }
    public void dragRelease() { //method called when mouse released without entering a dragging mode
    }
    public MapObject clickObject(float x, float y) {
        return getObjectAt(x, y);
    }
    public abstract MapObject getObjectAt(float x, float y);
    public abstract boolean clickedEnd(MapObject o, float x); //assuming this object was returned by clickObject, y should already be confirmed to be in range.
    public void select(MapObject p) //Add a single object to selection.
    {
        if (selectedObjects == null)
            selectedObjects = new MapObjectTreeMap<>();

        selectedObjects.add(p);
        p.selected = true;

        lastReturnedSelection = null;
    }
    public void selectObjects(MapObjectTreeMap<? extends MapObject> toSelect) {
        if (selectedObjects == null) {
            selectedObjects = new MapObjectTreeMap<>();
        }
        selectedObjects.addAllUnique(toSelect);
        toSelect.forEachObject((obj)->obj.selected = true);

        lastReturnedSelection = null;
    }
    public void deselect(MapObject p)
    {
        p.selected = false;
        if (selectedObjects != null)
        {
            selectedObjects.removeObject(p);
        }

        lastReturnedSelection = null;
    }

    public void resnap()
    {
        boolean onlySelected = hasSelection();
        if (!onlySelected)
            selectAll();

        TreeMap<Long, Snap> allSnaps = map.getCurrentSnaps();
        int changed = 0;

        MapObjectTreeMap<MapObject> originalPositions = getSelection(true);
        if (originalPositions == null || originalPositions.isEmpty()) return;

        MapObjectTreeMap<MapObject> newPositions = new MapObjectTreeMap<>();

        for (Map.Entry<Long, ArrayList<MapObject>> objs : originalPositions.entrySet())
        {
            if (allSnaps.containsKey(objs.getKey()))
            {
                newPositions.put(objs.getKey(), objs.getValue());
                continue;
            }

            long newSnap = objs.getKey();
            if (allSnaps.containsKey(newSnap + 1))
            {
                newSnap += 1;
            }
            else if (allSnaps.containsKey(newSnap - 1))
            {
                newSnap -= 1;
            }
            else {
                Long higherSnap = allSnaps.higherKey(newSnap),
                        lowerSnap = allSnaps.lowerKey(newSnap);

                if (higherSnap != null && lowerSnap != null) {
                    if (newSnap - lowerSnap < higherSnap - newSnap) {
                        newSnap = lowerSnap;
                    }
                    else {
                        newSnap = higherSnap;
                    }
                }
                else if (higherSnap != null) {
                    newSnap = higherSnap;
                }
                else if (lowerSnap != null) {
                    newSnap = lowerSnap;
                }
            }

            if (newSnap != objs.getKey())
            {
                newPositions.put(newSnap, objs.getValue());
                changed += objs.getValue().size();
            }
            else {
                newPositions.put(objs.getKey(), objs.getValue());
            }
        }

        map.registerChange(new MapObjectChange(map, "Resnap", originalPositions, newPositions).preDo());

        parent.showText("Resnapped " + changed + " objects.");

        if (!onlySelected)
            clearSelection();
    }

    public abstract Toolset getToolset();

    public void dispose()
    {
        parent = null;
        map = null;
        pix = null; //no need to dispose textures, asset master handles them.
    }
}
