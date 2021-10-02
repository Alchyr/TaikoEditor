package alchyr.taikoedit.editor;

import java.util.*;

public class DivisorOptions {
    public final Set<Integer> snappingOptions;
    public final List<Integer> activeSnappings;

    private int maxDivisor;

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
        maxDivisor = 0;
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
            maxDivisor = 0;
        else
        {
            maxDivisor = activeSnappings.get(activeSnappings.size() - 1);
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
            maxDivisor = 0;
        else
        {
            maxDivisor = activeSnappings.get(activeSnappings.size() - 1);
        }

        for (BeatDivisors divisors : dependents)
        {
            divisors.refresh();
        }
    }

    public void set(int divisor)
    {
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
            maxDivisor = 0;
        else
        {
            maxDivisor = activeSnappings.get(activeSnappings.size() - 1);
        }

        for (BeatDivisors divisors : dependents)
        {
            divisors.refresh();
        }
    }

    public void adjust(int adjustment)
    {
        if (adjustment > 0)
        {
            set(Math.max(0, --maxDivisor));
        }
        else
        {
            set(Math.min(16, ++maxDivisor));
        }
    }

    @Override
    public String toString() {
        return maxDivisor > 0 ? "1/" + maxDivisor : "None";
    }
}