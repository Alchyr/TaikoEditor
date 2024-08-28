package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.BreakInfo;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.structures.Pair;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.Supplier;

public class BreakAdjust extends MapChange {
    private final BreakInfo breakSection;

    private final boolean start;
    private final long origVal, newVal;

    @Override
    public void send(DataOutputStream out) throws IOException {
        out.writeBoolean(start);
        out.writeLong(start ? origVal : breakSection.start); //start was changed, so send original value
        out.writeLong(!start ? origVal : breakSection.end);
        out.writeLong(newVal);
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        boolean start = in.readBoolean();
        long breakStart = in.readLong();
        long breakEnd = in.readLong();
        long newPos = in.readLong();

        return ()->{
            for (BreakInfo breakSection : map.getBreaks()) {
                if (breakSection.start == breakStart && breakSection.end == breakEnd) {
                    return new BreakAdjust(map, breakSection, start, start ? breakStart : breakEnd, newPos);
                }
            }

            logger.warn("Received break change that doesn't correspond to a break in the map");

            return null;
        };
    }

    public BreakAdjust(EditorBeatmap map, BreakInfo breakSection, boolean start, long origVal, long newVal) {
        super(map, "Break Adjustment");
        this.breakSection = breakSection;

        this.start = start;
        this.origVal = origVal;
        this.newVal = newVal;
    }

    @Override
    public void undo() {
        if (start) {
            breakSection.start = origVal;
        }
        else {
            breakSection.end = origVal;
        }
    }

    @Override
    public void perform() {
        if (start) {
            breakSection.start = newVal;
        }
        else {
            breakSection.end = newVal;
        }
    }

    @Override
    public boolean isValid() {
        if (!map.getBreaks().contains(breakSection)) return false;
        return (start ? breakSection.start : breakSection.end) == origVal;
    }
}
