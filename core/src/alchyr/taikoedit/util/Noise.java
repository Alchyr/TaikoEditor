package alchyr.taikoedit.util;

import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;

//Utilizes Perlin noise.
public class Noise {
    private static final ArrayList<Integer> values = new ArrayList<>();
    static {
        for (int i = 0; i < 256; ++i)
            values.add(i);
    }

    private final int[] p = new int[512];

    private final double scale;

    public Noise(long seed, double scale)
    {
        SeededRandom rng = new SeededRandom(seed);
        this.scale = scale;

        ArrayList<Integer> sourceData = new ArrayList<>(values);

        for (int i = 0; i < 256 ; i++)
            p[i + 256] = p[i] = sourceData.remove(rng.random(sourceData.size() - 1));
    }

    /*public double torusNoise(double x, double y)
    {
        double angleX = MathUtils.PI2 * x,
               angleY = MathUtils.PI2 * y;
        return noise4D(cos(angle_x) / TAU, sin(angle_x) / TAU,
                cos(angle_y) / TAU, sin(angle_y) / TAU);
    }*/

    public double octaveNoise(double x, double y, double z, int octaves, double persistence) {
        double total = 0;
        double amplitude = 1;
        double frequency = 1;
        double maxValue = 0;  // Used for normalizing result to 0.0 - 1.0

        for(int i = 0; i < octaves; ++i) {
            total += noise(x * frequency, y * frequency, z * frequency) * amplitude;

            maxValue += amplitude;

            amplitude *= persistence;
            frequency *= 2;
        }

        return total / maxValue;
    }

    public double noise(double x, double y, double z)
    {
        x = x / scale;
        y = y / scale;
        z = z / scale;

        int X = (int)Math.floor(x) & 255,                  // FIND UNIT CUBE THAT
            Y = (int)Math.floor(y) & 255,                  // CONTAINS POINT.
            Z = (int)Math.floor(z) & 255;

        x -= Math.floor(x);                                // FIND RELATIVE X,Y,Z
        y -= Math.floor(y);                                // OF POINT IN CUBE.
        z -= Math.floor(z);

        double u = fade(x),                                // COMPUTE FADE CURVES
               v = fade(y),                                // FOR EACH OF X,Y,Z.
               w = fade(z);

        int A = p[X  ]+Y, AA = p[A]+Z, AB = p[A+1]+Z,      // HASH COORDINATES OF
            B = p[X+1]+Y, BA = p[B]+Z, BB = p[B+1]+Z;      // THE 8 CUBE CORNERS,

        double returnVal = lerp(w, lerp(v, lerp(u, grad(p[AA  ], x  , y  , z   ),          // AND ADD
                grad(p[BA  ], x-1, y  , z   )),     // BLENDED
                lerp(u, grad(p[AB  ], x  , y-1, z   ),      // RESULTS
                        grad(p[BB  ], x-1, y-1, z   ))), // FROM  8
                lerp(v, lerp(u, grad(p[AA+1], x  , y  , z-1 ),      // CORNERS
                        grad(p[BA+1], x-1, y  , z-1 )),  // OF CUBE
                        lerp(u, grad(p[AB+1], x  , y-1, z-1 ),
                                grad(p[BB+1], x-1, y-1, z-1 ))));

        return MathUtils.clamp(lerp(w, lerp(v, lerp(u, grad(p[AA  ], x  , y  , z   ),          // AND ADD
                                       grad(p[BA  ], x-1, y  , z   )),     // BLENDED
                               lerp(u, grad(p[AB  ], x  , y-1, z   ),      // RESULTS
                                       grad(p[BB  ], x-1, y-1, z   ))), // FROM  8
                       lerp(v, lerp(u, grad(p[AA+1], x  , y  , z-1 ),      // CORNERS
                                       grad(p[BA+1], x-1, y  , z-1 )),  // OF CUBE
                               lerp(u, grad(p[AB+1], x  , y-1, z-1 ),
                                       grad(p[BB+1], x-1, y-1, z-1 )))), 0.0, 1.0);
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y,
                v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
