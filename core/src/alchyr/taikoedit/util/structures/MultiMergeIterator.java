package alchyr.taikoedit.util.structures;

import alchyr.taikoedit.util.GeneralUtils;

import java.util.*;
import java.util.function.*;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

//wait I could just use streams
//whatever
//What is needed:
//An iterator that will provide arraylist stacks and probably also a lastKey method to get the key
//the contents of those stacks should be merged from multiple sources and then filtered.
//the returned arraylist will be reused by the iterator? Alternate between two lists? last returned and next prepped
//sounds kinda like streams... bleh
//the stream code for this would be pretty gross so just making it myself I guess


public class MultiMergeIterator<W extends Comparable<W>, H, Y extends Collection<H>> implements Iterator<Y> {
    private static final Predicate defaultFilter = (o)->false;
    private final List<IteratorInfo<W, H, Y, ?>> nextValues;
    private final Comparator<IteratorInfo<W, H, Y, ?>> comparator;

    private final List<PriorityHolder<H, Y>> prioritySortingList;
    private Predicate<H> filters;

    private final Supplier<Y> collectionSupplier;
    private final Object[] collections;
    private int nextCollection;

    private W lastKey = null;

    public MultiMergeIterator(Supplier<Y> collectionSupplier) {
        this(collectionSupplier, 2, Comparator.naturalOrder());
    }
    public MultiMergeIterator(Supplier<Y> collectionSupplier, Comparator<W> comparator) {
        this(collectionSupplier, 2, comparator);
    }
    public MultiMergeIterator(Supplier<Y> collectionSupplier, int collectionCount, Comparator<W> comparator) {
        nextValues = new ArrayList<>();
        this.comparator = Comparator.comparing((o)->o.nextKey, comparator);

        prioritySortingList = new ArrayList<>(4);
        filters = defaultFilter;

        this.collectionSupplier = collectionSupplier;
        collections = new Object[collectionCount];
        for (int i = 0; i < collectionCount; ++i) {
            collections[i] = collectionSupplier.get();
        }
        nextCollection = 0;
    }

    public void clear() {
        nextValues.clear();
        filters = defaultFilter;
    }

    //Filters should be added first.
    public MultiMergeIterator<W, H, Y> addFilter(Predicate<H> condition) {
        if (filters == defaultFilter) filters = condition;
        else filters = filters.and(condition);

        return this;
    }

    //Duplicate keys within a single iterator are not permitted.
    public <V> MultiMergeIterator<W, H, Y> addIterator(Iterator<V> iter, Function<V, W> keyExtractor, BiConsumer<Y, V> valueStorer) {
        return addIterator(iter, keyExtractor, valueStorer, 0);
    }
    public <V> MultiMergeIterator<W, H, Y> addIterator(Iterator<V> iter, Function<V, W> keyExtractor, BiConsumer<Y, V> valueStorer, int priority) {
        //accept iterator of some kind -
        //need iterator, ability to get key from iterator value, ability to go from iterator type to add to collection of return type
        IteratorInfo<W, H, Y, V> info = new IteratorInfo<>(collectionSupplier, iter, keyExtractor, valueStorer, filters, priority);
        if (info.nextKey != null) {
            GeneralUtils.insertSorted(nextValues, info, comparator);
        }
        return this;
    }

    @Override
    public boolean hasNext() {
        return !nextValues.isEmpty();
    }

    public W getLastKey() {
        if (lastKey == null)
            throw new IllegalStateException();
        return lastKey;
    }

    @Override
    public Y next() {
        Y collection = (Y) collections[nextCollection];
        collection.clear();
        prioritySortingList.clear();

        nextCollection = (nextCollection + 1) % collections.length;

        IteratorInfo<W, H, Y, ?> next = nextValues.get(0);
        lastKey = next.nextKey;

        while (lastKey.equals(next.nextKey)) { //Should definitely be true first time, and null will also be false.
            //next.nextValue contains necessary info here
            GeneralUtils.insertSorted(prioritySortingList, next.valueHolder, (o)->o.priority);

            nextValues.remove(0); //remove packed

            next.prepNext(filters); //prep for next value, add back to nextValues if not null
            if (next.nextKey != null) {
                GeneralUtils.insertSorted(nextValues, next, comparator);
            }

            if (nextValues.isEmpty()) break;

            next = nextValues.get(0); //check next value
        }

        for (PriorityHolder<H, Y> info : prioritySortingList) {
            collection.addAll(info.collection);
        }

        return collection;
    }

    private static class IteratorInfo<N extends Comparable<N>, O, P extends Collection<O>, E> {
        final PriorityHolder<O, P> valueHolder;
        Iterator<E> iterator;
        Function<E, N> keyExtractor;
        BiConsumer<P, E> valueStorer;
        private N nextKey;
        private P nextValue;

        public IteratorInfo(Supplier<P> collectionBuilder, Iterator<E> iter, Function<E, N> keyExtractor, BiConsumer<P, E> valueStorer, Predicate<O> filters, int priority) {
            this.iterator = iter;
            this.keyExtractor = keyExtractor;
            this.valueStorer = valueStorer;
            this.valueHolder = new PriorityHolder<>(priority, collectionBuilder.get());
            this.nextValue = collectionBuilder.get();
            prepNext(filters);
        }

        void prepNext(Predicate<O> filters) {
            //store "next" into valueHolder
            valueHolder.set(nextValue);

            //prep "next"
            do {
                if (iterator.hasNext()) {
                    E next = iterator.next();
                    nextKey = keyExtractor.apply(next);
                    pack(nextValue, next);
                    nextValue.removeIf(filters);
                }
                else {
                    nextKey = null;
                }
            } while (nextKey != null && nextValue.isEmpty());
        }

        public void pack(P collection, E val) {
            nextValue.clear();
            valueStorer.accept(collection, val);
        }
    }

    private static class PriorityHolder<A, B extends Collection<A>> {
        final int priority;
        final B collection;

        PriorityHolder(int priority, B collection) {
            this.priority = priority;
            this.collection = collection;
        }

        public void set(B nextValue) {
            collection.clear();
            collection.addAll(nextValue);
        }
    }
}
