package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;

public class MultiDeletion extends MapChange {
    private final Map<ChangeType, PositionalObjectTreeMap<PositionalObject>> deletions;

    public MultiDeletion(EditorBeatmap map, NavigableMap<Long, ArrayList<PositionalObject>> deletedObjects)
    {
        super(map);

        this.deletions = new HashMap<>();
        for (Map.Entry<Long, ArrayList<PositionalObject>> deletedStack : deletedObjects.entrySet()) {
            for (PositionalObject obj : deletedStack.getValue()) {
                deletions.compute(getChangeType(obj), (k, v)->{
                    if (v == null) {
                        v = new PositionalObjectTreeMap<>();
                    }
                    v.add(obj);
                    return v;
                });
            }
        }
    }

    @Override
    public MapChange undo() {
        for (Map.Entry<ChangeType, PositionalObjectTreeMap<PositionalObject>> deletion : deletions.entrySet()) {
            PositionalObjectTreeMap<PositionalObject> objs = deletion.getValue();
            switch (deletion.getKey())
            {
                case OBJECTS:
                    map.preAddObjects(objs);
                    map.objects.addAll(objs);
                    map.updateVolume(objs);
                    break;
                case GREEN_LINE:
                    map.effectPoints.addAll(objs);
                    map.allPoints.addAll(objs);
                    map.updateLines(objs.entrySet(), null);
                    break;
                case RED_LINE:
                    map.timingPoints.addAll(objs);
                    map.allPoints.addAll(objs);
                    map.regenerateDivisor();
                    map.updateLines(objs.entrySet(), null);
                    break;
            }
        }

        map.gameplayChanged();
        return this;
    }

    @Override
    public MapChange perform() {
        for (Map.Entry<ChangeType, PositionalObjectTreeMap<PositionalObject>> deletion : deletions.entrySet()) {
            PositionalObjectTreeMap<PositionalObject> objs = deletion.getValue();
            switch (deletion.getKey())
            {
                case OBJECTS:
                    map.removeObjects(objs);
                    break;
                case GREEN_LINE:
                    map.effectPoints.removeAll(objs);
                    map.allPoints.removeAll(objs);
                    map.updateLines(null, objs.entrySet());
                    break;
                case RED_LINE:
                    map.timingPoints.removeAll(objs);
                    map.allPoints.removeAll(objs);
                    map.regenerateDivisor();
                    map.updateLines(null, objs.entrySet());
                    break;
            }
        }

        map.gameplayChanged();
        return this;
    }
}
