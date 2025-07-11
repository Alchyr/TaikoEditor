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
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class RimChange extends MapChange {
    private final List<Hit> toKat;
    private final List<Hit> toDon;

    @Override
    public void send(DataOutputStream out) throws IOException {
        writeObjects(out, toKat.size(), map.objects, toKat);
        writeObjects(out, toDon.size(), map.objects, toDon);
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        KnownAmountSupplier<List<MapObject>> toKatSupplier = readObjects(in, map);
        KnownAmountSupplier<List<MapObject>> toDonSupplier = readObjects(in, map);

        if (toKatSupplier == null || toDonSupplier == null) return null;

        return ()->{
            List<MapObject> toKat = toKatSupplier.get();
            List<MapObject> toDon = toDonSupplier.get();
            List<Hit> hitToKat = new ArrayList<>();
            List<Hit> hitToDon = new ArrayList<>();

            for (MapObject o : toKat) {
                if (!(o instanceof Hit)) {
                    TaikoEditor.editorLogger.warn("Attempted to construct RimChange with non-Hit");
                    return null;
                }
                hitToKat.add((Hit) o);
            }

            for (MapObject o : toDon) {
                if (!(o instanceof Hit)) {
                    TaikoEditor.editorLogger.warn("Attempted to construct RimChange with non-Hit");
                    return null;
                }
                hitToDon.add((Hit) o);
            }

            return new RimChange(map, hitToKat, hitToDon);
        };
    }

    @Override
    public boolean isValid() {
        for (HitObject obj : toKat) {
            if (!map.objects.containsKeyedValue(obj.getPos(), obj)) {
                return false;
            }
        }
        for (HitObject obj : toDon) {
            if (!map.objects.containsKeyedValue(obj.getPos(), obj)) {
                return false;
            }
        }
        return true;
    }


    public RimChange(EditorBeatmap map, List<Hit> toKat, List<Hit> toDon)
    {
        super(map, "Swap Color");

        this.toKat = toKat == null ? Collections.emptyList() : toKat;
        this.toDon = toDon == null ? Collections.emptyList() : toDon;
    }

    @Override
    public void undo() {
        for (Hit o : toKat) {
            o.setIsRim(false);
        }
        for (Hit o : toDon) {
            o.setIsRim(true);
        }
    }
    @Override
    public void perform() {
        for (Hit o : toKat) {
            o.setIsRim(true);
        }
        for (Hit o : toDon) {
            o.setIsRim(false);
        }
    }
}