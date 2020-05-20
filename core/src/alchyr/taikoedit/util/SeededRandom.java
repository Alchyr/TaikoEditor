package alchyr.taikoedit.util;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

import java.util.List;
import java.util.function.Function;

public class SeededRandom {
    private RandomXS128 rng;

    public SeededRandom(long seed)
    {
        this.rng = new RandomXS128(seed);
    }

    public SeededRandom(SeededRandom generator)
    {
        this.rng = new RandomXS128(generator.nextLong());
    }


    public long nextLong()
    {
        return rng.nextLong();
    }


    //All random methods are copied directly (comments included) from MathUtils of Gdx.

    /** Returns a random number between 0 (inclusive) and the specified value (inclusive). */
    public int random (int range) {
        return rng.nextInt(range + 1);
    }

    /** Returns a random number between start (inclusive) and end (inclusive). */
    public int random (int start, int end) {
        return start + rng.nextInt(end - start + 1);
    }

    /** Returns a random number between 0 (inclusive) and the specified value (inclusive). */
    public long random (long range) {
        return (long)(rng.nextDouble() * range);
    }

    /** Returns a random number between start (inclusive) and end (inclusive). */
    public long random (long start, long end) {
        return start + (long)(rng.nextDouble() * (end - start));
    }

    /** Returns a random boolean value. */
    public boolean randomBoolean () {
        return rng.nextBoolean();
    }

    /** Returns true if a random value between 0 and 1 is less than the specified value. */
    public boolean randomBoolean (float chance) {
        return MathUtils.random() < chance;
    }

    /** Returns random number between 0.0 (inclusive) and 1.0 (exclusive). */
    public float random () {
        return rng.nextFloat();
    }

    /** Returns a random number between 0 (inclusive) and the specified value (exclusive). */
    public float random (float range) {
        return rng.nextFloat() * range;
    }

    /** Returns a random number between start (inclusive) and end (exclusive). */
    public float random (float start, float end) {
        return start + rng.nextFloat() * (end - start);
    }

    /** Returns -1 or 1, randomly. */
    public int randomSign () {
        return 1 | (rng.nextInt() >> 31);
    }

    /** Returns a triangularly distributed random number between -1.0 (exclusive) and 1.0 (exclusive), where values around zero are
     * more likely.
     * <p>
     * This is an optimized version of {@link #randomTriangular(float, float, float) randomTriangular(-1, 1, 0)} */
    public float randomTriangular () {
        return rng.nextFloat() - rng.nextFloat();
    }

    /** Returns a triangularly distributed random number between {@code -max} (exclusive) and {@code max} (exclusive), where values
     * around zero are more likely.
     * <p>
     * This is an optimized version of {@link #randomTriangular(float, float, float) randomTriangular(-max, max, 0)}
     * @param max the upper limit */
    public float randomTriangular (float max) {
        return (rng.nextFloat() - rng.nextFloat()) * max;
    }

    /** Returns a triangularly distributed random number between {@code min} (inclusive) and {@code max} (exclusive), where the
     * {@code mode} argument defaults to the midpoint between the bounds, giving a symmetric distribution.
     * <p>
     * This method is equivalent of {@link #randomTriangular(float, float, float) randomTriangular(min, max, (min + max) * .5f)}
     * @param min the lower limit
     * @param max the upper limit */
    public float randomTriangular (float min, float max) {
        return randomTriangular(min, max, (min + max) * 0.5f);
    }

    /** Returns a triangularly distributed random number between {@code min} (inclusive) and {@code max} (exclusive), where values
     * around {@code mode} are more likely.
     * @param min the lower limit
     * @param max the upper limit
     * @param mode the point around which the values are more likely */
    public float randomTriangular (float min, float max, float mode) {
        float u = rng.nextFloat();
        float d = max - min;
        if (u <= (mode - min) / d) return min + (float)Math.sqrt(u * d * (mode - min));
        return max - (float)Math.sqrt((1 - u) * d * (max - mode));
    }

    public <T> T weightedRandom(List<T> objects, Function<T, Float> getWeight)
    {
        float totalWeight = 0;
        for (T t : objects)
            totalWeight += getWeight.apply(t);

        return weightedRandom(objects ,getWeight, totalWeight);
    }
    public <T> T weightedRandom(List<T> objects, Function<T, Float> getWeight, float totalWeight)
    {
        float random = random(totalWeight);
        for (T object : objects) {
            random -= getWeight.apply(object);

            if (random <= 0)
                return object;
        }

        return objects.get(objects.size() - 1);
    }
}
