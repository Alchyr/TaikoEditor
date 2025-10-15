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

public class OmitBarlineChange extends MapChange {
    private final List<TimingPoint> modifiedLines;
    private final HashMap<TimingPoint, Boolean> wasOmitted;
    private final boolean toOmitted;

    @Override
    public void send(DataOutputStream out) throws IOException {
        writeObjects(out, modifiedLines.size(), map.allPoints, modifiedLines);
        out.writeBoolean(toOmitted);
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        KnownAmountSupplier<List<MapObject>> mapObjectsSupplier = readObjects(in, map);
        boolean toOmitted = in.readBoolean();

        if (mapObjectsSupplier == null) return null;

        return ()->{
            List<MapObject> mapObjects = mapObjectsSupplier.get();
            List<TimingPoint> timingPoints = new ArrayList<>();

            for (MapObject o : mapObjects) {
                if (!(o instanceof TimingPoint)) {
                    TaikoEditor.editorLogger.warn("Attempted to construct OmittedBarlineChange with non-TimingPoint");
                    return null;
                }
                timingPoints.add((TimingPoint) o);
            }

            return new KiaiChange(map, timingPoints, toOmitted);
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

    public OmitBarlineChange(EditorBeatmap map, List<TimingPoint> modifiedLines, boolean toOmitted)
    {
        super(map, toOmitted ? "Omit Barlines" : "Unomit Barlines");

        this.modifiedLines = modifiedLines;
        this.toOmitted = toOmitted;
        wasOmitted = new HashMap<>();

        for (TimingPoint p : modifiedLines) {
            wasOmitted.put(p, p.omitted);
        }
    }

    @Override
    public void undo() {
        for (TimingPoint p : modifiedLines) {
            p.omitted = wasOmitted.get(p);
        }

        map.regenerateDivisor(true);
        map.gameplayChanged();
    }
    @Override
    public void perform() {
        for (TimingPoint p : modifiedLines) {
            p.omitted = toOmitted;
        }

        map.regenerateDivisor(true);
        map.gameplayChanged();
    }

    @Override
    public MapChange reconstruct() {
        return new OmitBarlineChange(map, modifiedLines, toOmitted);
    }
}