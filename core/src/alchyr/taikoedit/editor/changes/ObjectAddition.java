package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.util.structures.Pair;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

public class ObjectAddition extends MapChange {
    private final HitObject added;
    private final boolean singleObject;
    private final NavigableMap<Long, ArrayList<PositionalObject>> addedObjects;
    private PositionalObjectTreeMap<PositionalObject> replacedObjects = new PositionalObjectTreeMap<>();

    public ObjectAddition(EditorBeatmap map, HitObject addedObject)
    {
        super(map);

        singleObject = true;
        this.added = addedObject;
        addedObjects = null;
    }

    public ObjectAddition(EditorBeatmap map, NavigableMap<Long, ArrayList<PositionalObject>> addedObjects)
    {
        super(map);

        singleObject = false;
        this.added = null;
        this.addedObjects = addedObjects;
    }

    @Override
    public MapChange undo() {
        if (singleObject)
        {
            map.removeObject(added);
        }
        else if (addedObjects != null)
        {
            map.removeObjects(addedObjects);
        }
        if (!replacedObjects.isEmpty())
        {
            map.preAddObjects(replacedObjects);
            map.objects.addAll(replacedObjects);
            map.updateVolume(replacedObjects);
        }
        return this;
    }

    @Override
    public MapChange perform() {
        if (singleObject)
        {
            replacedObjects.clear();
            ArrayList<HitObject> replaced = map.objects.get(added.getPos());
            if (replaced != null) {
                for (HitObject h : replaced) {
                    replacedObjects.add(h);
                }
                map.removeObjects(replacedObjects);
            }

            map.preAddObject(added);
            map.objects.add(added);
            map.updateVolume(added);
        }
        else if (addedObjects != null)
        {
            replacedObjects.clear();
            for (Map.Entry<Long, ArrayList<PositionalObject>> addedStack : addedObjects.entrySet())
            {
                ArrayList<HitObject> replaced = map.objects.get(addedStack.getKey());
                if (replaced != null) {
                    for (HitObject h : replaced) {
                        replacedObjects.add(h);
                    }
                }
            }
            if (!replacedObjects.isEmpty())
                map.removeObjects(replacedObjects);

            map.preAddObjects(addedObjects);
            map.objects.addAll(addedObjects);
            map.updateVolume(addedObjects);
        }
        return this;
    }
}
