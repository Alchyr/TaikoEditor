package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.structures.Pair;
import alchyr.taikoedit.util.structures.PositionalObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

public class SingleLineAddition extends MapChange {
    //public enum LineType { Only effect lines. Timing should not be done using this program.

    private final TimingPoint added;
    private List<Pair<Long, ArrayList<TimingPoint>>> replacedObjects;


    public SingleLineAddition(EditorBeatmap map, TimingPoint addedObject)
    {
        super(map);

        this.added = addedObject;
        replacedObjects = null;
    }

    @Override
    public MapChange undo() {
        map.allPoints.removeObject(added);
        if (added.uninherited)
            map.timingPoints.removeObject(added);
        else
            map.effectPoints.removeObject(added);

        if (replacedObjects != null)
        {
            for (Pair<Long, ArrayList<TimingPoint>> replaced : replacedObjects)
            {
                map.effectPoints.put(replaced.a, replaced.b);
                for (TimingPoint p : replaced.b)
                    map.allPoints.add(p);
            }
        }

        if (added.uninherited)
            map.regenerateDivisor();
        map.updateLines(replacedObjects, added);
        map.gameplayChanged();
        return this;
    }

    @Override
    public MapChange perform() {
        ArrayList<TimingPoint> replaced;

        if (added.uninherited)
            replaced = map.timingPoints.remove(added.getPos());
        else
            replaced = map.effectPoints.remove(added.getPos());

        if (replaced != null)
        {
            for (TimingPoint p : replaced)
                map.allPoints.removeObject(p);
            replacedObjects = new ArrayList<>();
            replacedObjects.add(new Pair<>(added.getPos(), replaced));
        }
        else
        {
            replacedObjects = null;
        }

        map.allPoints.add(added);
        if (added.uninherited)
            map.timingPoints.add(added);
        else
            map.effectPoints.add(added);

        if (added.uninherited)
            map.regenerateDivisor();
        map.updateLines(added, replacedObjects);
        map.gameplayChanged();
        return this;
    }
}