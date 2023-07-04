package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;

import java.util.ArrayList;
import java.util.Map;

public class LineMovement extends MapChange {
    private final PositionalObjectTreeMap<PositionalObject> movedObjects; //contains objects at their *current* positions
    private final long moveAmount;
    private final boolean changesTiming;

    public LineMovement(EditorBeatmap map, PositionalObjectTreeMap<PositionalObject> movedObjects, long offset)
    {
        super(map);

        this.movedObjects = movedObjects;
        this.moveAmount = offset;

        boolean hasTimingChange = false;
        for (Map.Entry<Long, ArrayList<PositionalObject>> stack : movedObjects.entrySet()) {
            for (PositionalObject o : stack.getValue()) {
                if (o instanceof TimingPoint && ((TimingPoint) o).uninherited) {
                    hasTimingChange = true;
                    break;
                }
            }
        }
        changesTiming = hasTimingChange;

        invalidateSelection = true;
    }

    @Override
    public MapChange undo() {
        PositionalObjectTreeMap<PositionalObject> moved = new PositionalObjectTreeMap<>();

        //remove from maps
        map.timingPoints.removeAll(movedObjects);
        map.effectPoints.removeAll(movedObjects);
        map.allPoints.removeAll(movedObjects);

        //adjust positions
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
        {
            e.getValue().forEach((o)->o.setPos(e.getKey() - moveAmount));
            moved.put(e.getKey() - moveAmount, e.getValue());
        }

        //put points back into maps
        for (Map.Entry<Long, ArrayList<PositionalObject>> entry : moved.entrySet()) {
            for (PositionalObject o : entry.getValue()) {
                if (o instanceof TimingPoint) {
                    if (((TimingPoint) o).uninherited) {
                        map.timingPoints.add((TimingPoint) o);
                    }
                    else {
                        map.effectPoints.add((TimingPoint) o);
                    }
                }
            }
        }
        map.allPoints.addAll(moved);

        //handle changes
        if (changesTiming)
            map.regenerateDivisor();
        map.updateLines(moved.entrySet(), movedObjects.entrySet());
        map.gameplayChanged();

        movedObjects.clear();
        movedObjects.putAll(moved);
        return this;
    }
    @Override
    public MapChange perform() {
        PositionalObjectTreeMap<PositionalObject> moved = new PositionalObjectTreeMap<>();

        //remove from maps
        map.timingPoints.removeAll(movedObjects);
        map.effectPoints.removeAll(movedObjects);
        map.allPoints.removeAll(movedObjects);

        //adjust positions
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
        {
            e.getValue().forEach((o)->o.setPos(e.getKey() + moveAmount));
            moved.put(e.getKey() + moveAmount, e.getValue());
        }

        //put points back into maps
        for (Map.Entry<Long, ArrayList<PositionalObject>> entry : moved.entrySet()) {
            for (PositionalObject o : entry.getValue()) {
                if (o instanceof TimingPoint) {
                    if (((TimingPoint) o).uninherited) {
                        map.timingPoints.add((TimingPoint) o);
                    }
                    else {
                        map.effectPoints.add((TimingPoint) o);
                    }
                }
            }
        }
        map.allPoints.addAll(moved);

        //handle changes
        if (changesTiming)
            map.regenerateDivisor();
        map.updateLines(moved.entrySet(), movedObjects.entrySet());
        map.gameplayChanged();

        movedObjects.clear();
        movedObjects.putAll(moved);
        return this;
    }

    public MapChange redo() {
        //Set to original positions (store in "moved"), then move to new positions and update map accordingly.
        PositionalObjectTreeMap<PositionalObject> moved = new PositionalObjectTreeMap<>();

        //store original positions in moved
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
        {
            moved.put(e.getKey() - moveAmount, e.getValue());
        }

        //movedObjects contains the objects with keys linked on new position, moved has the old position keys
        if (changesTiming)
            map.regenerateDivisor();
        map.updateLines(movedObjects.entrySet(), moved.entrySet());
        map.gameplayChanged();
        return this;
    }
}