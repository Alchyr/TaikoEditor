package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.structures.Pair;

public class BreakAdjust extends MapChange {
    private final Pair<Long, Long> breakSection;

    private final boolean start;
    private final long origVal, newVal;

    public BreakAdjust(EditorBeatmap map, Pair<Long, Long> breakSection, boolean start, long origVal, long newVal) {
        super(map);
        this.breakSection = breakSection;

        this.start = start;
        this.origVal = origVal;
        this.newVal = newVal;
    }

    @Override
    public MapChange undo() {
        if (start) {
            breakSection.a = origVal;
        }
        else {
            breakSection.b = origVal;
        }
        return this;
    }

    @Override
    public MapChange perform() {
        if (start) {
            breakSection.a = newVal;
        }
        else {
            breakSection.b = newVal;
        }
        return this;
    }
}
