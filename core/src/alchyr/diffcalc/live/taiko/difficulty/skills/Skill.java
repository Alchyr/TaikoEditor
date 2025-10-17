package alchyr.diffcalc.live.taiko.difficulty.skills;

import alchyr.diffcalc.live.DifficultyHitObject;

public abstract class Skill {
    /// <summary>
    /// Process a <see cref="DifficultyHitObject"/> and update current strain values accordingly.
    /// </summary>
    public abstract void Process(DifficultyHitObject current);
    public abstract double DifficultyValue();
}
