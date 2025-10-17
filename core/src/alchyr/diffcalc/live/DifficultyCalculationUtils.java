package alchyr.diffcalc.live;

import com.badlogic.gdx.math.MathUtils;

public class DifficultyCalculationUtils {
    /// <summary>
    /// Converts BPM value into milliseconds
    /// </summary>
    /// <param name="bpm">Beats per minute</param>
    /// <param name="delimiter">Which rhythm delimiter to use, default is 1/4</param>
    /// <returns>BPM conveted to milliseconds</returns>
    public static double BPMToMilliseconds(double bpm) {
        return BPMToMilliseconds(bpm, 4);
    }
    public static double BPMToMilliseconds(double bpm, int delimiter)
    {
        return 60000.0 / delimiter / bpm;
    }

    /// <summary>
    /// Converts milliseconds value into a BPM value
    /// </summary>
    /// <param name="ms">Milliseconds</param>
    /// <param name="delimiter">Which rhythm delimiter to use, default is 1/4</param>
    /// <returns>Milliseconds conveted to beats per minute</returns>
    public static double MillisecondsToBPM(double ms) {
        return MillisecondsToBPM(ms, 4);
    }
    public static double MillisecondsToBPM(double ms, int delimiter)
    {
        return 60000.0 / (ms * delimiter);
    }

    /// <summary>
    /// Calculates a S-shaped logistic function (https://en.wikipedia.org/wiki/Logistic_function)
    /// </summary>
    /// <param name="x">Value to calculate the function for</param>
    /// <param name="maxValue">Maximum value returnable by the function</param>
    /// <param name="multiplier">Growth rate of the function</param>
    /// <param name="midpointOffset">How much the function midpoint is offset from zero <paramref name="x"/></param>
    /// <returns>The output of logistic function of <paramref name="x"/></returns>

    public static double Logistic(double x, double midpointOffset, double multiplier) {
        return Logistic(x, midpointOffset, multiplier, 1);
    }
    public static double Logistic(double x, double midpointOffset, double multiplier, double maxValue) {
        return maxValue / (1 + Math.exp(multiplier * (midpointOffset - x)));
    }

    /// <summary>
    /// Calculates a S-shaped logistic function (https://en.wikipedia.org/wiki/Logistic_function)
    /// </summary>
    /// <param name="maxValue">Maximum value returnable by the function</param>
    /// <param name="exponent">Exponent</param>
    /// <returns>The output of logistic function</returns>
    public static double Logistic(double exponent) {
        return Logistic(exponent, 1);
    }
    public static double Logistic(double exponent, double maxValue) {
        return maxValue / (1 + Math.exp(exponent));
    }

    /// <summary>
    /// Returns the <i>p</i>-norm of an <i>n</i>-dimensional vector (https://en.wikipedia.org/wiki/Norm_(mathematics))
    /// </summary>
    /// <param name="p">The value of <i>p</i> to calculate the norm for.</param>
    /// <param name="values">The coefficients of the vector.</param>
    /// <returns>The <i>p</i>-norm of the vector.</returns>
    public static double Norm(double p, double... values) {
        double sum = 0;
        for (double d : values) sum += Math.pow(d, p);
        return Math.pow(sum, 1 / p);
    }

    /// <summary>
    /// Calculates a Gaussian-based bell curve function (https://en.wikipedia.org/wiki/Gaussian_function)
    /// </summary>
    /// <param name="x">Value to calculate the function for</param>
    /// <param name="mean">The mean (center) of the bell curve</param>
    /// <param name="width">The width (spread) of the curve</param>
    /// <param name="multiplier">Multiplier to adjust the curve's height</param>
    /// <returns>The output of the bell curve function of <paramref name="x"/></returns>
    public static double BellCurve(double x, double mean, double width) {
        return BellCurve(x, mean, width, 1);
    }
    public static double BellCurve(double x, double mean, double width, double multiplier) {
        return multiplier * Math.exp(Math.E * -(Math.pow(x - mean, 2) / Math.pow(width, 2)));
    }

    /// <summary>
    /// Calculates a Smoothstep Bellcurve that returns 1 for x = mean, and smoothly reducing it's value to 0 over width
    /// </summary>
    /// <param name="x">Value to calculate the function for</param>
    /// <param name="mean">Value of x, for which return value will be the highest (=1)</param>
    /// <param name="width">Range [mean - width, mean + width] where function will change values</param>
    /// <returns>The output of the smoothstep bell curve function of <paramref name="x"/></returns>
    public static double SmoothstepBellCurve(double x) {
        return SmoothstepBellCurve(x, .5, .5);
    }
    public static double SmoothstepBellCurve(double x, double mean, double width)
    {
        x -= mean;
        x = x > 0 ? (width - x) : (width + x);
        return Smoothstep(x, 0, width);
    }

    /// <summary>
    /// Smoothstep function (https://en.wikipedia.org/wiki/Smoothstep)
    /// </summary>
    /// <param name="x">Value to calculate the function for</param>
    /// <param name="start">Value at which function returns 0</param>
    /// <param name="end">Value at which function returns 1</param>
    public static double Smoothstep(double x, double start, double end)
    {
        x = MathUtils.clamp((x - start) / (end - start), 0.0, 1.0);

        return x * x * (3.0 - 2.0 * x);
    }

    /// <summary>
    /// Smootherstep function (https://en.wikipedia.org/wiki/Smoothstep#Variations)
    /// </summary>
    /// <param name="x">Value to calculate the function for</param>
    /// <param name="start">Value at which function returns 0</param>
    /// <param name="end">Value at which function returns 1</param>
    public static double Smootherstep(double x, double start, double end)
    {
        x = MathUtils.clamp((x - start) / (end - start), 0.0, 1.0);

        return x * x * x * (x * (6.0 * x - 15.0) + 10.0);
    }

    /// <summary>
    /// Reverse linear interpolation function (https://en.wikipedia.org/wiki/Linear_interpolation)
    /// </summary>
    /// <param name="x">Value to calculate the function for</param>
    /// <param name="start">Value at which function returns 0</param>
    /// <param name="end">Value at which function returns 1</param>
    public static double ReverseLerp(double x, double start, double end)
    {
        return MathUtils.clamp((x - start) / (end - start), 0.0, 1.0);
    }

    /// <summary>
    /// Error function (https://en.wikipedia.org/wiki/Error_function)
    /// </summary>
    /// <param name="x">Value to calculate the function for</param>
    public static double Erf(double x)
    {
        if (x == 0)
            return 0;

        if (x == Double.POSITIVE_INFINITY)
            return 1;

        if (x == Double.NEGATIVE_INFINITY)
            return -1;

        if (Double.isNaN(x))
            return Double.NaN;

        // Constants for approximation (Abramowitz and Stegun formula 7.1.26)
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double tau = t * (0.254829592
                + t * (-0.284496736
                + t * (1.421413741
                + t * (-1.453152027
                + t * 1.061405429))));

        double erf = 1.0 - tau * Math.exp(-x * x);

        return x >= 0 ? erf : -erf;
    }

    /// <summary>
    /// Complementary error function (https://en.wikipedia.org/wiki/Error_function)
    /// </summary>
    /// <param name="x">Value to calculate the function for</param>
    public static double Erfc(double x) {
        return 1 - Erf(x);
    }

    /// <summary>
    /// Inverse error function (https://en.wikipedia.org/wiki/Error_function)
    /// </summary>
    /// <param name="x">Value to calculate the function for</param>
    public static double ErfInv(double x)
    {
        if (x <= -1)
            return Double.NEGATIVE_INFINITY;

        if (x >= 1)
            return Double.POSITIVE_INFINITY;

        if (x == 0)
            return 0;

        double a = 0.147;
        double sgn = Math.signum(x);
        x = Math.abs(x);

        double ln = Math.log(1 - x * x);
        double t1 = 2 / (Math.PI * a) + ln / 2;
        double t2 = ln / a;
        double baseApprox = Math.sqrt(t1 * t1 - t2) - t1;

        // Correction reduces max error from -0.005 to -0.00045.
        double c = x >= 0.85 ? Math.pow((x - 0.85) / 0.293, 8) : 0;
        double erfInv = sgn * (Math.sqrt(baseApprox) + c);

        return erfInv;
    }

    /// <summary>
    /// Inverse complementary error function (https://en.wikipedia.org/wiki/Error_function)
    /// </summary>
    /// <param name="x">Value to calculate the function for</param>
    public static double ErfcInv(double x) {
        return ErfInv(1 - x);
    }
}
