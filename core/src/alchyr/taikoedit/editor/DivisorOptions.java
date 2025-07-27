package alchyr.taikoedit.editor;

import java.util.*;

//This is pretty awful
//Controls the options, controls setting the current divisor.
//the BeatDivisors in dependents hold the actual snappings for each individual difficulty.
public class DivisorOptions {
    public static final NavigableSet<Integer> allSnaps = new TreeSet<>();
    private static final NavigableSet<Integer> commonSnaps = new TreeSet<>();
    private static final NavigableSet<Integer> evenSnaps = new TreeSet<>();
    private static final NavigableSet<Integer> swingSnaps = new TreeSet<>();
    static {
        for (int i = 1; i <= 16; ++i)
            allSnaps.add(i);
        allSnaps.add(18);
        allSnaps.add(27);
        allSnaps.add(32);
        allSnaps.add(36);
        allSnaps.add(48);

        commonSnaps.add(1);
        commonSnaps.add(2);
        commonSnaps.add(3);
        commonSnaps.add(4);
        commonSnaps.add(6);
        commonSnaps.add(8);
        commonSnaps.add(12);
        commonSnaps.add(16);

        evenSnaps.add(1);
        evenSnaps.add(2);
        evenSnaps.add(4);
        evenSnaps.add(8);
        evenSnaps.add(16);

        swingSnaps.add(1);
        swingSnaps.add(2);
        swingSnaps.add(3);
        swingSnaps.add(6);
        swingSnaps.add(12);
    }

    private NavigableSet<Integer> currentSnaps = allSnaps;

    public final Set<Integer> divisorOptions;
    public final List<Integer> activeDivisors;

    private SnapMode mode = SnapMode.ALL;

    public enum SnapMode {
        ALL,
        COMMON,
        EVEN,
        SWING
    }

    private int currentSnapping;

    private final List<IDivisorListener> dependents;

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
    public void addDependent(IDivisorListener thing)
    {
        dependents.add(thing);
    }

    public void removeDependent(IDivisorListener thing) {
        dependents.remove(thing);
    }

    public void addDivisor(int divisor)
    {
        divisorOptions.add(divisor);
    }


    public SnapMode getMode() {
        return mode;
    }
    public void swapMode() {
        switch (mode) {
            case ALL:
                mode = SnapMode.COMMON;
                currentSnaps = commonSnaps;
                break;
            case COMMON:
                mode = SnapMode.EVEN;
                currentSnaps = evenSnaps;
                break;
            case EVEN:
                mode = SnapMode.SWING;
                currentSnaps = swingSnaps;
                break;
            case SWING:
                mode = SnapMode.ALL;
                currentSnaps = allSnaps;
                break;
        }

        if (!currentSnaps.contains(currentSnapping)) {
            adjust(-1, false);
        }
    }

    public int getCurrentSnapping() {
        return currentSnapping;
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

        for (IDivisorListener listener : dependents)
        {
            listener.refresh();
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

        for (IDivisorListener listener : dependents)
        {
            listener.refresh();
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

        for (IDivisorListener listener : dependents)
        {
            listener.refresh();
        }
    }

    public void adjust(float adjustDirection, boolean useAllSnaps)
    {
        if (adjustDirection > 0)
        {
            set(prev(useAllSnaps ? allSnaps : currentSnaps));
        }
        else
        {
            set(next(useAllSnaps ? allSnaps : currentSnaps));
        }
    }

    private int prev(NavigableSet<Integer> snappingSet) {
        int dec = currentSnapping - 1;

        if (dec <= 0)
            return 0;

        if (snappingSet.contains(dec))
            return dec;

        Integer prev = snappingSet.lower(currentSnapping);
        if (prev == null)
            return currentSnapping;
        return prev;
    }
    private int next(NavigableSet<Integer> snappingSet) {
        int inc = currentSnapping + 1;
        if (snappingSet.contains(inc)) {
            return inc;
        }
        else {
            Integer next = snappingSet.higher(currentSnapping);
            if (next == null)
                return currentSnapping;
            return next;
        }
    }

    @Override
    public String toString() {
        return currentSnapping > 0 ? "1/" + currentSnapping : "N/A";
    }

    public interface IDivisorListener {
        void refresh();
    }
}