package alchyr.diffcalc.test.taiko.difficulty.skills;

import alchyr.diffcalc.test.DifficultyHitObject;
import alchyr.diffcalc.test.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.taikoedit.editor.maps.components.HitObject;

import static alchyr.taikoedit.TaikoEditor.editorLogger;
import static java.lang.Math.cos;

public class Burst extends Skill {
    private static final double DECAY = 0.01;
    private static final double OPPOSITE_VALUE = 0.8;

    private static final double VALUE_SCALE = 1.5; //scales value of individual objects
    private static final double RESULT_SCALE = 12; //scales adjusted cumulative value

    private int sameColorLength = 1;
    private TaikoDifficultyHitObject.HitType previousHitType = null;

    private double donStrain = 0;
    private double katStrain = 0;

    @Override
    protected double SkillMultiplier() {
        return 1;
    }

    @Override
    protected double StrainDecayBase() {
        return 0;
    }

    @Override
    protected double StrainValueOf(DifficultyHitObject current) {
        double decay = Math.pow(DECAY, current.deltaTime / 1000);

        donStrain *= decay;
        katStrain *= decay;

        if (current.baseObject.type != HitObject.HitObjectType.CIRCLE) {
            sameColorLength = 0;
            previousHitType = null;
            return 0.0;
        }
        if (current.deltaTime >= 500) {
            sameColorLength = 0;
            donStrain = 0;
            katStrain = 0;
            previousHitType = null;
        }

        TaikoDifficultyHitObject taikoCurrent = (TaikoDifficultyHitObject)current;

        if (current.lastObject.type == HitObject.HitObjectType.CIRCLE && taikoCurrent.hitType != previousHitType) {
            sameColorLength = 0;
        }
        previousHitType = taikoCurrent.hitType;

        //Last object is not a circle or is opposite color, then length is at 0, and this is 1
        ++sameColorLength;

        double value;

        value = 0.2*(sameColorLength + 6)*(5-0.2*cos(Math.PI * sameColorLength)/(sameColorLength + 6));
        value /= 0.49 * sameColorLength + 3;
        value -= 1.9;
        value *= 4;
        value = Math.min(value, 0.515) * VALUE_SCALE;
        /*value = 0.05 - (0.1 / (3 * sameColorLength + 3))*cos(Math.PI * sameColorLength);
        value *= sameColorLength > 2 ? 10 : 6;*/

        if (taikoCurrent.hitType == TaikoDifficultyHitObject.HitType.Rim) {
            katStrain += value;
            donStrain += OPPOSITE_VALUE * value;
            value = katStrain;
        }
        else {
            donStrain += value;
            katStrain += OPPOSITE_VALUE * value;
            value = donStrain;
        }

        taikoCurrent.BURST_BASE = value;
        taikoCurrent.BURST_DEBUG = RESULT_SCALE * Math.pow(value, 4) / Math.pow(value + 1.7, 3.9);
        return taikoCurrent.BURST_DEBUG;
    }

    @Override
    public double DifficultyValue() {
        if (StrainPeaks.isEmpty())
            return 0;

        //Find a better method for this.

        double difficulty = 0;
        double weight = 1;

        // Difficulty is the weighted sum of the highest strains from every section.
        // We're sorting from highest to lowest strain.
        StrainPeaks.sort((a, b)->Double.compare(b, a));
        editorLogger.info(this.getClass().getSimpleName() + " highest peak: " + StrainPeaks.get(0));
        for (double strain : StrainPeaks)
        {
            difficulty += strain * weight;
            weight *= DecayWeight;
        }

        return difficulty;
    }
}
