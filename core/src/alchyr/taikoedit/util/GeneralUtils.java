package alchyr.taikoedit.util;

import alchyr.taikoedit.util.interfaces.functional.VoidMethod;
import alchyr.taikoedit.util.structures.Pair;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.JsonValue;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;
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

    /* The below code is part of the SciJava library, replicated with slight modifications as the entire library is not needed. */

    /*
     * #%L
     * SciJava Common shared library for SciJava software.
     * %%
     * Copyright (C) 2009 - 2017 Board of Regents of the University of
     * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
     * Institute of Molecular Cell Biology and Genetics.
     * %%
     * Redistribution and use in source and binary forms, with or without
     * modification, are permitted provided that the following conditions are met:
     *
     * 1. Redistributions of source code must retain the above copyright notice,
     *    this list of conditions and the following disclaimer.
     * 2. Redistributions in binary form must reproduce the above copyright notice,
     *    this list of conditions and the following disclaimer in the documentation
     *    and/or other materials provided with the distribution.
     *
     * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
     * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
     * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
     * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
     * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
     * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
     * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
     * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
     * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
     * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
     * POSSIBILITY OF SUCH DAMAGE.
     * #L%
     */


    /**
     * Gets the base location of the given class.
     * <p>
     * If the class is directly on the file system (e.g.,
     * "/path/to/my/package/MyClass.class") then it will return the base directory
     * (e.g., "file:/path/to").
     * </p>
     * <p>
     * If the class is within a JAR file (e.g.,
     * "/path/to/my-jar.jar!/my/package/MyClass.class") then it will return the
     * path to the JAR (e.g., "file:/path/to/my-jar.jar").
     * </p>
     *
     * @param c The class whose location is desired.
     */
    public static URL getLocation(final Class<?> c) {
        if (c == null) return null; // could not load the class

        // try the easy way first
        try {
            final URL codeSourceLocation =
                    c.getProtectionDomain().getCodeSource().getLocation();
            if (codeSourceLocation != null) return codeSourceLocation;
        }
        catch (final SecurityException e) {
            // NB: Cannot access protection domain.
        }
        catch (final NullPointerException e) {
            // NB: Protection domain or code source is null.
        }

        // NB: The easy way failed, so we try the hard way. We ask for the class
        // itself as a resource, then strip the class's path from the URL string,
        // leaving the base path.

        // get the class's raw resource path
        final URL classResource = c.getResource(c.getSimpleName() + ".class");
        if (classResource == null) return null; // cannot find class resource

        final String url = classResource.toString();
        final String suffix = c.getCanonicalName().replace('.', '/') + ".class";
        if (!url.endsWith(suffix)) return null; // weird URL

        // strip the class's path from the URL string
        final String base = url.substring(0, url.length() - suffix.length());

        String path = base;

        // remove the "jar:" prefix and "!/" suffix, if present
        if (path.startsWith("jar:")) path = path.substring(4, path.length() - 2);

        try {
            return new URL(path);
        }
        catch (final MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Converts the given {@link URL} to its corresponding {@link File}.
     * <p>
     * This method is similar to calling {@code new File(url.toURI())} except that
     * it also handles "jar:file:" URLs, returning the path to the JAR file.
     * </p>
     *
     * @param url The URL to convert.
     * @return A file path suitable for use with e.g. {@link FileInputStream}
     * @throws IllegalArgumentException if the URL does not correspond to a file.
     */
    public static File urlToFile(final URL url) {
        return url == null ? null : urlToFile(url.toString());
    }

    /**
     * Converts the given URL string to its corresponding {@link File}.
     *
     * @param url The URL to convert.
     * @return A file path suitable for use with e.g. {@link FileInputStream}
     * @throws IllegalArgumentException if the URL does not correspond to a file.
     */
    public static File urlToFile(final String url) {
        String path = url;
        if (path.startsWith("jar:")) {
            // remove "jar:" prefix and "!/" suffix
            final int index = path.indexOf("!/");
            path = path.substring(4, index);
        }
        try {
            if (isWindows() && path.matches("file:[A-Za-z]:.*")) {
                path = "file:/" + path.substring(5);
            }
            return new File(new URL(path).toURI());
        }
        catch (final MalformedURLException | URISyntaxException e) {
            // NB: URL is not completely well-formed.
        }
        if (path.startsWith("file:")) {
            // pass through the URL as-is, minus "file:" prefix
            path = path.substring(5);
            return new File(path);
        }
        throw new IllegalArgumentException("Invalid URL: " + url);
    }

    /** Whether the operating system is Windows-based. */
    public static boolean isWindows() {
        return osName().startsWith("Win");
    }

    /** Gets the name of the operating system. */
    public static String osName() {
        final String osName = System.getProperty("os.name");
        return osName == null ? "Unknown" : osName;
    }
}
