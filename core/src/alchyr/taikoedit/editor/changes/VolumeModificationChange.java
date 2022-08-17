package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.GeneralUtils;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class VolumeModificationChange extends MapChange {
    private final PositionalObjectTreeMap<PositionalObject> modifiedObjects;
    private final HashMap<PositionalObject, Integer> originalVolumes;
    private final HashMap<PositionalObject, Integer> newVolumes;

    public VolumeModificationChange(EditorBeatmap map, PositionalObjectTreeMap<PositionalObject> modifiedObjects, PositionalObjectTreeMap<PositionalObject> allChangeObjects)
    {
        super(map);

        this.originalVolumes = new HashMap<>();
        this.newVolumes = new HashMap<>();

        //Transfer changes from the modified objects to all other objects with the same positions
        Iterator<Map.Entry<Long, ArrayList<PositionalObject>>> modified = modifiedObjects.entrySet().iterator(),
                toChange = allChangeObjects.entrySet().iterator();

        Map.Entry<Long, ArrayList<PositionalObject>> focus, secondary;
        if (toChange.hasNext()) {
            secondary = toChange.next();
        }
        else { //No auto-modification of stacked objects
            this.modifiedObjects = modifiedObjects;

            for (ArrayList<PositionalObject> objects : modifiedObjects.values()) {
                for (PositionalObject o : objects) {
                    this.originalVolumes.put(o, o.registerVolumeChange());
                    this.newVolumes.put(o, o.getVolume());
                }
            }
            return;
        }

        //go through each stack
        while (modified.hasNext()) {
            focus = modified.next();

            for (PositionalObject o : focus.getValue()) {
                this.originalVolumes.put(o, o.registerVolumeChange());
                this.newVolumes.put(o, o.getVolume());
            }

            //find next corresponding stack
            while (secondary.getKey() < focus.getKey() && toChange.hasNext()) {
                secondary = toChange.next();
            }

            //corresponds
            if (secondary.getKey().equals(focus.getKey())) {
                int volume = GeneralUtils.listLast(focus.getValue()).getVolume();
                for (PositionalObject o : secondary.getValue()) {
                    this.originalVolumes.putIfAbsent(o, o.getVolume());
                    this.newVolumes.putIfAbsent(o, volume);
                    o.setVolume(volume);
                }
            }
        }

        this.modifiedObjects = allChangeObjects;
    }

    @Override
    public MapChange undo() {
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : modifiedObjects.entrySet())
        {
            for (PositionalObject o : e.getValue()) {
                o.setVolume(originalVolumes.get(o));
            }
        }
        map.updateEffectPoints(modifiedObjects.entrySet(), null);
        return this;
    }
    @Override
    public MapChange perform() {
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : modifiedObjects.entrySet())
        {
            for (PositionalObject o : e.getValue()) {
                o.setVolume(newVolumes.get(o));
            }
        }
        map.updateEffectPoints(modifiedObjects.entrySet(), null);
        return this;
    }
}