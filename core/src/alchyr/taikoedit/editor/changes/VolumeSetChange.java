package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class VolumeSetChange extends MapChange {
    private final PositionalObjectTreeMap<PositionalObject> modifiedObjects;
    private final HashMap<PositionalObject, Integer> originalValues;
    private final HashMap<PositionalObject, Integer> newValues;

    public VolumeSetChange(EditorBeatmap map, PositionalObjectTreeMap<PositionalObject> modifiedObjects, int newValue)
    {
        super(map);

        this.modifiedObjects = modifiedObjects;

        this.originalValues = new HashMap<>();
        this.newValues = new HashMap<>();

        for (ArrayList<PositionalObject> objects : modifiedObjects.values()) {
            for (PositionalObject o : objects) {
                this.originalValues.put(o, o.getVolume());
                this.newValues.put(o, newValue);
            }
        }
    }

    @Override
    public MapChange undo() {
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : modifiedObjects.entrySet())
        {
            for (PositionalObject o : e.getValue()) {
                o.setVolume(originalValues.get(o));
            }
        }
        map.updateLines(modifiedObjects.entrySet(), null);
        return this;
    }
    @Override
    public MapChange perform() {
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : modifiedObjects.entrySet())
        {
            for (PositionalObject o : e.getValue()) {
                o.setVolume(newValues.get(o));
                o.registerVolumeChange();
            }
        }
        map.updateLines(modifiedObjects.entrySet(), null);
        return this;
    }
}