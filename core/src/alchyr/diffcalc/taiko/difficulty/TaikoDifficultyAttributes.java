package alchyr.diffcalc.taiko.difficulty;

import alchyr.diffcalc.DifficultyAttributes;
import alchyr.diffcalc.taiko.difficulty.skills.Skill;

public class TaikoDifficultyAttributes extends DifficultyAttributes {
    public double ContinuousRating = 0;
    public double BurstRating = 0;

    public TaikoDifficultyAttributes(Skill[] skills, double sr) {
        super(skills, sr);
    }
}
