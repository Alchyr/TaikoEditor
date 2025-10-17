package alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour.data;

import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.hitobjects.Hit;

import java.util.ArrayList;
import java.util.List;

public class MonoStreak {
    public final List<TaikoDifficultyHitObject> hitObjects = new ArrayList<>();
    public AlternatingMonoPattern parent = null;
    public int index;
    public TaikoDifficultyHitObject firstObject() {
        return hitObjects.get(0);
    }
    public TaikoDifficultyHitObject lastObject() {
        return hitObjects.get(hitObjects.size() - 1);
    }
    public int HitType() {
        HitObject obj = firstObject().baseObject;
        if (obj instanceof Hit) {
            return ((Hit) obj).isRim() ? 2 : 1;
        }
        return 0;
    }
    public int runLength() {
        return hitObjects.size();
    }
}
