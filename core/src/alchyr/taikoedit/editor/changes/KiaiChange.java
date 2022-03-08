package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;

import java.util.HashMap;
import java.util.List;

public class KiaiChange extends MapChange {
    private final List<TimingPoint> modifiedLines;
    private final HashMap<TimingPoint, Boolean> wasKiai;
    private final boolean toKiai;

    public KiaiChange(EditorBeatmap map, List<TimingPoint> modifiedLines, boolean toKiai)
    {
        super(map);

        this.modifiedLines = modifiedLines;
        this.toKiai = toKiai;
        wasKiai = new HashMap<>();

        for (TimingPoint p : modifiedLines) {
            wasKiai.put(p, p.kiai);
        }
    }

    @Override
    public MapChange undo() {
        for (TimingPoint p : modifiedLines) {
            p.kiai = wasKiai.get(p);
        }
        map.updateKiai(modifiedLines);
        return this;
    }
    @Override
    public MapChange perform() {
        for (TimingPoint p : modifiedLines) {
            p.kiai = toKiai;
        }
        map.updateKiai(modifiedLines);
        return this;
    }
}