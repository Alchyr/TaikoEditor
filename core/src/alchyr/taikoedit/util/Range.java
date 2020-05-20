package alchyr.taikoedit.util;

import com.badlogic.gdx.math.MathUtils;

public class Range {
    public int min;
    public int max;

    public Range(int min, int max)
    {
        this.min = min;
        this.max = max;
    }

    public int random(SeededRandom rng)
    {
        return rng.random(min, max);
    }

    public int clamp(int value)
    {
        return MathUtils.clamp(value, min, max);
    }
}
