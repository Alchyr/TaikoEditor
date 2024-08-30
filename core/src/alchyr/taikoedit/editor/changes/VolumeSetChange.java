package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
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

public class VolumeSetChange extends MapChange {
    private final MapObjectTreeMap<MapObject> modifiedObjects;
    private final HashMap<MapObject, Integer> originalValues;
    private final int newVolume;


    @Override
    public void send(DataOutputStream out) throws IOException {
        writeObjects(out, modifiedObjects.count(), map.objects, modifiedObjects.singleValuesIterator());
        out.writeInt(newVolume);
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        KnownAmountSupplier<List<MapObject>> mapObjectsSupplier = readObjects(in, map);

        if (mapObjectsSupplier == null) return null;

        int newVal = in.readInt();

        return ()->{
            List<MapObject> mapObjects = mapObjectsSupplier.get();

            MapObjectTreeMap<MapObject> modifiedObjects = new MapObjectTreeMap<>();

            for (MapObject obj : mapObjects) {
                modifiedObjects.add(obj);
            }

            return new VolumeSetChange(map, modifiedObjects, newVal);
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

    public VolumeSetChange(EditorBeatmap map, MapObjectTreeMap<MapObject> modifiedObjects, int newVolume)
    {
        super(map, "Set Volume");

        this.modifiedObjects = modifiedObjects;

        this.originalValues = new HashMap<>();
        this.newVolume = newVolume;

        for (ArrayList<MapObject> objects : modifiedObjects.values()) {
            for (MapObject o : objects) {
                this.originalValues.put(o, o.getVolume());
            }
        }
    }

    @Override
    public void undo() {
        for (Map.Entry<Long, ArrayList<MapObject>> e : modifiedObjects.entrySet())
        {
            for (MapObject o : e.getValue()) {
                o.setVolume(originalValues.get(o));
            }
        }
        map.updateLines(modifiedObjects.entrySet(), null);
    }
    @Override
    public void perform() {
        for (Map.Entry<Long, ArrayList<MapObject>> e : modifiedObjects.entrySet())
        {
            for (MapObject o : e.getValue()) {
                o.setVolume(newVolume);
            }
        }
        map.updateLines(modifiedObjects.entrySet(), null);
    }

    @Override
    public MapChange reconstruct() {
        return new VolumeSetChange(map, modifiedObjects, newVolume);
    }
}