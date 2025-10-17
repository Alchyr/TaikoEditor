package alchyr.diffcalc.live.taiko.difficulty.skills;

import alchyr.diffcalc.live.DifficultyCalculationUtils;
import alchyr.diffcalc.live.DifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.evaluators.RhythmEvaluator;
import alchyr.diffcalc.live.taiko.difficulty.evaluators.StaminaEvaluator;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;

public class Rhythm extends StrainDecaySkill {
    private final double greatHitWindow;

    public Rhythm(double greatHitWindow) {
        super(1.0, 0.4);

        this.greatHitWindow = greatHitWindow;
    }

    @Override
    protected double StrainValueOf(DifficultyHitObject current) {
        double difficulty = RhythmEvaluator.EvaluateDifficultyOf(current, greatHitWindow);

        double staminaDifficulty = StaminaEvaluator.EvaluateDifficultyOf(current) - 0.5;
        difficulty *= DifficultyCalculationUtils.Logistic(staminaDifficulty, 1 / 15.0, 50.0);

        ((TaikoDifficultyHitObject) current).debugData[1] = difficulty;

        return difficulty;
    }
}
