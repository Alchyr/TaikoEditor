package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.hitobjects.Hit;

import java.util.List;

public class RimChange extends MapChange {
    private final List<Hit> modifiedObjects;
    private final boolean toRim;

    public RimChange(EditorBeatmap map, List<Hit> modifiedObjects, boolean toRim)
    {
        super(map);

        this.modifiedObjects = modifiedObjects;
        this.toRim = toRim;
    }

    @Override
    public MapChange undo() {
        for (Hit o : modifiedObjects)
            o.setIsRim(!toRim);
        return this;
    }
    @Override
    public MapChange perform() {
        for (Hit o : modifiedObjects)
            o.setIsRim(toRim);
        return this;
    }
}