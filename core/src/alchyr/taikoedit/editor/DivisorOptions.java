package alchyr.taikoedit.editor;

import java.util.*;

//This is pretty awful
//Controls the options, controls setting the current divisor.
//the BeatDivisors in dependents hold the actual snappings for each individual difficulty.
public class DivisorOptions {
    public static final NavigableSet<Integer> autoGen = new TreeSet<>();
    static {
        for (int i = 1; i <= 16; ++i)
            autoGen.add(i);
        autoGen.add(18);
        autoGen.add(27);
        autoGen.add(32);
        autoGen.add(36);
        autoGen.add(48);
    }

    public final Set<Integer> divisorOptions;
    public final List<Integer> activeDivisors;

    private int currentSnapping;

    private final List<BeatDivisors> dependents;

    public DivisorOptions()
    {
        divisorOptions = new HashSet<>();
        activeDivisors = new ArrayList<>();
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

    public void removeDependent(BeatDivisors divisors) {
        dependents.remove(divisors);
    }

    public void addDivisor(int divisor)
    {
        divisorOptions.add(divisor);
    }

    public void reset()
    {
        activeDivisors.clear();
        currentSnapping = 0;
    }

    //Enables this divisor and any subdivisors. Will not disable any already enabled divisors.
    public void enable(int divisor)
    {
        divisorOptions.add(divisor);

        for (Integer option : divisorOptions)
        {
            if (divisor % option == 0)
            {
                if (!activeDivisors.contains(option))
                    activeDivisors.add(option);
            }
        }

        activeDivisors.sort(Comparator.reverseOrder());
        if (activeDivisors.isEmpty())
            currentSnapping = 0;
        else
        {
            currentSnapping = activeDivisors.get(0);
        }

        for (BeatDivisors divisors : dependents)
        {
            divisors.refresh();
        }
    }
    //Disables this divisor and any divisors that rely on it.
    public void disable(int divisor)
    {
        activeDivisors.removeIf((i)->i % divisor == 0);

        activeDivisors.sort(Comparator.reverseOrder());
        if (activeDivisors.isEmpty())
            currentSnapping = 0;
        else
        {
            currentSnapping = activeDivisors.get(0);
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

        activeDivisors.clear();

        if (divisor > 0)
        {
            divisorOptions.add(divisor);
            for (Integer option : divisorOptions)
            {
                if (divisor % option == 0)
                {
                    if (!activeDivisors.contains(option))
                        activeDivisors.add(option);
                }
                else
                {
                    activeDivisors.remove(option);
                }
            }
        }

        activeDivisors.sort(Comparator.reverseOrder());
        if (activeDivisors.isEmpty())
            currentSnapping = 0;
        else
        {
            currentSnapping = activeDivisors.get(0);
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

        if (divisorOptions.contains(dec) || autoGen.contains(dec))
            return dec;

        Integer prev = autoGen.lower(currentSnapping);
        if (prev == null)
            return currentSnapping;
        return prev;
    }
    private int next() {
        int inc = currentSnapping + 1;
        if (divisorOptions.contains(inc) || autoGen.contains(inc)) {
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