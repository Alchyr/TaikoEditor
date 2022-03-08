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
    private final HashMap<PositionalObject, Double> newValues;

    public ValueModificationChange(EditorBeatmap map, PositionalObjectTreeMap<PositionalObject> modifiedObjects)
    {
        super(map);

        this.modifiedObjects = modifiedObjects;

        this.originalValues = new HashMap<>();
        this.newValues = new HashMap<>();
        for (ArrayList<PositionalObject> objects : modifiedObjects.values()) {
            for (PositionalObject o : objects) {
                this.originalValues.put(o, o.registerChange());
                this.newValues.put(o, o.getValue());
            }
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
        map.updateSv();
        return this;
    }
    @Override
    public MapChange perform() {
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : modifiedObjects.entrySet())
        {
            for (PositionalObject o : e.getValue()) {
                o.setValue(newValues.get(o));
            }
        }
        map.updateSv();
        return this;
    }
}