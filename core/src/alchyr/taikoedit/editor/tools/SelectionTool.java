package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.input.InputBinding;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.views.EffectView;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.ILongObject;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.*;
import java.util.function.BiConsumer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.editorLogger;
import static alchyr.taikoedit.TaikoEditor.music;
import static alchyr.taikoedit.core.input.BindingGroup.shift;
import static alchyr.taikoedit.core.layers.EditorLayer.viewScale;

//The default tool. Should be included in most toolsets and support pretty much any view other than gameplay.

//Honestly I should probably have split this into a separate selection tool for objects vs timing points but whatever
//Too lazy to fix at this point
public class SelectionTool extends EditorTool {
    private static final int MIN_DRAG_DIST = 7;
    private static final int MIN_VERTICAL_DRAG_DIST = 10;
    private static final Color selectionColor = new Color(0.8f, 0.8f, 0.8f, 0.35f);

    private static SelectionTool tool;

    public static volatile boolean seeked = false; //Prevents seeking while dragging during playback more than once per frame

    ///Extra features - changing cursor based on hover?
    public boolean effectMode = true; //true = sv, false = volume
    private SelectionToolMode mode;

    //Selection data
    private MapView selectingView;
    private double clickStartTime;
    private float selectionEnd;

    //used for dragging objects
    private DragMode dragMode; //At start of drag, doesn't enable immediately. Requires a small amount of cursor distance before dragging will actually occur.

    private RenderableMouseHold hold = null;

    private enum DragMode {
        NONE,
        HORIZONTAL,
        VERTICAL
    }

    private enum SelectionToolMode {
        NONE,
        DRAGGING,
        DRAGGING_END,
        SELECTING
    }

    private final Texture pix = assetMaster.get("ui:pixel");


    private SelectionTool()
    {
        super("Selection");
        mode = SelectionToolMode.NONE;
    }

    public static SelectionTool get()
    {
        if (tool == null)
            tool = new SelectionTool();

        return tool;
    }

    @Override
    public boolean consumesRightClick() {
        return mode != SelectionToolMode.NONE;
    }

    //First check -> clicking on an object? If ctrl is held and it's selected, deselect it. Otherwise, select it.
    //Move onto drag logic.
    //If not clicking on an object, move to drag selection logic, and remove click logic from drag selection?

    //Selection functionality:
        /*
        On click: If ctrl is NOT held, clear current selections in the current view
            //IF CLICKING DIRECTLY ON A SELECTED OBJECT, CHANGE MODE TO DRAGGING IT INSTEAD OF SELECTING
            //Cannot drag while holding ctrl.
        convert first click position to a millisecond position within the song
        when drag is updated, determine distance between dragged position and current x
        If distance is more than a certain amount, change mode from "click" selection to "box" selection
        On release, if click selection, see if closest object of selecting view is at an appropriate distance
        If aoe selection, select all objects within the drag range.
         */

    /*
    DRAG logic:
        Initial time is saved
        Offset = different between time of dragged point and initial time
        Find closest snap to object (copy spinner/slider placement logic)
        offset = different between last offset and new offset
        store new offset in last offset
        Move objects by offset
     */

    @Override
    public MouseHoldObject click(MapView view, float x, float y, int button, int modifiers) {
        switch (mode)
        {
            case SELECTING:
                cancel();
                return MouseHoldObject.nothing;
            case NONE:
                hold = null;
                boolean delete = button == Input.Buttons.RIGHT;
                selectingView = view;
                MapObject dragObject = selectingView.clickObject(x, y);
                boolean shift = (modifiers & InputBinding.InputInfo.SHIFT_ID) != 0;
                boolean canDrag = true;
                boolean canSelect = false;
                boolean dragEnd = false;

                if (shift && view.hasSelection()) {
                    canDrag = false;

                    long clickTime = (long) selectingView.getTimeFromPosition(x);
                    long selectionStart = view.getSelection().firstKey(), selectionEnd = view.getSelection().lastKey();

                    if (clickTime < selectionStart) {
                        selectingView.addSelectionRange(clickTime, selectionStart);
                    }
                    else if (clickTime > selectionEnd) {
                        selectingView.addSelectionRange(selectionEnd, clickTime);
                    }

                    if (delete) {
                        selectingView.deleteSelection();
                        dragMode = DragMode.NONE;
                        selectingView = null;
                        return MouseHoldObject.nothing;
                    }
                }
                else if (dragObject != null && (modifiers & InputBinding.InputInfo.CTRL_ID) == 0)
                {
                    //ctrl not held
                    if (!dragObject.selected)
                    {
                        //replace selection
                        selectingView.clearSelection();
                        if (delete)
                        {
                            selectingView.deleteObject(dragObject);
                            dragMode = DragMode.NONE;
                            selectingView = null;
                            return MouseHoldObject.nothing;
                        }
                        else
                        {
                            selectingView.select(dragObject);

                            if (selectingView.clickedEnd(dragObject, x))
                            {
                                canDrag = false;
                                dragEnd = true;
                            }
                        }
                    }
                    else
                    {
                        //adjust selection
                        if (delete)
                        {
                            selectingView.deleteSelection();
                            dragMode = DragMode.NONE;
                            selectingView = null;
                            return MouseHoldObject.nothing;
                        }
                        else
                        {
                            if (selectingView.clickedEnd(dragObject, x))
                            {
                                canDrag = false;
                                dragEnd = true;
                            }
                        }
                    }
                }
                else if (dragObject != null && (modifiers & 1) != 0)
                {
                    //ctrl held
                    if (delete)
                    {
                        if (dragObject.selected)
                        {
                            selectingView.deselect(dragObject);
                        }
                        selectingView.deleteObject(dragObject);
                    }
                    else //just swap clicked object's selection state
                    {
                        if (dragObject.selected)
                            selectingView.deselect(dragObject);
                        else
                            selectingView.select(dragObject);
                    }

                    canDrag = false;
                }
                else if (dragObject == null)
                {
                    //Clicked on nothing
                    if ((modifiers & 1) == 0)
                    {
                        //Not holding ctrl
                        selectingView.clearSelection();
                    }
                    canDrag = false;
                    canSelect = !delete;
                }

                if (canDrag)
                {
                    clickStartTime = selectingView.getTimeFromPosition(x);
                    int clickStartY = Gdx.input.getY();
                    mode = SelectionToolMode.DRAGGING;
                    MapObjectTreeMap<MapObject> selection = selectingView.getSelection();
                    if (selection == null) return null;
                    return hold = new DraggingMouseHoldObject(this, selectingView, selection, dragObject, clickStartY);
                }
                else if (canSelect)
                {
                    clickStartTime = selectingView.getTimeFromPosition(x);
                    mode = SelectionToolMode.SELECTING;
                    selectionEnd = x;
                    return hold = new SelectionMouseHoldObject(tool, selectingView);
                }
                else if (dragEnd)
                {
                    clickStartTime = selectingView.getTimeFromPosition(x);
                    mode = SelectionToolMode.DRAGGING_END;
                    return hold = new EndDraggingMouseHoldObject(this, selectingView, dragObject);
                }
                dragMode = DragMode.NONE;
                selectingView = null;
                return null;
            case DRAGGING:
                if (button == Input.Buttons.RIGHT)
                {
                    //Delete dragged objects, which is selection
                    //Return dragged objects to original position then delete
                    view.deleteSelection();
                }
                cancel();
                return MouseHoldObject.nothing;
        }

        return null;
    }

    private void reset()
    {
        selectingView = null;
        dragMode = DragMode.NONE;
        mode = SelectionToolMode.NONE;
        hold = null;
    }

    @Override
    public void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed) {
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        seeked = false;
        if (selectingView != null && hold != null)
        {
            hold.render(sb, sr);
        }
    }

    @Override
    public void cancel() {
        tryCancel();
    }

    private boolean tryCancel() {
        boolean cancel = mode != SelectionToolMode.NONE;

        if (hold != null) {
            hold.onRelease(Gdx.input.getX(), 0);
            EditorLayer.processor.cancelMouseHold(hold);
        }

        reset();
        return cancel;
    }

    private static abstract class RenderableMouseHold extends MouseHoldObject {
        public RenderableMouseHold(BiConsumer<Float, Float> onDrag, BiConsumer<Float, Float> onRelease) {
            super(onDrag, onRelease);
        }

        public void render(SpriteBatch sb, ShapeRenderer sr) {

        }
    }

    private static class SelectionMouseHoldObject extends RenderableMouseHold
    {
        private final SelectionTool tool;
        private final MapView view;

        public SelectionMouseHoldObject(SelectionTool tool, MapView selectingView)
        {
            super(null, null);

            this.tool = tool;
            this.view = selectingView;

            onDrag = this::updateSelectionDrag;
            onRelease = (x,y)->this.releaseSelection(view, x, y);
        }

        private void updateSelectionDrag(float x, float y)
        {
            if (tool.dragMode == DragMode.HORIZONTAL || tool.selectionEnd - x < -MIN_DRAG_DIST || tool.selectionEnd - x > MIN_DRAG_DIST)
            {
                tool.dragMode = DragMode.HORIZONTAL;
                tool.selectionEnd = x;
            }
        }
        private void releaseSelection(MapView view, float x, float y)
        {
            if (tool.mode == SelectionToolMode.SELECTING && tool.dragMode == DragMode.HORIZONTAL)
            {
                view.addSelectionRange((long) tool.clickStartTime, (long) view.getTimeFromPosition(x));
            }
            tool.reset();
        }

        @Override
        public void update(float elapsed) {
            if (tool.mode == SelectionToolMode.SELECTING)
            {
                float mul = BindingGroup.alt() ? 4 : 1;
                if (Gdx.input.getX() <= 1)
                {
                    if (music.isPlaying()) {
                        TaikoEditor.onMain(()->{
                            if (!seeked) {
                                seeked = true;
                                music.seekSecond(music.getSecondTime() - mul * Math.max(0.12, Gdx.graphics.getDeltaTime() * 12));
                            }
                        });
                    }
                    else {
                        music.seekSecond(music.getSecondTime() - Math.max(0.01, mul * elapsed * 6));
                    }
                }
                else if (Gdx.input.getX() >= SettingsMaster.getWidth() - 1)
                {
                    music.seekSecond(music.getSecondTime() + Math.max(0.01, mul * elapsed * 6));
                }
            }
        }

        public void render(SpriteBatch sb, ShapeRenderer sr) {
            if (tool.mode == SelectionToolMode.SELECTING && tool.dragMode == DragMode.HORIZONTAL) //Render selection effect on objects you are dragging over before they're actually selected
            {
                int start = view.getPositionFromTime(tool.clickStartTime);

                if (start < tool.selectionEnd)
                {
                    NavigableMap<Long, ? extends ArrayList<? extends MapObject>> hovered = view.getVisibleRange((long) tool.clickStartTime, (long) view.getTimeFromPosition(tool.selectionEnd));

                    if (hovered != null)
                    {
                        for (ArrayList<? extends MapObject> list : hovered.values())
                            for (MapObject o : list)
                                view.renderSelection(o, sb, sr);
                    }

                    sb.setColor(selectionColor);
                    sb.draw(tool.pix, start, view.bottom, tool.selectionEnd - start, view.height);
                }
                else if (start > tool.selectionEnd)
                {
                    NavigableMap<Long, ? extends ArrayList<? extends MapObject>> hovered = view.getVisibleRange((long) view.getTimeFromPosition(tool.selectionEnd), (long) tool.clickStartTime);

                    if (hovered != null)
                    {
                        for (ArrayList<? extends MapObject> list : hovered.values())
                            for (MapObject o : list)
                                view.renderSelection(o, sb, sr);
                    }

                    sb.setColor(selectionColor);
                    sb.draw(tool.pix, tool.selectionEnd, view.bottom, start - tool.selectionEnd, view.height);
                }
            }
        }
    }

    private static class DraggingMouseHoldObject extends RenderableMouseHold
    {
        private final SelectionTool tool;
        private final MapView view;
        private double lastY;

        private final MapObjectTreeMap<MapObject> copyObjects;
        private final HashMap<MapObject, MapObject> copyMap; //map of original object -> copy object

        private final MapObject dragObject, referenceObject;
        private final long dragObjStartPos;

        private long totalHorizontalOffset = 0;
        private double totalVerticalOffset = 0;

        public DraggingMouseHoldObject(SelectionTool tool, MapView selectingView, MapObjectTreeMap<MapObject> selection, MapObject dragObject, double lastY)
        {
            super(null, null);

            this.tool = tool;
            this.view = selectingView;
            this.lastY = lastY;
            this.dragObject = dragObject;
            this.dragObjStartPos = dragObject.getPos();

            copyObjects = new MapObjectTreeMap<>();
            copyMap = new HashMap<>(selection.count());

            selection.forEachObject((obj)->{
                obj.hide(()->this.onRelease == null);
                MapObject copy = obj.shiftedCopy(obj.getPos());
                copyObjects.add(copy);
                copyMap.put(obj, copy);
            });


            referenceObject = copyObjects.get(dragObjStartPos).get(0);
            copyObjects.forEachObject((obj)->{
                obj.selected = true;
            });

            onRelease = (x, y)->this.releaseDrag(this.view);
        }

        private void releaseDrag(MapView view)
        {
            if (tool.mode == SelectionToolMode.DRAGGING && view.hasSelection())
            {
                if (tool.dragMode == DragMode.HORIZONTAL && totalHorizontalOffset != 0)
                {
                    //transfer copy positions? Probably not necessary. Amount changed is known, just move the objects in registerMove.
                    view.registerMove(totalHorizontalOffset);
                }
                if (tool.dragMode == DragMode.VERTICAL && totalVerticalOffset != 0)
                {
                    if (view instanceof EffectView && !tool.effectMode) {
                        ((EffectView) view).registerVolumeChange(copyMap);
                    }
                    else {
                        view.registerValueChange(copyMap);
                    }
                }
                //Trigger it here. Should not be triggered as you move objects as that would add a bunch of extra items to undo queue.
                if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT))
                {
                    editorLogger.info("Cancelled drag by right clicking. Delete selection.");
                    view.deleteSelection(); //Deletion occurs after movement, so this is saved as two separate actions.
                }
                else if (tool.dragMode == DragMode.NONE) {
                    view.dragRelease(); //Did not do any dragging, treat this as a delayed "click"
                }
            }
            tool.reset();
        }

        @Override
        public void update(float elapsed) {
            if (view.hasSelection() && tool.mode == SelectionToolMode.DRAGGING)
            {
                view.displayAdditionalObjects(copyObjects);

                long newPosition = (long) view.getTimeFromPosition(Gdx.input.getX());
                long horizontalChange = newPosition - (long) tool.clickStartTime; //different from current time and start time
                double verticalChange = (Gdx.input.getY() - lastY); //Should not use Gdx.input.getY when actual position matters, but only care about difference.
                //Find closest snap to this new offset

                if (tool.dragMode == DragMode.NONE && view.allowVerticalDrag() && (verticalChange > MIN_VERTICAL_DRAG_DIST || verticalChange < -MIN_VERTICAL_DRAG_DIST)) {
                    tool.dragMode = DragMode.VERTICAL;
                }

                verticalChange *= (shift() ? 0.01 : 0.05);

                if (tool.dragMode == DragMode.HORIZONTAL || (tool.dragMode == DragMode.NONE && (horizontalChange * viewScale > MIN_DRAG_DIST || horizontalChange * viewScale < -MIN_DRAG_DIST)))
                {
                    Snap closest = view.getClosestSnap(dragObjStartPos + horizontalChange, 500);

                    //horizontalChange = distance between initial click and current position
                    //Target position should be relative to drag object, so move distance equal to offset from dragObject's position

                    if (closest == null || BindingGroup.alt()) {
                        horizontalChange = newPosition - referenceObject.getPos();
                    }
                    else {
                        horizontalChange = closest.pos - referenceObject.getPos();
                    }

                    if (horizontalChange != 0) {
                        tool.dragMode = DragMode.HORIZONTAL;
                        view.movingObjects();
                        totalHorizontalOffset += horizontalChange;

                        for (Map.Entry<Long, ArrayList<MapObject>> e : copyObjects.entrySet())
                        {
                            for (MapObject o : e.getValue())
                                o.setPos(o.getPos() + horizontalChange);
                        }

                        //view.updateSelectionPositions();
                    }

                    //Should move slower than normal selection.
                    if (tool.dragMode == DragMode.HORIZONTAL) {
                        if (Gdx.input.getX() <= 1)
                        {
                            float mul = BindingGroup.alt() ? 4 : 1;
                            if (music.isPlaying()) {
                                TaikoEditor.onMain(()->{
                                    if (!seeked) {
                                        seeked = true;
                                        music.seekSecond(music.getSecondTime() - mul * Math.max(0.12, Gdx.graphics.getDeltaTime() * 12));
                                    }
                                });
                            }
                            else {
                                music.seekSecond(music.getSecondTime() - Math.max(0.01, mul * elapsed * 6));
                            }
                        }
                        else if (Gdx.input.getX() >= SettingsMaster.getWidth() - 1)
                        {
                            music.seekSecond(music.getSecondTime() + Math.max(0.01, (BindingGroup.alt() ? 4 : 1) * elapsed * 6));
                        }
                    }
                }
                else if (tool.dragMode == DragMode.VERTICAL) {
                    //Lazy way - just disable hiding and use the original objects if vertical drag is triggered?
                    //proper way - pass copy objects into updateVerticalDrag
                    //then copy final values over on release. This also avoids any fuckery if another editor sets value of a line being dragged during adjustment.
                    totalVerticalOffset += verticalChange; //Track separately every time so holding shift can adjust just new input
                    view.updateVerticalDrag(dragObject, copyObjects, copyMap, totalVerticalOffset);

                    lastY = Gdx.input.getY();
                }
            }
        }

        /*@Override
        public void render(SpriteBatch sb, ShapeRenderer sr) {
            view.getSelection().forEachObject((obj)->{
                MapObject cpy = copyMap.get(obj);
                if (cpy != null) {
                    view.renderObject(cpy, sb, sr);
                }
            });
        }*/
    }

    private static class EndDraggingMouseHoldObject extends RenderableMouseHold
    {
        private final SelectionTool tool;
        private final MapObject dragObject;
        private final MapObject copyObject;
        private final MapView view;

        private final long initialEnd;

        private long totalHorizontalOffset = 0;

        public EndDraggingMouseHoldObject(SelectionTool tool, MapView selectingView, MapObject dragObject)
        {
            super(null, null);

            this.tool = tool;
            this.initialEnd = ((ILongObject) dragObject).getEndPos();
            this.dragObject = dragObject;
            this.copyObject = dragObject.shiftedCopy(dragObject.getPos());
            copyObject.selected = true;
            this.view = selectingView;

            dragObject.hide(()->this.onRelease == null);

            this.onRelease = (x, y)->this.releaseEndDrag();
        }

        private void releaseEndDrag()
        {
            if (tool.mode == SelectionToolMode.DRAGGING_END)
            {
                if (totalHorizontalOffset != 0)
                {
                    //This only supports ILongObjects so there's no need to abstract it by going through the view
                    view.map.registerAndPerformDurationChange((ILongObject)dragObject, totalHorizontalOffset);
                }

                if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT))
                {
                    view.deselect(dragObject);
                    view.deleteObject(dragObject);
                }
            }
            tool.reset();
        }

        @Override
        public void update(float elapsed) {
            if (view.hasSelection() && tool.mode == SelectionToolMode.DRAGGING_END)
            {
                view.displayAdditionalObject(copyObject);

                double newPosition = view.getTimeFromPosition(Gdx.input.getX());
                long offsetChange = (long) (newPosition - tool.clickStartTime); //different from current time and start time
                //Find closest snap to this new offset

                if (tool.dragMode == DragMode.HORIZONTAL || offsetChange * viewScale > MIN_DRAG_DIST || offsetChange * viewScale < -MIN_DRAG_DIST) {
                    tool.dragMode = DragMode.HORIZONTAL;

                    //In this case, dragObjStartPos
                    Snap closest = view.getClosestSnap(initialEnd + offsetChange, 500);


                    if (closest == null || BindingGroup.alt()) { //Just go to cursor position
                        offsetChange = (long) newPosition - ((ILongObject) copyObject).getEndPos();
                    }
                    else { //Snap to closest snap
                        offsetChange = closest.pos - ((ILongObject) copyObject).getEndPos();
                    }

                    long newEnd = ((ILongObject) copyObject).getEndPos() + offsetChange;
                    if (newEnd <= copyObject.getPos())
                        newEnd = copyObject.getPos() + 1;

                    totalHorizontalOffset += newEnd - ((ILongObject) copyObject).getEndPos();
                    ((ILongObject) copyObject).setEndPos(newEnd);



                    //Should move slower than normal selection.
                    if (Gdx.input.getX() <= 1)
                    {
                        float mul = BindingGroup.alt() ? 4 : 1;
                        if (music.isPlaying()) {
                            TaikoEditor.onMain(()->{
                                if (!seeked) {
                                    seeked = true;
                                    music.seekSecond(music.getSecondTime() - mul * Math.max(0.15, Gdx.graphics.getDeltaTime() * 10));
                                }
                            });
                        }
                        else {
                            music.seekSecond(music.getSecondTime() - mul * elapsed * 2);
                        }
                    }
                    else if (Gdx.input.getX() >= SettingsMaster.getWidth() - 1)
                    {
                        music.seekSecond(music.getSecondTime() + (music.isPlaying() ? elapsed * 8 : elapsed * 2) * (BindingGroup.alt() ? 4 : 1));
                    }
                }
            }
        }

        /*@Override
        public void render(SpriteBatch sb, ShapeRenderer sr) {
            view.renderObject(copyObject, sb, sr);
        }*/
    }

    @Override
    public boolean supportsView(MapView view) {
        return view.type != MapView.ViewType.GAMEPLAY_VIEW;
    }
}
