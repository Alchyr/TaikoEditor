package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.ILongObject;
import alchyr.taikoedit.util.interfaces.KnownAmountSupplier;
import alchyr.taikoedit.util.structures.MapObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class FinisherChange extends MapChange {
    private final List<HitObject> modifiedObjects;
    private final boolean toFinisher;

    @Override
    public void send(DataOutputStream out) throws IOException {
        writeObjects(out, modifiedObjects.size(), map.objects, modifiedObjects);
        out.writeBoolean(toFinisher);
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        KnownAmountSupplier<List<MapObject>> mapObjectsSupplier = readObjects(in, map);
        boolean toFinisher = in.readBoolean();

        if (mapObjectsSupplier == null) return null;

        return ()->{
            List<MapObject> mapObjects = mapObjectsSupplier.get();
            List<HitObject> hitObjects = new ArrayList<>();

            for (MapObject o : mapObjects) {
                if (!(o instanceof HitObject)) {
                    TaikoEditor.editorLogger.warn("Attempted to construct FinisherChange with non-HitObject");
                    return null;
                }
                hitObjects.add((HitObject) o);
            }

            return new FinisherChange(map, hitObjects, toFinisher);
        };
    }

    @Override
    public boolean isValid() {
        for (HitObject obj : modifiedObjects) {
            if (!map.objects.containsKeyedValue(obj.getPos(), obj)) {
                return false;
            }
        }
        return true;
    }

    public FinisherChange(EditorBeatmap map, List<HitObject> modifiedObjects, boolean toFinisher)
    {
        super(map, "Swap Finisher");

        this.modifiedObjects = modifiedObjects;
        this.toFinisher = toFinisher;
    }

    @Override
    public void undo() {
        for (HitObject o : modifiedObjects) {
            o.setIsFinish(!toFinisher);
        }
    }
    @Override
    public void perform() {
        for (HitObject o : modifiedObjects) {
            o.setIsFinish(toFinisher);
        }
    }
}