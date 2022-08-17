package alchyr.taikoedit.editor;

import java.util.*;

//This is pretty awful
//Controls the options, controls setting the current divisor.
//the BeatDivisors in dependents hold the actual snappings for each individual difficulty.
public class DivisorOptions {
    private static final NavigableSet<Integer> autoGen = new TreeSet<>();
    static {
        for (int i = 1; i <= 16; ++i)
            autoGen.add(i);
        autoGen.add(32);
        autoGen.add(48);
    }

    public final Set<Integer> snappingOptions;
    public final List<Integer> activeSnappings;

    private int currentSnapping;

    private final List<BeatDivisors> dependents;

    public DivisorOptions()
    {
        snappingOptions = new HashSet<>();
        activeSnappings = new ArrayList<>();
        dependents = new ArrayList<>();

        for (int i : BeatDivisors.commonSnappings)
        {
            addDivisor(i);
        }
    }
    public void addDependent(BeatDivisors divisors)
    {
        dependents.add(divisors);
    }

    public void addDivisor(int divisor)
    {
        snappingOptions.add(divisor);
    }

    public void reset()
    {
        activeSnappings.clear();
        currentSnapping = 0;
    }

    //Enables this divisor and any subdivisors. Will not disable any already enabled divisors.
    public void enable(int divisor)
    {
        snappingOptions.add(divisor);

        for (Integer option : snappingOptions)
        {
            if (divisor % option == 0)
            {
                if (!activeSnappings.contains(option))
                    activeSnappings.add(option);
            }
        }

        Collections.sort(activeSnappings);

        if (activeSnappings.isEmpty())
            currentSnapping = 0;
        else
        {
            currentSnapping = activeSnappings.get(activeSnappings.size() - 1);
        }

        for (BeatDivisors divisors : dependents)
        {
            divisors.refresh();
        }
    }
    //Disables this divisor and any divisors that rely on it.
    public void disable(int divisor)
    {
        activeSnappings.removeIf((i)->i % divisor == 0);
        Collections.sort(activeSnappings);

        if (activeSnappings.isEmpty())
            currentSnapping = 0;
        else
        {
            currentSnapping = activeSnappings.get(activeSnappings.size() - 1);
        }

        for (BeatDivisors divisors : dependents)
        {
            divisors.refresh();
        }
    }

    public void set(int divisor)
    {
        if (divisor < 0)
            divisor = 0;

        activeSnappings.clear();

        if (divisor > 0)
        {
            snappingOptions.add(divisor);
            for (Integer option : snappingOptions)
            {
                if (divisor % option == 0)
                {
                    if (!activeSnappings.contains(option))
                        activeSnappings.add(option);
                }
                else
                {
                    activeSnappings.remove(option);
                }
            }
        }

        Collections.sort(activeSnappings);

        if (activeSnappings.isEmpty())
            currentSnapping = 0;
        else
        {
            currentSnapping = activeSnappings.get(activeSnappings.size() - 1);
        }

        for (BeatDivisors divisors : dependents)
        {
            divisors.refresh();
        }
    }

    public void adjust(float adjustment)
    {
        if (adjustment > 0)
        {
            set(prev());
        }
        else
        {
            set(next());
        }
    }

    private int prev() {
        int dec = currentSnapping - 1;

        if (dec <= 0)
            return 0;

        if (snappingOptions.contains(dec) || autoGen.contains(dec))
            return dec;

        Integer prev = autoGen.lower(currentSnapping);
        if (prev == null)
            return currentSnapping;
        return prev;
    }
    private int next() {
        int inc = currentSnapping + 1;
        if (snappingOptions.contains(inc) || autoGen.contains(inc)) {
            return inc;
        }
        else {
            Integer next = autoGen.higher(currentSnapping);
            if (next == null)
                return currentSnapping;
            return next;
        }
    }

    @Override
    public String toString() {
        return currentSnapping > 0 ? "1/" + currentSnapping : "None";
    }
}