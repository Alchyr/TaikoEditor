package alchyr.taikoedit.util;

import alchyr.taikoedit.util.structures.Pair;
import com.badlogic.gdx.utils.JsonValue;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class GeneralUtils {
    public static Map<String, Object> readProperties(JsonValue properties)
    {
        HashMap<String, Object> map = new HashMap<>();
        readProperties(properties, map);
        return map;
    }

    public static void readProperties(JsonValue properties, Map<String, Object> propertyMap)
    {
        for (JsonValue property = properties.child; property != null; property = property.next)
        {
            String[] data = property.name.split("\\|");

            switch (data[0].toUpperCase())
            {
                case "RANGE":
                    int[] values = property.asIntArray();
                    propertyMap.put(data[1], new Range(values[0], values[1]));
                    break;
                case "MAP":
                    propertyMap.put(data[3], generateMap(data, property));
                    break;
                case "ARRAY":
                    switch (data[1].toUpperCase())
                    {
                        case "STRING":
                            propertyMap.put(data[2], property.asStringArray());
                            break;
                        case "FLOAT":
                            propertyMap.put(data[2], property.asFloatArray());
                            break;
                        case "INT":
                            propertyMap.put(data[2], property.asIntArray());
                            break;
                    }
                    break;
                case "FLOAT":
                    propertyMap.put(data[1], property.asFloat());
                    break;
                case "INT":
                    propertyMap.put(data[1], property.asInt());
                    break;
                default:
                    propertyMap.put(data[1], property.asString());
                    break;
            }
        }
    }

    public static HashMap<String, Object> generateMap(String[] data, JsonValue property)
    {
        HashMap<String, Object> map = new HashMap<>();

        for (JsonValue entry = property.child; entry != null; entry = entry.next)
        {
            switch (data[1].toUpperCase())
            {
                case "ARRAY":
                    switch (data[2].toUpperCase())
                    {
                        case "STRING":
                            map.put(entry.name, entry.asStringArray());
                            break;
                        case "FLOAT":
                            map.put(entry.name, entry.asFloatArray());
                            break;
                        case "INT":
                            map.put(entry.name, entry.asIntArray());
                            break;
                    }
                    break;
                case "STRING":
                    map.put(entry.name, entry.asString());
                    break;
                case "FLOAT":
                    map.put(entry.name, entry.asFloat());
                    break;
                case "INT":
                    map.put(entry.name, entry.asInt());
                    break;
            }
        }

        return map;
    }

    public static <T> Set<T> intersect(Collection<T> a, Collection<T> b)
    {
        HashSet<T> intersection = new HashSet<>(a);

        intersection.retainAll(b);

        return intersection;
    }

    public static <T> List<Pair<T, T>> makePairs(Collection<T> a, Collection<T> b, BiPredicate<T, T> matching)
    {
        ArrayList<Pair<T, T>> pairList = new ArrayList<>();

        ArrayList<T> copyListA = new ArrayList<>(a), copyListB = new ArrayList<>(b);

        Iterator<T> ia, ib;

        ia = copyListA.iterator();
        ib = copyListB.iterator();

        T na, nb;

        outer: while (ia.hasNext())
        {
            na = ia.next();

            while (ib.hasNext())
            {
                nb = ib.next();
                if (matching.test(na, nb))
                {
                    pairList.add(new Pair<>(na, nb));
                    ia.remove();
                    ib.remove();
                    ib = copyListB.iterator();
                    continue outer;
                }
            }

            //No match found.
        }

        return pairList;
    }

    //Modifies the list it is passed.
    public static <T> List<Pair<T, T>> makePairsUnsafe(Collection<T> a, Collection<T> b, BiPredicate<T, T> matching, Predicate<T> exclude)
    {
        ArrayList<Pair<T, T>> pairList = new ArrayList<>();

        Iterator<T> ia, ib;

        ia = a.iterator();
        ib = b.iterator();

        T na, nb;

        while (ia.hasNext())
        {
            na = ia.next();

            if (exclude.test(na))
                continue;

            while (ib.hasNext())
            {
                nb = ib.next();

                if (exclude.test(nb))
                    continue;

                if (matching.test(na, nb))
                {
                    pairList.add(new Pair<>(na, nb));
                    ia.remove();
                    ib.remove();
                    break;
                }
            }

            ib = b.iterator();
        }

        return pairList;
    }
}
