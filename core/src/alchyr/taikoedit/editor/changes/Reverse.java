package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.ILongObject;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;

import java.util.*;

public class Reverse extends MapChange {
    private PositionalObjectTreeMap<PositionalObject> reversedObjects;
    private final boolean resnap;
    private final ChangeType type;

    private final HashMap<PositionalObject, Long> originalPositions;

    public Reverse(EditorBeatmap map, ChangeType type, boolean resnap, PositionalObjectTreeMap<PositionalObject> reversedObjects)
    {
        super(map);
        this.type = type;
        this.resnap = resnap;
        this.reversedObjects = reversedObjects;

        this.originalPositions = new HashMap<>();
        for (Map.Entry<Long, ArrayList<PositionalObject>> stack : reversedObjects.entrySet()) {
            for (PositionalObject o : stack.getValue())
                this.originalPositions.put(o, stack.getKey());
        }

        invalidateSelection = true;
    }

    @Override
    public MapChange undo() {
        PositionalObjectTreeMap<PositionalObject> reversedCopy = new PositionalObjectTreeMap<>();

        switch (type)
        {
            case OBJECTS:
                map.removeObjects(reversedObjects);
                break;
            case EFFECT:
                map.effectPoints.removeAll(reversedObjects);
                map.allPoints.removeAll(reversedObjects);
                break;
        }

        for (Map.Entry<Long, ArrayList<PositionalObject>> entry : reversedObjects.entrySet())
        {
            for (PositionalObject o : entry.getValue())
            {
                o.setPos(originalPositions.get(o));
                reversedCopy.add(o);
            }
        }

        reversedObjects = reversedCopy;

        switch (type)
        {
            case OBJECTS:
                map.preAddObjects(reversedObjects);
                map.objects.addAll(reversedObjects);
                map.updateVolume(reversedObjects);
                break;
            case EFFECT:
                map.effectPoints.addAll(reversedObjects);
                map.allPoints.addAll(reversedObjects);
                for (Map.Entry<Long, ArrayList<PositionalObject>> e : reversedObjects.entrySet()) {
                    map.updateVolume(e.getKey());
                }
                break;
        }

        return this;
    }

    @Override
    public MapChange perform() {
        Snap closest;
        TreeMap<Long, Snap> snaps = map.getAllSnaps();

        long start = reversedObjects.firstKey(), end = reversedObjects.lastKey(), newPos;

        PositionalObjectTreeMap<PositionalObject> reversedCopy = new PositionalObjectTreeMap<>();

        switch (type)
        {
            case OBJECTS:
                map.removeObjects(reversedObjects);
                break;
            case EFFECT:
                map.effectPoints.removeAll(reversedObjects);
                map.allPoints.removeAll(reversedObjects);
                break;
        }

        for (Map.Entry<Long, ArrayList<PositionalObject>> entry : reversedObjects.entrySet())
        {
            newPos = end - (entry.getValue().get(0).getPos() - start);

            if (resnap)
            {
                closest = snaps.get(newPos);
                if (closest == null)
                    closest = snaps.get(newPos + 1);
                if (closest == null)
                    closest = snaps.get(newPos - 1);
                if (closest != null)
                    newPos = closest.pos;
            }

            for (PositionalObject o : entry.getValue())
            {
                if (o instanceof ILongObject)
                {
                    o.setPos(newPos - ((ILongObject) o).getDuration());
                }
                else
                {
                    o.setPos(newPos);
                }
                reversedCopy.add(o);
            }
        }

        reversedObjects = reversedCopy;

        switch (type)
        {
            case OBJECTS:
                map.preAddObjects(reversedObjects);
                map.objects.addAll(reversedObjects);
                map.updateVolume(reversedObjects);
                break;
            case EFFECT:
                map.effectPoints.addAll(reversedObjects);
                map.allPoints.addAll(reversedObjects);
                for (Map.Entry<Long, ArrayList<PositionalObject>> e : reversedObjects.entrySet()) {
                    map.updateVolume(e.getKey());
                }
                break;
        }

        return this;
    }
}
