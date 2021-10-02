package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;

import java.util.List;

public class FinisherChange extends MapChange {
    private final List<HitObject> modifiedObjects;
    private final boolean toFinisher;

    public FinisherChange(EditorBeatmap map, List<HitObject> modifiedObjects, boolean toFinisher)
    {
        super(map);

        this.modifiedObjects = modifiedObjects;
        this.toFinisher = toFinisher;
    }

    @Override
    public MapChange undo() {
        for (HitObject o : modifiedObjects) {
            o.finish = !toFinisher;
        }
        return this;
    }
    @Override
    public MapChange perform() {
        for (HitObject o : modifiedObjects) {
            o.finish = toFinisher;
        }
        return this;
    }
}