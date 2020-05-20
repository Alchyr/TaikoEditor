package alchyr.taikoedit.util;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;

public class Line {
    public Vector2 a, b;

    private float distance;

    public Line(Vector2 a, Vector2 b)
    {
        this.a = a;
        this.b = b;

        distance = -1;
    }

    public float distance()
    {
        return distance == -1 ? distance = a.dst(b) : distance; //try to minimize distance calculations assuming the position of the vectors will not change. sqrt is slow.
    }

    public static int compareLengths_MAX(Line segment0, Line segment1)
    {
        return Float.compare(segment1.a.dst(segment1.b), segment0.a.dst(segment0.b));
    }

    public static int compareLengths(Line edge0, Line edge1)
    {
        return compareLengths_MAX(edge1, edge0);
    }

    public void draw(ShapeRenderer sr)
    {
        sr.line(a, b);
    }

    public boolean connected(Line other)
    {
        return other != this && (a.equals(other.a) || a.equals(other.b) || b.equals(other.b) || b.equals(other.a));
    }

    public boolean equivalent(Line other)
    {
        return other == this || (a.equals(other.a) && b.equals(other.b)) || (a.equals(other.b) && b.equals(other.a));
    }
}
