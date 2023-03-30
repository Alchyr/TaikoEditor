package alchyr.taikoedit.editor.changes;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.hitobjects.Hit;
import alchyr.taikoedit.util.structures.Pair;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

//Repositioning dons and kats to match setting position
public class RepositionChange extends MapChange {
    private final PositionalObjectTreeMap<Hit> repositioned;
    private final HashMap<HitObject, Pair<Integer, Integer>> originalPositions;

    //all objects
    public RepositionChange(EditorBeatmap map)
    {
        super(map);

        this.repositioned = new PositionalObjectTreeMap<>();
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
    public RepositionChange(EditorBeatmap map, PositionalObjectTreeMap<PositionalObject> selected)
    {
        super(map);

        this.repositioned = new PositionalObjectTreeMap<>();
        this.originalPositions = new HashMap<>();

        for (Map.Entry<Long, ArrayList<PositionalObject>> stack : selected.entrySet()) {
            for (PositionalObject h : stack.getValue()) {
                if (h instanceof Hit) {
                    this.originalPositions.put((HitObject) h, new Pair<>(((Hit) h).x, ((Hit) h).y));
                    this.repositioned.add((Hit) h);
                }
            }
        }
    }

    @Override
    public MapChange undo() {
        for (ArrayList<Hit> stack : repositioned.values()) {
            for (Hit h : stack) {
                h.x = originalPositions.get(h).a;
                h.y = originalPositions.get(h).b;
            }
        }

        return this;
    }

    @Override
    public MapChange perform() {
        for (ArrayList<Hit> stack : repositioned.values()) {
            for (Hit h : stack) {
                h.updatePosition();
            }
        }

        return this;
    }

    public MapChange perform(Consumer<Hit> repositioner) {
        for (ArrayList<Hit> stack : repositioned.values()) {
            for (Hit h : stack) {
                repositioner.accept(h);
            }
        }

        return this;
    }
}