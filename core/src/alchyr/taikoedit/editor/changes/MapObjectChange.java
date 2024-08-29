package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.editor.maps.components.hitobjects.Hit;
import alchyr.taikoedit.editor.maps.components.hitobjects.Slider;
import alchyr.taikoedit.editor.maps.components.hitobjects.Spinner;
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
import java.util.function.BiFunction;
import java.util.function.Supplier;


public class MapObjectChange extends MapChange {
    private static final MapObjectTreeMap<MapObject> empty = new MapObjectTreeMap<>();
    public static final int OBJECT_CHANGE = 0;
    public static final int LINE_CHANGE = 1;

    private int changeType;
    private final MapObjectTreeMap<MapObject> removeObjects;
    private final MapObjectTreeMap<MapObject> addObjects;

    private boolean keyed = false; //set to true after first "perform"

    //for communicating
    private boolean newObjects = false, movingObjects = false;
    private final Map<MapObject, MapObject> copyObjects;
    //newObjects generally means a single new object. Sends info to construct object.
    //copyObjects means objects copied from existing objects. Sends keys of original objects and new positions.
    //movingObjects means removedObjects are the same as added, just with different keys.
    private boolean changesTiming;


    /*
        MOST IMPORTANT:
        ADDING OBJECTS
        the keys of added objects MUST be synchronized
        this is an issue if server and client both try adding objects before this change is communicated to the other

        object keys are stored as integers.
        Currently client limit is set to 8.
        Each client will start the numbering of objects *added* by them at a different point.

        This number offset is passed to the beatmap when adding objects.
        Starting from Integer.MIN_VALUE is the keys of auto-generated objects - those already in the map.
        from there, server and each additional client has an offset of 333333. This gives enough room for a bit more than 8 clients.
        If someone adds more than 300000 objects... I don't think the editor would handle it well anyways, let alone in online editing.

        server is fine as it is authoritative
        client has to deal with it:
        first, if the change from server is accepted (it has to be)

        Required information for an addition:
        if adding a new single object, basic object parameters

        copy and paste/cut and paste - keys of the objects being copied. This information is currently not provided to this class.
     */

    private static final byte EXISTING_OBJECTS = 0;
    private static final byte NEW_OBJECTS = 1;
    private static final byte MOVE_OBJECTS = 2;
    private static final byte COPY_OBJECTS = 3;

    @Override
    public void send(DataOutputStream out) throws IOException {
        writeObjects(out, removeObjects.count(), map.objects, removeObjects.singleValuesIterator());

        if (newObjects) {
            out.write(NEW_OBJECTS);
            out.writeInt(addObjects.count());
            for (Map.Entry<Long, ArrayList<MapObject>> stack : addObjects.entrySet()) {
                for (MapObject obj : stack.getValue()) {
                    writeNewObject(out, stack.getKey(), obj);
                }
            }
        }
        else if (copyObjects != null) {
            out.write(COPY_OBJECTS);
            out.writeInt(addObjects.count());
            for (Map.Entry<Long, ArrayList<MapObject>> stack : addObjects.entrySet()) {
                for (MapObject obj : stack.getValue()) {
                    MapObject orig = copyObjects.get(obj);
                    writeObject(out, orig);
                    out.writeInt(obj.key);
                }
            }
            out.writeInt(addObjects.size());
            for (Map.Entry<Long, ArrayList<MapObject>> stack : addObjects.entrySet()) {
                out.writeLong(stack.getKey());
            }
        }
        else if (movingObjects) {
            out.write(MOVE_OBJECTS);
            //addObjects.count must be the same as removeObjects count
            for (Map.Entry<Long, ArrayList<MapObject>> stack : addObjects.entrySet()) {
                for (MapObject obj : stack.getValue()) {
                    out.writeInt(obj.key); //Theoretically I believe I could get away with just the positions, but I don't want to rely on remove and add object ordering being exactly the same
                    out.writeLong(stack.getKey());
                    //Really, I *should* able to do it per-stack? I don't *think* I have any way of moving two objects in the same stack to different positions at the same time.
                    //But, the cost for doing this is pretty negligible...
                    //meh
                    //No wait, there is *one* way. Reversing (ctrl+g) a long object stacked on an object of a different length.
                }
            }
        }
        else {
            out.write(EXISTING_OBJECTS);
            writeObjects(out, addObjects.count(), map.objects, addObjects.singleValuesIterator());
        }
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        KnownAmountSupplier<List<MapObject>> removeObjects = readObjects(in, map);
        int type = in.read();
        if (removeObjects == null) return null;

        if (type == NEW_OBJECTS) {
            int amt = in.readInt();
            List<Supplier<MapObject>> newObjectSuppliers = new ArrayList<>();
            for (int i = 0; i < amt; ++i) {
                newObjectSuppliers.add(readNewObject(in));
            }

            return ()->{
                MapObjectTreeMap<MapObject> removeObjectsMap = new MapObjectTreeMap<>();
                removeObjects.get().forEach(removeObjectsMap::add);

                MapObjectTreeMap<MapObject> addObjectsMap = new MapObjectTreeMap<>();
                newObjectSuppliers.forEach((s)->addObjectsMap.add(s.get()));

                MapObjectChange change = new MapObjectChange(map, nameKey, removeObjectsMap, addObjectsMap);
                change.newObjects = true; //Shouldn't be necessary, but I want to set it anyways.
                return change;
            };
        }
        else if (type == COPY_OBJECTS) {
            int count = in.readInt();
            List<Supplier<MapObject>> origObjects = new ArrayList<>(count);
            List<Integer> copyKeys = new ArrayList<>(count);
            for (int i = 0; i < count; ++i) {
                origObjects.add(readObject(in, map));
                copyKeys.add(in.readInt());
            }
            count = in.readInt();
            List<Long> copyStackPositions = new ArrayList<>(count);
            for (int i = 0; i < count; ++i) {
                copyStackPositions.add(in.readLong());
            }

            return ()->{
                MapObjectTreeMap<MapObject> removeObjectsMap = new MapObjectTreeMap<>();
                removeObjects.get().forEach(removeObjectsMap::add);

                MapObjectTreeMap<MapObject> addObjectsMap = new MapObjectTreeMap<>();
                long stackPos = Long.MIN_VALUE;
                int stackIndex = -1;

                for (int i = 0; i < origObjects.size(); ++i) {
                    MapObject original = origObjects.get(i).get();
                    if (stackIndex == -1 || stackPos != original.getPos()) {
                        ++stackIndex;
                        stackPos = original.getPos();
                        if (stackIndex >= copyStackPositions.size()) {
                            logger.warn("RAN OUT OF STACK POSITIONS FOR COPY OBJECTS");
                            return null;
                        }
                    }
                    MapObject copy = original.shiftedCopy(copyStackPositions.get(stackIndex));
                    copy.key = copyKeys.get(i);
                    addObjectsMap.add(copy);
                }

                return new MapObjectChange(map, nameKey, removeObjectsMap, addObjectsMap);
            };
        }
        else if (type == MOVE_OBJECTS) {
            int amt = removeObjects.getAmount();
            Map<Integer, Long> newPositions = new HashMap<>();
            for (int i = 0; i < amt; ++i) {
                int objKey = in.readInt();
                long newPos = in.readLong();
                newPositions.put(objKey, newPos);
            }

            return ()->{
                MapObjectTreeMap<MapObject> removeObjectsMap = new MapObjectTreeMap<>();
                MapObjectTreeMap<MapObject> newPositionMap = new MapObjectTreeMap<>();
                removeObjects.get().forEach((obj)->{
                    removeObjectsMap.add(obj);
                    newPositionMap.addKey(newPositions.get(obj.key), obj);
                });

                return new MapObjectChange(map, nameKey, removeObjectsMap, newPositionMap);
            };
        }
        else if (type == EXISTING_OBJECTS) { //Probably just a removal?
            KnownAmountSupplier<List<MapObject>> addObjects = readObjects(in, map);
            if (addObjects == null) return null;

            return ()->{
                MapObjectTreeMap<MapObject> removeObjectsMap = new MapObjectTreeMap<>();
                removeObjects.get().forEach(removeObjectsMap::add);

                MapObjectTreeMap<MapObject> addObjectsMap = new MapObjectTreeMap<>();
                addObjects.get().forEach(addObjectsMap::add);

                return new MapObjectChange(map, nameKey, removeObjectsMap, addObjectsMap);
            };
        }
        else {
            logger.warn("IF THEY AREN'T NEW OR MOVED OR EXISTING, WHAT ARE THEY?!");
        }

        return null;
    }

    private void writeNewObject(DataOutputStream out, long pos, MapObject obj) throws IOException {
        out.writeInt(obj.key);
        out.writeLong(pos);
        if (obj instanceof TimingPoint) { //timing points are *almost* a copy, but not quite. Easier to just do this.
            out.writeBoolean(true);
            out.writeUTF(obj.toString());
        }
        else if (obj instanceof HitObject) { //New objects should be those added through tools which have very limited properties.
            out.writeBoolean(false);
            switch (((HitObject) obj).type) {
                case CIRCLE:
                    out.write(0);
                    out.writeBoolean(((Hit) obj).isRim());
                    out.writeBoolean(((HitObject) obj).isFinish());
                    break;
                case SLIDER:
                    out.write(1);
                    out.writeLong(((Slider) obj).getDuration());
                    out.writeBoolean(((HitObject) obj).isFinish());
                    break;
                case SPINNER:
                    out.write(2);
                    out.writeLong(((Spinner) obj).getDuration());
                    break;
            }
        }
        else {
            logger.warn("CANNOT SEND UNKNOWN TYPE OF MAPOBJECT " + obj);
        }
    }

    private static Supplier<MapObject> readNewObject(DataInputStream in) throws IOException {
        int key = in.readInt();
        long pos = in.readLong();
        if (in.readBoolean()) {
            //timing point
            String data = in.readUTF();
            return ()->{
                TimingPoint t = new TimingPoint(data);
                t.setPos(pos);
                t.key = key;
                return t;
            };
        }
        else {
            //hitobject
            int type = in.read();
            switch (type) {
                case 0: //hit
                    boolean hitIsRim = in.readBoolean();
                    boolean hitIsFinish = in.readBoolean();
                    return ()->{
                        Hit h = new Hit(pos, hitIsRim);
                        h.setIsFinish(hitIsFinish);
                        h.key = key;
                        return h;
                    };
                case 1: //slider
                    long sliderDuration = in.readLong();
                    boolean sliderIsFinish = in.readBoolean();
                    return ()->{
                        Slider sl = new Slider(pos, sliderDuration);
                        sl.setIsFinish(sliderIsFinish);
                        sl.key = key;
                        return sl;
                    };
                case 2: //spinner
                    long spinnerDuration = in.readLong();
                    return ()->{
                        Spinner sp = new Spinner(pos, spinnerDuration);
                        sp.key = key;
                        return sp;
                    };
                default:
                    logger.warn("UNKNOWN HITOBJECT TYPE: " + type);
                    break;
            }
        }
        return null;
    }

    @Override
    public boolean isValid() {
        if (changeType == LINE_CHANGE) {
            for (Map.Entry<Long, ArrayList<MapObject>> stack : removeObjects.entrySet()) {
                for (MapObject o : stack.getValue()) {
                    if (!map.allPoints.containsKeyedValue(stack.getKey(), o)) {
                        return false;
                    }
                }
            }
        }
        else {
            for (Map.Entry<Long, ArrayList<MapObject>> stack : removeObjects.entrySet()) {
                for (MapObject o : stack.getValue()) {
                    if (!map.objects.containsKeyedValue(stack.getKey(), o)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /*@Override
    public void cancel() {
        //set keys of *added* objects back to default (-1)
    }*/

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
        this(map, nameKey, makeMap(addObject), null, shouldReplace);
    }

    /**
     * Constructor adding multiple objects, connected to any change that adds objects.
     * @param map
     * @param addObjects
     * @param shouldReplace
     */
    public MapObjectChange(EditorBeatmap map, String nameKey, MapObjectTreeMap<MapObject> addObjects, Map<MapObject, MapObject> originalObjects, BiFunction<MapObject, MapObject, Boolean> shouldReplace) {
        super(map, nameKey);

        newObjects = true;

        this.addObjects = addObjects; //addObjects is objects to add in their new positions
        this.copyObjects = originalObjects; //added object -> original object
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
        this.copyObjects = null;

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
        if (!addObjects.isEmpty() && !keyed) {
            keyed = true;
            map.keyMapObjects(addObjects);
        }

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