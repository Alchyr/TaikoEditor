package alchyr.diffcalc.taiko.difficulty.skills;

import alchyr.diffcalc.DifficultyHitObject;

import java.util.ArrayList;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public abstract class Skill {
    /// <summary>
    /// The peak strain for each <see cref="DifficultyCalculator.SectionLength"/> section of the beatmap.
    /// </summary>
    public ArrayList<Double> StrainPeaks = new ArrayList<>();

    /// <summary>
    /// Strain values are multiplied by this number for the given skill. Used to balance the value of different skills between each other.
    /// </summary>
    protected abstract double SkillMultiplier();

    /// <summary>
    /// Determines how quickly strain decays for the given skill.
    /// For example a value of 0.15 indicates that strain decays to 15% of its original value in one second.
    /// </summary>
    protected abstract double StrainDecayBase();

    /// <summary>
    /// The weight by which each strain value decays.
    /// </summary>
    protected double DecayWeight = 0.9;

    /// <summary>
    /// <see cref="DifficultyHitObject"/>s that were processed previously. They can affect the strain values of the following objects.
    /// </summary>
    protected DifficultyHitObject[] Previous = new DifficultyHitObject[2]; // Contained objects not used yet

    /// <summary>
    /// The current strain level.
    /// </summary>
    public double CurrentStrain = 0;

    private double currentSectionPeak = 0; // We also keep track of the peak strain level in the current section.

    /// <summary>
    /// Process a <see cref="DifficultyHitObject"/> and update current strain values accordingly.
    /// </summary>
    public void Process(DifficultyHitObject current)
    {
        CurrentStrain *= strainDecay(current.deltaTime);
        CurrentStrain += StrainValueOf(current) * SkillMultiplier();

        currentSectionPeak = Math.max(CurrentStrain, currentSectionPeak);

        Previous[1] = Previous[0];
        Previous[0] = current;
    }

    /// <summary>
    /// Saves the current peak strain level to the list of strain peaks, which will be used to calculate an overall difficulty.
    /// </summary>
    public void SaveCurrentPeak()
    {
        if (Previous[0] != null)
            StrainPeaks.add(currentSectionPeak);
    }

    /// <summary>
    /// Sets the initial strain level for a new section.
    /// </summary>
    /// <param name="time">The beginning of the new section in milliseconds.</param>
    public void StartNewSectionFrom(double time)
    {
        // The maximum strain of the new section is not zero by default, strain decays as usual regardless of section boundaries.
        // This means we need to capture the strain level at the beginning of the new section, and use that as the initial peak level.
        if (Previous[0] != null)
            currentSectionPeak = GetPeakStrain(time);
    }

    /// <summary>
    /// Retrieves the peak strain at a point in time.
    /// </summary>
    /// <param name="time">The time to retrieve the peak strain at.</param>
    /// <returns>The peak strain.</returns>
    protected double GetPeakStrain(double time)
    {
        return CurrentStrain * strainDecay(time - Previous[0].baseObject.getPos());
    }

    /// <summary>
    /// Returns the calculated difficulty value representing all processed <see cref="DifficultyHitObject"/>s.
    /// </summary>
    public double DifficultyValue()
    {
        if (StrainPeaks.isEmpty())
            return 0;

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

    /// <summary>
    /// Calculates the strain value of a <see cref="DifficultyHitObject"/>. This value is affected by previously processed objects.
    /// </summary>
    protected abstract double StrainValueOf(DifficultyHitObject current);

    private double strainDecay(double ms) {
        return Math.pow(StrainDecayBase(), ms / 1000);
    }
}
