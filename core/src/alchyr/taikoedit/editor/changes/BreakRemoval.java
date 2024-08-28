package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.BreakInfo;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.structures.Pair;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.Supplier;

public class BreakRemoval extends MapChange {
    private final BreakInfo breakSection;

    @Override
    public void send(DataOutputStream out) throws IOException {
        out.writeLong(breakSection.start);
        out.writeLong(breakSection.end);
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        long breakStart = in.readLong();
        long breakEnd = in.readLong();

        return ()->{
            for (BreakInfo breakSection : map.getBreaks()) {
                if (breakSection.start == breakStart && breakSection.end == breakEnd) {
                    return new BreakRemoval(map, breakSection);
                }
            }

            logger.warn("Received break removal that doesn't correspond to a break in the map");
            return null;
        };
    }

    public BreakRemoval(EditorBeatmap map, BreakInfo breakSection) {
        super(map, "Break Removal");
        this.breakSection = breakSection;
    }

    @Override
    public void undo() {
        map.getBreaks().add(breakSection);
        map.sortBreaks();
    }

    @Override
    public void perform() {
        map.getBreaks().remove(breakSection);
    }

    @Override
    public boolean isValid() {
        return map.getBreaks().contains(breakSection);
    }
}
