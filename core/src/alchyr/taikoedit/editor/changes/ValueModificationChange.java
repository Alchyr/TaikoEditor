package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ValueModificationChange extends MapChange {
    private final PositionalObjectTreeMap<PositionalObject> modifiedObjects;
    private final HashMap<PositionalObject, Double> originalValues;
    private final double change;

    public ValueModificationChange(EditorBeatmap map, PositionalObjectTreeMap<PositionalObject> modifiedObjects, double change)
    {
        super(map);

        this.modifiedObjects = modifiedObjects;
        this.change = change;

        this.originalValues = new HashMap<>();
        for (ArrayList<PositionalObject> objects : modifiedObjects.values()) {
            for (PositionalObject o : objects)
                this.originalValues.put(o, o.registerChange());
        }
    }

    @Override
    public MapChange undo() {
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : modifiedObjects.entrySet())
        {
            for (PositionalObject o : e.getValue()) {
                o.setValue(originalValues.get(o));
            }
        }
        map.updateEffectPoints(modifiedObjects, modifiedObjects);
        return this;
    }
    @Override
    public MapChange perform() {
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : modifiedObjects.entrySet())
        {
            for (PositionalObject o : e.getValue()) {
                o.tempModification(change);
                o.registerChange();
            }
        }
        map.updateEffectPoints(modifiedObjects, modifiedObjects);
        return this;
    }
}