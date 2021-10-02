package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.util.structures.Pair;
import alchyr.taikoedit.util.structures.PositionalObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

public class ObjectAddition extends MapChange {
    private final HitObject added;
    private final boolean singleObject;
    private final NavigableMap<Long, ArrayList<PositionalObject>> addedObjects;
    private List<Pair<Long, ArrayList<HitObject>>> replacedObjects;

    public ObjectAddition(EditorBeatmap map, HitObject addedObject)
    {
        super(map);

        singleObject = true;
        this.added = addedObject;
        addedObjects = null;
        replacedObjects = null;
    }

    public ObjectAddition(EditorBeatmap map, NavigableMap<Long, ArrayList<PositionalObject>> addedObjects)
    {
        super(map);

        singleObject = false;
        this.added = null;
        this.addedObjects = addedObjects;
        replacedObjects = null;
    }

    @Override
    public MapChange undo() {
        if (singleObject)
        {
            map.objects.removeObject(added);
        }
        else if (addedObjects != null)
        {
            map.objects.removeAll(addedObjects);
        }
        if (replacedObjects != null)
        {
            for (Pair<Long, ArrayList<HitObject>> replaced : replacedObjects)
            {
                map.objects.put(replaced.a, replaced.b);
            }
            map.updateVolume(replacedObjects);
        }
        return this;
    }

    @Override
    public MapChange perform() {
        if (singleObject)
        {
            ArrayList<HitObject> replaced = map.objects.remove(added.pos);
            if (replaced != null)
            {
                replacedObjects = new ArrayList<>();
                replacedObjects.add(new Pair<>(added.pos, replaced));
            }
            else
            {
                replacedObjects = null;
            }
            map.objects.add(added);
            map.updateVolume(added);
        }
        else if (addedObjects != null)
        {
            replacedObjects = new ArrayList<>();
            for (Map.Entry<Long, ArrayList<PositionalObject>> e : addedObjects.entrySet())
            {
                ArrayList<HitObject> replaced = map.objects.remove(e.getKey());
                if (replaced != null)
                {
                    replacedObjects.add(new Pair<>(e.getKey(), replaced));
                }

                for (PositionalObject o : e.getValue())
                    map.objects.add((HitObject) o);
            }
            if (replacedObjects.isEmpty())
                replacedObjects = null;

            map.updateVolume(addedObjects);
        }
        return this;
    }
}
