package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.ILongObject;
import alchyr.taikoedit.util.structures.MapObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.Supplier;

public class DurationChange extends MapChange {
    private final ILongObject changed;
    private final long changeAmount;

    @Override
    public void send(DataOutputStream out) throws IOException {
        writeObject(out, (MapObject) changed);
        out.writeLong(changeAmount);
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        Supplier<MapObject> objSupplier = readObject(in, map);
        long changeAmount = in.readLong();

        return ()->{
            MapObject obj = objSupplier.get();

            if (!(obj instanceof ILongObject)) {
                logger.warn("Received duration change that doesn't correspond to a long object in the map");
                return null;
            }

            return new DurationChange(map, (ILongObject) obj, changeAmount);
        };
    }

    @Override
    public boolean isValid() {
        return map.objects.containsKeyedValue(((MapObject) changed).getPos(), changed);
    }

    public DurationChange(EditorBeatmap map, ILongObject changed, long changeAmount)
    {
        super(map, "Duration Change");
        this.changed = changed;
        this.changeAmount = changeAmount;
    }

    @Override
    public void undo() {
        changed.setDuration(changed.getDuration() - changeAmount);
        map.adjustedEnd(changed, -changeAmount);
    }

    @Override
    public void perform() {
        changed.setDuration(changed.getDuration() + changeAmount);
        map.adjustedEnd(changed, changeAmount);
    }
}
