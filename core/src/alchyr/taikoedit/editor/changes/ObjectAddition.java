package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.maps.components.HitObject;
import alchyr.taikoedit.util.structures.Pair;
import alchyr.taikoedit.util.structures.PositionalObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

public class ObjectAddition extends MapChange {
    private final HitObject added;
    private final boolean singleObject;
    private final NavigableMap<Integer, ArrayList<PositionalObject>> addedObjects;
    private List<Pair<Integer, ArrayList<HitObject>>> replacedObjects;

    public ObjectAddition(EditorBeatmap map, HitObject addedObject)
    {
        super(map);

        singleObject = true;
        this.added = addedObject;
        addedObjects = null;
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
            for (Pair<Integer, ArrayList<HitObject>> replaced : replacedObjects)
            {
                map.objects.put(replaced.a, replaced.b);
            }
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
        }
        else if (addedObjects != null)
        {
            replacedObjects = new ArrayList<>();
            for (Map.Entry<Integer, ArrayList<PositionalObject>> e : addedObjects.entrySet())
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
        }
        return this;
    }
}
