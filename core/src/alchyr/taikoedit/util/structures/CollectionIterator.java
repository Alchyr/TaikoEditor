package alchyr.taikoedit.util.structures;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public class CollectionIterator<T> implements Iterator<T> {
    private final Iterator<? extends Collection<T>> baseIterator;
    private Iterator<T> subIterator, lastIterator = null;

    public CollectionIterator(Iterator<? extends Collection<T>> baseIterator) {
        this.baseIterator = baseIterator;

        if (baseIterator.hasNext()) {
            subIterator = baseIterator.next().iterator();
        }
        else {
            subIterator = Collections.emptyIterator();
        }
    }

    @Override
    public boolean hasNext() {
        return subIterator.hasNext();
    }

    @Override
    public T next() {
        lastIterator = subIterator;
        T val = subIterator.next();

        while (baseIterator.hasNext() && !subIterator.hasNext()) {
            subIterator = baseIterator.next().iterator();
        }
        return val;
    }

    @Override
    public void remove() {
        if (lastIterator == null)
            throw new IllegalStateException();
        lastIterator.remove();
        lastIterator = null;
    }
}
