package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class VolumeSetChange extends MapChange {
    private final MapObjectTreeMap<MapObject> modifiedObjects;
    private final HashMap<MapObject, Integer> originalValues;
    private final HashMap<MapObject, Integer> newValues;


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

    public VolumeSetChange(EditorBeatmap map, MapObjectTreeMap<MapObject> modifiedObjects, int newValue)
    {
        super(map, "Set Volume");

        this.modifiedObjects = modifiedObjects;

        this.originalValues = new HashMap<>();
        this.newValues = new HashMap<>();

        for (ArrayList<MapObject> objects : modifiedObjects.values()) {
            for (MapObject o : objects) {
                this.originalValues.put(o, o.getVolume());
                this.newValues.put(o, newValue);
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
                o.setVolume(newValues.get(o));
            }
        }
        map.updateLines(modifiedObjects.entrySet(), null);
    }
}