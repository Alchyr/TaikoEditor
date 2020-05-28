package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;

import java.util.ArrayList;
import java.util.Map;

public class Movement extends MapChange {
    private final ChangeType type;
    private final PositionalObjectTreeMap<PositionalObject> movedObjects;
    private final int moveAmount;

    public Movement(EditorBeatmap map, ChangeType type, PositionalObjectTreeMap<PositionalObject> movedObjects, int offset)
    {
        super(map);

        this.type = type;

        this.movedObjects = movedObjects;
        this.moveAmount = offset;
    }

    @SuppressWarnings("unchecked")
    @Override
    public MapChange undo() {
        PositionalObjectTreeMap<PositionalObject> moved = new PositionalObjectTreeMap<>();

        switch (type)
        {
            case OBJECTS:
                for (Map.Entry<Integer, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPosition(e.getKey() - moveAmount));
                    moved.put(e.getKey() - moveAmount, e.getValue());
                }
                map.objects.removeAll(movedObjects);
                map.objects.addAll(movedObjects);
                break;
            case TIMING:
                for (Map.Entry<Integer, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPosition(e.getKey() - moveAmount));
                    moved.put(e.getKey() - moveAmount, e.getValue());
                }
                map.timingPoints.removeAll(movedObjects);
                map.timingPoints.addAll(movedObjects);
                break;
            case EFFECT:
                for (Map.Entry<Integer, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPosition(e.getKey() - moveAmount));
                    moved.put(e.getKey() - moveAmount, e.getValue());
                }
                map.effectPoints.removeAll(movedObjects);
                map.effectPoints.addAll(movedObjects);
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
                for (Map.Entry<Integer, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPosition(e.getKey() + moveAmount));
                    moved.put(e.getKey() + moveAmount, e.getValue());
                }
                map.objects.removeAll(movedObjects);
                map.objects.addAll(movedObjects);
                break;
            case TIMING:
                for (Map.Entry<Integer, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPosition(e.getKey() + moveAmount));
                    moved.put(e.getKey() + moveAmount, e.getValue());
                }
                map.timingPoints.removeAll(movedObjects);
                map.timingPoints.addAll(movedObjects);
                break;
            case EFFECT:
                for (Map.Entry<Integer, ArrayList<PositionalObject>> e : movedObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPosition(e.getKey() + moveAmount));
                    moved.put(e.getKey() + moveAmount, e.getValue());
                }
                map.effectPoints.removeAll(movedObjects);
                map.effectPoints.addAll(movedObjects);
                break;
        }
        movedObjects.clear();
        movedObjects.putAll(moved);
        return this;
    }
}