package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;

import java.util.ArrayList;
import java.util.Map;

public class ObjectMovement extends MapChange {
    private final PositionalObjectTreeMap<PositionalObject> movedObjects; //contains objects at their *current* positions
    private final long moveAmount;

    public ObjectMovement(EditorBeatmap map, PositionalObjectTreeMap<PositionalObject> movedObjects, long offset)
    {
        super(map);

        this.movedObjects = movedObjects;
        this.moveAmount = offset;

        invalidateSelection = true;
    }

    @Override
    public MapChange undo() {
        PositionalObjectTreeMap<PositionalObject> moved = new PositionalObjectTreeMap<>();

        //Remove from current location
        map.removeObjects(movedObjects);

        //Adjust position
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
        {
            e.getValue().forEach((o)->o.setPos(e.getKey() - moveAmount));
            moved.put(e.getKey() - moveAmount, e.getValue());
        }
        map.preAddObjects(moved);
        map.objects.addAll(moved);
        map.updateVolume(moved);

        //Update tracking of movedObjects
        movedObjects.clear();
        movedObjects.putAll(moved);

        map.gameplayChanged();
        return this;
    }
    @Override
    public MapChange perform() {
        PositionalObjectTreeMap<PositionalObject> moved = new PositionalObjectTreeMap<>();

        //Remove from current location
        map.removeObjects(movedObjects);

        //Adjust position
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
        {
            e.getValue().forEach((o)->o.setPos(e.getKey() + moveAmount));
            moved.put(e.getKey() + moveAmount, e.getValue());
        }
        map.preAddObjects(moved);
        map.objects.addAll(moved);
        map.updateVolume(moved);

        //Update tracking of movedObjects
        movedObjects.clear();
        movedObjects.putAll(moved);

        map.gameplayChanged();
        return this;
    }

    public MapChange redo() {
        //Set to original positions (store in "moved"), then move to new positions and update map accordingly.
        PositionalObjectTreeMap<PositionalObject> moved = new PositionalObjectTreeMap<>();

        map.objects.removeAll(movedObjects);
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
        {
            e.getValue().forEach((o)->o.setPos(e.getKey() - moveAmount));
            moved.put(e.getKey() - moveAmount, e.getValue());
        }
        map.objects.removeAll(movedObjects);
        movedObjects.clear();


        map.removeObjects(moved);

        for (Map.Entry<Long, ArrayList<PositionalObject>> e : moved.entrySet())
        {
            e.getValue().forEach((o)->o.setPos(e.getKey() + moveAmount));
            movedObjects.put(e.getKey() + moveAmount, e.getValue());
        }
        map.preAddObjects(movedObjects);
        map.objects.addAll(movedObjects);
        map.updateVolume(movedObjects);


        map.gameplayChanged();
        return this;
    }
}