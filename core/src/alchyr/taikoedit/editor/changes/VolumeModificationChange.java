package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.GeneralUtils;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

public class VolumeModificationChange extends MapChange {
    private final MapObjectTreeMap<MapObject> modifiedObjects;
    private final Map<MapObject, Integer> originalVolumes;
    private final Map<Long, Integer> newVolumes;


    @Override
    public void send(DataOutputStream out) throws IOException {
        //out.write();
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        return null;
    }

    @Override
    public boolean isValid() {
        return false;
    }

    public VolumeModificationChange(EditorBeatmap map, MapObjectTreeMap<MapObject> modifiedObjects, Map<Long, Integer> newVolumeMap)
    {
        super(map, "Adjust Volume");

        this.originalVolumes = new HashMap<>();
        this.newVolumes = newVolumeMap;
        this.modifiedObjects = modifiedObjects;

        for (ArrayList<MapObject> points : map.getStackedObjects(modifiedObjects, map.allPoints).values()) {
            for (MapObject point : points) {
                this.originalVolumes.put(point, point.getVolume());
            }
        }
    }

    @Override
    public void undo() {
        for (Map.Entry<Long, ArrayList<MapObject>> points : map.getStackedObjects(modifiedObjects, map.allPoints).entrySet()) {
            for (MapObject point : points.getValue()) {
                Integer originalVol = originalVolumes.get(point);
                if (originalVol != null) {
                    point.setVolume(originalVol);
                }
            }
        }

        map.updateLines(modifiedObjects.entrySet(), null);
    }
    @Override
    public void perform() {
        for (Map.Entry<Long, ArrayList<MapObject>> points : map.getStackedObjects(modifiedObjects, map.allPoints).entrySet()) {
            for (MapObject point : points.getValue()) {
                point.setVolume(newVolumes.get(points.getKey()));
            }
        }

        map.updateLines(modifiedObjects.entrySet(), null);
    }
}