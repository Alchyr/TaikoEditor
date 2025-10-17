package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.util.interfaces.KnownAmountSupplier;
import alchyr.taikoedit.util.structures.MapObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

public class NewComboChange extends MapChange {
    private final List<HitObject> modifiedObjects;
    private final HashMap<HitObject, Boolean> wasNewCombo;
    private final boolean toNewCombo;

    @Override
    public void send(DataOutputStream out) throws IOException {
        writeObjects(out, modifiedObjects.size(), map.objects, modifiedObjects);
        out.writeBoolean(toNewCombo);
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        KnownAmountSupplier<List<MapObject>> mapObjectsSupplier = readObjects(in, map);
        boolean toNewCombo = in.readBoolean();

        if (mapObjectsSupplier == null) return null;

        return ()->{
            List<MapObject> mapObjects = mapObjectsSupplier.get();
            List<HitObject> hitObjects = new ArrayList<>();

            for (MapObject o : mapObjects) {
                if (!(o instanceof HitObject)) {
                    TaikoEditor.editorLogger.warn("Attempted to construct NewComboChange with non-HitObject");
                    return null;
                }
                hitObjects.add((HitObject) o);
            }

            return new NewComboChange(map, hitObjects, toNewCombo);
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

    public NewComboChange(EditorBeatmap map, List<HitObject> modifiedObjects, boolean toNewCombo)
    {
        super(map, toNewCombo ? "Add New Combo" : "Remove New Combo");

        this.modifiedObjects = modifiedObjects;
        this.toNewCombo = toNewCombo;
        wasNewCombo = new HashMap<>();

        for (HitObject o : modifiedObjects) {
            wasNewCombo.put(o, o.newCombo);
        }
    }

    @Override
    public void undo() {
        for (HitObject o : modifiedObjects) {
            o.newCombo = wasNewCombo.get(o);
        }

        map.gameplayChanged();
    }
    @Override
    public void perform() {
        for (HitObject o : modifiedObjects) {
            o.newCombo = toNewCombo;
        }

        map.gameplayChanged();
    }

    @Override
    public MapChange reconstruct() {
        return new NewComboChange(map, modifiedObjects, toNewCombo);
    }
}