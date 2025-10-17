package alchyr.diffcalc.live.taiko.difficulty.skills;

import alchyr.diffcalc.live.DifficultyHitObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class StrainSkill extends Skill {
    protected double DecayWeight() {
        return 0.9;
    }

    protected int SectionLength() {
        return 400;
    }

    private double currentSectionPeak, currentSectionEnd;

    private final List<Double> strainPeaks = new ArrayList<>();
    protected final List<Double> ObjectStrains = new ArrayList<>();

    protected StrainSkill() { //normally would have mods
        super();
    }

    protected abstract double StrainValueAt(DifficultyHitObject current);

    @Override
    public final void Process(DifficultyHitObject current) {
        if (current.index == 0)
            currentSectionEnd = Math.ceil(current.startTime / SectionLength()) * SectionLength();

        while (current.startTime > currentSectionEnd) {
            saveCurrentPeak();
            startNewSectionFrom(currentSectionEnd, current);
            currentSectionEnd += SectionLength();
        }

        double strain = StrainValueAt(current);
        currentSectionPeak = Math.max(strain, currentSectionPeak);

        ObjectStrains.add(strain);
    }

    public double CountTopWeightedStrains()
    {
        if (ObjectStrains.isEmpty()) return 0;

        double consistentTopStrain = DifficultyValue() / 10;

        if (consistentTopStrain == 0)
            return ObjectStrains.size();

        double sum = 0;
        for (double d : ObjectStrains) {
            sum += 1.1 / (1 + Math.exp(-10 * (d / consistentTopStrain - 0.88)));
        }
        return sum;
    }

    public void saveCurrentPeak()
    {
        strainPeaks.add(currentSectionPeak);
    }

    public void startNewSectionFrom(double time, DifficultyHitObject current)
    {
        currentSectionPeak = CalculateInitialStrain(time, current);
    }

    protected abstract double CalculateInitialStrain(double time, DifficultyHitObject current);

    public Iterable<Double> GetCurrentStrainPeaks() {
        List<Double> temp = new ArrayList<>(strainPeaks); //mimic behavior of .append creating a new enumerable instance rather than modifying original
        temp.add(currentSectionPeak);
        return temp;
    }

    public Iterable<Double> GetObjectStrains() {
        return ObjectStrains;
    }

    private final List<Double> temp = new ArrayList<>();
    @Override
    public double DifficultyValue() {
        double difficulty = 0;
        double weight = 1;

        temp.clear();
        GetCurrentStrainPeaks().forEach((peak)->{
            if (peak > 0) temp.add(peak);
        });

        temp.sort((Comparator.<Double>naturalOrder()).reversed());

        for (double strain : temp) {
            difficulty += strain * weight;
            weight *= DecayWeight();
        }

        return difficulty;
    }
}
