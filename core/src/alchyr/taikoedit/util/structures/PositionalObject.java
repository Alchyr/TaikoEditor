package alchyr.taikoedit.util.structures;

public abstract class PositionalObject implements Comparable<PositionalObject> {
    public int pos = 0;

    @Override
    public int compareTo(PositionalObject o) {
        return Integer.compare(pos, o.pos);
    }
}
