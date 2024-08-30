package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.hitobjects.Hit;
import alchyr.taikoedit.util.structures.Pair;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

//Repositioning dons and kats to match setting position
public class RepositionChange extends MapChange {
    private final MapObjectTreeMap<Hit> repositioned;
    private final HashMap<HitObject, Pair<Integer, Integer>> originalPositions;

    //Will not be consistent over network, but not worth making consistent.
    @Override
    public void send(DataOutputStream out) throws IOException {
    }

    public static Supplier<MapChange> build(EditorBeatmap map, DataInputStream in, String nameKey) throws IOException {
        return ()->new RepositionChange(map);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    public RepositionChange(EditorBeatmap map)
    {
        super(map, "Reposition Objects");

        this.repositioned = new MapObjectTreeMap<>();
        this.originalPositions = new HashMap<>();

        for (Map.Entry<Long, ArrayList<HitObject>> stack : map.objects.entrySet()) {
            for (HitObject h : stack.getValue()) {
                if (h instanceof Hit) {
                    this.originalPositions.put(h, new Pair<>(h.x, h.y));
                    this.repositioned.add((Hit) h);
                }
            }
        }
    }
    public RepositionChange(EditorBeatmap map, MapObjectTreeMap<MapObject> selected)
    {
        super(map, "Reposition Objects");

        this.repositioned = new MapObjectTreeMap<>();
        this.originalPositions = new HashMap<>();

        for (Map.Entry<Long, ArrayList<MapObject>> stack : selected.entrySet()) {
            for (MapObject h : stack.getValue()) {
                if (h instanceof Hit) {
                    this.originalPositions.put((HitObject) h, new Pair<>(((Hit) h).x, ((Hit) h).y));
                    this.repositioned.add((Hit) h);
                }
            }
        }
    }

    @Override
    public void undo() {
        for (ArrayList<Hit> stack : repositioned.values()) {
            for (Hit h : stack) {
                h.x = originalPositions.get(h).a;
                h.y = originalPositions.get(h).b;
            }
        }
    }

    @Override
    public void perform() {
        for (ArrayList<Hit> stack : repositioned.values()) {
            for (Hit h : stack) {
                h.updatePosition();
            }
        }
    }

    public MapChange perform(Consumer<Hit> repositioner) {
        for (ArrayList<Hit> stack : repositioned.values()) {
            for (Hit h : stack) {
                repositioner.accept(h);
            }
        }

        return this;
    }

    @Override
    public MapChange reconstruct() {
        return new RepositionChange(map);
    }
}