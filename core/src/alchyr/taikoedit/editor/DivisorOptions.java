package alchyr.taikoedit.editor;

import java.util.*;

public class DivisorOptions {
    private final Set<Integer> snappingOptions;
    public final List<Integer> activeSnappings;

    public DivisorOptions()
    {
        snappingOptions = new HashSet<>();
        activeSnappings = new ArrayList<>();

        for (int i : BeatDivisors.commonSnappings)
        {
            addDivisor(i);
        }
    }

    public void addDivisor(int divisor)
    {
        snappingOptions.add(divisor);
    }

    public void reset()
    {
        activeSnappings.clear();
    }

    public void activate(int divisor)
    {
        for (Integer option : snappingOptions)
        {
            if (divisor % option == 0)
            {
                if (!activeSnappings.contains(option))
                    activeSnappings.add(option);
            }
        }
        Collections.sort(activeSnappings);
    }
}