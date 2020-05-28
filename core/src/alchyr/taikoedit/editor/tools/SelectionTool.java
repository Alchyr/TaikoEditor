package alchyr.taikoedit.editor.tools;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.maps.components.ILongObject;
import alchyr.taikoedit.maps.components.hitobjects.Slider;
import alchyr.taikoedit.maps.components.hitobjects.Spinner;
import alchyr.taikoedit.util.input.KeyHoldManager;
import alchyr.taikoedit.util.input.MouseHoldObject;
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

//The default tool. Should be included in most toolsets and support pretty much any view other than gameplay.
public class SelectionTool extends EditorTool {
    private static final int MIN_DRAG_DIST = 10;
    private static final Color selectionColor = new Color(0.7f, 0.7f, 0.7f, 0.35f);

    private static SelectionTool tool;

    ///TODO : Changing length of spinner/slider
    ///If dragobject not null and spinner/slider and clicking End

    ///Extra features - changing cursor based on hover?

    private SelectionToolMode mode;

    //Selection data
    private MapView currentlySelecting;
    private int clickStartPos;
    private int selectionEnd;

    //used for dragging objects
    private PositionalObject dragObject;
    private int dragObjStartPos;
    private int totalOffset;

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
    public MouseHoldObject click(MapView view, int x, int y, int button, KeyHoldManager keyHolds) {
        switch (mode)
        {
            case SELECTING:
                cancel();
                if (button != Input.Buttons.LEFT)
                    return null;
            case NONE:
                boolean delete = button == Input.Buttons.RIGHT;
                currentlySelecting = view;
                dragObject = currentlySelecting.clickObject(x, y);
                boolean canDrag = true; //this should probably be a single int
                boolean canSelect = false;
                boolean dragEnd = false;

                if (dragObject != null && !keyHolds.isHeld(Input.Keys.CONTROL_LEFT))
                {
                    if (!dragObject.selected)
                    {
                        currentlySelecting.clearSelection();
                        if (delete)
                        {
                            currentlySelecting.deleteObject(dragObject);
                            canDrag = false;
                        }
                        else
                        {
                            currentlySelecting.select(dragObject);

                            if (currentlySelecting.clickedEnd(dragObject, x))
                            {
                                canDrag = false;
                                dragEnd = true;
                            }
                        }
                    }
                    else
                    {
                        if (delete)
                        {
                            currentlySelecting.deleteSelection();
                            canDrag = false;
                        }
                        else
                        {
                            if (currentlySelecting.clickedEnd(dragObject, x))
                            {
                                canDrag = false;
                                dragEnd = true;
                            }
                        }
                    }
                }
                else if (dragObject != null && keyHolds.isHeld(Input.Keys.CONTROL_LEFT))
                {
                    if (delete)
                    {
                        if (dragObject.selected)
                        {
                            currentlySelecting.deselect(dragObject);
                        }
                        currentlySelecting.deleteObject(dragObject);
                    }
                    else
                    {
                        if (dragObject.selected)
                            currentlySelecting.deselect(dragObject);
                        else
                            currentlySelecting.select(dragObject);
                    }

                    canDrag = false;
                }
                else if (dragObject == null)
                {
                    if (!keyHolds.isHeld(Input.Keys.CONTROL_LEFT))
                    {
                        currentlySelecting.clearSelection();
                    }
                    canDrag = false;
                    canSelect = !delete;
                }

                if (canDrag)
                {
                    clickStartPos = currentlySelecting.getTimeFromPosition(x);
                    dragObjStartPos = dragObject.pos;
                    mode = SelectionToolMode.DRAGGING;
                    totalOffset = 0;
                    return dragging;
                }
                else if (canSelect)
                {
                    clickStartPos = currentlySelecting.getTimeFromPosition(x);
                    mode = SelectionToolMode.SELECTING;
                    selectionEnd = x;
                    return selection;
                }
                else if (dragEnd)
                {
                    clickStartPos = currentlySelecting.getTimeFromPosition(x);
                    dragObjStartPos = ((ILongObject) dragObject).getEndPos();
                    mode = SelectionToolMode.DRAGGING_END;
                    totalOffset = 0;
                    return draggingEnd;
                }
                currentlySelecting = null;
                return null;
            case DRAGGING:
                if (button == Input.Buttons.RIGHT)
                {
                    //Delete dragged objects, which is selection
                    //Return dragged objects to original position then delete
                    view.deleteSelection();
                }
                cancel();
                break;
        }

        return null;
    }

    private void updateSelectionDrag(int x, int y)
    {
        selectionEnd = x;
    }
    private boolean releaseSelection(int x, int y)
    {
        if (currentlySelecting != null)
        {
            if (mode == SelectionToolMode.SELECTING)
            {
                currentlySelecting.addSelectionRange(clickStartPos, currentlySelecting.getTimeFromPosition(x));
            }
        }
        reset();
        return true;
    }
    private boolean releaseDrag(int x, int y)
    {
        if (currentlySelecting != null)
        {
            if (mode == SelectionToolMode.DRAGGING && currentlySelecting.hasSelection())
            {
                if (totalOffset != 0)
                {
                    currentlySelecting.registerMove(totalOffset);
                }
                //Trigger it here. Should not be triggered as you move objects as that would add a bunch of extra items to undo queue.
                if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT))
                {
                    editorLogger.info("Cancelled drag by right clicking. Delete selection.");
                    currentlySelecting.deleteSelection(); //Deletion occurs after movement, so this is saved as two separate actions.
                }
            }
        }
        reset();
        return true;
    }
    private boolean releaseEndDrag(int x, int y)
    {
        if (currentlySelecting != null)
        {
            if (mode == SelectionToolMode.DRAGGING_END && dragObject != null)
            {
                if (totalOffset != 0)
                {
                    //This only supports ILongObjects so there's no need to abstract it by going through the view
                    currentlySelecting.map.registerDurationChange((ILongObject)dragObject, totalOffset);
                }
                //Trigger it here. Should not be triggered as you move objects as that would add a bunch of extra items to undo queue.
                if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT))
                {
                    currentlySelecting.deselect(dragObject);
                    currentlySelecting.deleteObject(dragObject);
                }
            }
        }
        reset();
        return true;
    }

    private void reset()
    {
        currentlySelecting = null;
        dragObject = null;
        dragObjStartPos = 0;
        totalOffset = 0;
        mode = SelectionToolMode.NONE;
    }

    @Override
    public void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed) {

    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        if (currentlySelecting != null)
        {
            if (mode == SelectionToolMode.SELECTING)
            {
                int start = currentlySelecting.getPositionFromTime(clickStartPos, SettingsMaster.getMiddle());

                if (start < selectionEnd)
                {
                    NavigableMap<Integer, ? extends ArrayList<? extends PositionalObject>> hovered = currentlySelecting.getVisisbleRange(clickStartPos, currentlySelecting.getTimeFromPosition(selectionEnd));

                    if (hovered != null)
                    {
                        for (ArrayList<? extends PositionalObject> list : hovered.values())
                            for (PositionalObject o : list)
                                currentlySelecting.renderSelection(o, sb, sr);
                    }

                    sb.setColor(selectionColor);
                    sb.draw(pix, start, currentlySelecting.bottom, selectionEnd - start, currentlySelecting.topY - currentlySelecting.bottom);
                }
                else if (start > selectionEnd)
                {
                    NavigableMap<Integer, ? extends ArrayList<? extends PositionalObject>> hovered = currentlySelecting.getVisisbleRange(currentlySelecting.getTimeFromPosition(selectionEnd), clickStartPos);

                    if (hovered != null)
                    {
                        for (ArrayList<? extends PositionalObject> list : hovered.values())
                            for (PositionalObject o : list)
                                currentlySelecting.renderSelection(o, sb, sr);
                    }

                    sb.setColor(selectionColor);
                    sb.draw(pix, selectionEnd, currentlySelecting.bottom, start - selectionEnd, currentlySelecting.topY - currentlySelecting.bottom);
                }
            }
        }
    }

    @Override
    public void cancel() {
        if (mode == SelectionToolMode.SELECTING)
        {
            EditorLayer.processor.cancelMouseHold(selection);
        }
        else if (mode == SelectionToolMode.DRAGGING)
        {
            if (totalOffset != 0)
            {
                currentlySelecting.registerMove(totalOffset);
            }

            EditorLayer.processor.cancelMouseHold(dragging);
        }
        else if (mode == SelectionToolMode.DRAGGING_END)
        {
            if (totalOffset != 0)
            {

            }
            EditorLayer.processor.cancelMouseHold(draggingEnd);
        }
        reset();
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
                    music.seekSecond(music.getSecondTime(0) - elapsed * 6);
                }
                else if (Gdx.input.getX() >= SettingsMaster.getWidth() - 1)
                {
                    music.seekSecond(music.getSecondTime(0) + elapsed * 6);
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
            if (currentlySelecting != null && currentlySelecting.hasSelection() && mode == SelectionToolMode.DRAGGING)
            {
                int newPosition = currentlySelecting.getTimeFromPosition(Gdx.input.getX());
                int newOffset = newPosition - clickStartPos; //different from current time and start time
                //Find closest snap to this new offset

                Snap closest = currentlySelecting.getClosestSnap(dragObjStartPos + newOffset, 500);

                //newOffset = distance between initial click and current position
                //Target position should be relative to drag object, so move distance equal to offset from dragObject's position

                if (closest != null)
                {
                    newOffset = closest.pos - dragObject.pos;
                }
                else {
                    newOffset -= dragObject.pos;
                }

                totalOffset += newOffset;

                PositionalObjectTreeMap<PositionalObject> moved = new PositionalObjectTreeMap<>();

                int offsetChange = newOffset;

                for (Map.Entry<Integer, ArrayList<PositionalObject>> e : currentlySelecting.getSelection().entrySet())
                {
                    e.getValue().forEach((o)->o.setPosition(e.getKey() + offsetChange));
                    moved.put(e.getKey() + offsetChange, e.getValue());
                }
                currentlySelecting.map.objects.removeAll(currentlySelecting.getSelection());
                currentlySelecting.getSelection().clear();
                currentlySelecting.map.objects.addAll(moved);
                currentlySelecting.getSelection().addAll(moved);


                //Should move slower than normal selection.
                if (Gdx.input.getX() <= 1)
                {
                    music.seekSecond(music.getSecondTime(0) - elapsed * 2);
                }
                else if (Gdx.input.getX() >= SettingsMaster.getWidth() - 1)
                {
                    music.seekSecond(music.getSecondTime(0) + elapsed * 2);
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
            if (currentlySelecting != null && currentlySelecting.hasSelection() && mode == SelectionToolMode.DRAGGING_END)
            {
                int newPosition = currentlySelecting.getTimeFromPosition(Gdx.input.getX());
                int newOffset = newPosition - clickStartPos; //different from current time and start time
                //Find closest snap to this new offset

                //In this case, dragObjStartPos
                Snap closest = currentlySelecting.getClosestSnap(dragObjStartPos + newOffset, 500);

                //newOffset = distance between initial click and current position

                if (closest != null)
                {
                    newOffset = closest.pos - ((ILongObject) dragObject).getEndPos();
                }
                else {
                    newOffset -= ((ILongObject) dragObject).getEndPos();
                }

                int newEnd = ((ILongObject) dragObject).getEndPos() + newOffset;
                if (newEnd <= dragObject.pos)
                    newEnd = dragObject.pos + 1;

                totalOffset += newEnd - ((ILongObject) dragObject).getEndPos();
                ((ILongObject) dragObject).setEndPos(newEnd);



                //Should move slower than normal selection.
                if (Gdx.input.getX() <= 1)
                {
                    music.seekSecond(music.getSecondTime(0) - elapsed * 2);
                }
                else if (Gdx.input.getX() >= SettingsMaster.getWidth() - 1)
                {
                    music.seekSecond(music.getSecondTime(0) + elapsed * 2);
                }
            }
        }
    }
}
