package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;

import java.util.ArrayList;
import java.util.Map;

public class Movement extends MapChange {
    private final ChangeType type;
    private final PositionalObjectTreeMap<PositionalObject> movedObjects;
    private final long moveAmount;

    public Movement(EditorBeatmap map, ChangeType type, PositionalObjectTreeMap<PositionalObject> movedObjects, long offset)
    {
        super(map);

        this.type = type;

        this.movedObjects = movedObjects;
        this.moveAmount = offset;

        invalidateSelection = true;
    }

    @Override
    public MapChange undo() {
        PositionalObjectTreeMap<PositionalObject> moved = new PositionalObjectTreeMap<>();

        switch (type)
        {
            case OBJECTS:
                for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPosition(e.getKey() - moveAmount));
                    moved.put(e.getKey() - moveAmount, e.getValue());
                }
                map.objects.removeAll(movedObjects);
                map.objects.addAll(movedObjects);
                map.updateVolume(movedObjects);
                break;
            case EFFECT:
                for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPosition(e.getKey() - moveAmount));
                    moved.put(e.getKey() - moveAmount, e.getValue());
                }
                map.effectPoints.removeAll(movedObjects);
                map.effectPoints.addAll(movedObjects);

                map.updateEffectPoints(moved, movedObjects);
                break;
        }
        movedObjects.clear();
        movedObjects.putAll(moved);
        return this;
    }
    @Override
    public MapChange perform() {
        PositionalObjectTreeMap<PositionalObject> moved = new PositionalObjectTreeMap<>();

        switch (type)
        {
            case OBJECTS:
                for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPosition(e.getKey() + moveAmount));
                    moved.put(e.getKey() + moveAmount, e.getValue());
                }
                map.objects.removeAll(movedObjects);
                map.objects.addAll(movedObjects);
                map.updateVolume(movedObjects);
                break;
            case EFFECT:
                for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPosition(e.getKey() + moveAmount));
                    moved.put(e.getKey() + moveAmount, e.getValue());
                }
                map.effectPoints.removeAll(movedObjects);
                map.effectPoints.addAll(movedObjects);

                //moved contains the objects with keys linked on new position, movedObjects still has the old position keys
                map.updateEffectPoints(moved, movedObjects);
                break;
        }
        movedObjects.clear();
        movedObjects.putAll(moved);
        return this;
    }
}