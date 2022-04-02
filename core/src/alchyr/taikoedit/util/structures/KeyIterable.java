package alchyr.taikoedit.util.structures;

import java.util.Iterator;
import java.util.Spliterator;

interface KeyIterable<T> {
    /** Returns ascending iterator from the perspective of this submap */
    Iterator<T> keyIterator();

    Spliterator<T> keySpliterator();

    /** Returns descending iterator from the perspective of this submap */
    Iterator<T> descendingKeyIterator();
}
