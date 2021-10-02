package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;


//basically just a combination of deletion and addition
public class Replacement extends MapChange {
    private final ChangeType type;
    private final PositionalObjectTreeMap<PositionalObject> deletedObjects;
    private final PositionalObjectTreeMap<PositionalObject> addedObjects;

    public Replacement(EditorBeatmap map, ChangeType type, PositionalObjectTreeMap<PositionalObject> deletedObjects, PositionalObjectTreeMap<PositionalObject> addedObjects)
    {
        super(map);

        this.type = type;
        this.deletedObjects = deletedObjects;
        this.addedObjects = addedObjects;
    }

    @Override
    public MapChange undo() {
        switch (type)
        {
            case OBJECTS:
                map.objects.removeAll(addedObjects);
                map.objects.addAll(deletedObjects);
                map.updateVolume(deletedObjects);
                break;
            case EFFECT:
                map.effectPoints.removeAll(addedObjects);
                map.effectPoints.addAll(deletedObjects);
                break;
        }
        return this;
    }
    @Override
    public MapChange perform() {
        switch (type)
        {
            case OBJECTS:
                map.objects.removeAll(deletedObjects);
                map.objects.addAll(addedObjects);
                map.updateVolume(addedObjects);
                break;
            case EFFECT:
                map.effectPoints.removeAll(deletedObjects);
                map.effectPoints.addAll(addedObjects);
                break;
        }
        return this;
    }
}