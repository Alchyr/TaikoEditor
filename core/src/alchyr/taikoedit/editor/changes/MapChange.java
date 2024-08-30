package alchyr.taikoedit.editor.changes;

import alchyr.networking.standard.ConnectionClient;
import alchyr.networking.standard.ConnectionServer;
import alchyr.networking.standard.Message;
import alchyr.networking.standard.MessageSender;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.interfaces.KnownAmountSupplier;
import alchyr.taikoedit.util.structures.BranchingStateQueue;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

//For undo support
public abstract class MapChange extends BranchingStateQueue.StateChange {
    protected static final Logger logger = LogManager.getLogger("MapChange");
    public static final int MAP_CHANGE = 0x11;
    public static final int MAP_CHANGE_DENIAL = 0x12;
    public static final int MAP_STATE_CHANGE = 0x13;

    private static final Map<Class<? extends MapChange>, Integer> mapChangeTypeIDs = new HashMap<>();
    private static final Map<Integer, ChangeBuilderBuilder> mapChangeBuilderBuilders = new HashMap<>();

    private static int changeTypeKey = 0;
    private static <T extends MapChange> void registerMapChange(Class<T> clz, ChangeBuilderBuilder builder) {
        //function takes parameters received from network and converts back into mapchange
        //Integer is "ID" of change
        int key = changeTypeKey++;
        mapChangeTypeIDs.put(clz, key);
        mapChangeBuilderBuilders.put(key, builder);
    }

    public static void registerMapChanges() {
        registerMapChange(BreakAdjust.class, BreakAdjust::build);
        registerMapChange(BreakRemoval.class, BreakRemoval::build);
        registerMapChange(DurationChange.class, DurationChange::build);
        registerMapChange(FinisherChange.class, FinisherChange::build);
        registerMapChange(KiaiChange.class, KiaiChange::build);
        registerMapChange(MapObjectChange.class, MapObjectChange::build);
        registerMapChange(RepositionChange.class, RepositionChange::build);
        registerMapChange(RimChange.class, RimChange::build);
        registerMapChange(ValueModificationChange.class, ValueModificationChange::build);
        registerMapChange(ValueSetChange.class, ValueSetChange::build);
        registerMapChange(VolumeModificationChange.class, VolumeModificationChange::build);
        registerMapChange(VolumeSetChange.class, VolumeSetChange::build);
    }

    //change on server -> sent to client -> client can send desync report if failure

    /*
    Change made -> Change is registered
    send change
    if server: server's clients receives message. How handle message?
    if client: receives message, messagehandler handles message


     */

    //add getAffectedObjects method, reject changes from clients if it affects an object affected by change accepted by server that hasn't been "confirmed" by all clients

    //SERVER OPTION: Force same views (otherwise, people can use different stuff)?
    // maybe with sub-option of "Clients cannot open or close views"
    //While server is open, clicking button will open menu to Close or change options

    //Information needed to transfer a map change:
    //which mapchange to make (int ID, register all changes)
    //Stuff in constructors:
    //Object selection
    //convey objects by pairs of long+int? time+index in stack?
    //Maybe 3 things, type -> pos -> index (differentiate between hitobject, green line, red line

    //Other Parameters - longs/bools/stuff




    public static void sendMapChange(ConnectionClient client, int stateKey, MapChange change) {
        logger.info("Sending map change: " + change.name);
        client.send(MAP_CHANGE, stateKey, change);
    }

    public static void sendMapStateChange(ConnectionClient client, EditorBeatmap map, int oldStateKey, int newStateKey) {
        logger.info("Sending map state change");
        client.send(MAP_STATE_CHANGE, map, oldStateKey, newStateKey);
    }

    public static void registerEditorForChanges(EditorLayer editor) {
        ConnectionClient.
                registerMessageType(MAP_CHANGE, (params)->new MessageSender(params) {
                    @Override
                    public void send(DataOutputStream out, Object[] params) throws IOException {
                        int stateKey = (int) params[0];
                        MapChange change = (MapChange) params[1];

                        out.write(MAP_CHANGE); //message type

                        out.write(mapChangeTypeIDs.get(change.getClass()));

                        out.writeInt(stateKey);

                        out.writeUTF(change.name);
                        out.writeUTF(change.map.getName());

                        change.send(out);

                        out.flush();
                    }
                },
                (in)->{
                    ChangeBuilderBuilder builder = mapChangeBuilderBuilders.get(in.read());

                    int stateKey = in.readInt();

                    String nameKey = in.readUTF();
                    String mapName = in.readUTF();

                    EditorBeatmap map = null;
                    for (EditorBeatmap maybeMap : editor.getActiveMaps()) {
                        if (maybeMap.getName().equals(mapName)) {
                            map = maybeMap;
                            break;
                        }
                    }

                    if (map == null) {
                        logger.warn("Received " + nameKey + " for unknown difficulty: " + mapName);
                        //null change + change index and branch = failed change
                        return new Message(Message.UTF, ConnectionServer.EVENT_SENT + "FAIL|Desync: Change made on unknown difficulty");
                    }

                    ChangeBuilder changeBuilder = new ChangeBuilder(builder.buildBuilder(map, in, nameKey), map, stateKey);
                    return new Message(MAP_CHANGE, changeBuilder);
                });

        ConnectionClient.
                registerMessageType(MAP_CHANGE_DENIAL, (params)->new MessageSender(params) {
                    @Override
                    public void send(DataOutputStream out, Object[] params) throws IOException {
                        out.write(MAP_CHANGE_DENIAL); //message type

                        EditorBeatmap map = (EditorBeatmap) params[0];

                        out.writeUTF(map.getName());
                        out.writeInt((int) params[1]); //changeIndex
                        out.writeInt((int) params[2]); //changeBranch

                        out.flush();
                    }
                },
                (in)->{
                    String mapName = in.readUTF();
                    int changeIndex = in.readInt();
                    int changeBranch = in.readInt();

                    EditorBeatmap map = null;
                    for (EditorBeatmap maybeMap : editor.getActiveMaps()) {
                        if (maybeMap.getName().equals(mapName)) {
                            map = maybeMap;
                            break;
                        }
                    }
                    if (map == null) {
                        logger.warn("Received rejected change for unknown difficulty: " + mapName);
                        //null change + changeID in second index means rejected change
                        return new Message(Message.UTF, ConnectionServer.EVENT_SENT + "FAIL|Desync: Change made on unknown difficulty");
                    }
                    return new Message(MAP_CHANGE_DENIAL, map, changeIndex, changeBranch);
                });

        ConnectionClient
                .registerMessageType(MAP_STATE_CHANGE, (params)->new MessageSender(params) {
                    @Override
                    public void send(DataOutputStream out, Object[] params) throws IOException {
                        out.write(MAP_STATE_CHANGE); //message type

                        EditorBeatmap map = (EditorBeatmap) params[0];

                        out.writeUTF(map.getName());
                        out.writeInt((int) params[1]); //Before stateKey

                        out.writeInt((int) params[2]); //Target stateKey

                        out.flush();
                    }
                },
                (in)->{
                    String mapName = in.readUTF();

                    int stateKey = in.readInt();
                    int newStateKey = in.readInt();

                    EditorBeatmap map = null;
                    for (EditorBeatmap maybeMap : editor.getActiveMaps()) {
                        if (maybeMap.getName().equals(mapName)) {
                            map = maybeMap;
                            break;
                        }
                    }
                    if (map == null) {
                        logger.warn("Received state change for unknown difficulty: " + mapName);
                        //null change + changeID in second index means rejected change
                        return new Message(Message.UTF, ConnectionServer.EVENT_SENT + "FAIL|Desync: Change made on unknown difficulty");
                    }
                    return new Message(MAP_STATE_CHANGE, map, stateKey, newStateKey);
                });
    }

    public final EditorBeatmap map;
    public boolean invalidateSelection = false; //Should be true for changes that would cause the PositionalObjectMap for selected objects to have incorrect positions NO LONGER USED

    private final String name;

    public MapChange(EditorBeatmap map, String nameKey)
    {
        this.map = map;

        this.name = nameKey; //If translated, this would access localization entry
    }

    public String getName() {
        return name;
    }

    public abstract void undo();
    /*public void cancel() {
        //After being undone, this method is called if a change is completely removed from changes.
    }*/

    public abstract void perform();

    public MapChange preDo() {
        perform();
        return this;
    }

    //public abstract void send(DataOutputStream out) throws IOException;
    public void send(DataOutputStream out) throws IOException {

    }

    /**
     * @return Whether or not the perform method can function as intended on the current map state.
     */
    public abstract boolean isValid();

    public MapChange reconstruct() {
        return this;
    }

    public static ChangeType getChangeType(MapObject o) {
        if (o instanceof TimingPoint) {
            if (((TimingPoint) o).uninherited)
                return ChangeType.RED_LINE;
            else
                return ChangeType.GREEN_LINE;
        }
        return ChangeType.OBJECTS;
    }

    public enum ChangeType {
        OBJECTS,
        GREEN_LINE,
        RED_LINE
    }


    //CHANGE -
    //Object communication: two formats, "selected" and "not selected"
    //"selected" communicates all objects, "not selected" means choose all objects them omit the specified ones.
    //Choose which to use based on number of objects vs number of objects in map. (use the one that would take less).
    //Mainly so that something like a ctrl+a + whatever isn't stupid.
    //Bleh just rely on being able to restore map to expected state at time of change.
    //use position + type rather than index (type being don/kat/spinner/slider)
    //spinner/slider also come with Length
    //and a "finisher" boolean is also always included
    protected static final int OBJ_MULTI = 1;
    protected static final int OBJ_EXCLUDE = 2;

    protected static final int OBJ_GAME = 1;
    protected static final int OBJ_TIMING = 2;

    protected static void writeObject(DataOutputStream out, MapObject obj) throws IOException {
        out.writeInt(obj.key);
    }

    protected static Supplier<MapObject> readObject(DataInputStream in, EditorBeatmap map) throws IOException {
        int key = in.readInt();
        return ()->map.mapObjectMap.get(key);
    }


    protected static void writeObjects(DataOutputStream out, int count, MapObjectTreeMap<?> sourceMap, Iterable<? extends MapObject> mapObjects) throws IOException {
        writeObjects(out, count, sourceMap, mapObjects.iterator());
    }
    protected static void writeObjects(DataOutputStream out, int count, MapObjectTreeMap<?> sourceMap, Iterator<? extends MapObject> mapObjects) throws IOException {
        if (count <= sourceMap.count() * 2 / 3) {
            out.write(OBJ_MULTI);
            out.writeInt(count);
            while (mapObjects.hasNext()) {
                MapObject obj = mapObjects.next();
                out.writeInt(obj.key);
            }
        }
        else {
            out.write(OBJ_EXCLUDE);

            Set<Integer> toWrite = new HashSet<>();
            sourceMap.forEachObject((obj)->toWrite.add(obj.key)); //get all objects from source map

            while (mapObjects.hasNext()) { //remove all objects that should be affected
                MapObject obj = mapObjects.next();
                toWrite.remove(obj.key);
            }

            out.writeInt(toWrite.size()); //number of objects that are excluded

            if (!sourceMap.isEmpty() && sourceMap.firstEntry().getValue().get(0) instanceof TimingPoint) {
                out.write(OBJ_TIMING);
            }
            else {
                out.write(OBJ_GAME);
            }

            for (Integer key : toWrite) {
                out.writeInt(key);
            }
        }
    }

    protected static KnownAmountSupplier<List<MapObject>> readObjects(DataInputStream in, EditorBeatmap map) throws IOException {
        int type = in.read();
        int amt = in.readInt();

        if (type == OBJ_MULTI) {
            List<Integer> keys = new ArrayList<>();
            for (int i = 0; i < amt; ++i) {
                keys.add(in.readInt());
            }
            return new KnownAmountSupplier<>(amt, ()->{
                List<MapObject> mapObjects = new ArrayList<>();
                for (int key : keys) {
                    MapObject obj = map.mapObjectMap.get(key);
                    if (obj == null) {
                        editorLogger.warn("Received invalid map object");
                        return null;
                    }
                    mapObjects.add(obj);
                }
                return mapObjects;
            });
        }
        else if (type == OBJ_EXCLUDE) {
            int objType = in.read();

            List<Integer> keys = new ArrayList<>();
            for (int i = 0; i < amt; ++i) {
                keys.add(in.readInt());
            }

            return new KnownAmountSupplier<>(amt, ()->{
                List<MapObject> mapObjects = new ArrayList<>();
                if (objType == OBJ_GAME) {
                    map.objects.forEachObject(mapObjects::add);
                }
                else {
                    map.allPoints.forEachObject(mapObjects::add);
                }

                for (int key : keys) {
                    MapObject obj = map.mapObjectMap.get(key);
                    if (obj == null) {
                        editorLogger.warn("Received invalid map object key");
                        return null;
                    }
                    if (!mapObjects.remove(obj)) {
                        editorLogger.warn("Received invalid map object (not currently in map)");
                        return null;
                    }
                }

                return mapObjects;
            });
        }
        else {
            editorLogger.warn("Tried to read multiple objects in unknown format");
            for (int i = 0; i < amt; ++i) {
                in.readInt();
            }
            return null;
        }
    }


    private interface ChangeBuilderBuilder {
        Supplier<MapChange> buildBuilder(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException;
    }

    public static class ChangeBuilder {
        public final EditorBeatmap map;
        private final Supplier<MapChange> builder;
        public final int stateKey;

        public ChangeBuilder(Supplier<MapChange> builder, EditorBeatmap map, int stateKey) {
            this.builder = builder;
            this.map = map;
            this.stateKey = stateKey;
        }

        public MapChange build() {
            try {
                return builder.get();
            }
            catch (Exception e) {
                editorLogger.error("Failed to build change", e);
            }
            return null;
        }
    }
}
