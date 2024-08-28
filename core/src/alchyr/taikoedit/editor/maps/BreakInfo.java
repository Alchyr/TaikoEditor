package alchyr.taikoedit.editor.maps;

import java.util.Objects;

public class BreakInfo {
    public long start, end;
    public boolean isTentative;

    public BreakInfo(BreakInfo breakPeriod) {
        this(breakPeriod.start, breakPeriod.end);
    }
    public BreakInfo(long start, long end) {
        this(start, end, false);
    }
    public BreakInfo(long start, long end, boolean isTentative) {
        this.start = start;
        this.end = end;
        this.isTentative = isTentative;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BreakInfo breakInfo = (BreakInfo) o;
        return start == breakInfo.start && end == breakInfo.end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return "2," + start + "," + end;
    }
}
