package alchyr.diffcalc.live.taiko.difficulty.skills;

import alchyr.diffcalc.live.DifficultyCalculationUtils;
import alchyr.diffcalc.live.DifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.evaluators.ReadingEvaluator;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.taikoedit.editor.maps.components.hitobjects.Hit;

public class Reading extends StrainDecaySkill {
    public Reading() {
        super(1.0, 0.4);
    }

    private double currentStrain;

    @Override
    protected double StrainValueOf(DifficultyHitObject current) {
        if (!(current.baseObject instanceof Hit)) {
            return 0.0;
        }

        TaikoDifficultyHitObject taikoObject = (TaikoDifficultyHitObject) current;
        int index = taikoObject.colourData.monoStreak == null ? 0 : taikoObject.colourData.monoStreak.hitObjects.indexOf(taikoObject);

        currentStrain *= DifficultyCalculationUtils.Logistic(index, 4, -1 / 25.0, 0.5) + 0.5;

        currentStrain *= StrainDecayBase;
        currentStrain += ReadingEvaluator.EvaluateDifficultyOf(taikoObject) * SkillMultiplier;

        return currentStrain;
    }
}
