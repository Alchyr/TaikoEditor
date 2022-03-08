package alchyr.diffcalc;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.editor.maps.components.HitObject;

public abstract class DifficultyHitObject {
    public HitObject baseObject, lastObject;

    public double deltaTime;

    public DifficultyHitObject()
    {
        baseObject = null;
        lastObject = null;

        deltaTime = 0;
    }

    public DifficultyHitObject(HitObject current)
    {
        this.baseObject = current;
        this.lastObject = null;

        this.deltaTime = 0;
    }

    public DifficultyHitObject(HitObject current, HitObject previous)
    {
        this.baseObject = current;
        this.lastObject = previous;

        this.deltaTime = (baseObject.getPos() - lastObject.getPos()) / EditorLayer.music.getTempo();
    }
}

//add preprocessing in taiko difficulty hit object