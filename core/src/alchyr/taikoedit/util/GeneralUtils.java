package alchyr.taikoedit.util;

import alchyr.taikoedit.util.interfaces.functional.VoidMethod;
import alchyr.taikoedit.util.structures.Pair;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.JsonValue;
import org.apache.logging.log4j.Logger;

import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.function.*;

import static alchyr.taikoedit.TaikoEditor.osuDecimalFormat;

public class GeneralUtils {
    //Placeholder Generics
    private static final Consumer<?> nConsumer = (o)->{};
    public static <T> Consumer<T> noConsumer() {
        return (Consumer<T>) nConsumer;
    }
    private static final Supplier<?> nSupplier = ()->null;
    public static <T> Supplier<T> nullSupplier() {
        return (Supplier<T>) nSupplier;
    }
    public static final VoidMethod doNothing = ()->{};

    public static final DecimalFormat oneDecimal = new DecimalFormat("##0.#", osuDecimalFormat);
    private static final char[] charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz123456789".toCharArray();

    public static String generateCode(int len) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < len; ++i) {
            code.append(charset[MathUtils.random(charset.length - 1)]);
        }
        return code.toString();
    }

    public static Map<String, Object> readProperties(JsonValue properties)
    {
        HashMap<String, Object> map = new HashMap<>();
        readProperties(properties, map);
        return map;
    }

    public static int charCount(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); ++i) {
            if (s.charAt(i) == c)
                ++count;
        }
        return count;
    }

    public static <T> boolean arraySectionEquals(T[] a, T[] b, int start, int len) {
        if (a == b)
            return true;
        if (a == null || b == null)
            return false;
        if (a.length != b.length)
            return false;
        if (start + len > a.length) return false;

        for (int i = 0; i < len; ++i)
            if (a[start + i] != b[start + i])
                return false;

        return true;
    }
    public static boolean arraySectionEquals(byte[] a, byte[] b, int start, int len) {
        if (a == b)
            return true;
        if (a == null || b == null)
            return false;
        if (a.length != b.length)
            return false;
        if (start + len > a.length) return false;

        for (int i = 0; i < len; ++i)
            if (a[start + i] != b[start + i])
                return false;

        return true;
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

    public static <T, U extends Comparable<U>> void insertSorted(List<T> list, T item, Function<T, U> extractKey) {
        insertSorted(list, item, Comparator.comparing(extractKey));
    }
    public static <T> void insertSorted(List<T> list, T item, Comparator<? super T> comparator) {
        if (list.isEmpty()) {
            list.add(item);
            return;
        }

        int index = Collections.binarySearch(list, item, comparator);
        if (index < 0)
            index = ~index;
        list.add(index, item);
    }

    public static <T> T iterateListsUntilNull(Iterator<? extends List<? extends T>> iter) {
        if (iter.hasNext())
            return listLast(iter.next());
        else
            return null;
    }
    public static <T> T listLast(List<T> next) {
        return next.isEmpty() ? null : next.get(next.size() - 1);
    }

    public static void logStackTrace(Logger l, Throwable e) {
        StringBuilder sb = new StringBuilder();
        logStackTrace(sb, e);
        l.error(sb.toString());
    }
    private static void logStackTrace(StringBuilder sb, Throwable e) {
        Set<Throwable> dejaVu =
                Collections.newSetFromMap(new IdentityHashMap<>());
        dejaVu.add(e);

        StackTraceElement[] trace = e.getStackTrace();

        sb.append(e).append('\n');
        for (StackTraceElement traceElement : trace)
            sb.append("\tat ").append(traceElement).append("\n");

        for (Throwable se : e.getSuppressed())
            logEnclosedStackTrace(sb, se, trace, "Suppressed: ", "\t", dejaVu);

        Throwable cause = e.getCause();
        if (cause != null) {
            logEnclosedStackTrace(sb, cause, trace, "Caused by: ", "", dejaVu);
        }
    }
    private static void logEnclosedStackTrace(StringBuilder sb, Throwable e, StackTraceElement[] enclosingTrace, String caption, String prefix, Set<Throwable> dejaVu) {
        if (dejaVu.contains(e)) {
            sb.append("\t[CIRCULAR REFERENCE:").append(e).append("]").append('\n');
        }
        else {
            dejaVu.add(e);

            // Compute number of frames in common between this and enclosing trace
            StackTraceElement[] trace = e.getStackTrace();
            int m = trace.length - 1;
            int n = enclosingTrace.length - 1;
            while (m >= 0 && n >=0 && trace[m].equals(enclosingTrace[n])) {
                m--; n--;
            }
            int framesInCommon = trace.length - 1 - m;

            // Print our stack trace
            sb.append(prefix).append(caption).append(e).append('\n');
            for (int i = 0; i <= m; i++)
                sb.append(prefix).append("\tat ").append(trace[i]).append('\n');
            if (framesInCommon != 0)
                sb.append(prefix).append("\t... ").append(framesInCommon).append(" more").append('\n');

            // Print suppressed exceptions, if any
            for (Throwable se : e.getSuppressed())
                logEnclosedStackTrace(sb, se, trace, "Suppressed: ", prefix + "\t", dejaVu);

            // Print cause, if any
            Throwable cause = e.getCause();
            if (cause != null)
                logEnclosedStackTrace(sb, cause, trace, "Caused by: ", prefix, dejaVu);
        }
    }
}
