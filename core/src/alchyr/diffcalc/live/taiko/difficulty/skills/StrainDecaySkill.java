package alchyr.diffcalc.live.taiko.difficulty.skills;

import alchyr.diffcalc.live.DifficultyHitObject;

public abstract class StrainDecaySkill extends StrainSkill {
    protected final double SkillMultiplier;
    protected final double StrainDecayBase;
    protected double CurrentStrain;

    protected StrainDecaySkill(double skillMultiplier, double strainDecayBase) {
        SkillMultiplier = skillMultiplier;
        StrainDecayBase = strainDecayBase;
    }

    @Override
    protected double CalculateInitialStrain(double time, DifficultyHitObject current) {
        return CurrentStrain * strainDecay(time - current.previous(0).startTime);
    }

    @Override
    protected final double StrainValueAt(DifficultyHitObject current) {
        CurrentStrain *= strainDecay(current.deltaTime);
        CurrentStrain += StrainValueOf(current) * SkillMultiplier;

        return CurrentStrain;
    }

    protected abstract double StrainValueOf(DifficultyHitObject current);

    private double strainDecay(double ms) {
        return Math.pow(StrainDecayBase, ms / 1000);
    }
}
