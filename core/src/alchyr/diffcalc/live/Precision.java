package alchyr.diffcalc.live;

//osu framework utils
public class Precision {
    public static final float FLOAT_EPSILON = 1e-3f;
    public static final double DOUBLE_EPSILON = 1e-7;


    /// <summary>
    /// Computes whether a value is definitely greater than another given an acceptable difference.
    /// </summary>
    /// <param name="value1">The value to compare.</param>
    /// <param name="value2">The value to compare against.</param>
    /// <param name="acceptableDifference">The acceptable difference. Defaults to <see cref="FLOAT_EPSILON"/>.</param>
    /// <returns>Whether <paramref name="value1"/> is definitely greater than <paramref name="value2"/>.</returns>
    public static boolean DefinitelyBigger(float value1, float value2) {
        return DefinitelyBigger(value1, value2, FLOAT_EPSILON);
    }
    public static boolean DefinitelyBigger(float value1, float value2, float acceptableDifference) {
        return value1 - acceptableDifference > value2;
    }

    /// <summary>
    /// Computes whether a value is definitely greater than another given an acceptable difference.
    /// </summary>
    /// <param name="value1">The value to compare.</param>
    /// <param name="value2">The value to compare against.</param>
    /// <param name="acceptableDifference">The acceptable difference. Defaults to <see cref="DOUBLE_EPSILON"/>.</param>
    /// <returns>Whether <paramref name="value1"/> is definitely greater than <paramref name="value2"/>.</returns>
    public static boolean DefinitelyBigger(double value1, double value2) {
        return DefinitelyBigger(value1, value2, DOUBLE_EPSILON);
    }
    public static boolean DefinitelyBigger(double value1, double value2, double acceptableDifference) {
        return value1 - acceptableDifference > value2;
    }

    /// <summary>
    /// Computes whether a value is almost greater than another given an acceptable difference.
    /// </summary>
    /// <param name="value1">The value to compare.</param>
    /// <param name="value2">The value to compare against.</param>
    /// <param name="acceptableDifference">The acceptable difference. Defaults to <see cref="FLOAT_EPSILON"/>.</param>
    /// <returns>Whether <paramref name="value1"/> is almost greater than <paramref name="value2"/>.</returns>
    public static boolean AlmostBigger(float value1, float value2) {
        return AlmostBigger(value1, value2, FLOAT_EPSILON);
    }
    public static boolean AlmostBigger(float value1, float value2, float acceptableDifference) {
        return value1 > value2 - acceptableDifference;
    }

    /// <summary>
    /// Computes whether a value is almost greater than another given an acceptable difference.
    /// </summary>
    /// <param name="value1">The value to compare.</param>
    /// <param name="value2">The value to compare against.</param>
    /// <param name="acceptableDifference">The acceptable difference. Defaults to <see cref="DOUBLE_EPSILON"/>.</param>
    /// <returns>Whether <paramref name="value1"/> is almost greater than <paramref name="value2"/>.</returns>
    public static boolean AlmostBigger(double value1, double value2) {
        return AlmostBigger(value1, value2, DOUBLE_EPSILON);
    }
    public static boolean AlmostBigger(double value1, double value2, double acceptableDifference) {
        return value1 > value2 - acceptableDifference;
    }

    /// <summary>
    /// Computes whether two values are equal within an acceptable difference.
    /// </summary>
    /// <param name="value1">The first value.</param>
    /// <param name="value2">The second value.</param>
    /// <param name="acceptableDifference">The acceptable difference. Defaults to <see cref="FLOAT_EPSILON"/>.</param>
    /// <returns>Whether <paramref name="value1"/> and <paramref name="value2"/> are almost equal.</returns>
    public static boolean AlmostEquals(float value1, float value2) {
        return AlmostEquals(value1, value2, FLOAT_EPSILON);
    }
    public static boolean AlmostEquals(float value1, float value2, float acceptableDifference) {
        return Math.abs(value1 - value2) <= acceptableDifference;
    }

    /// <summary>
    /// Computes whether two values are equal within an acceptable difference.
    /// </summary>
    /// <param name="value1">The first value.</param>
    /// <param name="value2">The second value.</param>
    /// <param name="acceptableDifference">The acceptable difference. Defaults to <see cref="DOUBLE_EPSILON"/>.</param>
    /// <returns>Whether <paramref name="value1"/> and <paramref name="value2"/> are almost equal.</returns>
    public static boolean AlmostEquals(double value1, double value2) {
        return AlmostEquals(value1, value2, DOUBLE_EPSILON);
    }
    public static boolean AlmostEquals(double value1, double value2, double acceptableDifference) {
        return Math.abs(value1 - value2) <= acceptableDifference;
    }
}
