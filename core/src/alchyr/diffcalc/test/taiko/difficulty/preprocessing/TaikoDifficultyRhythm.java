package alchyr.diffcalc.test.taiko.difficulty.preprocessing;

public class TaikoDifficultyRhythm {
    private int numerator, denominator;

    /// <summary>
    /// The difficulty multiplier associated with this rhythm change.
    /// </summary>
    public final double Difficulty;

    /// <summary>
    /// The ratio of current <see cref="osu.Game.Rulesets.Difficulty.Preprocessing.DifficultyHitObject.DeltaTime"/>
    /// to previous <see cref="osu.Game.Rulesets.Difficulty.Preprocessing.DifficultyHitObject.DeltaTime"/> for the rhythm change.
    /// A <see cref="Ratio"/> above 1 indicates a slow-down; a <see cref="Ratio"/> below 1 indicates a speed-up.
    /// </summary>
    public final double Ratio;

    /// <summary>
    /// Creates an object representing a rhythm change.
    /// </summary>
    /// <param name="numerator">The numerator for <see cref="Ratio"/>.</param>
    /// <param name="denominator">The denominator for <see cref="Ratio"/></param>
    /// <param name="difficulty">The difficulty multiplier associated with this rhythm change.</param>
    public TaikoDifficultyRhythm(int numerator, int denominator, double difficulty)
    {
        Ratio = numerator / (double)denominator;
        Difficulty = difficulty;

        this.numerator = numerator;
        this.denominator = denominator;
    }


    @Override
    public String toString() {
        return numerator + "/" + denominator;
    }
}
