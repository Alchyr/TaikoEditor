package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.maps.components.HitObject;
import alchyr.taikoedit.maps.components.TimingPoint;
import alchyr.taikoedit.util.structures.PositionalObject;

import java.util.ArrayList;
import java.util.Map;
import java.util.NavigableMap;

public class Deletion extends MapChange {
    private final ChangeType type;
    private final boolean singleObject;
    private final NavigableMap<Integer, ArrayList<PositionalObject>> deletedObjects;
    private final PositionalObject deletedObject;

    public Deletion(EditorBeatmap map, ChangeType type, NavigableMap<Integer, ArrayList<PositionalObject>> deletedObjects)
    {
        super(map);

        this.type = type;

        singleObject = false;
        this.deletedObject = null;
        this.deletedObjects = deletedObjects;
    }
    public Deletion(EditorBeatmap map, ChangeType type, PositionalObject deletedObject)
    {
        super(map);

        this.type = type;

        singleObject = true;
        this.deletedObject = deletedObject;
        deletedObjects = null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public MapChange undo() {
        if (singleObject)
        {
            assert deletedObject != null;
            switch (type)
            {
                case OBJECTS:
                    map.objects.add((HitObject) deletedObject);
                    break;
                case TIMING:
                    map.timingPoints.add((TimingPoint) deletedObject);
                    break;
                case EFFECT:
                    map.effectPoints.add((TimingPoint) deletedObject);
                    break;
            }
        }
        else
        {
            assert deletedObjects != null;
            switch (type)
            {
                case OBJECTS:
                    map.objects.addAll(deletedObjects);
                    break;
                case TIMING:
                    map.timingPoints.addAll(deletedObjects);
                    break;
                case EFFECT:
                    map.effectPoints.addAll(deletedObjects);
                    break;
            }
        }
        return this;
    }
    @Override
    public MapChange perform() {
        if (singleObject)
        {
            assert deletedObject != null;
            switch (type)
            {
                case OBJECTS:
                    map.objects.removeObject(deletedObject);
                    break;
                case TIMING:
                    map.timingPoints.removeObject(deletedObject);
                    break;
                case EFFECT:
                    map.effectPoints.removeObject(deletedObject);
                    break;
            }
        }
        else
        {
            assert deletedObjects != null;
            switch (type)
            {
                case OBJECTS:
                    map.objects.removeAll(deletedObjects);
                    break;
                case TIMING:
                    map.timingPoints.removeAll(deletedObjects);
                    break;
                case EFFECT:
                    map.effectPoints.removeAll(deletedObjects);
                    break;
            }
        }
        return this;
    }
}
