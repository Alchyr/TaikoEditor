package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.structures.Pair;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;

import java.util.*;

public class MultiLineAddition extends MapChange {
    private final PositionalObjectTreeMap<PositionalObject> addedLines;
    private final PositionalObjectTreeMap<PositionalObject> greenLines;
    private final PositionalObjectTreeMap<PositionalObject> redLines;
    private Map<Long, ArrayList<TimingPoint>> replacedObjects;


    public MultiLineAddition(EditorBeatmap map, PositionalObjectTreeMap<PositionalObject> addedLines)
    {
        super(map);

        this.addedLines = addedLines;
        this.greenLines = new PositionalObjectTreeMap<>();
        this.redLines = new PositionalObjectTreeMap<>();

        ArrayList<PositionalObject> red = new ArrayList<>(), green = new ArrayList<>();
        for (Map.Entry<Long, ArrayList<PositionalObject>> entry : addedLines.entrySet()) {
            if (!red.isEmpty()) red = new ArrayList<>();
            if (!green.isEmpty()) green = new ArrayList<>();

            for (PositionalObject o : entry.getValue()) {
                if (o instanceof TimingPoint) {
                    if (((TimingPoint) o).uninherited)
                        red.add(o);
                    else
                        green.add(o);
                }
            }

            if (!red.isEmpty()) redLines.put(entry.getKey(), red);
            if (!green.isEmpty()) greenLines.put(entry.getKey(), green);
        }

        replacedObjects = null;
    }

    @Override
    public MapChange undo() {
        map.effectPoints.removeAll(greenLines);
        map.timingPoints.removeAll(redLines);
        map.allPoints.removeAll(addedLines);

        if (replacedObjects != null)
        {
            map.allPoints.addAll(replacedObjects);
            for (Map.Entry<Long, ArrayList<TimingPoint>> replaced : replacedObjects.entrySet())
            {
                for (TimingPoint p : replaced.getValue()) {
                    if (p.uninherited)
                        map.timingPoints.add(p);
                    else
                        map.effectPoints.add(p);
                }
            }
        }


        if (!redLines.isEmpty())
            map.regenerateDivisor();
        map.updateLines(replacedObjects == null ? null : replacedObjects.entrySet(), addedLines.entrySet());
        map.gameplayChanged();
        return this;
    }

    @Override
    public MapChange perform() {
        replacedObjects = new HashMap<>();
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : redLines.entrySet())
        {
            ArrayList<TimingPoint> replaced = map.timingPoints.remove(e.getKey());
            if (replaced != null)
            {
                for (TimingPoint p : replaced)
                    map.allPoints.removeObject(p);
                if (replacedObjects.containsKey(e.getKey()))
                    replacedObjects.get(e.getKey()).addAll(replaced);
                else
                    replacedObjects.put(e.getKey(), replaced);
            }

            for (PositionalObject o : e.getValue()) {
                map.timingPoints.add((TimingPoint) o);
                map.allPoints.add((TimingPoint) o);
            }
        }
        for (Map.Entry<Long, ArrayList<PositionalObject>> e : greenLines.entrySet())
        {
            ArrayList<TimingPoint> replaced = map.effectPoints.remove(e.getKey());
            if (replaced != null)
            {
                for (TimingPoint p : replaced)
                    map.allPoints.removeObject(p);
                if (replacedObjects.containsKey(e.getKey()))
                    replacedObjects.get(e.getKey()).addAll(replaced);
                else
                    replacedObjects.put(e.getKey(), replaced);
            }

            for (PositionalObject o : e.getValue()) {
                map.effectPoints.add((TimingPoint) o);
                map.allPoints.add((TimingPoint) o);
            }
        }
        if (replacedObjects.isEmpty())
            replacedObjects = null;


        if (!redLines.isEmpty())
            map.regenerateDivisor();
        map.updateLines(addedLines.entrySet(), replacedObjects == null ? null : replacedObjects.entrySet());
        map.gameplayChanged();
        return this;
    }
}