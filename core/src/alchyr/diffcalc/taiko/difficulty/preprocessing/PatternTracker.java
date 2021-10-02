package alchyr.diffcalc.taiko.difficulty.preprocessing;

import alchyr.diffcalc.DifficultyHitObject;

import java.util.ArrayList;
import java.util.List;


//Problems:
/*
    First objects of patterns get hit way too hard due to matching every other first object of same color.
    dkdkdkd, or even just something like ddkdkkd, as long as there's a few exact matches similarity is increased way too much.

    Make rhythm more impactful?

    Instead of comparing to MAX_TRACK for multiplier, compare to the current length of the sequence?

 */

public class PatternTracker {
    private static final int MAX_TRACK = 16; //maximum number of tracked objects
    private final List<TaikoDifficultyHitObject> sequence = new ArrayList<>();

    public double track(DifficultyHitObject h)
    {
        if (!(h instanceof TaikoDifficultyHitObject))
            return 0;

        TaikoDifficultyHitObject taikoHitObject = (TaikoDifficultyHitObject) h;

        double simplicity = 0;

        if (sequence.size() > 0)
        {
            TaikoDifficultyHitObject checkObject;
            TestSequences testSequences = new TestSequences(sequence);
            boolean mono = true;

            //Loop through currently tracked objects in reverse order
            for (int i = sequence.size() - 1; i >= 0; --i)
            {
                checkObject = sequence.get(i); //since the new object is not in the sequence yet, this is the "previous".

                //check the next object for extended sequences
                simplicity += testSequences.checkNext();

                //Check for similarity with the next object.
                switch (taikoHitObject.getSimilarity(checkObject))
                {
                    case MATCH:
                        //Since an exact match was found, see if there's an extended repeating sequence.
                        testSequences.add(i, sequence.size(), true);
                        break;
                    case COLOR:
                        //This means a color pattern match, but no rhythm. It will have less possible similarity, but still check for repeating sequence.
                        testSequences.add(i, sequence.size(), false);
                        break;
                    case RHYTHM:
                        simplicity += 0.25 * ((double)i / sequence.size());
                        break;
                }

                if (mono && (mono = checkObject.hitType == taikoHitObject.hitType))
                {
                    simplicity += 0.1 * (1 - (double)i / sequence.size()); //mono simplicity increases as it gets longer
                }
            }

            simplicity += testSequences.finish(); //clear leftover sequence
        }


        //finished processing object. Add it to sequence.
        sequence.add(taikoHitObject);

        if (sequence.size() > MAX_TRACK)
            sequence.remove(0);


        simplicity *= 10;
        //taikoHitObject.SIMPLICITY_DEBUG = simplicity;
        return simplicity;
    }

    public void clear()
    {
        sequence.clear();
    }


    private static class TestSequences
    {
        private final List<TaikoDifficultyHitObject> fullSequence;
        private TestSequence activeSequence;

        public TestSequences(List<TaikoDifficultyHitObject> fullSequence)
        {
            this.fullSequence = fullSequence;
        }

        public void add(int startIndex, int sequenceSize, boolean isPerfectMatch)
        {
            //When adding a new sequence: If there are no existing, just add it.

            //IF there are existing sequences, check that:
            // - Current existing sequence is not already an exact match, which means this is a repeat. (kddkddkddkddk)
            // - (kddkdkddkd) - Finds a repeat at object 7, finds that it is not similar on object 5, which is also a perfect match which would add a new sequence
            //    1234567890

            //Is it possible to find an exact match where it is not either a perfect repeat or the previous sequence breaks?

            //Any given object is the xth object of a color.
            //This means there are y objects of the other color preceding the pattern it is in.
            //To find a perfect match without breaking the previous sequence,
            //there must be exactly x objects of the same color preceding the y objects of the other color.
            //Otherwise the x+1th object of the same color would break the pattern before the xth object is reached.
            //Therefore, if an active sequence exists and is unbroken when another perfect match is found, there has been a complete repeat of a sequence.

            //ddkkddkddkkdd
            // 012345678901
            //Match sequence starts at 7. Pattern breaks on 5. Second match starts at 4. Another perfect match is found at 0, resulting in a full repeat found.
            //Result: One partial match, one distant exact repeat.
            //kddkkddkkdd
            //0123456789
            //Sequence size: 10 (final d is not yet in the sequence.)
            //Match sequence starts at 6 (offset = 10-6=4), keeps going, finds that a full repeat has occurred on the perfect match at 2. Continues checking. Pattern breaks at 0.
            //Result: Single full repeat and a partial repeat.

            //Edge case:
            // d    d    d
            //Single objects of same color with >500 ms gap will be counted as exact repeats. This is accurate, so this is fine.

            if (activeSequence == null)
            {
                activeSequence = new TestSequence(startIndex, sequenceSize - startIndex, isPerfectMatch);
            }
            //if active sequence is not null, there should be a repeat found in the active sequence.
        }

        //returns increase of simplicity when a sequence ends
        public double checkNext()
        {
            if (activeSequence != null)
            {
                if (activeSequence.checkNext(fullSequence))
                {
                    //sequence has ended.
                    double similarity = activeSequence.getFinalSimilarity(fullSequence);

                    activeSequence = null;

                    return similarity;
                }
            }
            return 0;
        }

        public double finish()
        {
            if (activeSequence != null)
                return activeSequence.getFinalSimilarity(fullSequence);

            return 0;
        }

        private static class TestSequence
        {
            private int startIndex, checkIndex;
            private final int offset;

            private double similarity;
            private int length;

            private double repeatSimilarity; //If a full repeat occurs, stores the added similarity from it.

            private boolean perfectMatch;


            public TestSequence(int startIndex, int offset, boolean isPerfectMatch)
            {
                this.startIndex = this.checkIndex = startIndex;
                this.offset = offset;

                similarity = isPerfectMatch ? 1 : 0.8;
                length = 1;
                repeatSimilarity = 0;

                perfectMatch = isPerfectMatch;
            }

            public boolean checkNext(List<TaikoDifficultyHitObject> sequence)
            {
                --checkIndex;

                if (checkIndex + offset == startIndex) //has gotten all the way to starting point. This means the pattern has repeated fully. (Or partially, if only color matches.)
                {
                    startIndex = checkIndex;
                    repeatSimilarity += getSimilarity(sequence);

                    //start a "new" sequence.
                    similarity = 1;
                    perfectMatch = true;
                    length = 0;
                }

                //Check for similarity with the next object.
                switch (sequence.get(checkIndex).getSimilarity(sequence.get(checkIndex + offset)))
                {
                    case MATCH:
                        //sequence continues
                        ++length;
                        break;
                    case COLOR:
                        //if only color matches, continue checking, but it will have a slightly lower similarity rating than a perfect match.
                        if (perfectMatch)
                        {
                            similarity *= 0.85;
                            perfectMatch = false;
                        }
                        ++length;
                        break;
                    //If the only similarity is rhythm or no similarity at all, the match is over.
                    case NONE:
                        similarity *= 0.4;
                        return true;
                    case RHYTHM:
                        similarity *= 0.65;
                        return true;
                }

                return false;
            }

            public double getFinalSimilarity(List<TaikoDifficultyHitObject> sequence)
            {
                //determine final similarity bonus, adding in similarity from repeats
                return repeatSimilarity + getSimilarity(sequence);
            }

            private double getSimilarity(List<TaikoDifficultyHitObject> sequence)
            {
                //determine current similarity bonus based on similarity and distance from current object
                return similarity * ((double)startIndex / sequence.size()) * ((double)length / offset); //scales linearly
            }
        }
    }
}
