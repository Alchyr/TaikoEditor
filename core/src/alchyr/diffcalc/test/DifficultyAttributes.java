package alchyr.diffcalc.test;

import alchyr.diffcalc.test.taiko.difficulty.skills.Skill;

public class DifficultyAttributes {
    public Skill[] Skills;

    public double StarRating;
    public int MaxCombo;

    public DifficultyAttributes()
    {
    }

    public DifficultyAttributes(Skill[] skills, double starRating)
    {
        Skills = skills;
        StarRating = starRating;
    }
}
