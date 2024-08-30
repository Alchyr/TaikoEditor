package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.interfaces.KnownAmountSupplier;
import alchyr.taikoedit.util.structures.MapObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

public class KiaiChange extends MapChange {
    private final List<TimingPoint> modifiedLines;
    private final HashMap<TimingPoint, Boolean> wasKiai;
    private final boolean toKiai;

    @Override
    public void send(DataOutputStream out) throws IOException {
        writeObjects(out, modifiedLines.size(), map.allPoints, modifiedLines);
        out.writeBoolean(toKiai);
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        KnownAmountSupplier<List<MapObject>> mapObjectsSupplier = readObjects(in, map);
        boolean toKiai = in.readBoolean();

        if (mapObjectsSupplier == null) return null;

        return ()->{
            List<MapObject> mapObjects = mapObjectsSupplier.get();
            List<TimingPoint> timingPoints = new ArrayList<>();

            for (MapObject o : mapObjects) {
                if (!(o instanceof TimingPoint)) {
                    TaikoEditor.editorLogger.warn("Attempted to construct KiaiChange with non-TimingPoint");
                    return null;
                }
                timingPoints.add((TimingPoint) o);
            }

            return new KiaiChange(map, timingPoints, toKiai);
        };
    }

    @Override
    public boolean isValid() {
        for (TimingPoint obj : modifiedLines) {
            if (!map.allPoints.containsKeyedValue(obj.getPos(), obj)) {
                return false;
            }
        }
        return true;
    }

    public KiaiChange(EditorBeatmap map, List<TimingPoint> modifiedLines, boolean toKiai)
    {
        super(map, toKiai ? "Add Kiai" : "Remove Kiai");

        this.modifiedLines = modifiedLines;
        this.toKiai = toKiai;
        wasKiai = new HashMap<>();

        for (TimingPoint p : modifiedLines) {
            wasKiai.put(p, p.kiai);
        }
    }

    @Override
    public void undo() {
        for (TimingPoint p : modifiedLines) {
            p.kiai = wasKiai.get(p);
        }
        map.updateKiai(modifiedLines);
    }
    @Override
    public void perform() {
        for (TimingPoint p : modifiedLines) {
            p.kiai = toKiai;
        }
        map.updateKiai(modifiedLines);
    }

    @Override
    public MapChange reconstruct() {
        return new KiaiChange(map, modifiedLines, toKiai); //refresh the "wasKiai" map
    }
}