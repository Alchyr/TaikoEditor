package alchyr.diffcalc.live.taiko;

import alchyr.diffcalc.live.DifficultyAttributes;
import alchyr.diffcalc.live.DifficultyCalculationUtils;
import alchyr.diffcalc.live.DifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour.TaikoColourDifficultyPreprocessor;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.rhythm.TaikoRhythmDifficultyPreprocessor;
import alchyr.diffcalc.live.taiko.difficulty.skills.*;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.hitobjects.Hit;

import java.util.*;


public class TaikoDifficultyCalculator {
    //Version 20250306

    private static final double difficulty_multiplier = 0.084375;
    private static final double rhythm_skill_multiplier = 0.750 * difficulty_multiplier;
    private static final double reading_skill_multiplier = 0.100 * difficulty_multiplier;
    private static final double colour_skill_multiplier = 0.375 * difficulty_multiplier;
    private static final double stamina_skill_multiplier = 0.445 * difficulty_multiplier;

    public static DifficultyAttributes calculateDifficulty(EditorBeatmap map, Map<HitObject, TaikoDifficultyHitObject> calculationInfo)
    {
        calculationInfo.clear();

        TaikoDifficultyCalculator c = new TaikoDifficultyCalculator(map, calculationInfo);

        return c.calculate(1);
    }

    private Map<HitObject, TaikoDifficultyHitObject> calculationInfo;

    private double strainLengthBonus;
    private double patternMultiplier;

    private boolean isRelax;
    private boolean isConvert;

    private EditorBeatmap beatmap;
    private int maxCombo = 0;

    private TaikoDifficultyCalculator(EditorBeatmap map, Map<HitObject, TaikoDifficultyHitObject> calculationInfo)
    {
        this.beatmap = map;
        this.calculationInfo = calculationInfo;
        calculationInfo.clear();
    }

    private Skill[] CreateSkills(double clockRate) {
        isConvert = false;
        isRelax = false;

        return new Skill[]
        {
                new Rhythm((Math.floor(difficultyRange(beatmap.getFullMapInfo().od, 50, 35, 20)) - 0.5) / clockRate),
                new Reading(),
                new Colour(),
                new Stamina(false, isConvert),
                new Stamina(true, isConvert)
        };
    }

    private DifficultyAttributes calculate(double clockRate)
    {
        Skill[] skills = CreateSkills(clockRate);

        if (beatmap.objects.isEmpty())
            return CreateDifficultyAttributes(skills);

        List<DifficultyHitObject> difficultyHitObjects = CreateDifficultyHitObjects(clockRate);

        for (DifficultyHitObject hitObject : difficultyHitObjects) {
            for (Skill skill : skills) {
                skill.Process(hitObject);
            }
        }

        return CreateDifficultyAttributes(skills);
    }

    private List<DifficultyHitObject> CreateDifficultyHitObjects(double clockRate)
    {
        List<DifficultyHitObject> taikoDifficultyHitObjects = new ArrayList<>();
        List<TaikoDifficultyHitObject> centreObjects = new ArrayList<>();
        List<TaikoDifficultyHitObject> rimObjects = new ArrayList<>();
        List<TaikoDifficultyHitObject> noteObjects = new ArrayList<>();

        HitObject previous = null;

        maxCombo = 0;
        int i = 0;
        TaikoDifficultyHitObject difficultyObject;
        for (ArrayList<HitObject> stack : beatmap.objects.values()) {
            for (HitObject o : stack) {
                if (i >= 2) {
                    taikoDifficultyHitObjects.add(difficultyObject = new TaikoDifficultyHitObject(
                            o,
                            previous,
                            1,
                            taikoDifficultyHitObjects,
                            centreObjects,
                            rimObjects,
                            noteObjects,
                            taikoDifficultyHitObjects.size(),
                            beatmap
                    ));

                    calculationInfo.put(o, difficultyObject);
                }
                else {
                    if (o instanceof Hit) {
                        ++maxCombo;
                    }
                }
                previous = o;
                ++i;
            }
        }

        maxCombo += noteObjects.size();

        TaikoColourDifficultyPreprocessor.processAndAssign(taikoDifficultyHitObjects);
        TaikoRhythmDifficultyPreprocessor.processAndAssign(noteObjects);

        return taikoDifficultyHitObjects;
    }


    private DifficultyAttributes CreateDifficultyAttributes(Skill[] skills)
    {
        if (beatmap.objects.isEmpty())
            return new TaikoDifficultyAttributes();

        Rhythm rhythm = (Rhythm) skills[0];
        Reading reading = (Reading) skills[1];
        Colour colour = (Colour) skills[2];
        Stamina stamina = (Stamina) skills[3];
        Stamina singleColourStamina = (Stamina) skills[4];

        double rhythmSkill = rhythm.DifficultyValue() * rhythm_skill_multiplier;
        double readingSkill = reading.DifficultyValue() * reading_skill_multiplier;
        double colourSkill = colour.DifficultyValue() * colour_skill_multiplier;
        double staminaSkill = stamina.DifficultyValue() * stamina_skill_multiplier;
        double monoStaminaSkill = singleColourStamina.DifficultyValue() * stamina_skill_multiplier;
        double monoStaminaFactor = staminaSkill == 0 ? 1 : Math.pow(monoStaminaSkill / staminaSkill, 5);

        double staminaDifficultStrains = stamina.CountTopWeightedStrains();

        // As we don't have pattern integration in osu!taiko, we apply the other two skills relative to rhythm.
        patternMultiplier = Math.pow(staminaSkill * colourSkill, 0.10);

        strainLengthBonus = 1 + 0.15 * DifficultyCalculationUtils.ReverseLerp(staminaDifficultStrains, 1000, 1555);

        double combinedRating = combinedDifficultyValue(rhythm, reading, colour, stamina);
        double consistencyFactor;

        if (combinedRating == 0) {
            consistencyFactor = 0;
        }
        else {
            List<Double> hitObjectStrainPeaks = getHitObjectStrainPeaks(rhythm, reading, colour, stamina);

            if (hitObjectStrainPeaks.isEmpty()) {
                consistencyFactor = 0;
                combinedRating = 0;
            }
            else {
                hitObjectStrainPeaks.sort(Comparator.<Double>naturalOrder().reversed());
                int count = 1 + hitObjectStrainPeaks.size() / 20;
                double topAverageHitObjectStrain = 0;
                for (int i = 0; i < count; ++i) {
                    topAverageHitObjectStrain += hitObjectStrainPeaks.get(i);
                }
                topAverageHitObjectStrain /= count;

                double sum = 0;
                for (int i = 0; i < hitObjectStrainPeaks.size(); ++i) {
                    sum += hitObjectStrainPeaks.get(i);
                }

                consistencyFactor = sum / (topAverageHitObjectStrain * hitObjectStrainPeaks.size());
            }
        }



        double starRating = rescale(combinedRating * 1.4);

        // Calculate proportional contribution of each skill to the combinedRating.
        double skillRating = starRating / (rhythmSkill + readingSkill + colourSkill + staminaSkill);

        double rhythmDifficulty = rhythmSkill * skillRating;
        double readingDifficulty = readingSkill * skillRating;
        double colourDifficulty = colourSkill * skillRating;
        double staminaDifficulty = staminaSkill * skillRating;
        double mechanicalDifficulty = colourDifficulty + staminaDifficulty; // Mechanical difficulty is the sum of colour and stamina difficulties.

        TaikoDifficultyAttributes attributes = new TaikoDifficultyAttributes();

        attributes.StarRating = starRating;
        attributes.MechanicalDifficulty = mechanicalDifficulty;
        attributes.RhythmDifficulty = rhythmDifficulty;
        attributes.ReadingDifficulty = readingDifficulty;
        attributes.ColourDifficulty = colourDifficulty;
        attributes.StaminaDifficulty = staminaDifficulty;
        attributes.MonoStaminaFactor = monoStaminaFactor;
        attributes.StaminaTopStrains = staminaDifficultStrains;
        attributes.ConsistencyFactor = consistencyFactor;
        attributes.MaxCombo = maxCombo;

        return attributes;
    }

    private double combinedDifficultyValue(Rhythm rhythm, Reading reading, Colour colour, Stamina stamina) {
        List<Double> peaks = combinePeaks(
                rhythm.GetCurrentStrainPeaks(),
                reading.GetCurrentStrainPeaks(),
                colour.GetCurrentStrainPeaks(),
                stamina.GetCurrentStrainPeaks());

        if (peaks.isEmpty()) {
            return 0;
        }

        double difficulty = 0;
        double weight = 1;

        peaks.sort(Comparator.<Double>naturalOrder().reversed());
        for (double strain : peaks) {
            difficulty += strain * weight;
            weight *= 0.9;
        }

        return difficulty;
    }

    private List<Double> getHitObjectStrainPeaks(Rhythm rhythm, Reading reading, Colour colour, Stamina stamina) {
        return combinePeaks(
                rhythm.GetObjectStrains(),
                reading.GetObjectStrains(),
                colour.GetObjectStrains(),
                stamina.GetObjectStrains());
    }

    private List<Double> combinePeaks(Iterable<Double> rhythmPeaks, Iterable<Double> readingPeaks, Iterable<Double> colourPeaks, Iterable<Double> staminaPeaks)
    {
        List<Double> combinedPeaks = new ArrayList<>();

        Iterator<Double> rhythm = rhythmPeaks.iterator();
        Iterator<Double> reading = readingPeaks.iterator();
        Iterator<Double> colour = colourPeaks.iterator();
        Iterator<Double> stamina = staminaPeaks.iterator();

        while (rhythm.hasNext()) {
            double rhythmPeak = rhythm.next() * rhythm_skill_multiplier * patternMultiplier;
            double readingPeak = reading.next() * reading_skill_multiplier;
            double colourPeak = isRelax ? 0 : colour.next() * colour_skill_multiplier; // There is no colour difficulty in relax.
            double staminaPeak = stamina.next() * stamina_skill_multiplier * strainLengthBonus;
            staminaPeak /= isConvert || isRelax ? 1.5 : 1.0; // Available finger count is increased by 150%, thus we adjust accordingly.

            double peak = DifficultyCalculationUtils.Norm(2, DifficultyCalculationUtils.Norm(1.5, colourPeak, staminaPeak), rhythmPeak, readingPeak);

            // Sections with 0 strain are excluded to avoid worst-case time complexity of the following sort (e.g. /b/2351871).
            // These sections will not contribute to the difficulty.
            if (peak > 0)
                combinedPeaks.add(peak);
        }

        return combinedPeaks;
    }

    private static double rescale(double sr)
    {
        if (sr < 0)
            return sr;

        return 10.43 * Math.log(sr / 8 + 1);
    }

    private static double difficultyRange(double difficulty, double min, double mid, double max)
    {
        if (difficulty > 5)
            return mid + (max - mid) * difficultyRange(difficulty);
        if (difficulty < 5)
            return mid + (mid - min) * difficultyRange(difficulty);

        return mid;
    }

    private static double difficultyRange(double difficulty) {
        return (difficulty - 5) / 5;
    }
}
