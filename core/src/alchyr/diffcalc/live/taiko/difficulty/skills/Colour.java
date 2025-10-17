package alchyr.diffcalc.live.taiko.difficulty.skills;

import alchyr.diffcalc.live.DifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.evaluators.ColourEvaluator;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;

public class Colour extends StrainDecaySkill {

    public Colour() {
        super(0.12, 0.8);
    }

    @Override
    protected double StrainValueOf(DifficultyHitObject current) {
        double diff = ColourEvaluator.EvaluateDifficultyOf(current);
        ((TaikoDifficultyHitObject) current).debugData[0] = diff;
        return diff;
    }
}
