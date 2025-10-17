package alchyr.diffcalc.live.taiko.difficulty.skills;

import alchyr.diffcalc.live.DifficultyCalculationUtils;
import alchyr.diffcalc.live.DifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.evaluators.StaminaEvaluator;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;

public class Stamina extends StrainSkill {
    private static final double skillMultiplier = 1.1;
    private static final double strainDecayBase = 0.4;

    public final boolean SingleColourStamina;
    private final boolean isConvert;

    private double currentStrain;

    /// <summary>
    /// Creates a <see cref="Stamina"/> skill.
    /// </summary>
    /// <param name="mods">Mods for use in skill calculations.</param>
    /// <param name="singleColourStamina">Reads when Stamina is from a single coloured pattern.</param>
    /// <param name="isConvert">Determines if the currently evaluated beatmap is converted.</param>
    public Stamina(boolean singleColourStamina, boolean isConvert)
    {
        SingleColourStamina = singleColourStamina;
        this.isConvert = isConvert;
    }

    private double strainDecay(double ms) {
        return Math.pow(strainDecayBase, ms / 1000);
    }

    @Override
    protected double StrainValueAt(DifficultyHitObject current) {
        currentStrain *= strainDecay(current.deltaTime);
        double staminaDifficulty = StaminaEvaluator.EvaluateDifficultyOf(current) * skillMultiplier;

        // Safely prevents previous strains from shifting as new notes are added.
        TaikoDifficultyHitObject currentObject = (TaikoDifficultyHitObject) current;
        int index = currentObject.colourData.monoStreak == null ? 0 : currentObject.colourData.monoStreak.hitObjects.indexOf(currentObject);

        double monoLengthBonus = isConvert ? 1.0 : 1.0 + 0.5 * DifficultyCalculationUtils.ReverseLerp(index, 5, 20);

        // Mono-streak bonus is only applied to colour-based stamina to reward longer sequences of same-colour hits within patterns.
        if (!SingleColourStamina) {
            staminaDifficulty *= monoLengthBonus;
            ((TaikoDifficultyHitObject) current).debugData[2] = staminaDifficulty;
        }

        currentStrain += staminaDifficulty;

        // For converted maps, difficulty often comes entirely from long mono streams with no colour variation.
        // To avoid over-rewarding these maps based purely on stamina strain, we dampen the strain value once the index exceeds 10.
        return SingleColourStamina ? DifficultyCalculationUtils.Logistic(-(index - 10) / 2.0, currentStrain) : currentStrain;
    }

    @Override
    protected double CalculateInitialStrain(double time, DifficultyHitObject current) {
        return SingleColourStamina ? 0 : currentStrain * strainDecay(time - current.previous(0).startTime);
    }
}
