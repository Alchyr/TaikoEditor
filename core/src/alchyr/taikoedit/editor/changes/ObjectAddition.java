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
import java.util.function.BiFunction;

public class ObjectAddition extends MapChange {
    private final HitObject added;
    private final boolean singleObject;
    private final NavigableMap<Long, ArrayList<PositionalObject>> addedObjects;
    private final BiFunction<PositionalObject, PositionalObject, Boolean> shouldReplace;
    private final PositionalObjectTreeMap<PositionalObject> replacedObjects = new PositionalObjectTreeMap<>();

    public ObjectAddition(EditorBeatmap map, HitObject addedObject, BiFunction<PositionalObject, PositionalObject, Boolean> shouldReplace)
    {
        super(map);

        singleObject = true;
        this.added = addedObject;
        addedObjects = null;

        this.shouldReplace = shouldReplace;
    }

    public ObjectAddition(EditorBeatmap map, NavigableMap<Long, ArrayList<PositionalObject>> addedObjects, BiFunction<PositionalObject, PositionalObject, Boolean> shouldReplace)
    {
        super(map);

        singleObject = false;
        this.added = null;
        this.addedObjects = addedObjects;

        this.shouldReplace = shouldReplace;
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

        map.gameplayChanged();
        return this;
    }

    @Override
    public MapChange perform() {
        if (singleObject && added != null)
        {
            replacedObjects.clear();
            ArrayList<HitObject> replaced = map.objects.get(added.getPos());
            if (replaced != null) {
                for (HitObject h : replaced) {
                    if (shouldReplace.apply(added, h))
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
                    outer: for (HitObject h : replaced) {
                        for (PositionalObject n : addedStack.getValue()) {
                            if (shouldReplace.apply(n, h)) {
                                //If any of the added objects should replace the existing object, replace it
                                replacedObjects.add(h);
                                continue outer;
                            }
                        }
                    }
                }
            }
            if (!replacedObjects.isEmpty())
                map.removeObjects(replacedObjects);

            map.preAddObjects(addedObjects);
            map.objects.addAll(addedObjects);
            map.updateVolume(addedObjects);
        }

        map.gameplayChanged();
        return this;
    }
}
