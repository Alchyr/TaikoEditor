package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.structures.PositionalObject;

public class SingleDeletion extends MapChange {
    private final MapChange.ChangeType type;
    private final PositionalObject deleted;

    public SingleDeletion(EditorBeatmap map, PositionalObject deletedObject) {
        super(map);

        this.type = getChangeType(deletedObject);
        this.deleted = deletedObject;
    }

    @Override
    public MapChange undo() {
        switch (type)
        {
            case OBJECTS:
                map.preAddObject((HitObject) deleted);
                map.objects.add((HitObject) deleted);
                map.updateVolume((HitObject) deleted);
                break;
            case GREEN_LINE:
                map.effectPoints.add((TimingPoint) deleted);
                map.allPoints.add((TimingPoint) deleted);
                map.updateLines((TimingPoint) deleted, null);
                break;
            case RED_LINE:
                map.timingPoints.add((TimingPoint) deleted);
                map.allPoints.add((TimingPoint) deleted);
                map.regenerateDivisor();
                map.updateLines((TimingPoint) deleted, null);
                break;
        }
        map.gameplayChanged();
        return this;
    }

    @Override
    public MapChange perform() {
        switch (type)
        {
            case OBJECTS:
                map.removeObject(deleted);
                break;
            case GREEN_LINE:
                map.effectPoints.removeObject(deleted);
                map.allPoints.removeObject(deleted);
                map.updateLines(null, (TimingPoint) deleted);
                break;
            case RED_LINE:
                map.timingPoints.removeObject(deleted);
                map.allPoints.removeObject(deleted);
                map.regenerateDivisor();
                map.updateLines(null, (TimingPoint) deleted);
                break;
        }

        map.gameplayChanged();
        return this;
    }
}
