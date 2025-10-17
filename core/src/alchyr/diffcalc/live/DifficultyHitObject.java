package alchyr.diffcalc.live;

import alchyr.taikoedit.editor.maps.components.HitObject;

import java.util.List;

public abstract class DifficultyHitObject {
    public final List<DifficultyHitObject> difficultyHitObjects;
    public int index;
    public HitObject baseObject, lastObject;
    public double deltaTime;
    public double startTime;
    public double endTime;

    public DifficultyHitObject(HitObject base, HitObject last, double clockRate, List<DifficultyHitObject> objects, int index)
    {
        difficultyHitObjects = objects;
        this.index = index;

        baseObject = base;
        lastObject = last;

        deltaTime = (baseObject.getPos() - lastObject.getEndPos()) / clockRate;
        startTime = baseObject.getPos() / clockRate;
        endTime = baseObject.getEndPos() / clockRate;
    }

    public DifficultyHitObject previous(int backwardsIndex)
    {
        int targetIndex = index - (backwardsIndex + 1);
        return targetIndex >= 0 && targetIndex < difficultyHitObjects.size() ? difficultyHitObjects.get(targetIndex) : null;
    }

    public DifficultyHitObject next(int forwardsIndex)
    {
        int targetIndex = index + (forwardsIndex + 1);
        return targetIndex >= 0 && targetIndex < difficultyHitObjects.size() ? difficultyHitObjects.get(targetIndex) : null;
    }
}