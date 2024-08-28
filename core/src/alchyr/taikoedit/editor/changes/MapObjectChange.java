package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;


public class MapObjectChange extends MapChange {
    private static final MapObjectTreeMap<MapObject> empty = new MapObjectTreeMap<>();
    public static final int OBJECT_CHANGE = 0;
    public static final int LINE_CHANGE = 1;

    private int changeType;
    private final MapObjectTreeMap<MapObject> removeObjects;
    private final MapObjectTreeMap<MapObject> addObjects;

    private boolean movingObjects = false; //this is used only for communicating over network that this change uses the same objects for both
    private boolean changesTiming;


    /*
        MOST IMPORTANT:
        ADDING OBJECTS
        the keys of added objects MUST be synchronized
        this is an issue if server and client both try adding objects before this change is communicated to the other

        (When adding an object to map with methods in the class, if its key is the default value, assign it a key and add to map.)

        server is fine as it is authoritative
        client has to deal with it:
        first, if the change from server is accepted (it has to be)

        all following changes are cancelled
        "added" objects in cancelled changes should have their keys set to default value, and be removed from the map.
        the "initial key" should be conveyed with the change, and they key counter is set to that value.
        add objects from the server change (will use that key)

        the subsequent changes are re-done (if still valid)
        when re-added, since their keys have been reset to default, the added objects will receive new keys of the proper order.

        Required information for an addition:
        if adding a new single object, basic object parameters

        copy and paste/cut and paste - keys of the objects being copied. This information is currently not provided to this class.
     */

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


    /**
     * Constructor removing a single object.
     * @param map
     * @param removeObject
     */
    public MapObjectChange(EditorBeatmap map, String nameKey, MapObject removeObject) {
        this(map, nameKey, makeMap(removeObject));
    }

    /**
     * Constructor removing multiple objects.
     * @param map
     * @param removeObjects
     */
    public MapObjectChange(EditorBeatmap map, String nameKey, MapObjectTreeMap<MapObject> removeObjects) {
        this(map, nameKey, removeObjects, (MapObjectTreeMap<MapObject>) null);
    }

    /**
     * Constructor adding a single object.
     * @param map
     * @param addObject
     * @param shouldReplace
     */
    public MapObjectChange(EditorBeatmap map, String nameKey, MapObject addObject, BiFunction<MapObject, MapObject, Boolean> shouldReplace) {
        this(map, nameKey, makeMap(addObject), shouldReplace);
    }

    /**
     * Constructor adding multiple objects.
     * @param map
     * @param addObjects
     * @param shouldReplace
     */
    public MapObjectChange(EditorBeatmap map, String nameKey, MapObjectTreeMap<MapObject> addObjects, BiFunction<MapObject, MapObject, Boolean> shouldReplace) {
        super(map, nameKey);

        this.addObjects = addObjects; //addObjects is objects to add in their new positions
        if (addObjects.isEmpty()) {
            removeObjects = empty;
            return;
        }

        changesTiming = false;

        if (addObjects.firstEntry().getValue().get(0) instanceof TimingPoint) {
            changeType = LINE_CHANGE;
            outer:
            for (Map.Entry<Long, ArrayList<MapObject>> obj : this.addObjects.entrySet()) {
                for (MapObject o : obj.getValue()) {
                    if (((TimingPoint) o).uninherited) {
                        changesTiming = true;
                        break outer;
                    }
                }
            }
        }
        else {
            changeType = OBJECT_CHANGE;
        }

        removeObjects = getReplacements(map, this.addObjects, shouldReplace);

        invalidateSelection = true;
    }

    public MapObjectChange(EditorBeatmap map, String nameKey, MapObjectTreeMap<MapObject> removeObjects, MapObjectTreeMap<MapObject> addObjects)
    {
        super(map, nameKey);

        this.removeObjects = removeObjects == null ? empty : removeObjects; //removeObjects contains objects to remove with the key being the original positions
        this.addObjects = addObjects == null ? empty : addObjects; //addObjects is objects to add in their new positions

        if (this.removeObjects.isEmpty() && this.addObjects.isEmpty()) return;

        changesTiming = false;

        ArrayList<MapObject> checkList = this.addObjects.isEmpty() ? this.removeObjects.firstEntry().getValue() : this.addObjects.firstEntry().getValue();
        if (checkList.get(0) instanceof TimingPoint) {
            changeType = LINE_CHANGE;
            outer:
            for (Map.Entry<Long, ArrayList<MapObject>> obj : this.addObjects.isEmpty() ? this.removeObjects.entrySet() : this.addObjects.entrySet()) {
                for (MapObject o : obj.getValue()) {
                    if (((TimingPoint) o).uninherited) {
                        changesTiming = true;
                        break outer;
                    }
                }
            }
        }
        else {
            changeType = OBJECT_CHANGE;
        }

        if (!this.addObjects.isEmpty() && this.addObjects.count() == this.removeObjects.count()) {
            movingObjects = this.addObjects.firstEntry().getValue().containsAll(this.removeObjects.firstEntry().getValue());
        }

        invalidateSelection = true;
    }

    @Override
    public void undo() {
        switch (changeType)
        {
            case OBJECT_CHANGE:
                map.removeObjects(addObjects);

                for (Map.Entry<Long, ArrayList<MapObject>> obj : removeObjects.entrySet()) {
                    for (MapObject o : obj.getValue()) {
                        o.setPos(obj.getKey());
                    }
                }

                map.addObjects(removeObjects);
                map.finalizeBreaks();
                break;
            case LINE_CHANGE:
                map.timingPoints.removeAll(addObjects);
                map.effectPoints.removeAll(addObjects);
                map.allPoints.removeAll(addObjects);

                //adjust and put points back into maps
                for (Map.Entry<Long, ArrayList<MapObject>> obj : removeObjects.entrySet()) {
                    for (MapObject o : obj.getValue()) {
                        o.setPos(obj.getKey());
                        if (((TimingPoint) o).uninherited) {
                            map.timingPoints.add((TimingPoint) o);
                        }
                        else {
                            map.effectPoints.add((TimingPoint) o);
                        }
                    }
                }
                map.allPoints.addAll(removeObjects);

                if (changesTiming)
                    map.regenerateDivisor();

                map.updateLines(removeObjects.entrySet(), addObjects.entrySet());
                map.gameplayChanged();
                break;
        }

        map.gameplayChanged();
    }
    @Override
    public void perform() {
        switch (changeType)
        {
            case OBJECT_CHANGE:
                map.removeObjects(removeObjects);

                for (Map.Entry<Long, ArrayList<MapObject>> obj : addObjects.entrySet()) {
                    for (MapObject o : obj.getValue()) {
                        o.setPos(obj.getKey());
                    }
                }

                map.addObjects(addObjects);
                map.finalizeBreaks();
                break;
            case LINE_CHANGE:
                map.timingPoints.removeAll(removeObjects);
                map.effectPoints.removeAll(removeObjects);
                map.allPoints.removeAll(removeObjects);

                boolean changesTiming = false;

                //adjust and put points back into maps
                for (Map.Entry<Long, ArrayList<MapObject>> obj : addObjects.entrySet()) {
                    for (MapObject o : obj.getValue()) {
                        o.setPos(obj.getKey());
                        if (((TimingPoint) o).uninherited) {
                            map.timingPoints.add((TimingPoint) o);
                            map.allPoints.add((TimingPoint) o);
                            changesTiming = true;
                        }
                        else {
                            map.effectPoints.add((TimingPoint) o);
                            map.allPoints.add((TimingPoint) o);
                        }
                    }
                }

                if (changesTiming)
                    map.regenerateDivisor();

                map.updateLines(addObjects.entrySet(), removeObjects.entrySet());
                break;
        }

        map.gameplayChanged();
    }

    public MapChange confirm() {
        switch (changeType) {
            case OBJECT_CHANGE:
                //Set to original positions, then perform normal "perform"
                for (Map.Entry<Long, ArrayList<MapObject>> e : removeObjects.entrySet())
                {
                    e.getValue().forEach((o)->o.setPos(e.getKey()));
                }

                map.removeObjects(removeObjects);
                map.objects.removeAll(addObjects);

                for (Map.Entry<Long, ArrayList<MapObject>> obj : addObjects.entrySet()) {
                    for (MapObject o : obj.getValue()) {
                        o.setPos(obj.getKey());
                    }
                }

                map.addObjects(addObjects);
                map.finalizeBreaks();
                break;
            case LINE_CHANGE:
                if (changesTiming)
                    map.regenerateDivisor();
                //updateLines only cares about the key in PositionalMap and not object's actual position
                map.updateLines(addObjects.entrySet(), removeObjects.entrySet());
                break;
        }

        map.gameplayChanged();
        return this;
    }


    private static MapObjectTreeMap<MapObject> makeMap(MapObject o) {
        MapObjectTreeMap<MapObject> map = new MapObjectTreeMap<>();
        map.add(o);
        return map;
    }

    private MapObjectTreeMap<MapObject> getReplacements(EditorBeatmap map, MapObjectTreeMap<MapObject> addObjects, BiFunction<MapObject, MapObject, Boolean> shouldReplace) {
        MapObjectTreeMap<MapObject> replaced = new MapObjectTreeMap<>();

        switch (changeType) {
            case OBJECT_CHANGE:
                for (Map.Entry<Long, ArrayList<MapObject>> addedStack : addObjects.entrySet())
                {
                    ArrayList<HitObject> replaceStack = map.objects.get(addedStack.getKey());
                    if (replaceStack != null) {
                        outer: for (HitObject h : replaceStack) {
                            for (MapObject n : addedStack.getValue()) {
                                if (shouldReplace.apply(n, h)) {
                                    //If any of the added objects should replace the existing object, replace it
                                    replaced.add(h);
                                    continue outer;
                                }
                            }
                        }
                    }
                }
                break;
            case LINE_CHANGE:
                for (Map.Entry<Long, ArrayList<MapObject>> addedStack : addObjects.entrySet())
                {
                    ArrayList<TimingPoint> replaceStack = map.allPoints.get(addedStack.getKey());
                    if (replaceStack != null) {
                        outer: for (TimingPoint point : replaceStack) {
                            for (MapObject o : addedStack.getValue()) {
                                if (o instanceof TimingPoint && point.uninherited == ((TimingPoint) o).uninherited && shouldReplace.apply(point, o)) {
                                    replaced.add(point);
                                    continue outer;
                                }
                            }
                        }
                    }
                }
                break;
        }

        return replaced;
    }
}