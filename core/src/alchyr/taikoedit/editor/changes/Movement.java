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
                map.removeObjects(movedObjects);

                for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPos(e.getKey() - moveAmount));
                    moved.put(e.getKey() - moveAmount, e.getValue());
                }
                map.preAddObjects(moved);
                map.objects.addAll(moved);
                map.updateVolume(moved);
                break;
            case EFFECT:
                for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPos(e.getKey() - moveAmount));
                    moved.put(e.getKey() - moveAmount, e.getValue());
                }
                map.effectPoints.removeAll(movedObjects);
                map.allPoints.removeAll(movedObjects);
                map.effectPoints.addAll(moved);
                map.allPoints.addAll(moved);

                map.updateEffectPoints(moved.entrySet(), movedObjects.entrySet());
                break;
        }
        movedObjects.clear();
        movedObjects.putAll(moved);

        map.gameplayChanged();
        return this;
    }
    @Override
    public MapChange perform() {
        PositionalObjectTreeMap<PositionalObject> moved = new PositionalObjectTreeMap<>();

        switch (type)
        {
            case OBJECTS:
                map.removeObjects(movedObjects);

                for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPos(e.getKey() + moveAmount));
                    moved.put(e.getKey() + moveAmount, e.getValue());
                }
                map.preAddObjects(moved);
                map.objects.addAll(moved);
                map.updateVolume(moved);
                break;
            case EFFECT:
                for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPos(e.getKey() + moveAmount));
                    moved.put(e.getKey() + moveAmount, e.getValue());
                }
                map.effectPoints.removeAll(movedObjects);
                map.allPoints.removeAll(movedObjects);
                map.effectPoints.addAll(moved);
                map.allPoints.addAll(moved);

                //moved contains the objects with keys linked on new position, movedObjects still has the old position keys
                map.updateEffectPoints(moved.entrySet(), movedObjects.entrySet());
                break;
        }
        movedObjects.clear();
        movedObjects.putAll(moved);

        map.gameplayChanged();
        return this;
    }

    public MapChange redo() {
        PositionalObjectTreeMap<PositionalObject> moved = new PositionalObjectTreeMap<>();

        switch (type)
        {
            case OBJECTS:
                map.objects.removeAll(movedObjects);

                for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPos(e.getKey() - moveAmount));
                    moved.put(e.getKey() - moveAmount, e.getValue());
                }
                map.objects.addAll(moved);
                break;
            case EFFECT:
                for (Map.Entry<Long, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPos(e.getKey() - moveAmount));
                    moved.put(e.getKey() - moveAmount, e.getValue());
                }
                map.effectPoints.removeAll(movedObjects);
                map.allPoints.removeAll(moved);
                map.effectPoints.addAll(moved);
                break;
        }

        movedObjects.clear();

        switch (type)
        {
            case OBJECTS:
                map.removeObjects(moved);

                for (Map.Entry<Long, ArrayList<PositionalObject>> e : moved.entrySet())
                {
                    e.getValue().forEach((o)->o.setPos(e.getKey() + moveAmount));
                    movedObjects.put(e.getKey() + moveAmount, e.getValue());
                }
                map.preAddObjects(movedObjects);
                map.objects.addAll(movedObjects);
                map.updateVolume(movedObjects);
                break;
            case EFFECT:
                for (Map.Entry<Long, ArrayList<PositionalObject>> e : moved.entrySet())
                {
                    e.getValue().forEach((o)->o.setPos(e.getKey() + moveAmount));
                    movedObjects.put(e.getKey() + moveAmount, e.getValue());
                }
                map.effectPoints.removeAll(moved);
                map.allPoints.removeAll(moved);
                map.effectPoints.addAll(movedObjects);
                map.allPoints.addAll(movedObjects);

                //movedObjects contains the objects with keys linked on new position, moved has the old position keys
                map.updateEffectPoints(movedObjects.entrySet(), moved.entrySet());
                break;
        }

        map.gameplayChanged();
        return this;
    }
}