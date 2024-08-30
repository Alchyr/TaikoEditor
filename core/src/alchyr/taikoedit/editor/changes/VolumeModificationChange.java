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

public class VolumeModificationChange extends MapChange {
    private final MapObjectTreeMap<MapObject> modifiedObjects;
    private final Map<MapObject, Integer> originalVolumes;
    private final Map<Long, Integer> newVolumes;


    @Override
    public void send(DataOutputStream out) throws IOException {
        writeObjects(out, modifiedObjects.count(), map.allPoints, modifiedObjects.singleValuesIterator());

        out.writeInt(newVolumes.size());
        for (Map.Entry<Long, Integer> volume : newVolumes.entrySet()) {
            out.writeLong(volume.getKey());
            out.writeInt(volume.getValue());
        }
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        KnownAmountSupplier<List<MapObject>> mapObjectsSupplier = readObjects(in, map);

        if (mapObjectsSupplier == null) return null;

        int newVolumeAmount = in.readInt();
        HashMap<Long, Integer> newVol = new HashMap<>();
        for (int i = 0; i < newVolumeAmount; ++i) {
            long volKey = in.readLong();
            int newVal = in.readInt();
            newVol.put(volKey, newVal);
        }

        return ()->{
            List<MapObject> mapObjects = mapObjectsSupplier.get();

            MapObjectTreeMap<MapObject> modifiedObjects = new MapObjectTreeMap<>();

            for (MapObject obj : mapObjects) {
                modifiedObjects.add(obj);
            }

            return new VolumeModificationChange(map, modifiedObjects, newVol);
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

    public VolumeModificationChange(EditorBeatmap map, MapObjectTreeMap<MapObject> modifiedObjects, Map<Long, Integer> newVolumeMap)
    {
        super(map, "Adjust Volume");

        this.originalVolumes = new HashMap<>();
        this.newVolumes = newVolumeMap;
        this.modifiedObjects = map.getStackedObjects(modifiedObjects, map.allPoints);

        for (ArrayList<MapObject> points : modifiedObjects.values()) {
            for (MapObject point : points) {
                this.originalVolumes.put(point, point.getVolume());
            }
        }
    }

    @Override
    public void undo() {
        for (Map.Entry<Long, ArrayList<MapObject>> points : modifiedObjects.entrySet()) {
            for (MapObject point : points.getValue()) {
                point.setVolume(originalVolumes.get(point));
            }
        }

        map.updateLines(modifiedObjects.entrySet(), null);
    }
    @Override
    public void perform() {
        for (Map.Entry<Long, ArrayList<MapObject>> points : modifiedObjects.entrySet()) {
            for (MapObject point : points.getValue()) {
                point.setVolume(newVolumes.get(points.getKey()));
            }
        }

        map.updateLines(modifiedObjects.entrySet(), null);
    }

    @Override
    public MapChange reconstruct() {
        return new VolumeModificationChange(map, modifiedObjects, newVolumes);
    }
}