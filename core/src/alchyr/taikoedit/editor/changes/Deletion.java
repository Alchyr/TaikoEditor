package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.structures.PositionalObject;

import java.util.ArrayList;
import java.util.NavigableMap;

public class Deletion extends MapChange {
    private final ChangeType type;
    private final boolean singleObject;
    private final NavigableMap<Long, ArrayList<PositionalObject>> deletedObjects;
    private final PositionalObject deletedObject;

    public Deletion(EditorBeatmap map, ChangeType type, NavigableMap<Long, ArrayList<PositionalObject>> deletedObjects)
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

    @Override
    public MapChange undo() {
        if (singleObject)
        {
            assert deletedObject != null;
            switch (type)
            {
                case OBJECTS:
                    map.preAddObject((HitObject) deletedObject);
                    map.objects.add((HitObject) deletedObject);
                    map.updateVolume((HitObject) deletedObject);
                    break;
                case EFFECT:
                    map.effectPoints.add((TimingPoint) deletedObject);
                    map.allPoints.add((TimingPoint) deletedObject);
                    map.updateEffectPoints((TimingPoint) deletedObject, null);
                    break;
            }
        }
        else if (deletedObjects != null)
        {
            switch (type)
            {
                case OBJECTS:
                    map.preAddObjects(deletedObjects);
                    map.objects.addAll(deletedObjects);
                    map.updateVolume(deletedObjects);
                    break;
                case EFFECT:
                    map.effectPoints.addAll(deletedObjects);
                    map.allPoints.addAll(deletedObjects);
                    map.updateEffectPoints(deletedObjects.entrySet(), null);
                    break;
            }
        }
        return this;
    }
    @Override
    public MapChange perform() {
        if (singleObject && deletedObject != null)
        {
            switch (type)
            {
                case OBJECTS:
                    map.removeObject(deletedObject);
                    break;
                case EFFECT:
                    map.effectPoints.removeObject(deletedObject);
                    map.allPoints.removeObject(deletedObject);
                    map.updateEffectPoints(null, (TimingPoint) deletedObject);
                    break;
            }
        }
        else if (deletedObjects != null)
        {
            switch (type)
            {
                case OBJECTS:
                    map.removeObjects(deletedObjects);
                    break;
                case EFFECT:
                    map.effectPoints.removeAll(deletedObjects);
                    map.allPoints.removeAll(deletedObjects);
                    map.updateEffectPoints(null, deletedObjects.entrySet());
                    break;
            }
        }
        return this;
    }
}
