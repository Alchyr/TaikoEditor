package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.structures.Pair;

public class BreakRemoval extends MapChange {
    private Pair<Long, Long> breakSection;

    public BreakRemoval(EditorBeatmap map, Pair<Long, Long> breakSection) {
        super(map);
        this.breakSection = breakSection;
    }

    @Override
    public MapChange undo() {
        map.getBreaks().add(breakSection);
        map.sortBreaks();
        return this;
    }

    @Override
    public MapChange perform() {
        map.getBreaks().remove(breakSection);
        return this;
    }
}
