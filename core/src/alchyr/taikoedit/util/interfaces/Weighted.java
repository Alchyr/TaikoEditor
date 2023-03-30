package alchyr.taikoedit.util.interfaces;

import com.badlogic.gdx.math.MathUtils;

import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

public interface Weighted {
    static <T extends Weighted> T roll(Collection<T> set, Random rng) {
        float sum = 0;
        for (T obj : set)
            sum += obj.getWeight();
        float roll = rng == null ? MathUtils.random(0.0f, sum) : rng.nextFloat() * sum;
        Iterator<T> objIterator = set.iterator();
        T obj = null;
        while (objIterator.hasNext()) {
            obj = objIterator.next();
            if (roll < obj.getWeight()) {
                return obj;
            }
            roll -= obj.getWeight();
        }
        return obj;
    }

    float getWeight();
}
