package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.interfaces.KnownAmountSupplier;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ValueSetChange extends MapChange {
    private final MapObjectTreeMap<MapObject> modifiedObjects;
    private final HashMap<MapObject, Double> originalValues;
    private final double newValue;
    private final boolean redLines;


    @Override
    public void send(DataOutputStream out) throws IOException {
        writeObjects(out, modifiedObjects.count(), map.allPoints, modifiedObjects.singleValuesIterator());
        out.writeDouble(newValue);
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        KnownAmountSupplier<List<MapObject>> mapObjectsSupplier = readObjects(in, map);

        if (mapObjectsSupplier == null) return null;

        double newVal = in.readDouble();

        return ()->{
            List<MapObject> mapObjects = mapObjectsSupplier.get();

            MapObjectTreeMap<MapObject> modifiedObjects = new MapObjectTreeMap<>();

            for (MapObject obj : mapObjects) {
                modifiedObjects.add(obj);
            }

            return new ValueSetChange(map, modifiedObjects, newVal);
        };
    }

    @Override
    public boolean isValid() {
        for (Map.Entry<Long, ArrayList<MapObject>> stack : modifiedObjects.entrySet()) {
            for (MapObject o : stack.getValue()) {
                if (!map.allPoints.containsKeyedValue(stack.getKey(), o)) {
                    return false;
                }
            }
        }
        return true;
    }

    public ValueSetChange(EditorBeatmap map, MapObjectTreeMap<MapObject> modifiedObjects, double newValue)
    {
        super(map, "Set Line Value");

        this.modifiedObjects = modifiedObjects;
        this.newValue = newValue;
        this.redLines = ((TimingPoint) modifiedObjects.firstEntry().getValue().get(0)).uninherited;

        this.originalValues = new HashMap<>();
        for (ArrayList<MapObject> objects : modifiedObjects.values()) {
            for (MapObject o : objects) {
                this.originalValues.put(o, o.getValue());
            }
        }
    }

    @Override
    public void undo() {
        for (Map.Entry<Long, ArrayList<MapObject>> e : modifiedObjects.entrySet())
        {
            for (MapObject o : e.getValue()) {
                o.setValue(originalValues.get(o));
            }
        }

        if (redLines)
            map.regenerateDivisor();
        else
            map.updateSv();

        map.gameplayChanged();
    }
    @Override
    public void perform() {
        for (Map.Entry<Long, ArrayList<MapObject>> e : modifiedObjects.entrySet())
        {
            for (MapObject o : e.getValue()) {
                o.setValue(newValue);
            }
        }

        if (redLines)
            map.regenerateDivisor();
        else
            map.updateSv();

        map.gameplayChanged();
    }
}