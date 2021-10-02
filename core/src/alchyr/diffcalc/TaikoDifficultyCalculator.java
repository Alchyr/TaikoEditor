package alchyr.diffcalc;

import alchyr.diffcalc.taiko.difficulty.TaikoDifficultyAttributes;
import alchyr.diffcalc.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.diffcalc.taiko.difficulty.skills.General;
import alchyr.diffcalc.taiko.difficulty.skills.Skill;
import alchyr.diffcalc.taiko.difficulty.skills.Burst;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;

import java.util.ArrayList;
import java.util.HashMap;

/* * * * TODO LIST * * * *
 *
 *
 * UPDATE: Quite happy with it.
 * Current problems: Rapidly changing rhythms are a bit *too* effective in increasing difficulty, I think.
 * Should be hard to abuse though, with how it's set up. sun and spirit is the only example I have that's Very High right now.
 * Honestly, not even that overweighted.
 * Actually overweighted: "Alchyr's Normal Stream Map". 15 is just too high.
 * This might be able to dealt with using post-calculation scaling, since 10 is reasonable.
 * why do you hate me? at 9.3 is just too high. Maybe. Is it? The map *is* really hard.
 *
 * PROBLEM: Swap bonus (this is why why do you hate me is overrated.)
 * High swap bonus for 1 object boosts stuff way too much.
 * Note: Changing this will nerf a *lot* of maps, and require further adjustment to other stuff. Has to be done though.
 * Increasing base object value slightly will probably help to balance this out a bit, since 1 swaps are extremely common and were likely affecting all maps.
 * This will also help with stream maps with few swaps being underweighted right now.
 * Method A: pattern length 1 = 0.5x multiplier of swap bonus? Less?
 *
 * Heavy reliance on swap for difficulty: kdkdkdkdkdk is overweighted. Long mono heavy streams are somewhat underweighted. super dense monos are currently not problematic.
 * Stream map difficulty should be addressed separately.
 */

public class TaikoDifficultyCalculator {
    private static final double SR_SCALE = 0.783;
    private static final double CONTINUOUS_SCALE = 0.04;
    private static final double BURST_SCALE = 0.37;

    public static DifficultyAttributes calculateDifficulty(EditorBeatmap map, HashMap<HitObject, TaikoDifficultyHitObject> calculationInfo)
    {
        calculationInfo.clear();

        TaikoDifficultyCalculator c = new TaikoDifficultyCalculator(map, calculationInfo);

        return c.calculate();
    }

    private HashMap<HitObject, TaikoDifficultyHitObject> calculationInfo;




    //From here, based on osu.Game.Rulesets.Difficulty.DifficultyCalculator for ease of conversion

    private int SectionLength = 400;
    private EditorBeatmap beatmap;

    private TaikoDifficultyCalculator(EditorBeatmap map, HashMap<HitObject, TaikoDifficultyHitObject> calculationInfo)
    {
        this.beatmap = map;
        this.calculationInfo = calculationInfo;
    }


    private Skill[] CreateSkills() {
        return new Skill[]
        {
                new Burst(),
                //new Continuous()
                new General()
        };
    }


    private DifficultyAttributes calculate()
    {
        Skill[] skills = CreateSkills();

        if (beatmap.objects.isEmpty())
            return CreateDifficultyAttributes(skills);

        ArrayList<TaikoDifficultyHitObject> difficultyHitObjects = CreateDifficultyHitObjects();

        double sectionLength = SectionLength * EditorLayer.music.getTempo(); //ensures the actual section length is unchanged (map with dt judged same as a map whose objects have been squished)

        // The first object doesn't generate a strain, so we begin with an incremented section end
        double currentSectionEnd = Math.ceil(difficultyHitObjects.get(0).baseObject.pos / sectionLength) * sectionLength;

        for (TaikoDifficultyHitObject h : difficultyHitObjects)
        {
            while (h.baseObject.pos > currentSectionEnd)
            {
                for (Skill s : skills)
                {
                    s.SaveCurrentPeak();
                    s.StartNewSectionFrom(currentSectionEnd);
                }

                currentSectionEnd += sectionLength;
            }

            for (Skill s : skills)
                s.Process(h);

            calculationInfo.put(h.baseObject, h);
        }

        // The peak strain will not be saved for the last section in the above loop
        for (Skill s : skills)
            s.SaveCurrentPeak();

        return CreateDifficultyAttributes(skills);
    }

    private ArrayList<TaikoDifficultyHitObject> CreateDifficultyHitObjects()
    {
        ArrayList<TaikoDifficultyHitObject> taikoDifficultyHitObjects = new ArrayList<>();

        TaikoDifficultyHitObject previous = null;
        //PatternTracker patternTracker = new PatternTracker();

        for (ArrayList<HitObject> positionList : beatmap.objects.values())
        {
            for (HitObject h : positionList)
            {
                if (h.type == HitObject.HitObjectType.CIRCLE)
                {
                    if (previous != null)
                    {
                        taikoDifficultyHitObjects.add(previous = new TaikoDifficultyHitObject(h, previous));
                        //patternTracker.track(previous);
                        calculationInfo.put(h, previous);
                    }
                    else
                    {
                        previous = new TaikoDifficultyHitObject(h);
                        //patternTracker.track(previous);
                        calculationInfo.put(h, previous);
                    }
                }
            }
        }

        return taikoDifficultyHitObjects;
    }


    private DifficultyAttributes CreateDifficultyAttributes(Skill[] skills)
    {
        if (beatmap.objects.isEmpty())
            return new TaikoDifficultyAttributes(skills, 0);

        Burst burst = (Burst) skills[0];
        General continuous = (General) skills[1];

        double burstRating = burst.DifficultyValue() * BURST_SCALE;
        double continuousRating = continuous.DifficultyValue() * CONTINUOUS_SCALE;

        /*var colour = (Colour)skills[0];
        var rhythm = (Rhythm)skills[1];
        var staminaRight = (Stamina)skills[2];
        var staminaLeft = (Stamina)skills[3];

        double colourRating = colour.DifficultyValue() * colour_skill_multiplier;
        double rhythmRating = rhythm.DifficultyValue() * rhythm_skill_multiplier;
        double staminaRating = (staminaRight.DifficultyValue() + staminaLeft.DifficultyValue()) * stamina_skill_multiplier;

        double staminaPenalty = simpleColourPenalty(staminaRating, colourRating);
        staminaRating *= staminaPenalty;

        double combinedRating = locallyCombinedDifficulty(colour, rhythm, staminaRight, staminaLeft, staminaPenalty);
        double separatedRating = norm(1.5, colourRating, rhythmRating, staminaRating);*/

        /*double starRating = 1.4 * separatedRating + 0.5 * combinedRating;
        starRating = rescale(starRating);

        HitWindows hitWindows = new TaikoHitWindows();
        hitWindows.SetDifficulty(beatmap.BeatmapInfo.BaseDifficulty.OverallDifficulty);*/


        //Burst might have a bit too much of an impact at higher values:
        //Give it a more linear scaling rather than the normalization which weighs both equally, making somewhat of a curve?
        //Low impact if it is near/larger than continuous rating, rather than the current high one

        //Reduces the effect of burst rating as burst rating approaches continuous rating
        double sr = Math.pow(continuousRating, 3) + (Math.pow(burstRating, 3) * (continuousRating / (1.3 * burstRating)));
        sr = Math.pow(sr, 1/3.0) * SR_SCALE;

        TaikoDifficultyAttributes attributes = new TaikoDifficultyAttributes(skills, sr);
        attributes.ContinuousRating = continuousRating;
        attributes.BurstRating = burstRating;
        return attributes;
        /*{
                    StaminaStrain = staminaRating,
                    RhythmStrain = rhythmRating,
                    ColourStrain = colourRating,
                    // Todo: This int cast is temporary to achieve 1:1 results with osu!stable, and should be removed in the future
                    GreatHitWindow = (int)hitWindows.WindowFor(HitResult.Great) / clockRate,
                    MaxCombo = beatmap.HitObjects.Count(h => h is Hit),
            Skills = skills
        };*/
    }

    /// <summary>
    /// Returns the <i>p</i>-norm of an <i>n</i>-dimensional vector.
    /// </summary>
    /// <param name="p">The value of <i>p</i> to calculate the norm for.</param>
    /// <param name="values">The coefficients of the vector.</param>
    private double norm(double p, double... values) {
        double sum = 0;
        for (double val : values) {
            sum += Math.pow(val, p);
        }
        return Math.pow(sum, 1 / p);
    }
}
