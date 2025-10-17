package alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour;

import alchyr.diffcalc.live.DifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour.data.AlternatingMonoPattern;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour.data.MonoStreak;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour.data.RepeatingHitPatterns;
import alchyr.taikoedit.editor.maps.components.hitobjects.Hit;

import java.util.ArrayList;
import java.util.List;

public class TaikoColourDifficultyPreprocessor
{

    /// <summary>
    /// Processes and encodes a list of <see cref="TaikoDifficultyHitObject"/>s into a list of <see cref="TaikoColourData"/>s,
    /// assigning the appropriate <see cref="TaikoColourData"/>s to each <see cref="TaikoDifficultyHitObject"/>.
    /// </summary>
    public static void processAndAssign(List<DifficultyHitObject> hitObjects)
    {
        List<RepeatingHitPatterns> hitPatterns = encode(hitObjects);

        // Assign indexing and encoding data to all relevant objects.
        for (RepeatingHitPatterns repeatingHitPattern : hitPatterns) {
            // The outermost loop is kept a ForEach loop since it doesn't need index information, and we want to
            // keep i and j for AlternatingMonoPattern's and MonoStreak's index respectively, to keep it in line with
            // documentation.
            for (int i = 0; i < repeatingHitPattern.alternatingMonoPatterns.size(); ++i)
            {
                AlternatingMonoPattern monoPattern = repeatingHitPattern.alternatingMonoPatterns.get(i);
                monoPattern.parent = repeatingHitPattern;
                monoPattern.index = i;

                for (int j = 0; j < monoPattern.monoStreaks.size(); ++j)
                {
                    MonoStreak monoStreak = monoPattern.monoStreaks.get(j);
                    monoStreak.parent = monoPattern;
                    monoStreak.index = j;

                    for (TaikoDifficultyHitObject hitObject : monoStreak.hitObjects)
                    {
                        hitObject.colourData.repeatingHitPattern = repeatingHitPattern;
                        hitObject.colourData.alternatingMonoPattern = monoPattern;
                        hitObject.colourData.monoStreak = monoStreak;
                    }
                }
            }
        }
    }

    /// <summary>
    /// Encodes a list of <see cref="TaikoDifficultyHitObject"/>s into a list of <see cref="RepeatingHitPatterns"/>s.
    /// </summary>
    private static List<RepeatingHitPatterns> encode(List<DifficultyHitObject> data)
    {
        List<MonoStreak> monoStreaks = encodeMonoStreak(data);
        List<AlternatingMonoPattern> alternatingMonoPatterns = encodeAlternatingMonoPattern(monoStreaks);
        List<RepeatingHitPatterns> repeatingHitPatterns = encodeRepeatingHitPattern(alternatingMonoPatterns);

        return repeatingHitPatterns;
    }

    /// <summary>
    /// Encodes a list of <see cref="TaikoDifficultyHitObject"/>s into a list of <see cref="MonoStreak"/>s.
    /// </summary>
    private static List<MonoStreak> encodeMonoStreak(List<DifficultyHitObject> data)
    {
        List<MonoStreak> monoStreaks = new ArrayList<>();
        MonoStreak currentMonoStreak = null;

        for (int i = 0; i < data.size(); i++)
        {
            TaikoDifficultyHitObject taikoObject = (TaikoDifficultyHitObject)data.get(i);

            // This ignores all non-note objects, which may or may not be the desired behaviour
            TaikoDifficultyHitObject previousObject = taikoObject.previousNote(0);

            // If this is the first object in the list or the colour changed, create a new mono streak
            if (currentMonoStreak == null || previousObject == null || !(taikoObject.baseObject instanceof Hit) || (((Hit) taikoObject.baseObject).isRim() != ((Hit) previousObject.baseObject).isRim()))
            {
                currentMonoStreak = new MonoStreak();
                monoStreaks.add(currentMonoStreak);
            }

            // Add the current object to the encoded payload.
            currentMonoStreak.hitObjects.add(taikoObject);
        }

        return monoStreaks;
    }

    /// <summary>
    /// Encodes a list of <see cref="MonoStreak"/>s into a list of <see cref="AlternatingMonoPattern"/>s.
    /// </summary>
    private static List<AlternatingMonoPattern> encodeAlternatingMonoPattern(List<MonoStreak> data)
    {
        List<AlternatingMonoPattern> monoPatterns = new ArrayList<>();
        AlternatingMonoPattern currentMonoPattern = null;

        for (int i = 0; i < data.size(); i++)
        {
            // Start a new AlternatingMonoPattern if the previous MonoStreak has a different mono length, or if this is the first MonoStreak in the list.
            if (currentMonoPattern == null || data.get(i).runLength() != data.get(i - 1).runLength())
            {
                currentMonoPattern = new AlternatingMonoPattern();
                monoPatterns.add(currentMonoPattern);
            }

            // Add the current MonoStreak to the encoded payload.
            currentMonoPattern.monoStreaks.add(data.get(i));
        }

        return monoPatterns;
    }

    /// <summary>
    /// Encodes a list of <see cref="AlternatingMonoPattern"/>s into a list of <see cref="RepeatingHitPatterns"/>s.
    /// </summary>
    private static List<RepeatingHitPatterns> encodeRepeatingHitPattern(List<AlternatingMonoPattern> data)
    {
        List<RepeatingHitPatterns> hitPatterns = new ArrayList<>();
        RepeatingHitPatterns currentHitPattern = null;

        for (int i = 0; i < data.size(); i++)
        {
            // Start a new RepeatingHitPattern. AlternatingMonoPatterns that should be grouped together will be handled later within this loop.
            currentHitPattern = new RepeatingHitPatterns(currentHitPattern);

            // Determine if future AlternatingMonoPatterns should be grouped.
            boolean isCoupled = i < data.size() - 2 && data.get(i).isRepetitionOf(data.get(i + 2));

            if (!isCoupled)
            {
                // If not, add the current AlternatingMonoPattern to the encoded payload and continue.
                currentHitPattern.alternatingMonoPatterns.add(data.get(i));
            }
            else
            {
                // If so, add the current AlternatingMonoPattern to the encoded payload and start repeatedly checking if the
                // subsequent AlternatingMonoPatterns should be grouped by increasing i and doing the appropriate isCoupled check.
                while (isCoupled)
                {
                    currentHitPattern.alternatingMonoPatterns.add(data.get(i));
                    i++;
                    isCoupled = i < data.size() - 2 && data.get(i).isRepetitionOf(data.get(i + 2));
                }

                // Skip over viewed data and add the rest to the payload
                currentHitPattern.alternatingMonoPatterns.add(data.get(i));
                currentHitPattern.alternatingMonoPatterns.add(data.get(i + 1));
                i++;
            }

            hitPatterns.add(currentHitPattern);
        }

        // Final pass to find repetition intervals
        for (int i = 0; i < hitPatterns.size(); i++)
        {
            hitPatterns.get(i).findRepetitionInterval();
        }

        return hitPatterns;
    }
}
