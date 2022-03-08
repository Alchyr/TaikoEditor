package alchyr.taikoedit.editor.tools;

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
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.*;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.editorLogger;
import static alchyr.taikoedit.core.layers.EditorLayer.music;
import static alchyr.taikoedit.core.input.BindingGroup.shift;

//The default tool. Should be included in most toolsets and support pretty much any view other than gameplay.

//Honestly I should probably have split this into a separate selection tool for objects vs timing points but whatever
//Too lazy to fix at this point
public class SelectionTool extends EditorTool {
    private static final int MIN_DRAG_DIST = 5;
    private static final int MIN_VERTICAL_DRAG_DIST = 10;
    private static final Color selectionColor = new Color(0.8f, 0.8f, 0.8f, 0.35f);

    private static SelectionTool tool;

    ///Extra features - changing cursor based on hover?
    public boolean effectMode = true; //true = sv, false = volume
    private SelectionToolMode mode;

    //Selection data
    private MapView selectingView;
    private double clickStartTime;
    private int clickStartY;
    private float selectionEnd;

    //used for dragging objects
    private DragMode dragMode; //At start of drag, doesn't enable immediately. Requires a small amount of cursor distance before dragging will actually occur.
    private PositionalObject dragObject;
    private long dragObjStartPos;
    private long totalHorizontalOffset;
    private double totalVerticalOffset;

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


    private final SelectionMouseHoldObject selection;
    private final DraggingMouseHoldObject dragging;
    private final EndDraggingMouseHoldObject draggingEnd;


    private SelectionTool()
    {
        super("Selection");
        selection = new SelectionMouseHoldObject();
        dragging = new DraggingMouseHoldObject();
        draggingEnd = new EndDraggingMouseHoldObject();
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
        return true;
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
                boolean delete = button == Input.Buttons.RIGHT;
                selectingView = view;
                dragObject = selectingView.clickObject(x, y);
                boolean canDrag = true; //this should probably be a single int
                boolean canSelect = false;
                boolean dragEnd = false;

                if (dragObject != null && (modifiers & 1) == 0)
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
                    if ((modifiers & 1) == 0)
                    {
                        selectingView.clearSelection();
                    }
                    canDrag = false;
                    canSelect = !delete;
                }

                if (canDrag)
                {
                    clickStartTime = selectingView.getTimeFromPosition(x);
                    clickStartY = Gdx.input.getY();
                    dragObjStartPos = dragObject.getPos();
                    mode = SelectionToolMode.DRAGGING;
                    totalHorizontalOffset = 0;
                    totalVerticalOffset = 0;
                    return dragging;
                }
                else if (canSelect)
                {
                    clickStartTime = selectingView.getTimeFromPosition(x);
                    mode = SelectionToolMode.SELECTING;
                    selectionEnd = x;
                    return selection;
                }
                else if (dragEnd)
                {
                    clickStartTime = selectingView.getTimeFromPosition(x);
                    dragObjStartPos = ((ILongObject) dragObject).getEndPos();
                    mode = SelectionToolMode.DRAGGING_END;
                    totalHorizontalOffset = 0;
                    totalVerticalOffset = 0;
                    return draggingEnd;
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

    private void updateSelectionDrag(float x, float y)
    {
        if (dragMode == DragMode.HORIZONTAL || selectionEnd - x < -MIN_DRAG_DIST || selectionEnd - x > MIN_DRAG_DIST)
        {
            dragMode = DragMode.HORIZONTAL;
            selectionEnd = x;
        }
    }
    private void releaseSelection(float x, float y)
    {
        if (selectingView != null)
        {
            if (mode == SelectionToolMode.SELECTING && dragMode == DragMode.HORIZONTAL)
            {
                selectingView.addSelectionRange((int) clickStartTime, (int) selectingView.getTimeFromPosition(x));
            }
        }
        reset();
    }
    private void releaseDrag(float x, float y)
    {
        if (selectingView != null)
        {
            if (mode == SelectionToolMode.DRAGGING && selectingView.hasSelection())
            {
                if (totalHorizontalOffset != 0 && dragMode == DragMode.HORIZONTAL)
                {
                    selectingView.registerMove(totalHorizontalOffset);
                }
                if (totalVerticalOffset != 0 && dragMode == DragMode.VERTICAL)
                {
                    if (selectingView instanceof EffectView && !effectMode) {
                        ((EffectView) selectingView).registerVolumeChange();
                    }
                    else {
                        selectingView.registerValueChange();
                    }
                }
                //Trigger it here. Should not be triggered as you move objects as that would add a bunch of extra items to undo queue.
                if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT))
                {
                    editorLogger.info("Cancelled drag by right clicking. Delete selection.");
                    selectingView.deleteSelection(); //Deletion occurs after movement, so this is saved as two separate actions.
                }
                else if (dragMode == DragMode.NONE) {
                    selectingView.clickRelease();
                }
            }
        }
        reset();
    }
    private void releaseEndDrag(float x, float y)
    {
        if (selectingView != null)
        {
            if (mode == SelectionToolMode.DRAGGING_END && dragObject != null)
            {
                if (totalHorizontalOffset != 0)
                {
                    //This only supports ILongObjects so there's no need to abstract it by going through the view
                    selectingView.map.registerDurationChange((ILongObject)dragObject, totalHorizontalOffset);
                }
                //Trigger it here. Should not be triggered as you move objects as that would add a bunch of extra items to undo queue.
                if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT))
                {
                    selectingView.deselect(dragObject);
                    selectingView.deleteObject(dragObject);
                }
            }
        }
        reset();
    }

    private void reset()
    {
        selectingView = null;
        dragObject = null;
        dragObjStartPos = 0;
        dragMode = DragMode.NONE;
        totalHorizontalOffset = 0;
        mode = SelectionToolMode.NONE;
    }

    @Override
    public void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed) {

    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        if (selectingView != null)
        {
            if (mode == SelectionToolMode.SELECTING && dragMode == DragMode.HORIZONTAL) //Render selection effect on objects you are dragging over before they're actually selected
            {
                int start = selectingView.getPositionFromTime(clickStartTime, SettingsMaster.getMiddle());

                if (start < selectionEnd)
                {
                    NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> hovered = selectingView.getVisibleRange((long) clickStartTime, (long) selectingView.getTimeFromPosition(selectionEnd));

                    if (hovered != null)
                    {
                        for (ArrayList<? extends PositionalObject> list : hovered.values())
                            for (PositionalObject o : list)
                                selectingView.renderSelection(o, sb, sr);
                    }

                    sb.setColor(selectionColor);
                    sb.draw(pix, start, selectingView.bottom, selectionEnd - start, selectingView.height);
                }
                else if (start > selectionEnd)
                {
                    NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>> hovered = selectingView.getVisibleRange((long) selectingView.getTimeFromPosition(selectionEnd), (long) clickStartTime);

                    if (hovered != null)
                    {
                        for (ArrayList<? extends PositionalObject> list : hovered.values())
                            for (PositionalObject o : list)
                                selectingView.renderSelection(o, sb, sr);
                    }

                    sb.setColor(selectionColor);
                    sb.draw(pix, selectionEnd, selectingView.bottom, start - selectionEnd, selectingView.height);
                }
            }
        }
    }

    @Override
    public void cancel() {
        tryCancel();
    }

    private boolean tryCancel() {
        boolean cancel = mode != SelectionToolMode.NONE;
        if (mode == SelectionToolMode.SELECTING)
        {
            EditorLayer.processor.cancelMouseHold(selection);
        }
        else if (mode == SelectionToolMode.DRAGGING)
        {
            releaseDrag(0, 0);

            EditorLayer.processor.cancelMouseHold(dragging);
        }
        else if (mode == SelectionToolMode.DRAGGING_END)
        {
            if (totalHorizontalOffset != 0)
            {
                selectingView.map.registerDurationChange((ILongObject)dragObject, totalHorizontalOffset);
            }
            EditorLayer.processor.cancelMouseHold(draggingEnd);
        }
        reset();
        return cancel;
    }

    private class SelectionMouseHoldObject extends MouseHoldObject
    {
        public SelectionMouseHoldObject()
        {
            super(SelectionTool.this::updateSelectionDrag, SelectionTool.this::releaseSelection);
        }

        @Override
        public void update(float elapsed) {
            if (mode == SelectionToolMode.SELECTING)
            {
                if (Gdx.input.getX() <= 1)
                {
                    music.seekSecond(music.getSecondTime() - elapsed * 6);
                }
                else if (Gdx.input.getX() >= SettingsMaster.getWidth() - 1)
                {
                    music.seekSecond(music.getSecondTime() + elapsed * 6);
                }
            }
        }
    }

    private class DraggingMouseHoldObject extends MouseHoldObject
    {
        public DraggingMouseHoldObject()
        {
            super(null, SelectionTool.this::releaseDrag);
        }

        @Override
        public void update(float elapsed) {
            if (selectingView != null && selectingView.hasSelection() && mode == SelectionToolMode.DRAGGING)
            {
                long newPosition = (long) selectingView.getTimeFromPosition(Gdx.input.getX());
                long horizontalChange = newPosition - (long) clickStartTime; //different from current time and start time
                double verticalChange = (Gdx.input.getY() - clickStartY);
                //Find closest snap to this new offset

                if (dragMode == DragMode.NONE && selectingView.allowVerticalDrag && (verticalChange > MIN_VERTICAL_DRAG_DIST || verticalChange < -MIN_VERTICAL_DRAG_DIST)) {
                    dragMode = DragMode.VERTICAL;
                    selectingView.dragging();
                }

                verticalChange *= (shift() ? 0.01 : 0.05);

                if (dragMode == DragMode.HORIZONTAL || (dragMode == DragMode.NONE && (horizontalChange > MIN_DRAG_DIST || horizontalChange < -MIN_DRAG_DIST)))
                {
                    Snap closest = selectingView.getClosestSnap(dragObjStartPos + horizontalChange, 500);

                    //horizontalChange = distance between initial click and current position
                    //Target position should be relative to drag object, so move distance equal to offset from dragObject's position

                    if (closest == null || BindingGroup.alt()) {
                        horizontalChange = newPosition - dragObject.getPos();
                    }
                    else {
                        horizontalChange = (long) closest.pos - dragObject.getPos();
                    }

                    if (horizontalChange != 0) {
                        dragMode = DragMode.HORIZONTAL;
                        selectingView.dragging();
                        totalHorizontalOffset += horizontalChange;

                        PositionalObjectTreeMap<PositionalObject> moved = new PositionalObjectTreeMap<>();

                        for (Map.Entry<Long, ArrayList<PositionalObject>> e : selectingView.getSelection().entrySet())
                        {
                            for (PositionalObject o : e.getValue())
                                o.setPos(e.getKey() + horizontalChange);
                            moved.put(e.getKey() + horizontalChange, e.getValue());
                        }

                        selectingView.getEditMap().removeAll(selectingView.getSelection());
                        selectingView.getSelection().clear();
                        selectingView.getEditMap().addAll(moved);
                        selectingView.getSelection().addAll(moved);
                    }

                    //Should move slower than normal selection.
                    if (dragMode == DragMode.HORIZONTAL) {
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
                else if (dragMode == DragMode.VERTICAL) {
                    if (selectingView instanceof EffectView) {
                        if (effectMode) {
                            for (Map.Entry<Long, ArrayList<PositionalObject>> e : selectingView.getSelection().entrySet())
                            {
                                for (PositionalObject o : e.getValue()) {
                                    o.tempModification(totalVerticalOffset);
                                }
                            }
                        }
                        else {
                            for (Map.Entry<Long, ArrayList<PositionalObject>> e : selectingView.getSelection().entrySet())
                            {
                                for (PositionalObject o : e.getValue()) {
                                    o.volumeModification(totalVerticalOffset);
                                }
                            }
                        }

                        //Do not check for removed points here for simpler check.
                        //Proper update will occur on release.
                        selectingView.map.updateEffectPoints(selectingView.getSelection().entrySet(), null);
                    }
                    else {
                        for (Map.Entry<Long, ArrayList<PositionalObject>> e : selectingView.getSelection().entrySet())
                        {
                            for (PositionalObject o : e.getValue()) {
                                o.tempModification(totalVerticalOffset);
                            }
                        }
                    }

                    totalVerticalOffset += verticalChange; //Track separately every time so holding shift can adjust just new input
                    clickStartY = Gdx.input.getY();
                }
            }
        }
    }

    private class EndDraggingMouseHoldObject extends MouseHoldObject
    {
        public EndDraggingMouseHoldObject()
        {
            super(null, SelectionTool.this::releaseEndDrag);
        }

        @Override
        public void update(float elapsed) {
            if (selectingView != null && selectingView.hasSelection() && mode == SelectionToolMode.DRAGGING_END)
            {
                double newPosition = selectingView.getTimeFromPosition(Gdx.input.getX());
                long offsetChange = (long) (newPosition - clickStartTime); //different from current time and start time
                //Find closest snap to this new offset

                if (dragMode == DragMode.HORIZONTAL || offsetChange > MIN_DRAG_DIST || offsetChange < -MIN_DRAG_DIST) {
                    dragMode = DragMode.HORIZONTAL;

                    //In this case, dragObjStartPos
                    Snap closest = selectingView.getClosestSnap(dragObjStartPos + offsetChange, 500);


                    if (closest == null || BindingGroup.alt()) { //Just go to cursor position
                        offsetChange = (long) newPosition - ((ILongObject) dragObject).getEndPos();
                    }
                    else { //Snap to closest snap
                        offsetChange = (long) closest.pos - ((ILongObject) dragObject).getEndPos();
                    }

                    long newEnd = ((ILongObject) dragObject).getEndPos() + offsetChange;
                    if (newEnd <= dragObject.getPos())
                        newEnd = dragObject.getPos() + 1;

                    totalHorizontalOffset += newEnd - ((ILongObject) dragObject).getEndPos();
                    ((ILongObject) dragObject).setEndPos(newEnd);



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
    }

    @Override
    public boolean supportsView(MapView view) {
        return view.type != MapView.ViewType.GAMEPLAY_VIEW;
    }
}
