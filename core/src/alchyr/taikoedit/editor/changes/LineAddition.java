package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.structures.Pair;
import alchyr.taikoedit.util.structures.PositionalObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

public class LineAddition extends MapChange {
    //public enum LineType { Only effect lines. Timing should not be done using this program.

    private final TimingPoint added;
    private final boolean singleLine;
    private final NavigableMap<Long, ArrayList<PositionalObject>> addedLines;
    private List<Pair<Long, ArrayList<TimingPoint>>> replacedObjects;


    public LineAddition(EditorBeatmap map, TimingPoint addedObject)
    {
        super(map);

        singleLine = true;
        this.added = addedObject;
        addedLines = null;
        replacedObjects = null;
    }

    public LineAddition(EditorBeatmap map, NavigableMap<Long, ArrayList<PositionalObject>> addedLines)
    {
        super(map);

        singleLine = false;
        this.added = null;
        this.addedLines = addedLines;
        replacedObjects = null;
    }

    @Override
    public MapChange undo() {
        if (singleLine && added != null)
        {
            map.effectPoints.removeObject(added);

            if (replacedObjects != null)
            {
                for (Pair<Long, ArrayList<TimingPoint>> replaced : replacedObjects)
                {
                    map.effectPoints.put(replaced.a, replaced.b);
                }
                map.updateEffectPoints(replacedObjects, added);
            }
        }
        else if (addedLines != null)
        {
            map.effectPoints.removeAll(addedLines);

            if (replacedObjects != null)
            {
                for (Pair<Long, ArrayList<TimingPoint>> replaced : replacedObjects)
                {
                    map.effectPoints.put(replaced.a, replaced.b);
                }
                map.updateEffectPoints(replacedObjects, addedLines);
            }
        }
        return this;
    }

    @Override
    public MapChange perform() {
        //if prompt: Add new layer that stops updates, with a callback to this method on confirmation then return.

        if (singleLine && added != null)
        {
            ArrayList<TimingPoint> replaced = map.effectPoints.get(added.pos);
            if (replaced != null)
            {
                replacedObjects = new ArrayList<>();
                replacedObjects.add(new Pair<>(added.pos, replaced));
            }
            else
            {
                replacedObjects = null;
            }
            map.effectPoints.add(added);
            map.updateEffectPoints(added, replacedObjects);
        }
        else if (addedLines != null)
        {
            replacedObjects = new ArrayList<>();
            for (Map.Entry<Long, ArrayList<PositionalObject>> e : addedLines.entrySet())
            {
                ArrayList<TimingPoint> replaced = map.effectPoints.remove(e.getKey());
                if (replaced != null)
                {
                    replacedObjects.add(new Pair<>(e.getKey(), replaced));
                }

                for (PositionalObject o : e.getValue())
                    map.effectPoints.add((TimingPoint) o);
            }
            if (replacedObjects.isEmpty())
                replacedObjects = null;

            map.updateEffectPoints(addedLines, replacedObjects);
        }
        return this;
    }
}