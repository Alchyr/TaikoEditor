package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.hitobjects.Hit;
import alchyr.taikoedit.util.interfaces.KnownAmountSupplier;
import alchyr.taikoedit.util.structures.MapObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class RimChange extends MapChange {
    private final List<Hit> modifiedObjects;
    private final boolean toRim;

    @Override
    public void send(DataOutputStream out) throws IOException {
        writeObjects(out, modifiedObjects.size(), map.objects, modifiedObjects);
        out.writeBoolean(toRim);
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        KnownAmountSupplier<List<MapObject>> mapObjectsSupplier = readObjects(in, map);
        boolean toRim = in.readBoolean();

        if (mapObjectsSupplier == null) return null;

        return ()->{
            List<MapObject> mapObjects = mapObjectsSupplier.get();
            List<Hit> hitObjects = new ArrayList<>();

            for (MapObject o : mapObjects) {
                if (!(o instanceof Hit)) {
                    TaikoEditor.editorLogger.warn("Attempted to construct RimChange with non-Hit");
                    return null;
                }
                hitObjects.add((Hit) o);
            }

            return new RimChange(map, hitObjects, toRim);
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


    public RimChange(EditorBeatmap map, List<Hit> modifiedObjects, boolean toRim)
    {
        super(map, "Swap Color");

        this.modifiedObjects = modifiedObjects;
        this.toRim = toRim;
    }

    @Override
    public void undo() {
        for (Hit o : modifiedObjects)
            o.setIsRim(!toRim);
    }
    @Override
    public void perform() {
        for (Hit o : modifiedObjects)
            o.setIsRim(toRim);
    }
}