package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.maps.components.ILongObject;

public class DurationChange extends MapChange {
    private final ILongObject changed;
    private final int changeAmount;

    public DurationChange(EditorBeatmap map, ILongObject changed, int changeAmount)
    {
        super(map);
        this.changed = changed;
        this.changeAmount = changeAmount;
    }

    @Override
    public MapChange undo() {
        changed.setDuration(changed.getDuration() - changeAmount);
        return this;
    }

    @Override
    public MapChange perform() {
        changed.setDuration(changed.getDuration() + changeAmount);
        return this;
    }
}
