package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.interfaces.KnownAmountSupplier;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;
import alchyr.taikoedit.util.structures.Pair;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ValueModificationChange extends MapChange {
    private final MapObjectTreeMap<MapObject> modifiedObjects;
    private final Map<MapObject, Double> originalValues;
    private final Map<MapObject, Double> newValues;


    @Override
    public void send(DataOutputStream out) throws IOException {
        writeObjects(out, modifiedObjects.count(), map.allPoints, modifiedObjects.singleValuesIterator());
        for (Map.Entry<MapObject, Double> value : originalValues.entrySet()) {
            out.writeInt(value.getKey().key);
            out.writeDouble(value.getValue());
            out.writeDouble(newValues.get(value.getKey()));
        }
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        KnownAmountSupplier<List<MapObject>> mapObjectsSupplier = readObjects(in, map);

        if (mapObjectsSupplier == null) return null;

        HashMap<Integer, Pair<Double, Double>> data = new HashMap<>();
        for (int i = 0; i < mapObjectsSupplier.getAmount(); ++i) {
            int objKey = in.readInt();
            double origVal = in.readDouble();
            double newVal = in.readDouble();
            data.put(objKey, new Pair<>(origVal, newVal));
        }

        return ()->{
            List<MapObject> mapObjects = mapObjectsSupplier.get();

            MapObjectTreeMap<MapObject> modifiedObjects = new MapObjectTreeMap<>();

            for (MapObject obj : mapObjects) {
                modifiedObjects.add(obj);
            }

            return new ValueModificationChange(map, modifiedObjects, data);
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

    public ValueModificationChange(EditorBeatmap map, MapObjectTreeMap<MapObject> modifiedObjects, Map<MapObject, Double> newValueMap)
    {
        super(map, "Adjust Line Value");

        this.modifiedObjects = modifiedObjects;

        this.originalValues = new HashMap<>();
        this.newValues = newValueMap;
        for (ArrayList<MapObject> objects : modifiedObjects.values()) {
            for (MapObject o : objects) {
                this.originalValues.put(o, o.getValue());
            }
        }
    }

    private ValueModificationChange(EditorBeatmap map, MapObjectTreeMap<MapObject> modifiedObjects, HashMap<Integer, Pair<Double, Double>> data) {
        super(map, "Adjust Line Value");

        this.modifiedObjects = modifiedObjects;
        this.originalValues = new HashMap<>();
        this.newValues = new HashMap<>();

        this.modifiedObjects.forEachObject((obj)->{
            Pair<Double, Double> values = data.get(obj.key);
            originalValues.put(obj, values.a);
            newValues.put(obj, values.b);
        });
    }

    @Override
    public void undo() {
        for (Map.Entry<Long, ArrayList<MapObject>> e : modifiedObjects.entrySet())
        {
            for (MapObject o : e.getValue()) {
                o.setValue(originalValues.get(o));
            }
        }
        
        map.updateSv();
        map.gameplayChanged();
    }
    @Override
    public void perform() {
        for (Map.Entry<Long, ArrayList<MapObject>> e : modifiedObjects.entrySet())
        {
            for (MapObject o : e.getValue()) {
                o.setValue(newValues.get(o));
            }
        }

        map.updateSv();
        map.gameplayChanged();
    }
}