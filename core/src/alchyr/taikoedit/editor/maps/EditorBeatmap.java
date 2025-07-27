package alchyr.taikoedit.editor.maps;

import alchyr.networking.standard.ConnectionClient;
import alchyr.networking.standard.ConnectionServer;
import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.editor.BeatDivisors;
import alchyr.taikoedit.editor.DivisorOptions;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.editor.Timeline;
import alchyr.taikoedit.editor.changes.*;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.ILongObject;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.editor.maps.components.hitobjects.Slider;
import alchyr.taikoedit.editor.views.EffectView;
import alchyr.taikoedit.editor.views.GameplayView;
import alchyr.taikoedit.management.MapMaster;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.management.assets.FileHelper;
import alchyr.taikoedit.util.GeneralUtils;
import alchyr.taikoedit.util.structures.BranchingStateQueue;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;
import alchyr.taikoedit.util.structures.Pair;
import com.badlogic.gdx.utils.StreamUtils;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.function.BiFunction;

import static alchyr.taikoedit.TaikoEditor.editorLogger;
import static alchyr.taikoedit.TaikoEditor.music;

//Will be referenced by displays for rendering, and modifications in editor will be performed on it to ensure all displays are connected to the same map object
public class EditorBeatmap {
    private static final int BOOKMARK_REMOVE_DIST = 1000; //ms gap on either side of deletion attempt where a bookmark can be removed

    public boolean dirty = false; //Are there unsaved changes

    //For hitobjects/timing points use a structure that allows for fast find/insertion at the desired position but also fast iteration?
    public final MapObjectTreeMap<TimingPoint> timingPoints; //red lines
    public final MapObjectTreeMap<TimingPoint> effectPoints; //green lines
    public final MapObjectTreeMap<TimingPoint> allPoints;
    public final MapObjectTreeMap<HitObject> objects;

    public final HashMap<Integer, MapObject> mapObjectMap;
    private int objectKey = Integer.MIN_VALUE + 1;
    public int objectKeyOffset = 0;
    private static final int OBJECT_KEY_OFFSET_SPACING = 3333333; //Enough for... 1k+ clients. I don't think the editor could handle 3 million objects being placed (very well) anyways.

    private final TreeMap<Long, Integer> volumeMap;
    private final TreeMap<Long, Boolean> kiaiMap; //each boolean is a spot where kiai is turned on or off.

    public boolean autoBreaks = true; //set to false if invalid breaks on load, which will disable automatic modification of breaks.

    private BeatDivisors divisor;


    private FullMapInfo fullMapInfo;


    //For edit objects
    private NavigableMap<Long, ArrayList<HitObject>> editObjects;
    private long lastObjectStart = Long.MIN_VALUE, lastObjectEnd = Long.MIN_VALUE;

    private NavigableMap<Long, ArrayList<TimingPoint>> editPoints;
    private long lastEditPointStart = Long.MIN_VALUE, lastEditPointEnd = Long.MIN_VALUE;

    private NavigableMap<Long, ArrayList<TimingPoint>> editEffectPoints;
    private long lastEffectPointStart = Long.MIN_VALUE, lastEffectPointEnd = Long.MIN_VALUE;

    private NavigableMap<Long, ArrayList<TimingPoint>> editTimingPoints;
    private long lastTimingPointStart = Long.MIN_VALUE, lastTimingPointEnd = Long.MIN_VALUE;


    //NVM: When editing timing is implemented, add a hook "OnTimingChanged" which will be used by BeatDivisors to re-generate whatever values are necessary
    //This should also be used to ensure that if sameSong is true, timing changes will be kept synced with the other difficulties.
    //Not necessary, timing will never be implemented for offset reasons.


    private Timeline timeline = null;

    //Not the most "ideal" method, but it's quick and easy.
    private List<EffectView> effectViews = new ArrayList<>();
    private List<GameplayView> gameplayViews = new ArrayList<>();


    //Loading map from file
    public EditorBeatmap(Mapset set, MapInfo map)
    {
        timingPoints = new MapObjectTreeMap<>();
        effectPoints = new MapObjectTreeMap<>();
        allPoints = new MapObjectTreeMap<>();
        objects = new MapObjectTreeMap<>();
        mapObjectMap = new HashMap<>();

        volumeMap = new TreeMap<>();
        kiaiMap = new TreeMap<>();

        parse(set, map);

        keyMapObjects();
    }
    //Creating new map
    public EditorBeatmap(EditorBeatmap base, FullMapInfo map, boolean keepObjects, boolean keepSv, boolean keepVolume)
    {
        timingPoints = new MapObjectTreeMap<>();
        effectPoints = new MapObjectTreeMap<>();
        allPoints = new MapObjectTreeMap<>();
        objects = new MapObjectTreeMap<>();
        mapObjectMap = new HashMap<>();

        volumeMap = new TreeMap<>();
        kiaiMap = new TreeMap<>();

        this.fullMapInfo = map;

        for (Map.Entry<Long, ArrayList<TimingPoint>> points : base.timingPoints.entrySet()) {
            for (TimingPoint p : points.getValue()) {
                TimingPoint cpy = new TimingPoint(p);
                if (!keepVolume) {
                    cpy.volume = 100;
                    cpy.kiai = false;
                }
                timingPoints.add(cpy);
                volumeMap.put(cpy.getPos(), cpy.volume);
            }
        }

        if (keepSv && keepVolume) {
            for (Map.Entry<Long, ArrayList<TimingPoint>> points : base.effectPoints.entrySet()) {
                for (TimingPoint p : points.getValue()) {
                    effectPoints.add(new TimingPoint(p));
                    volumeMap.put(p.getPos(), p.volume);
                }
            }
        }
        else if (keepSv) {
            for (Map.Entry<Long, ArrayList<TimingPoint>> points : base.effectPoints.entrySet()) {
                for (TimingPoint p : points.getValue()) {
                    TimingPoint cpy = new TimingPoint(p);
                    cpy.volume = 100;
                    cpy.kiai = false;
                    effectPoints.add(new TimingPoint(cpy));
                    volumeMap.put(cpy.getPos(), cpy.volume);
                }
            }
        }
        else if (keepVolume) {
            //Keep volume/kiai changes but not sv changes.
            //It's easier to filter the unnecessary points out after combining into the allPoints map.
            for (Map.Entry<Long, ArrayList<TimingPoint>> points : base.effectPoints.entrySet()) {
                for (TimingPoint p : points.getValue()) {
                    TimingPoint cpy = new TimingPoint(p);
                    cpy.setValue(1);
                    effectPoints.add(new TimingPoint(cpy));
                }
            }
        }

        allPoints.addAll(timingPoints);
        allPoints.addAll(effectPoints);

        boolean kiaiActive = false;
        int lastVolume = Integer.MIN_VALUE;

        if (keepVolume && !keepSv) {
            Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> stackIterator = allPoints.entrySet().iterator();
            Iterator<TimingPoint> pointIterator;
            ArrayList<TimingPoint> nextStack;
            TimingPoint next;

            boolean stackKiai = false;

            while (stackIterator.hasNext()) {
                nextStack = stackIterator.next().getValue();

                if (nextStack != null) {
                    pointIterator = nextStack.iterator();

                    while (pointIterator.hasNext()) {
                        next = pointIterator.next();

                        if (!next.uninherited && next.volume == lastVolume && next.kiai == stackKiai) {
                            //Remove green lines with the same settings as the last non-removed line
                            pointIterator.remove();
                            effectPoints.removeObject(next);
                        }
                        else {
                            lastVolume = next.volume;
                            stackKiai = next.kiai;
                        }
                    }

                    if (nextStack.isEmpty()) {
                        stackIterator.remove();
                    }
                    else {
                        next = GeneralUtils.listLast(nextStack);
                        volumeMap.put(next.getPos(), next.volume);
                        if (next.kiai != kiaiActive) {
                            kiaiMap.put(next.getPos(), next.kiai);
                            kiaiActive = next.kiai;
                        }
                    }
                }
            }

            if (kiaiActive)
                kiaiMap.put((long) music.getMsLength(), false);
        }
        else {
            //generate kiai map
            for (Map.Entry<Long, ArrayList<TimingPoint>> stack : allPoints.entrySet()) {
                TimingPoint t = GeneralUtils.listLast(stack.getValue());

                if (t.kiai != kiaiActive) {
                    kiaiMap.put(t.getPos(), t.kiai);
                    kiaiActive = t.kiai;
                }
            }

            if (kiaiActive)
                kiaiMap.put((long) music.getMsLength(), false);
        }

        if (keepObjects) {
            for (Map.Entry<Long, ArrayList<HitObject>> stack : base.objects.entrySet()) {
                for (HitObject o : stack.getValue()) {
                    HitObject cpy = (HitObject) o.shiftedCopy(o.getPos());
                    objects.add(cpy);
                }
            }
        }
        else {
            fullMapInfo.breakPeriods.clear();
        }

        if (volumeMap.isEmpty()) {
            volumeMap.put(Long.MAX_VALUE, 60);
        }
        updateVolume(objects);

        keyMapObjects();
    }

    public FullMapInfo getFullMapInfo() {
        return fullMapInfo;
    }

    public void keyMapObjects() {
        this.mapObjectMap.clear();
        this.objectKey = Integer.MIN_VALUE + 1;

        objects.forEachObject((obj)->{
            obj.key = this.objectKey++;
            this.mapObjectMap.put(obj.key, obj);
        });
        allPoints.forEachObject((obj)->{
            obj.key = this.objectKey++;
            this.mapObjectMap.put(obj.key, obj);
        });
    }

    public void keyMapObjects(MapObjectTreeMap<MapObject> newObjects) {
        for (ArrayList<MapObject> stack : newObjects.values()) {
            for (MapObject obj : stack) {
                if (obj.key == Integer.MIN_VALUE) {
                    obj.key = this.objectKey++ + this.objectKeyOffset;
                }
                MapObject old = this.mapObjectMap.put(obj.key, obj);
                if (old != null && old != obj) {
                    editorLogger.warn("DUPLICATE OBJECT KEY: " + obj.key);
                }
            }
        }
    }

    public int getStateKey() {
        return changes.currentKey();
    }

    /* EDITING METHODS */
    private final BranchingStateQueue<MapChange> changes = new BranchingStateQueue<>();


    //Networorking
    private ConnectionServer server = null;
    private ConnectionClient client = null;

    public void setServer(ConnectionServer server) {
        this.server = server;
        if (server == null) {
            objectKeyOffset = 0;
        }
        else {
            objectKeyOffset = OBJECT_KEY_OFFSET_SPACING;
        }
    }
    public void setClient(ConnectionClient client) {
        this.client = client;
        if (client == null) {
            objectKeyOffset = 0;
        }
        else {
            objectKeyOffset = OBJECT_KEY_OFFSET_SPACING * (2 + client.ID);
        }
    }

    public BranchingStateQueue<MapChange> changeState() {
        return changes;
    }

    public void clearState() {
        changes.clear();
    }


    //sending redo/undo: "setstate" giving branch and depth
    public boolean canUndo() {
        return changes.canUndo();
    }
    public boolean undo()
    {
        int currentState = getStateKey();
        MapChange undone = changes.undo();
        if (undone != null) {
            dirty = true;

            if (server != null) {
                for (ConnectionClient client : server.getClients()) {
                    MapChange.sendMapStateChange(client, this, currentState, getStateKey());
                }
            }
            else if (client != null) {
                MapChange.sendMapStateChange(client, this, currentState, getStateKey());
            }

            return undone.invalidateSelection;
        }
        return false;
    }
    public boolean canRedo() {
        return changes.canRedo();
    }
    public boolean redo()
    {
        int currentState = getStateKey();
        MapChange redone = changes.redo();
        if (redone != null) {
            dirty = true;

            if (server != null) {
                for (ConnectionClient client : server.getClients()) {
                    MapChange.sendMapStateChange(client, this, currentState, getStateKey());
                }
            }
            else if (client != null) {
                MapChange.sendMapStateChange(client, this, currentState, getStateKey());
            }

            return redone.invalidateSelection;
        }
        return false;
    }

    public MapChange lastChange() {
        return changes.current();
    }

    public void registerChange(MapChange change) {
        registerChange(change, true);
    }
    public void registerChange(MapChange change, boolean send) {
        dirty = true;
        int stateKey = changes.addChange(change);

        if (!send) return;

        if (server != null) { //Server is authoritative, it's up to clients to match the server.
            for (ConnectionClient client : server.getClients()) {
                MapChange.sendMapChange(client, stateKey, change);
            }
        }
        else if (client != null) {
            MapChange.sendMapChange(client, stateKey, change);
        }
    }

    public void networkChangeState(ConnectionClient sourceClient, int stateKey, int newStateKey) {
        if (server != null) {
            int currentState = changes.currentKey();
            editorLogger.info("Received state change from client.");

            if (currentState != stateKey) {
                //this means a change on server end occurred.
                //This will be sent to client and should override the local state change of the client.
                editorLogger.info("State does not match. This state change will be ignored.");
                return;
            }

            editorLogger.info("Changing to state " + newStateKey);
            List<MapChange> undone = changes.changeState(newStateKey, true, false);
            if (undone == null) {
                //Failure
                this.client.fail("DESYNC: failed to return to expected state");
                return;
            }
            editorLogger.info("Changed state: " + changes.currentKey());

            for (ConnectionClient client : server.getClients()) {
                if (!client.equals(sourceClient)) {
                    MapChange.sendMapStateChange(client, this, currentState, newStateKey);
                }
            }
        }
        else if (this.client != null) {
            //First, move to expected state.
            dirty = true;

            int currentState = changes.currentKey();
            editorLogger.info("Received state change from server.");
            List<MapChange> undone;
            if (currentState != stateKey) {
                editorLogger.info("Not in expected state, changing to state " + stateKey);
                undone = changes.changeState(stateKey, true, true);
                if (undone == null) {
                    //Failure
                    this.client.fail("DESYNC: failed to return to expected state");
                    return;
                }
                //Any removed changes here are completely ignored.
                editorLogger.info("Changed state: " + changes.currentKey());
            }

            //now do the "actual" change
            editorLogger.info("Changing to state " + newStateKey);
            undone = changes.changeState(newStateKey, true, false);
            if (undone == null) {
                //Failure
                this.client.fail("DESYNC: failed to return to expected state");
                return;
            }
            editorLogger.info("Changed state: " + changes.currentKey());
        }
    }

    //Changes sent by clients happen on server When Received, and are then sent back to client with a changeIndex saying what their change index *should* be.
    //Changes sent by clients can be rejected.
    //If a change is rejected, it can be re-attempted by client with an updated changeIndex if it remains valid.
    public void receiveNetworkChange(ConnectionClient sourceClient, MapChange.ChangeBuilder changeBuilder) { //Note: undo/redo should be handled separately
        if (server != null) {
            //Working as server
            //First, move to expected state
            int currentState = changes.currentKey();
            editorLogger.info("Received change from client. Current state: " + currentState + " Expected state: " + changeBuilder.stateKey);
            List<MapChange> undone;

            if (currentState != changeBuilder.stateKey) {
                undone = changes.changeState(changeBuilder.stateKey, true, false);

                if (undone == null) {
                    //Failure
                    this.client.fail("DESYNC: failed to return to expected state");
                    return;
                }
                editorLogger.info("Changed state: " + changes.currentKey());
            }

            //Construct change. Verify that it's valid. It should be. If it isn't, send desync message (for now, just disconnect?)
            MapChange change = null;
            try {
                change = changeBuilder.build();
            }
            catch (Exception e) {
                editorLogger.warn("Failed to build change from server:", e);
            }

            if (currentState != changes.currentKey()) { //return to actual state
                undone = changes.changeState(currentState, true, false);

                if (undone == null) {
                    //Failure
                    this.client.fail("DESYNC: failed to return to expected state");
                    return;
                }
                editorLogger.info("Changed state: " + changes.currentKey());
            }

            if (change == null) {
                this.client.fail("DESYNC: change could not be processed");
                return;
            }
            if (!change.isValid()) {
                editorLogger.info("Change ignored; it is invalid");
                return;
            }
            change = change.reconstruct(); //Adjust change to be based on current state
            registerChange(change.preDo(), false);

            for (ConnectionClient client : server.getClients()) {
                if (client != sourceClient) {
                    MapChange.sendMapChange(client, currentState, change);
                }
            }

        }
        else if (this.client != null) {
            //Received change from server
            //First, move to expected state
            int currentState = changes.currentKey();
            editorLogger.info("Received change from server. Current state: " + currentState + " Expected state: " + changeBuilder.stateKey);
            List<MapChange> undone = null;

            if (currentState != changeBuilder.stateKey) {
                undone = changes.changeState(changeBuilder.stateKey, true, true);

                if (undone == null) {
                    //Failure
                    this.client.fail("DESYNC: failed to return to expected state");
                    return;
                }
                editorLogger.info("Changed state: " + changes.currentKey());
            }

            //Construct change. Verify that it's valid. It should be. If it isn't, send desync message (for now, just disconnect?)
            MapChange change = null;
            try {
                change = changeBuilder.build();
            }
            catch (Exception e) {
                editorLogger.warn("Failed to build change from server:", e);
            }
            if (change == null || !change.isValid()) {
                this.client.fail("DESYNC: change could not be processed or is invalid");
                return;
            }
            registerChange(change.preDo(), false);

            //Redo following changes, if they remain valid. (if moved to different branch all changes are automatically considered invalid)
            if (undone != null && !undone.isEmpty() && undone.get(0) != null) {
                editorLogger.info("Redoing changes.");
                for (MapChange toRedo : undone) {
                    if (!toRedo.isValid()) {
                        return;
                    }
                    toRedo = toRedo.reconstruct(); //Adjust change to be based on current state
                    registerChange(toRedo.preDo(), false); //This change was already sent; server will handle it.
                }
            }
        }
    }

    public void registerAndPerformAddObject(String nameKey, MapObject o, BiFunction<MapObject, MapObject, Boolean> shouldReplace)
    {
        registerChange(new MapObjectChange(this, nameKey, o, shouldReplace).preDo());
    }
    public void registerAndPerformAddObjects(String nameKey, MapObjectTreeMap<MapObject> addObjects, Map<MapObject, MapObject> originalObjects, BiFunction<MapObject, MapObject, Boolean> shouldReplace) {
        registerChange(new MapObjectChange(this, nameKey, addObjects, originalObjects, shouldReplace).preDo());
    }

    /**
     * Deletes objects/lines and adds to undo queue.
     * @param deletion Things to delete. This list should not be modified elsewhere.
     */
    public void registerAndPerformDelete(MapObjectTreeMap<MapObject> deletion)
    {
        registerChange(new MapObjectChange(this, deletion.count() == 1 ? "Delete Object" : "Delete Objects", deletion).preDo());
    }

    /**
     * Deletes an object/line.
     * @param o Thing to delete.
     */
    public void registerAndPerformDelete(MapObject o)
    {
        registerChange(new MapObjectChange(this, "Delete Object", o).preDo());
    }

    public void registerReverse(boolean resnap, MapObjectTreeMap<MapObject> toReverse) {
        MapObjectTreeMap<MapObject> reversed = new MapObjectTreeMap<>();

        Snap closest;
        TreeMap<Long, Snap> snaps = getAllSnaps();
        long start = toReverse.firstKey(), end = toReverse.lastKey(), newPos;

        for (Map.Entry<Long, ArrayList<MapObject>> entry : toReverse.entrySet())
        {
            for (MapObject o : entry.getValue())
            {
                newPos = end - (entry.getValue().get(0).getPos() - start);
                if (o instanceof ILongObject)
                {
                    newPos = newPos - ((ILongObject) o).getDuration();
                }

                if (resnap)
                {
                    closest = snaps.get(newPos);
                    if (closest == null)
                        closest = snaps.get(newPos + 1);
                    if (closest == null)
                        closest = snaps.get(newPos - 1);
                    if (closest != null)
                        newPos = closest.pos;
                }

                reversed.addKey(newPos, o);
            }
        }

        registerChange(new MapObjectChange(this, "Reverse Objects", toReverse, reversed).preDo());
    }
    public void registerAndPerformObjectMovement(MapObjectTreeMap<MapObject> movementObjects, long offset)
    {
        if (movementObjects.isEmpty()) return;

        boolean isLines = movementObjects.firstEntry().getValue().get(0) instanceof TimingPoint;
        MapObjectTreeMap<MapObject> newPositions = new MapObjectTreeMap<>();

        //store original positions in moved
        for (Map.Entry<Long, ArrayList<MapObject>> e : movementObjects.entrySet())
        {
            newPositions.put(e.getKey() + offset, e.getValue());
        }

        registerChange(new MapObjectChange(this, isLines ? "Move Lines" : "Move Objects", movementObjects, newPositions).preDo());
    }
    public void registerAndPerformDurationChange(ILongObject obj, long change)
    {
        registerChange(new DurationChange(this, obj, change).preDo());
    }
    public void registerAndPerformValueChange(MapObjectTreeMap<MapObject> modifiedObjects, Map<MapObject, Double> newValueMap)
    {
        registerChange(new ValueModificationChange(this, modifiedObjects, newValueMap).preDo());
        gameplayChanged();
    }
    public void registerAndPerformVolumeChange(MapObjectTreeMap<MapObject> modifiedObjects, Map<Long, Integer> newVolumeMap) {
        registerChange(new VolumeModificationChange(this, modifiedObjects, newVolumeMap).preDo());
    }


    public MapObjectTreeMap<MapObject> getStackedObjects(MapObjectTreeMap<? extends MapObject> base, MapObjectTreeMap<? extends MapObject> source) {
        MapObjectTreeMap<MapObject> allStacked = new MapObjectTreeMap<>();
        ArrayList<? extends MapObject> stack;
        for (Long k : base.keySet()) {
            stack = source.get(k);
            if (stack != null)
                allStacked.put(k, new ArrayList<>(stack));
        }

        return allStacked;
    }

    //General Data
    public NavigableSet<Integer> getBookmarks()
    {
        return fullMapInfo.bookmarks;
    }
    public void addBookmark(int time) {
        dirty = true;
        fullMapInfo.bookmarks.add(time);
    }
    public void removeBookmark(int time) {
        if (fullMapInfo.bookmarks.remove(time)) {
            dirty = true;
            return;
        }
        Integer floor = fullMapInfo.bookmarks.floor(time), ceil = fullMapInfo.bookmarks.ceiling(time);
        if (floor == null && ceil == null)
            return;

        if (floor == null) {
            if (ceil - time < BOOKMARK_REMOVE_DIST) {
                fullMapInfo.bookmarks.remove(ceil);
                dirty = true;
            }
        }
        else if (ceil == null || (time - floor < ceil - time)) {
            if (time - floor < BOOKMARK_REMOVE_DIST) {
                fullMapInfo.bookmarks.remove(floor);
                dirty = true;
            }
        }
        else {
            if (ceil - time < BOOKMARK_REMOVE_DIST) {
                fullMapInfo.bookmarks.remove(ceil);
                dirty = true;
            }
        }
    }

    //BREAK INFO
    //Minimum space for a break to fit is 850 + end delay.
    //The start of a break is (at least) 200 ms after an object's end.
    //Minimum duration of a break is 650.
    //Minimum distance from end of break to next object is end delay based on approach rate.
    public long getMinBreakDuration() {
        return 850 + getBreakEndDelay();
    }
    public long getBreakEndDelay() {
        if (fullMapInfo.ar == 5f) {
            return 1200;
        }
        else if (fullMapInfo.ar < 5f) {
            if (SettingsMaster.lazerSnaps) {
                return Math.round(1200 + 600 * (5 - fullMapInfo.ar) / 5.0);
            }
            else {
                return (long) Math.floor(1200 + 600 * (5 - fullMapInfo.ar) / 5.0);
            }
        }
        else if (SettingsMaster.lazerSnaps) {
            return Math.round(1200 - 750 * (fullMapInfo.ar - 5.0) / 5.0);
        }
        else {
            return (long) Math.floor(1200 - 750 * (fullMapInfo.ar - 5.0) / 5.0);
        }
    }
    public List<BreakInfo> getBreaks() { return fullMapInfo.breakPeriods; }




    //Divisors
    public int getDefaultDivisor()
    {
        return fullMapInfo.beatDivisor;
    }

    public void setDivisorObject(BeatDivisors divisor)
    {
        this.divisor = divisor;
    }
    public BeatDivisors generateDivisor(DivisorOptions divisorOptions)
    {
        return divisor = new BeatDivisors(divisorOptions, this);
    }
    public void regenerateDivisor() {
        regenerateDivisor(false);
    }
    public void regenerateDivisor(boolean immediate) {
        if (immediate) {
            divisor.reset();
        }
        else {
            TaikoEditor.onMain(divisor::reset);
        }
    }
    public NavigableMap<Long, Snap> getActiveSnaps(double startPos, double endPos)
    {
        return divisor.getSnaps(startPos, endPos);
    }
    //Currently in use snappings (visible or not)
    public TreeMap<Long, Snap> getCurrentSnaps()
    {
        return divisor.getSnaps();
    }
    //All snappings
    public TreeMap<Long, Snap> getAllSnaps()
    {
        return divisor.getAllSnaps();
    }
    public HashSet<Snap> getSnaps(int divisor)
    {
        return this.divisor.getCombinedSnaps(divisor);
    }
    public TreeMap<Long, Snap> getBarlineSnaps() { return divisor.getBarlines(); }




    //Objects
    public NavigableMap<Long, ArrayList<HitObject>> getEditObjects(long startPos, long endPos)
    {
        if (startPos != lastObjectStart || endPos != lastObjectEnd)
        {
            lastObjectStart = startPos;
            lastObjectEnd = endPos;
            editObjects = objects.extendedDescendingSubMap(startPos, endPos);
        }
        return editObjects;
    }
    public NavigableMap<Long, ArrayList<HitObject>> getEditObjects()
    {
        return editObjects;
    }
    public NavigableMap<Long, ArrayList<HitObject>> getSubMap(long startPos, long endPos)
    {
        return objects.descendingSubMap(startPos, true, endPos, true);
    }



    //Actually making changes to the map
    public void addObjects(MapObjectTreeMap<MapObject> addObjects) {
        if (autoBreaks) {
            for (Map.Entry<Long, ArrayList<MapObject>> toAdd : addObjects.entrySet()) {
                preAddObjectAdjustBreaks(toAdd.getKey(), toAdd.getValue());
                objects.addAll(toAdd.getValue());
            }
            updateVolume(addObjects);
        }
        else {
            objects.addAll(addObjects);
            updateVolume(addObjects);
        }
    }
    public void removeObjects(NavigableMap<Long, ArrayList<MapObject>> remove) {
        if (autoBreaks) {
            for (Map.Entry<Long, ArrayList<MapObject>> toRemove : remove.entrySet()) {
                preRemoveObjectAdjustBreaks(toRemove.getKey(), toRemove.getValue());
                objects.removeStack(toRemove.getKey(), toRemove.getValue());
            }
        }
        else {
            objects.removeAll(remove);
        }
    }

    /**
     * Updates the volume for multiple HitObjects
     * @param objects The objects to update. (PositionalObjectTreeMap is the expected collection, though others will work.)
     */
    public void updateVolume(NavigableMap<Long, ? extends ArrayList<? extends MapObject>> objects)
    {
        if (objects.isEmpty())
            return;

        Map.Entry<Long, Integer> volumeEntry = volumeMap.floorEntry(objects.firstKey());
        long nextPos = Integer.MIN_VALUE;
        Map.Entry<Long, Integer> next = volumeMap.higherEntry(objects.firstKey());
        if (next != null)
            nextPos = next.getKey();

        for (Map.Entry<Long, ? extends ArrayList<? extends MapObject>> entry : objects.entrySet())
        {
            while (next != null && nextPos <= entry.getKey())
            {
                volumeEntry = next;

                next = volumeMap.higherEntry(nextPos);
                if (next != null)
                    nextPos = next.getKey();
            }

            float volume = volumeEntry != null ? volumeEntry.getValue() / 100.0f : (next != null ? next.getValue() / 100.0f : 1.0f);

            for (MapObject h : entry.getValue())
                ((HitObject)h).volume = volume;
        }
    }

    /**
     * Updates the volume for a single HitObject.
     * @param h The HitObject to update.
     */
    public void updateVolume(HitObject h)
    {
        Map.Entry<Long, Integer> volumeEntry = volumeMap.floorEntry(h.getPos());
        if (volumeEntry == null)
            volumeEntry = volumeMap.ceilingEntry(h.getPos());
        h.volume = volumeEntry != null ? volumeEntry.getValue() / 100.0f : 1.0f;
    }

    /**
     * Updates volume of all HitObjects from a given position to the next timing point after given position
     * @param startPos The timestamp in milliseconds in the map from which to start updating objects.
     */
    public void updateVolume(long startPos) {
        Long end = allPoints.higherKey(startPos);
        if (end == null)
            end = Long.MAX_VALUE;
        Map.Entry<Long, Integer> volumeEntry = volumeMap.floorEntry(startPos);
        if (volumeEntry == null)
            volumeEntry = volumeMap.ceilingEntry(startPos);
        float volume = volumeEntry == null ? 1.0f : volumeEntry.getValue() / 100.0f;
        for (ArrayList<HitObject> stack : objects.subMap(startPos, true, end, false).values()) {
            for (HitObject o : stack) {
                o.volume = volume;
            }
        }
    }

    //Automatic break adjustments are not tracked for undo/redo.
    //Should be called before the object(s) are actually added.
    public void preAddObjectAdjustBreaks(long pos, ArrayList<MapObject> stack) {
        if (autoBreaks) {
            long endPos = getEnd(stack);
            ArrayList<HitObject> existing = objects.get(pos);
            if (existing != null && endPos == pos) return; //this spot already occupied, and the stack contains no long objects

            /*
            Things to check when an object is added:
            First - Is it in the middle of an existing break?
            If it is, remove that break.
            Second - If it is more than 5000 ms from preceding/following objects, generate necessary breaks.
             */

            boolean sort = false, inBreak = false;

            List<BreakInfo> breaks = getBreaks();
            int index = 0;
            BreakInfo breakSection;
            long breakObjectBefore, breakObjectAfter, breakStart, breakEnd;

            Map.Entry<Long, ArrayList<HitObject>> tempStack;

            while (index < breaks.size()) {
                breakSection = breaks.get(index);
                ++index; //pre-increment to avoid ever going below 0 if removing current break

                breakStart = breakSection.start;
                breakEnd = breakSection.end;

                tempStack = objects.ceilingEntry(breakEnd);
                breakObjectAfter = tempStack == null ? Long.MAX_VALUE : tempStack.getKey();

                if (pos >= breakObjectAfter) continue; //Added object is past this break

                tempStack = objects.floorEntry(breakStart);
                breakObjectBefore = tempStack == null ? Long.MIN_VALUE : getEnd(tempStack.getValue());

                if (endPos <= breakObjectBefore) break; //Added object ends before this break
                //Breaks are sorted by start time, so that means there cannot be any more affected breaks

                inBreak = true; //object overlaps with at least one break

                //Handle the "before" chunk
                long breakLength = pos - breakObjectBefore;
                if (breakLength < getMinBreakDuration()) {
                    //No room for a break here.
                    --index;
                    breaks.remove(index);
                }
                else if (breakLength < 5000 && breakSection.isTentative) {
                    //This was added during the same change; just remove it if not required.
                    --index;
                    breaks.remove(index);
                    continue; //And there should definitely not be anything added after.
                }
                else {
                    //Shrink existing break.

                    //There SHOULD be enough space for this.
                    //Push the end of the break back only as much as necessary,
                    //and adjust the start of the break again only if necessary.
                    breakSection.end = Math.min(breakEnd, pos - getBreakEndDelay());
                    if (breakSection.end - breakSection.start < 650) {
                        breakSection.start = breakSection.end - 650;
                    }
                }

                //Handle the "after" chunk
                if (endPos <= breakObjectAfter - getMinBreakDuration()) {
                    //There is room to keep a break here. Since this is a "split" of an existing break, mark as tentative if existing break was.
                    //If tentative, that means the before section was at least 5000 ms.
                    long start = Math.max(breakStart, endPos + 200), end = Math.min(Math.max(endPos + 850, breakEnd), breakObjectAfter - getBreakEndDelay());
                    breakLength = breakObjectAfter - endPos;

                    if (breakLength >= 5000 || !breakSection.isTentative) {
                        BreakInfo newBreak = new BreakInfo(start, end, breakSection.isTentative); //at least 850 ms away, but use current break ending pos if it's valid
                        breaks.add(index, newBreak);
                        ++index;
                        sort = true;
                    }

                }
            }

            if (!inBreak) {
                //If no break overlap - see if adding a break is necessary
                tempStack = objects.lowerEntry(pos);
                if (tempStack != null) {
                    long start = getEnd(tempStack.getValue());

                    if (pos - start >= 5000) {
                        breaks.add(new BreakInfo(start + 200, pos - getBreakEndDelay(), true));
                        sort = true;
                    }
                }

                tempStack = objects.higherEntry(endPos);

                if (tempStack != null) {
                    if (tempStack.getKey() - endPos >= 5000) {
                        breaks.add(new BreakInfo(endPos + 200, tempStack.getKey() - getBreakEndDelay(), true));
                        sort = true;
                    }
                }
            }

            if (sort) {
                sortBreaks();
            }
        }
    }

    //Should be called before objects are actually removed.
    /**
     * Perform a "reasonable" check for break generation
     * Meaning objects are assumed to not overlap, meaning deleting a long object cannot make more than one gap
     * illegally long gaps can get updated later anyways

     * First: Check for existing adjacent breaks. If one exists both before and after, combine.
     * Otherwise, adjust the existing one If it is at its normal position (minimum distance or less from deleted object)
     * If no existing break and the distance between before and after stack is at least 5000, generate tentative break.
     * @param pos position of objects that will be removed
     * @param stack list of objects that will be removed
     */
    public void preRemoveObjectAdjustBreaks(long pos, ArrayList<MapObject> stack) {
        if (autoBreaks) {
            long endPos = getEnd(stack);

            Map.Entry<Long, ArrayList<HitObject>> existing = objects.floorEntry(pos);
            if (existing == null || existing.getKey() != pos) return; //no change

            boolean clearingStack = stack.containsAll(existing.getValue());

            List<BreakInfo> breaks = getBreaks();
            Map.Entry<Long, ArrayList<HitObject>> beforeStack = objects.lowerEntry(pos), afterStack = objects.higherEntry(pos);
            BreakInfo beforeBreak = null, afterBreak = null;

            if (beforeStack != null || afterStack != null) { //if no object before or after, definitely no breaks
                for (int i = 0; i < breaks.size(); ++i) {
                    BreakInfo breakInfo = breaks.get(i);

                    if (beforeStack != null && breakInfo.end > beforeStack.getKey() && breakInfo.end < pos) {
                        //Break exists that this is the first object after
                        beforeBreak = breakInfo;
                        if (afterStack != null && i + 1 < breaks.size()) {
                            afterBreak = breaks.get(i + 1);
                            if (afterBreak.start < pos || afterBreak.start >= afterStack.getKey()) {
                                afterBreak = null;
                            }
                        }
                        break;
                    }
                    if (afterStack != null && breakInfo.start > pos) {
                        //Not using endpos above because for a "normal" case don't want to do anything about a break after the end of an overlapping long object
                        //Only want a break that is Definitely directly after this One stack
                        if (breakInfo.start < afterStack.getKey()) {
                            afterBreak = breakInfo;
                        }
                        break;
                    }
                }
            }

            if (clearingStack) { //Clearing the stack. Technically most removal *should* be doing this.
                if (beforeBreak != null && afterBreak != null) { //merge
                    beforeBreak.end = afterBreak.end;
                    breaks.remove(afterBreak);
                }
                else if (beforeBreak != null) {
                    //Adjust beforeBreak
                    if (afterStack == null) {
                        breaks.remove(beforeBreak);
                    }
                    else if (beforeBreak.end == pos - getBreakEndDelay()) {
                        //Minimum distance
                        beforeBreak.end = afterStack.getKey() - getBreakEndDelay();
                    }
                }
                else if (afterBreak != null) {
                    if (beforeStack == null) {
                        breaks.remove(afterBreak);
                    }
                    else if (afterBreak.start == endPos + 200) {
                        long beforeStackEnd = getEnd(beforeStack.getValue());
                        if (beforeStackEnd > endPos) //Illegal Bad long object
                            beforeStackEnd = endPos;
                        afterBreak.start = beforeStackEnd + 200;
                    }
                }
            }
            else if (endPos > pos && endPos == getEnd(existing.getValue())) {
                //deleting the longest object in the stack
                ArrayList<MapObject> tempStack = new ArrayList<>(existing.getValue());
                tempStack.removeAll(stack);
                long newEndPos = getEnd(tempStack);
                if (newEndPos < endPos) { //there Is a Change
                    if (afterBreak != null) {
                        if (afterBreak.start == endPos + 200) {
                            afterBreak.start = newEndPos + 200;
                        }
                    }
                    else {
                        //adjust so that break generation will occur (if necessary)
                        beforeBreak = null;
                        beforeStack = existing;
                    }
                }
            }

            //Generate tentative break, if necessary
            if (beforeBreak == null && afterBreak == null && beforeStack != null && afterStack != null) {
                endPos = getEnd(beforeStack.getValue());
                if (afterStack.getKey() - endPos >= 5000) {
                    breaks.add(new BreakInfo(endPos + 200, afterStack.getKey() - getBreakEndDelay(), true));
                    sortBreaks();
                }
            }
        }
    }

    /**
     * Should be called after the length of a long object is changed.
     * @param h The object that was changed.
     * @param changeAmount The amount by which the length was changed.
     */
    public void adjustedEnd(ILongObject h, long changeAmount) {
        if (autoBreaks && h instanceof HitObject) {
            //If made longer, make sure any break directly following this object is adjusted/removed if necessary.
            //If made shorter, add a break if necessary. Otherwise, do nothing.
            //Should be called after the change occurs.

            ArrayList<HitObject> stack = objects.get(((HitObject) h).getPos());
            if (stack != null) {
                long end = getEnd(stack);
                long origEnd = h.getEndPos() - changeAmount;
                Map.Entry<Long, ArrayList<HitObject>> followingEntry = objects.ceilingEntry(origEnd);

                if (end < h.getEndPos() - changeAmount) { //Got shorter
                    //If a break exists after the original ending position, adjust it.
                    if (followingEntry != null) { //Entry is the stack closest to the end of the long object.
                        for (BreakInfo breakSection : getBreaks()) {
                            if (breakSection.start > origEnd) {
                                if (breakSection.start < followingEntry.getKey()) {
                                    if (breakSection.start - origEnd == 200) { //No Extra Spacing
                                        breakSection.start = end + 200;
                                    }
                                    return;
                                }
                                break;
                            }
                        }
                        //No following break found
                        if (followingEntry.getKey() - end >= 5000) {
                            getBreaks().add(new BreakInfo(end + 200, followingEntry.getKey() - getBreakEndDelay())); //It is.
                            sortBreaks();
                        }
                    } //If entry is null, there can be no break after the object so nothing has to be done.
                }
                else if (changeAmount > 0 && end == h.getEndPos()) { //it got longer and Is the longest object
                    if (getBreaks().isEmpty()) {
                        return; //If it got longer, there's no need to add a break.
                    }
                    //All that has to be done is shorten/remove any breaks covered by the newly adjusted object.

                    long pos = end - changeAmount; //Start from the old endpoint and go until the new endpoint

                    Iterator<Map.Entry<Long, ArrayList<HitObject>>> objectIterator = objects.subMap(pos, false, end, false).entrySet().iterator();

                    //In most cases, there shouldn't be any objects. This code will only do anything if objects were covered.
                    Map.Entry<Long, ArrayList<HitObject>> entry = null, lastEntry;

                    Iterator<BreakInfo> breakIterator = getBreaks().iterator();
                    BreakInfo breakSection;

                    while (breakIterator.hasNext()) {
                        breakSection = breakIterator.next();

                        if (breakSection.end < pos) {
                            continue;
                        }

                        //This break is >= current pos.
                        entry = objects.floorEntry(breakSection.start);
                        if (entry == null) {
                            return; //It's Fucked
                        }
                        else {
                            //Adjust this break, if necessary.
                            long breakStart = getEnd(entry.getValue()), breakEnd;
                            if (breakStart > end) //This break is not next to/covered by
                                return;

                            entry = objects.ceilingEntry(breakSection.end);
                            if (entry != null) {
                                breakEnd = entry.getKey();
                            }
                            else {
                                return;
                            }

                            if (end > breakEnd - (850 + getBreakEndDelay())) {
                                //Totally covers this break.
                                breakIterator.remove(); //Remove it, move on.
                            }
                            else { //Only partially covers this break with valid break room remaining.
                                breakSection.start = Math.max(breakSection.start, end + 200);
                                if (breakSection.end < breakSection.start + 650)
                                    breakSection.end = breakSection.start + 650;
                            }
                        }
                    }
                }
            }
        }
    }

    public void finalizeBreaks() {
        for (BreakInfo breakSection : getBreaks()) {
            breakSection.isTentative = false;
        }
    }

    private static final HashSet<Long> updatePositions = new HashSet<>();
    private long removedPoint(long pos) {
        volumeMap.remove(pos); //remove volume point
        ArrayList<TimingPoint> timing = timingPoints.get(pos); //if there's still another line at that position
        if (timing != null)
            volumeMap.put(pos, timing.get(timing.size() - 1).volume); //put volume of that point

        Boolean removed = kiaiMap.remove(pos);
        if (removed != null) { // -1
            long cap = Math.max((long) music.getMsLength(), pos + 1);
            Map.Entry<Long, Boolean> kiaiEntry = kiaiMap.higherEntry(pos);
            if (kiaiEntry != null)
                cap = kiaiEntry.getKey();

            Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> points = allPoints.subMap(pos, false, cap, false).entrySet().iterator();
            TimingPoint next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;

            while (next != null) {
                if (next.kiai == removed) {
                    kiaiMap.put(next.getPos(), next.kiai); // -1 +1 = 0
                    break;
                }
                next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;
            }
            if (next == null) {
                //No swaps between. Just remove the higher entry.
                if (kiaiEntry != null) {
                    kiaiMap.remove(kiaiEntry.getKey()); //-1 -1 = -2
                }
                else {
                    if (!removed) { //removed a kiai end, no more points until end of map
                        kiaiMap.put(cap, false);
                    }
                }
            }
        }
        return pos;
    }
    private long addedPoint(long pos, TimingPoint p) {
        if (!volumeMap.containsKey(pos) || !p.uninherited) //no point here, or a green line which overrides
            volumeMap.put(pos, p.volume);

        //Update kiai.
        Map.Entry<Long, Boolean> kiaiEntry = kiaiMap.floorEntry(pos);
        if ((kiaiEntry != null && kiaiEntry.getValue() != p.kiai) || (kiaiEntry == null && p.kiai)) { //point has different kiai setting than point before it
            kiaiMap.put(p.getPos(), p.kiai);

            long cap = Math.max((long) music.getMsLength(), p.getPos() + 1);
            kiaiEntry = kiaiMap.higherEntry(p.getPos());
            if (kiaiEntry != null)
                cap = kiaiEntry.getKey();

            Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> points = allPoints.subMap(p.getPos(), false, cap, false).entrySet().iterator();
            TimingPoint next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;

            while (next != null) {
                if (next.kiai != p.kiai) {
                    kiaiMap.put(next.getPos(), next.kiai); // +1 +1 = +2
                    break;
                }
                next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;
            }
            if (next == null) {
                //No swaps between. Just remove the higher entry.
                if (kiaiEntry != null) {
                    kiaiMap.remove(kiaiEntry.getKey()); //-1 -1 = -2
                }
                else {
                    //No higher entry. If kiai was enabled, put cap at end of map.
                    if (p.kiai) {
                        kiaiMap.put(cap, false);
                    }
                }
            }
        }
        return pos;
    }
    public void updateKiai(List<TimingPoint> changed) {
        Map.Entry<Long, Boolean> kiaiEntry;

        for (TimingPoint p : changed) {
            if (kiaiMap.containsKey(p.getPos()) && kiaiMap.get(p.getPos()) != p.kiai) { //Was originally a swapping point.
                if (kiaiMap.remove(p.getPos()) != null) { // -1
                    long cap = Math.max((long) music.getMsLength(), p.getPos() + 1);
                    kiaiEntry = kiaiMap.higherEntry(p.getPos());
                    if (kiaiEntry != null)
                        cap = kiaiEntry.getKey();

                    Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> points = allPoints.subMap(p.getPos(), false, cap, false).entrySet().iterator();
                    TimingPoint next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;

                    while (next != null) {
                        if (next.kiai != p.kiai) {
                            kiaiMap.put(next.getPos(), next.kiai); // -1 +1 = 0
                            break;
                        }
                        next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;
                    }
                    if (next == null) {
                        //No swaps between. Just remove the higher entry.
                        if (kiaiEntry != null) {
                            kiaiMap.remove(kiaiEntry.getKey()); //-1 -1 = -2
                        }
                        else {
                            //No higher entry. If kiai was enabled, put cap at end of map.
                            if (p.kiai) {
                                kiaiMap.put(cap, false);
                            }
                        }
                    }
                }
            }
            else { //No swap here originally.
                kiaiEntry = kiaiMap.floorEntry(p.getPos());
                if ((kiaiEntry != null && kiaiEntry.getValue() != p.kiai) || (kiaiEntry == null && p.kiai)) { //point has different kiai setting than point before it
                    kiaiMap.put(p.getPos(), p.kiai);

                    long cap = Math.max((long) music.getMsLength(), p.getPos() + 1);
                    kiaiEntry = kiaiMap.higherEntry(p.getPos());
                    if (kiaiEntry != null)
                        cap = kiaiEntry.getKey();

                    Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> points = allPoints.subMap(p.getPos(), false, cap, false).entrySet().iterator();
                    TimingPoint next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;

                    while (next != null) {
                        if (next.kiai != p.kiai) {
                            kiaiMap.put(next.getPos(), next.kiai); // +1 +1 = +2
                            break;
                        }
                        next = points.hasNext() ? GeneralUtils.listLast(points.next().getValue()) : null;
                    }
                    if (next == null) {
                        //No swaps between. Just remove the higher entry.
                        if (kiaiEntry != null) {
                            kiaiMap.remove(kiaiEntry.getKey()); //+1 -1 = +0
                        }
                        else {
                            //No higher entry. If kiai was enabled, put cap at end of map.
                            if (p.kiai) {
                                kiaiMap.put(cap, false);
                            }
                        }
                    }
                }
            }
        }
    }

    //Note - works with two sets of same lines with different position keys
    public void updateLines(Iterable<? extends Map.Entry<Long, ? extends List<?>>> added, Iterable<? extends Map.Entry<Long, ? extends List<?>>> removed) {
        updateLines(added, removed, true);
    }

    /**
     * Updates linked effectViews, to update max/min sv and also update volume of objects/timing points on timeline
     * @param added
     * @param removed
     * @param updateTimeline
     */
    public void updateLines(Iterable<? extends Map.Entry<Long, ? extends List<?>>> added, Iterable<? extends Map.Entry<Long, ? extends List<?>>> removed, boolean updateTimeline) {
        if (!effectViews.isEmpty()) {
            if (removed != null) {
                TimingPoint temp;
                for (Map.Entry<Long, ? extends List<?>> p : removed) {
                    updatePositions.add(removedPoint(p.getKey()));
                }

                if (added != null) {
                    for (Map.Entry<Long, ? extends List<?>> e : added) {
                        temp = (TimingPoint) GeneralUtils.listLast(e.getValue());
                        updatePositions.add(addedPoint(e.getKey(), temp));
                    }
                }

                //All objects from after a removed or added point to the next point have to have their volume updated
                for (Long pos : updatePositions) {
                    updateVolume(pos);
                }

                for (EffectView effectView : effectViews)
                    effectView.recheckSvLimits();
            }
            else if (added != null) {
                TimingPoint effective;
                for (Map.Entry<Long, ? extends List<?>> e : added) {
                    if (!e.getValue().isEmpty()) {
                        effective = (TimingPoint) GeneralUtils.listLast(e.getValue());
                        addedPoint(e.getKey(), effective);
                        if (!effective.uninherited)
                            for (EffectView effectView : effectViews)
                                effectView.testNewSvLimit(effective.value);
                        updateVolume(e.getKey());
                    }
                }
            }
        }
        if (updateTimeline && timeline != null) {
            timeline.updateTimingPoints(this, added, removed);
        }
        updatePositions.clear();
    }

    //Used when only values of green lines are adjusted
    public void updateSv() {
        for (EffectView effectView : effectViews) {
            effectView.recheckSvLimits();
        }
    }
    public void updateLines(TimingPoint added, TimingPoint removed) {
        if (!effectViews.isEmpty()) {
            updateLines(Collections.singleton(new Pair<>(added.getPos(), Collections.singletonList(added))),
                    Collections.singleton(new Pair<>(removed.getPos(), Collections.singletonList(removed))));
        }
    }
    public void updateLines(TimingPoint added, List<Pair<Long, ArrayList<TimingPoint>>> removed) {
        if (!effectViews.isEmpty()) {
            updateLines(Collections.singleton(new Pair<>(added.getPos(), Collections.singletonList(added))), removed);
        }
    }
    public void updateLines(List<Pair<Long, ArrayList<TimingPoint>>> added, TimingPoint removed) {
        if (!effectViews.isEmpty()) {
            updateLines(added, Collections.singleton(new Pair<>(removed.getPos(), Collections.singletonList(removed))));
        }
    }

    public void gameplayChanged() {
        for (GameplayView view : gameplayViews) {
            if (view.autoRefresh()) {
                TaikoEditor.onMain(view::calculateTimes);
            }
        }
    }

    //Timing Points
    public NavigableMap<Long, ArrayList<TimingPoint>> getEditPoints(long startPos, long endPos)
    {
        if (startPos != lastEditPointStart || endPos != lastEditPointEnd)
        {
            lastEditPointStart = startPos;
            lastEditPointEnd = endPos;
            editPoints = allPoints.extendedDescendingSubMap(startPos, endPos);
        }
        return editPoints;
    }
    public NavigableMap<Long, ArrayList<TimingPoint>> getEditTimingPoints(long startPos, long endPos) {
        if (startPos != lastTimingPointStart || endPos != lastTimingPointEnd)
        {
            lastTimingPointStart = startPos;
            lastTimingPointEnd = endPos;
            editTimingPoints = timingPoints.extendedDescendingSubMap(startPos, endPos);
        }
        return editTimingPoints;
    }
    public NavigableMap<Long, ArrayList<TimingPoint>> getEditEffectPoints(long startPos, long endPos)
    {
        if (startPos != lastEffectPointStart || endPos != lastEffectPointEnd)
        {
            lastEffectPointStart = startPos;
            lastEffectPointEnd = endPos;
            editEffectPoints = effectPoints.extendedDescendingSubMap(startPos, endPos);
        }
        return editEffectPoints;
    }
    public NavigableMap<Long, ArrayList<TimingPoint>> getSubTimingMap(long startPos, long endPos)
    {
        return timingPoints.descendingSubMap(startPos, true, endPos, true);
    }
    public NavigableMap<Long, ArrayList<TimingPoint>> getSubEffectMap(long startPos, long endPos)
    {
        return effectPoints.descendingSubMap(startPos, true, endPos, true);
    }

    public void bindEffectView(EffectView view)
    {
        if (!effectViews.contains(view))
            effectViews.add(view);
    }
    public void removeEffectView(EffectView view)
    {
        effectViews.remove(view);
    }
    public void bindGameplayView(GameplayView view)
    {
        if (!gameplayViews.contains(view))
            gameplayViews.add(view);
    }
    public void removeGameplayView(GameplayView view)
    {
        gameplayViews.remove(view);
    }
    public void setTimeline(Timeline line) {
        this.timeline = line;
    }

    public TreeMap<Long, Boolean> getKiai() {
        return kiaiMap;
    }

    //Map properties
    public float getBaseSV() {
        return fullMapInfo.sliderMultiplier;
    }


    public void dispose()
    {
        timingPoints.clear();
        effectPoints.clear();
        allPoints.clear();
        objects.clear();

        volumeMap.clear();
        kiaiMap.clear();
    }



    public boolean is(MapInfo info)
    {
        return fullMapInfo.is(info);
    }


    /// Save/load ///
    private void parse(Mapset set, MapInfo map)
    {
        fullMapInfo = new FullMapInfo(set, map);

        if (!map.getMapFile().isFile())
            return;

        List<String> lines = FileHelper.readFileLines(map.getMapFile());

        if (lines == null || lines.isEmpty())
            return;

        int section = -1, eventSection = -1;

        //Sv tracking variables
        long currentPos;
        Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> timing = null, effect = null;
        Map.Entry<Long, ArrayList<TimingPoint>> nextTiming = null, nextEffect = null;
        double svRate = 1.4, currentBPM = 120;
        int volume = 100;

        TimingPoint temp;

        //-1 Header
        //0 General
        //1 Editor
        //2 Metadata
        //3 Difficulty
        //4 Events
        //5 TimingPoints
        //6 HitObjects

        //Events:
            //-1 start
            //0 background
            //1 break
            //2 anything else

        for (String line : lines)
        {
            if (line.isEmpty())
                continue;

            if (line.startsWith("["))
            {
                switch (line)
                {
                    case "[General]":
                        section = 0;
                        break;
                    case "[Editor]":
                        section = 1;
                        break;
                    case "[Metadata]":
                        section = 2;
                        break;
                    case "[Difficulty]":
                        section = 3;
                        break;
                    case "[Events]":
                        section = 4;
                        break;
                    case "[TimingPoints]":
                        section = 5;
                        break;
                    case "[HitObjects]":
                        section = 6;
                        //Done with points
                        allPoints.clear();
                        allPoints.addAll(timingPoints);
                        allPoints.addAll(effectPoints);

                        TimingPoint next;
                        for (List<TimingPoint> pointStack : allPoints.values()) {
                            next = pointStack.get(pointStack.size() - 1);
                            if (next.volume != volume) {
                                volume = next.volume;
                                volumeMap.put(next.getPos(), volume);
                            }
                        }

                        //Prepare to track sv for the purpose of calculating slider length
                        svRate = fullMapInfo.sliderMultiplier;
                        timing = timingPoints.entrySet().iterator();
                        effect = effectPoints.entrySet().iterator();

                        if (timing.hasNext())
                        {
                            nextTiming = timing.next();
                            currentBPM = nextTiming.getValue().get(0).value;
                            volume = nextTiming.getValue().get(0).volume;
                            svRate = fullMapInfo.sliderMultiplier;
                            if (timing.hasNext())
                                nextTiming = timing.next();
                            else
                                nextTiming = null; //Only one timing point.
                        }
                        else
                        {
                            nextTiming = null; //what the fuck why are there no timing points >:(
                            currentBPM = 120; //This is what osu uses as default so it's what I'm gonna use. Though really, there shouldn't be any objects if there's no timing points.
                        }

                        if (effect.hasNext())
                            nextEffect = effect.next(); //First SV doesn't apply until the first timing point is reached.
                        break;
                    case "[Colours]": //I don't give a fuck about colors this is taiko you can't even see them in game OR in editor
                        section = 7;
                        break;
                }
            }
            else
            {
                switch (section) {
                    case 0: //General
                        if (line.contains(":"))
                        {
                            switch (line.substring(0, line.indexOf(":")))
                            {
                                case "AudioLeadIn":
                                    fullMapInfo.audioLeadIn = Integer.parseInt(line.substring(12).trim());
                                    break;
                                case "PreviewTime":
                                    fullMapInfo.previewTime = Integer.parseInt(line.substring(12).trim());
                                    break;
                                case "Countdown":
                                    fullMapInfo.countdown = line.substring(10).trim().equals("1");
                                    break;
                                case "SampleSet":
                                    fullMapInfo.sampleSet = line.substring(10).trim();
                                    break;
                                case "StackLeniency":
                                    fullMapInfo.stackLeniency = line.substring(14).trim();
                                    break;
                                case "LetterboxInBreaks":
                                    fullMapInfo.letterboxInBreaks = line.substring(18).trim().equals("1");
                                    break;
                                case "SkinPreference":
                                    fullMapInfo.skinPreference = line.substring(15);
                                    break;
                                case "EpilepsyWarning":
                                    fullMapInfo.epilepsyWarning = line.substring(16).trim().equals("1");
                                    break;
                                case "WidescreenStoryboard":
                                    fullMapInfo.widescreenStoryboard = line.substring(21).trim().equals("1");
                                    break;
                            }
                        }
                        break;
                    case 1: //Editor
                        if (line.contains(":"))
                        {
                            switch (line.substring(0, line.indexOf(":")))
                            {
                                case "Bookmarks":
                                    for (String s : line.substring(10).split(","))
                                    {
                                        fullMapInfo.bookmarks.add(Integer.parseInt(s.trim()));
                                    }
                                    break;
                                case "DistanceSpacing":
                                    fullMapInfo.distanceSpacing = line.substring(16).trim();
                                    break;
                                case "BeatDivisor":
                                    fullMapInfo.beatDivisor = Integer.parseInt(line.substring(12).trim());
                                    break;
                                case "GridSize":
                                    fullMapInfo.gridSize = line.substring(9).trim();
                                    break;
                                case "TimelineZoom":
                                    fullMapInfo.timelineZoom = line.substring(13).trim();
                                    break;
                            }
                        }
                        break;
                    case 2: //Metadata
                        if (line.contains(":"))
                        {
                            switch (line.substring(0, line.indexOf(":")))
                            {
                                case "Title":
                                    fullMapInfo.title = line.substring(6);
                                    fullMapInfo.titleUnicode = line.substring(6);
                                    break;
                                case "TitleUnicode":
                                    fullMapInfo.titleUnicode = line.substring(13);
                                    break;
                                case "Artist":
                                    fullMapInfo.artist = line.substring(7);
                                    fullMapInfo.artistUnicode = line.substring(7);
                                    break;
                                case "ArtistUnicode":
                                    fullMapInfo.artistUnicode = line.substring(14);
                                    break;
                                case "Creator":
                                    fullMapInfo.creator = line.substring(8);
                                    break;
                                case "Version":
                                    fullMapInfo.setDifficultyName(line.substring(8));
                                    break;
                                case "Source":
                                    fullMapInfo.source = line.substring(7);
                                    break;
                                case "Tags":
                                    fullMapInfo.tags = line.substring(5).trim().split(" ");
                                    break;
                                case "BeatmapID":
                                    fullMapInfo.beatmapID = Integer.parseInt(line.substring(10).trim());
                                    break;
                                case "BeatmapSetID":
                                    fullMapInfo.beatmapSetID = Integer.parseInt(line.substring(13).trim());
                                    break;
                            }
                        }
                        break;
                    case 3: //Difficulty
                        if (line.contains(":"))
                        {
                            switch (line.substring(0, line.indexOf(":")))
                            {
                                case "HPDrainRate":
                                    fullMapInfo.hp = Float.parseFloat(line.substring(12).trim());
                                    break;
                                case "CircleSize":
                                    fullMapInfo.cs = Float.parseFloat(line.substring(11).trim());
                                    break;
                                case "OverallDifficulty":
                                    fullMapInfo.od = Float.parseFloat(line.substring(18).trim());
                                    break;
                                case "ApproachRate":
                                    fullMapInfo.ar = Float.parseFloat(line.substring(13).trim());
                                    break;
                                case "SliderMultiplier":
                                    fullMapInfo.sliderMultiplier = Float.parseFloat(line.substring(17).trim());
                                    break;
                                case "SliderTickRate":
                                    fullMapInfo.sliderTickRate = Float.parseFloat(line.substring(15).trim());
                                    break;
                            }
                        }
                        break;
                    case 4: //Events
                        if (line.startsWith("//"))
                        {
                            //In events
                            switch (line)
                            {
                                case "//Background and Video events":
                                    eventSection = 0;
                                    continue;
                                case "//Break Periods":
                                    eventSection = 1;
                                    continue;
                                case "//Storyboard Layer 0 (Background)": //This line and all event lines past it should be included in storyboard text
                                    eventSection = 2;
                                    break;
                            }
                        }
                        if (line.startsWith("2") || line.startsWith("Break")) {
                            String[] parts = line.split(",");
                            fullMapInfo.breakPeriods.add(new BreakInfo(Long.parseLong(parts[1].trim()), Long.parseLong(parts[2].trim())));
                        }
                        //The rest
                        else if (eventSection == 0) { //Background and Video events
                            if (!line.startsWith("//")) {
                                String[] parts = line.split(",");

                                if (parts.length == 5) {
                                    if (parts[2].startsWith("\"") && parts[2].endsWith("\"")) {
                                        parts[2] = parts[2].substring(1, parts[2].length() - 1);
                                    }
                                    if (FileHelper.isImageFilename(parts[2])) {
                                        String bgFile = FileHelper.concat(map.getMapFile().getParent(), parts[2]);
                                        map.setBackground(bgFile);
                                        if (set.background == null || !set.background.equals(bgFile)) {
                                            set.background = bgFile;
                                            MapMaster.mapDatabase.save();
                                        }
                                    }
                                }
                            }
                            fullMapInfo.backgroundEvents.add(line.split(","));
                        } else {
                            fullMapInfo.fullStoryboard.add(line);
                        }
                        break;
                    case 5: //TimingPoints
                        TimingPoint p = new TimingPoint(line); //ordering is not guaranteed at this point
                        if (p.uninherited)
                            timingPoints.add(p);
                        else
                            effectPoints.add(p);
                        break;
                    case 6: //HitObjects
                        HitObject h = HitObject.create(line);
                        currentPos = h.getPos();

                        long lastTimingPos = Long.MIN_VALUE;
                        long lastEffectPos = Long.MIN_VALUE;

                        while (timing != null && nextTiming != null && nextTiming.getKey() <= currentPos)
                        {
                            temp = GeneralUtils.listLast(nextTiming.getValue());
                            currentBPM = temp.value;
                            lastTimingPos = nextTiming.getKey();
                            svRate = fullMapInfo.sliderMultiplier; //return to base sv

                            if (timing.hasNext())
                                nextTiming = timing.next();
                            else
                                nextTiming = null;
                        }
                        while (effect != null && nextEffect != null && nextEffect.getKey() <= currentPos)
                        {
                            temp = GeneralUtils.listLast(nextEffect.getValue());
                            lastEffectPos = nextEffect.getKey();
                            svRate = fullMapInfo.sliderMultiplier * temp.value;

                            if (effect.hasNext())
                                nextEffect = effect.next();
                            else
                                nextEffect = null;
                        }
                        if (lastEffectPos < lastTimingPos)
                        {
                            svRate = fullMapInfo.sliderMultiplier; //return to base sv and volume of the timing point
                        }

                        if (h.type == HitObject.HitObjectType.SLIDER)
                        {
                            ((Slider)h).calculateDuration(currentBPM, svRate);
                        }
                        updateVolume(h);
                        objects.add(h);
                        break;
                }
            }
        }

        if (volumeMap.isEmpty()) //wtf no points at all
            volumeMap.put(Long.MAX_VALUE, volume);

        boolean kiai = false, nextKiai;
        for (Map.Entry<Long, ArrayList<TimingPoint>> stack : allPoints.entrySet()) {
            nextKiai = stack.getValue().get(stack.getValue().size() - 1).kiai;
            if (nextKiai != kiai) {
                kiaiMap.put(stack.getKey(), kiai = nextKiai);
            }
        }
        if (kiai) //kiai never turned off
            kiaiMap.put((long) music.getMsLength(), false);

        sortBreaks();
        autoBreaks = testBreaks();
    }

    public void sortBreaks() {
        fullMapInfo.breakPeriods.sort(Comparator.comparingLong(a -> a.start));
    }

    private boolean testBreaks() {
        Long test;
        Optional<HitObject> longest;
        for (BreakInfo breakPeriod : getBreaks()) {
            test = null;

            Map.Entry<Long, ArrayList<HitObject>> preObject = objects.floorEntry(breakPeriod.end);
            if (preObject == null)
                return false;

            longest = preObject.getValue().stream().max(Comparator.comparingLong(HitObject::getEndPos));
            if (longest.isPresent()) {
                test = longest.get().getEndPos();
            }

            if (test == null || breakPeriod.start - test < 200) { //break is too close to the object before it, or no preceding object or it's during the break
                return false;
            }
            test = objects.ceilingKey(breakPeriod.start);
            if (test == null || test < breakPeriod.end + getBreakEndDelay()) { //object during break
                return false;
            }
        }
        return true;
    }

    /**
     *
     * @return null if successful, or error message string
     */
    public String save()
    {
        FileOutputStream out = null;
        BufferedWriter w = null;
        try
        {
            String mapInfo = fullMapInfo.toString();
            String lines = timingPoints();
            String objects = hitObjects();

            //Successfully generated map save text without crashing.

            File newFile = fullMapInfo.generateMapFile();

            if (fullMapInfo.getMapFile().exists())
            {
                String backup = newFile.getPath();
                backup = backup.substring(0, backup.lastIndexOf('.')) + ".BACKUP";
                File backupFile = new File(backup);
                if (backupFile.exists())
                    backupFile.delete();
                try
                {
                    if (fullMapInfo.getMapFile().renameTo(backupFile))
                    {
                        editorLogger.info("Created backup successfully.");
                    }
                    else
                    {
                        editorLogger.error("Failed to create backup.");
                    }
                }
                catch (Exception e)
                {
                    //No backup :(
                    editorLogger.error("Failed to create backup.", e);
                    try {
                        File f = new File("error.txt");
                        PrintWriter pWriter = null;

                        try {
                            pWriter = new PrintWriter(f);
                            pWriter.println("Version: " + TaikoEditor.VERSION);
                            pWriter.println("Error occurred during save: " + e.getMessage());
                            e.printStackTrace(pWriter);
                        }
                        catch (Exception ignored) {
                        }
                        finally {
                            StreamUtils.closeQuietly(pWriter);
                        }
                    }
                    catch (Exception ignored) {

                    }
                }
            }

            out = new FileOutputStream(newFile, false);
            w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

            w.write(mapInfo);
            w.write(lines);
            w.write(objects);

            w.close();
            out.close();

            dirty = false;

            fullMapInfo.setMapFile(newFile);
            return null;
        }
        catch (Exception e)
        {
            //Failure
            if (w != null)
            {
                try
                {
                    w.close();
                }
                catch (Exception ignored) {}
            }
            if (out != null)
            {
                try
                {
                    out.close();
                }
                catch (Exception ignored) {}
            }
            editorLogger.error("Failed to save beatmap.", e);

            String errorMsg = "Failed to save: " + e.getMessage();

            //Log failure
            try {
                File f = new File("error.txt");
                PrintWriter pWriter = null;

                try {
                    pWriter = new PrintWriter(f);
                    pWriter.println("Version: " + TaikoEditor.VERSION);
                    pWriter.println(errorMsg);
                    e.printStackTrace(pWriter);
                }
                catch (Exception ignored) {
                }
                finally {
                    StreamUtils.closeQuietly(pWriter);
                }
            }
            catch (Exception ignored) {

            }

            editorLogger.info("Attempting to save to backup location.");
            boolean malformed = false;
            File emergency = null;

            try {

                String mapInfo = "";
                String lines = "";
                String objects = "";
                try {
                    mapInfo = fullMapInfo.toString();
                }
                catch (Exception ignored) {
                    malformed = true;
                }
                try {
                    lines = timingPoints();
                }
                catch (Exception ignored) {
                    malformed = true;
                }
                try {
                    objects = hitObjects();
                }
                catch (Exception ignored) {
                    malformed = true;
                }

                try {
                    Toolkit.getDefaultToolkit()
                            .getSystemClipboard()
                            .setContents(new StringSelection(mapInfo + lines + objects), null);
                }
                catch (Exception ignored) {

                }

                File parent = fullMapInfo.getMapFile().getParentFile(), target;
                do {
                    target = parent;
                    parent = target.getParentFile();
                } while (parent != null);

                emergency = new File(target, fullMapInfo.generateFilename());

                out = new FileOutputStream(emergency, false);
                w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

                w.write(mapInfo);
                w.write(lines);
                w.write(objects);

                w.close();
                out.close();

                dirty = false;
            }
            catch (Exception ignored) {

            }

            return errorMsg + " | Copied map to clipboard" +
                    (emergency != null ? " and attempted to save " + (malformed ? "malformed " : "") + "map to " + emergency.getPath() : "");
        }
    }

    private String timingPoints()
    {
        if (allPoints.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("\r\n[TimingPoints]\r\n");

        for (Map.Entry<Long, ArrayList<TimingPoint>> stack : allPoints.entrySet()) {
            stack.getValue().sort(Comparator.comparingInt((point)->point.uninherited ? 0 : 1));
            for (TimingPoint t : stack.getValue())
                sb.append(t.toString()).append("\r\n");
        }

        return sb.toString();
    }
    private String hitObjects()
    {
        if (timingPoints.isEmpty() && effectPoints.isEmpty() && hitObjects().isEmpty())
            return "";


        //Sv tracking variables
        long currentPos;
        Iterator<Map.Entry<Long, ArrayList<TimingPoint>>> timing, effect;
        Map.Entry<Long, ArrayList<TimingPoint>> nextTiming = null, nextEffect = null;
        double svRate = 1.4, currentBPM = 120;

        svRate = fullMapInfo.sliderMultiplier;
        timing = timingPoints.entrySet().iterator();
        effect = effectPoints.entrySet().iterator();

        if (timing.hasNext())
        {
            nextTiming = timing.next();
            currentBPM = nextTiming.getValue().get(0).value;
            if (timing.hasNext())
                nextTiming = timing.next();
            else
                nextTiming = null; //Only one timing point.
        }

        if (effect.hasNext())
            nextEffect = effect.next(); //First SV doesn't apply until the first timing point is reached.



        StringBuilder sb = new StringBuilder("\r\n\r\n[HitObjects]\r\n");



        for (Map.Entry<Long, ArrayList<HitObject>> stacked : objects.entrySet())
        {
            currentPos = stacked.getKey();

            long lastTimingPos = Long.MIN_VALUE;
            long lastEffectPos = Long.MIN_VALUE;

            while (nextTiming != null && nextTiming.getKey() <= currentPos)
            {
                currentBPM = nextTiming.getValue().get(nextTiming.getValue().size() - 1).value;
                lastTimingPos = nextTiming.getKey();
                svRate = fullMapInfo.sliderMultiplier; //return to base sv

                if (timing.hasNext())
                    nextTiming = timing.next();
                else
                    nextTiming = null;
            }
            while (nextEffect != null && nextEffect.getKey() <= currentPos)
            {
                lastEffectPos = nextEffect.getKey();
                svRate = fullMapInfo.sliderMultiplier * nextEffect.getValue().get(nextEffect.getValue().size() - 1).value;

                if (effect.hasNext())
                    nextEffect = effect.next();
                else
                    nextEffect = null;
            }
            if (lastEffectPos < lastTimingPos)
            {
                svRate = fullMapInfo.sliderMultiplier; //return to base sv
            }


            for (HitObject h : stacked.getValue())
                sb.append(h.toString(currentBPM, svRate)).append("\r\n");
        }

        return sb.toString();
    }

    public String getName()
    {
        return fullMapInfo.getDifficultyName();
    }






    /// EXPERIMENTAL TJA SAVE SUPPORT ///


    //TODO:
    /*
        Make metadata information a class.
        TJA and osu will have separate metadata classes.
        Editing beatmap properties will depend on the metadata class.
        Enable negative scroll rate if TJA.

        BRANCHES:
        Support them?
        If a section is a "branch":
        Have a special highlight.
        Hotkey/buttons to swap between branches/add branch.

        tbh probably not going to do any of this
        at most I'd probably just add save as tja support
     */

    public boolean saveTJA()
    {
        if (true)
            return false;

        //All difficulties are stored in one file. Move this to set.

        FileOutputStream out = null;
        BufferedWriter w = null;
        try
        {
            String tjaFile = fullMapInfo.getMapFile().getParentFile().getPath();
            tjaFile += ".tja";

            out = new FileOutputStream(tjaFile, false);
            w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

            Map.Entry<Long, ArrayList<TimingPoint>> firstEntry = timingPoints.firstEntry();

            //required part of file header
            double bpm = firstEntry.getValue().get(0).getBPM();
            long offset = firstEntry.getValue().get(0).getPos();

            w.write("// Generated using TaikoEditor\n");
            w.write("TITLE:" + fullMapInfo.titleUnicode + "\n");
            w.write("TITLEEN:" + fullMapInfo.title + "\n"); //Is this even supported?


            //To write this as TJA, the objects must be converted into Measures.
            //Measure snappings will be determined by finding closest snaps, 1 millisecond before or after.
            //Unsnapped notes will cause an error, which should be reported to TextOverlay.
            //#BPMCHANGE can be used in the middle of a measure.
            //Timing points with barline hidden are not considered to be the start of a new measure.
            //The end of the measure will be based on barlines.
            //First, read an entire measure. Determine the necessary base snapping. Generate the measure.

            Iterator<Map.Entry<Long, ArrayList<HitObject>>> objStackItr = objects.entrySet().iterator();
            long measure = 0;


            w.close();
            out.close();
            return true;
        }
        catch (Exception e)
        {
            if (w != null)
            {
                try
                {
                    w.close();
                }
                catch (Exception ignored) {}
            }
            if (out != null)
            {
                try
                {
                    out.close();
                }
                catch (Exception ignored) {}
            }
            editorLogger.error("Failed to save beatmap.");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * @return the endpoint of the longest object in the stack, or Long.MIN_VALUE if it is empty.
     */
    private long getEnd(ArrayList<? extends MapObject> stack) {
        if (stack.isEmpty()) return Long.MIN_VALUE;
        Optional<? extends MapObject> longest = stack.stream().filter((obj)->obj instanceof ILongObject).max(Comparator.comparingLong((a)->((ILongObject) a).getEndPos()));
        return longest.map((o)->((ILongObject)o).getEndPos()).orElse(stack.get(0).getPos());
    }
}
