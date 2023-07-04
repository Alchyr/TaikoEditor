package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ValueSetChange extends MapChange {
    private final PositionalObjectTreeMap<PositionalObject> modifiedObjects;
    private final HashMap<PositionalObject, Double> originalValues;
    private final double newValue;
    private final boolean redLines;

    public ValueSetChange(EditorBeatmap map, boolean redLines, PositionalObjectTreeMap<PositionalObject> modifiedObjects, double newValue)
    {
        super(map);

        this.modifiedObjects = modifiedObjects;
        this.newValue = newValue;
        this.redLines = redLines;

        this.originalValues = new HashMap<>();
        for (ArrayList<PositionalObject> objects : modifiedObjects.values()) {
            for (PositionalObject o : objects) {
                this.originalValues.put(o, o.getValue());
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

        if (redLines)
            map.regenerateDivisor();
        else
            map.updateSv();

        map.gameplayChanged();

        return this;
    }
    @Override
    public MapChange perform() {
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : modifiedObjects.entrySet())
        {
            for (PositionalObject o : e.getValue()) {
                o.setValue(newValue);
            }
        }

        if (redLines)
            map.regenerateDivisor();
        else
            map.updateSv();

        map.gameplayChanged();

        return this;
    }
}