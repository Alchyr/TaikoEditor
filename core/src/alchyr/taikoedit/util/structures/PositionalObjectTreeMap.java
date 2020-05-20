/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package alchyr.taikoedit.util.structures;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

//Modified version of java.util.TreeMap
public class PositionalObjectTreeMap<V extends PositionalObject>
        extends AbstractMap<Integer, ArrayList<V>>
        implements NavigableMap<Integer, ArrayList<V>>, Cloneable, java.io.Serializable
{
    transient Collection<ArrayList<V>> values; //Cannot reference values within AbstractMap

    /**
     * The comparator used to maintain order in this tree map, or
     * null if it uses the natural ordering of its keys.
     *
     * @serial
     */
    private final Comparator<? super Integer> comparator;

    private transient Entry<V> root;

    /**
     * The number of entries in the tree
     */
    private transient int size = 0;

    /**
     * The total number of values in the tree
     */
    private transient int count = 0;

    /**
     * The number of structural modifications to the tree.
     */
    private transient int modCount = 0;

    /**
     * Constructs a new, empty tree map, using the natural ordering of its
     * keys.  All keys inserted into the map must implement the {@link
     * Comparable} interface.  Furthermore, all such keys must be
     * <em>mutually comparable</em>: {@code k1.compareTo(k2)} must not throw
     * a {@code ClassCastException} for any keys {@code k1} and
     * {@code k2} in the map.  If the user attempts to put a key into the
     * map that violates this constraint (for example, the user attempts to
     * put a string key into a map whose keys are integers), the
     * {@code put(Object key, Object value)} call will throw a
     * {@code ClassCastException}.
     */
    public PositionalObjectTreeMap() {
        comparator = null;
    }

    /**
     * Constructs a new, empty tree map, ordered according to the given
     * comparator.  All keys inserted into the map must be <em>mutually
     * comparable</em> by the given comparator: {@code comparator.compare(k1,
     * k2)} must not throw a {@code ClassCastException} for any keys
     * {@code k1} and {@code k2} in the map.  If the user attempts to put
     * a key into the map that violates this constraint, the {@code put(Object
     * key, Object value)} call will throw a
     * {@code ClassCastException}.
     *
     * @param comparator the comparator that will be used to order this map.
     *        If {@code null}, the {@linkplain Comparable natural
     *        ordering} of the keys will be used.
     */
    public PositionalObjectTreeMap(Comparator<? super Integer> comparator) {
        this.comparator = comparator;
    }

    /**
     * Constructs a new tree map containing the same mappings as the given
     * map, ordered according to the <em>natural ordering</em> of its keys.
     * All keys inserted into the new map must implement the {@link
     * Comparable} interface.  Furthermore, all such keys must be
     * <em>mutually comparable</em>: {@code k1.compareTo(k2)} must not throw
     * a {@code ClassCastException} for any keys {@code k1} and
     * {@code k2} in the map.  This method runs in n*log(n) time.
     *
     * @param  m the map whose mappings are to be placed in this map
     * @throws ClassCastException if the keys in m are not {@link Comparable},
     *         or are not mutually comparable
     * @throws NullPointerException if the specified map is null
     */
    public PositionalObjectTreeMap(Map<? extends Integer, ? extends ArrayList<V>> m) {
        comparator = null;
        putAll(m);
    }

    /**
     * Constructs a new tree map containing the same mappings and
     * using the same ordering as the specified sorted map.  This
     * method runs in linear time.
     *
     * @param  m the sorted map whose mappings are to be placed in this map,
     *         and whose comparator is to be used to sort this map
     * @throws NullPointerException if the specified map is null
     */
    public PositionalObjectTreeMap(SortedMap<Integer, ArrayList<? extends V>> m) {
        comparator = m.comparator();
        try {
            buildFromSorted(m.size(), m.entrySet().iterator(), null, null);
        } catch (IOException | ClassNotFoundException ignored) {
        }
    }


    // Query Operations

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        return size;
    }

    /**
     * Returns the total number values within lists in this map.
     *
     * @return the total number values within lists in this map
     */
    public int count() {
        return count;
    }

    /**
     * Returns {@code true} if this map contains a mapping for the specified
     * key.
     *
     * @param key key whose presence in this map is to be tested
     * @return {@code true} if this map contains a mapping for the
     *         specified key
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     */
    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    /**
     * Returns {@code true} if this map maps one or more keys to the
     * specified value.  More formally, returns {@code true} if and only if
     * this map contains at least one mapping to a value {@code v} such
     * that {@code (value==null ? v==null : value.equals(v))}.  This
     * operation will probably require time linear in the map size for
     * most implementations.
     *
     * @param value value whose presence in this map is to be tested
     * @return {@code true} if a mapping to {@code value} exists;
     *         {@code false} otherwise
     * @since 1.2
     */
    public boolean containsValue(Object value) {
        for (Entry<V> e = getFirstEntry(); e != null; e = successor(e))
            if (val(value, e.value))
                return true;
        return false;
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * <p>More formally, if this map contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code key} compares
     * equal to {@code k} according to the map's ordering, then this
     * method returns {@code v}; otherwise it returns {@code null}.
     * (There can be at most one such mapping.)
     *
     * <p>A return value of {@code null} does not <em>necessarily</em>
     * indicate that the map contains no mapping for the key; it's also
     * possible that the map explicitly maps the key to {@code null}.
     * The {@link #containsKey containsKey} operation may be used to
     * distinguish these two cases.
     *
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     */
    public ArrayList<V> get(Object key) {
        Entry<V> p = getEntry(key);
        return (p==null ? null : p.value);
    }

    public Comparator<? super Integer> comparator() {
        return comparator;
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    public Integer firstKey() {
        return key(getFirstEntry());
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    public Integer lastKey() {
        return key(getLastEntry());
    }

    /**
     * Copies all of the mappings from the specified map to this map.
     * These mappings replace any mappings that this map had for any
     * of the keys currently in the specified map.
     *
     * @param  map mappings to be stored in this map
     * @throws ClassCastException if the class of a key or value in
     *         the specified map prevents it from being stored in this map
     * @throws NullPointerException if the specified map is null or
     *         the specified map contains a null key and this map does not
     *         permit null keys
     */
    public void putAll(Map<? extends Integer, ? extends ArrayList<V>> map) {
        int mapSize = map.size();
        if (size==0 && mapSize!=0 && map instanceof SortedMap) {
            Comparator<?> c = ((SortedMap<?,?>)map).comparator();
            if (Objects.equals(c, comparator)) {
                ++modCount;
                try {
                    buildFromSorted(mapSize, map.entrySet().iterator(),
                            null, null);
                } catch (IOException | ClassNotFoundException ignored) {
                }
                return;
            }
        }
        super.putAll(map);
    }

    /**
     * Returns this map's entry for the given key, or {@code null} if the map
     * does not contain an entry for the key.
     *
     * @return this map's entry for the given key, or {@code null} if the map
     *         does not contain an entry for the key
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     */
    final Entry<V> getEntry(Object key) {
        // Offload comparator-based version for sake of performance
        if (comparator != null)
            return getEntryUsingComparator(key);
        if (key == null)
            throw new NullPointerException();
        @SuppressWarnings("unchecked")
        Comparable<? super Integer> k = (Comparable<? super Integer>) key;
        Entry<V> p = root;
        while (p != null) {
            int cmp = k.compareTo(p.key);
            if (cmp < 0)
                p = p.left;
            else if (cmp > 0)
                p = p.right;
            else
                return p;
        }
        return null;
    }

    /**
     * Version of getEntry using comparator. Split off from getEntry
     * for performance. (This is not worth doing for most methods,
     * that are less dependent on comparator performance, but is
     * worthwhile here.)
     */
    final Entry<V> getEntryUsingComparator(Object key) {
        Integer k = (Integer) key;
        Comparator<? super Integer> cpr = comparator;
        if (cpr != null) {
            Entry<V> p = root;
            while (p != null) {
                int cmp = cpr.compare(k, p.key);
                if (cmp < 0)
                    p = p.left;
                else if (cmp > 0)
                    p = p.right;
                else
                    return p;
            }
        }
        return null;
    }

    /**
     * Gets the entry corresponding to the specified key; if no such entry
     * exists, returns the entry for the least key greater than the specified
     * key; if no such entry exists (i.e., the greatest key in the Tree is less
     * than the specified key), returns {@code null}.
     */
    final Entry<V> getCeilingEntry(Integer key) {
        Entry<V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp < 0) {
                if (p.left != null)
                    p = p.left;
                else
                    return p;
            } else if (cmp > 0) {
                if (p.right != null) {
                    p = p.right;
                } else {
                    Entry<V> parent = p.parent;
                    Entry<V> ch = p;
                    while (parent != null && ch == parent.right) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            } else
                return p;
        }
        return null;
    }

    /**
     * Gets the entry corresponding to the specified key; if no such entry
     * exists, returns the entry for the greatest key less than the specified
     * key; if no such entry exists, returns {@code null}.
     */
    final Entry<V> getFloorEntry(Integer key) {
        Entry<V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp > 0) {
                if (p.right != null)
                    p = p.right;
                else
                    return p;
            } else if (cmp < 0) {
                if (p.left != null) {
                    p = p.left;
                } else {
                    Entry<V> parent = p.parent;
                    Entry<V> ch = p;
                    while (parent != null && ch == parent.left) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            } else
                return p;

        }
        return null;
    }

    /**
     * Gets the entry for the least key greater than the specified
     * key; if no such entry exists, returns the entry for the least
     * key greater than the specified key; if no such entry exists
     * returns {@code null}.
     */
    final Entry<V> getHigherEntry(Integer key) {
        Entry<V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp < 0) {
                if (p.left != null)
                    p = p.left;
                else
                    return p;
            } else {
                if (p.right != null) {
                    p = p.right;
                } else {
                    Entry<V> parent = p.parent;
                    Entry<V> ch = p;
                    while (parent != null && ch == parent.right) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            }
        }
        return null;
    }

    /**
     * Returns the entry for the greatest key less than the specified key; if
     * no such entry exists (i.e., the least key in the Tree is greater than
     * the specified key), returns {@code null}.
     */
    final Entry<V> getLowerEntry(Integer key) {
        Entry<V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp > 0) {
                if (p.right != null)
                    p = p.right;
                else
                    return p;
            } else {
                if (p.left != null) {
                    p = p.left;
                } else {
                    Entry<V> parent = p.parent;
                    Entry<V> ch = p;
                    while (parent != null && ch == parent.left) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            }
        }
        return null;
    }

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old
     * value is replaced.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     *
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}.
     *         (A {@code null} return can also indicate that the map
     *         previously associated {@code null} with {@code key}.)
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     */
    public ArrayList<V> put(Integer key, ArrayList<V> value) {
        Entry<V> t = root;
        if (t == null) {
            compare(key, key); // type (and possibly null) check

            root = new Entry<V>(key, value, null);
            size = 1;
            count = value.size();
            modCount++;
            return null;
        }
        int cmp;
        Entry<V> parent;
        // split comparator and comparable paths
        Comparator<? super Integer> cpr = comparator;
        if (cpr != null) {
            do {
                parent = t;
                cmp = cpr.compare(key, t.key);
                if (cmp < 0)
                    t = t.left;
                else if (cmp > 0)
                    t = t.right;
                else
                {
                    count -= t.value.size();
                    count += value.size();
                    return t.setValue(value);
                }
            } while (t != null);
        }
        else {
            do {
                parent = t;
                cmp = Integer.compare(key, t.key);
                if (cmp < 0)
                    t = t.left;
                else if (cmp > 0)
                    t = t.right;
                else
                {
                    count -= t.value.size();
                    count += value.size();
                    return t.setValue(value);
                }
            } while (t != null);
        }
        Entry<V> e = new Entry<>(key, value, parent);
        if (cmp < 0)
            parent.left = e;
        else
            parent.right = e;
        fixAfterInsertion(e);
        size++;
        count += value.size();
        modCount++;
        return null;
    }

    public void add(V value) {
        int key = value.pos;

        Entry<V> t = root;
        if (t == null) {
            compare(key, key); // type (and possibly null) check

            root = new Entry<>(key, value, null);
            size = 1;
            count = 1;
            modCount++;
            return;
        }
        int cmp;
        Entry<V> parent;
        // split comparator and comparable paths
        Comparator<? super Integer> cpr = comparator;
        if (cpr != null) {
            do {
                parent = t;
                cmp = cpr.compare(key, t.key);
                if (cmp < 0)
                    t = t.left;
                else if (cmp > 0)
                    t = t.right;
                else
                {
                    count++;
                    t.addValue(value);
                    return;
                }
            } while (t != null);
        }
        else {
            do {
                parent = t;
                cmp = Integer.compare(key, t.key);
                if (cmp < 0)
                    t = t.left;
                else if (cmp > 0)
                    t = t.right;
                else
                {
                    count++;
                    t.addValue(value);
                    return;
                }
            } while (t != null);
        }
        Entry<V> e = new Entry<>(key, value, parent);
        if (cmp < 0)
            parent.left = e;
        else
            parent.right = e;
        fixAfterInsertion(e);
        size++;
        count++;
        modCount++;
    }

    /**
     * Removes the mapping for this key from this TreeMap if present.
     *
     * @param  key key for which mapping should be removed
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}.
     *         (A {@code null} return can also indicate that the map
     *         previously associated {@code null} with {@code key}.)
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     */
    public ArrayList<V> remove(Object key) {
        Entry<V> p = getEntry(key);
        if (p == null)
            return null;

        ArrayList<V> oldValue = p.value;
        deleteEntry(p);
        return oldValue;
    }

    /**
     * Removes all of the mappings from this map.
     * The map will be empty after this call returns.
     */
    public void clear() {
        modCount++;
        size = 0;
        root = null;
    }

    /**
     * Returns a shallow copy of this {@code TreeMap} instance. (The keys and
     * values themselves are not cloned.)
     *
     * @return a shallow copy of this map
     */
    public Object clone() {
        PositionalObjectTreeMap<?> clone;
        try {
            clone = (PositionalObjectTreeMap<?>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }

        // Put clone into "virgin" state (except for comparator)
        clone.root = null;
        clone.size = 0;
        clone.modCount = 0;
        clone.entrySet = null;
        clone.navigableKeySet = null;
        clone.descendingMap = null;

        // Initialize clone with our mappings
        try {
            clone.buildFromSorted(size, entrySet().iterator(), null, null);
        } catch (IOException | ClassNotFoundException cannotHappen) {
        }

        return clone;
    }

    // NavigableMap API methods

    /**
     * @since 1.6
     */
    public Map.Entry<Integer, ArrayList<V>> firstEntry() {
        return exportEntry(getFirstEntry());
    }

    /**
     * @since 1.6
     */
    public Map.Entry<Integer, ArrayList<V>> lastEntry() {
        return exportEntry(getLastEntry());
    }

    /**
     * @since 1.6
     */
    public Map.Entry<Integer, ArrayList<V>> pollFirstEntry() {
        Entry<V> p = getFirstEntry();
        Map.Entry<Integer, ArrayList<V>> result = exportEntry(p);
        if (p != null)
            deleteEntry(p);
        return result;
    }

    /**
     * @since 1.6
     */
    public Map.Entry<Integer, ArrayList<V>> pollLastEntry() {
        Entry<V> p = getLastEntry();
        Map.Entry<Integer, ArrayList<V>> result = exportEntry(p);
        if (p != null)
            deleteEntry(p);
        return result;
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public Map.Entry<Integer, ArrayList<V>> lowerEntry(Integer key) {
        return exportEntry(getLowerEntry(key));
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public Integer lowerKey(Integer key) {
        return keyOrNull(getLowerEntry(key));
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public Map.Entry<Integer, ArrayList<V>> floorEntry(Integer key) {
        return exportEntry(getFloorEntry(key));
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public Integer floorKey(Integer key) {
        return keyOrNull(getFloorEntry(key));
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public Map.Entry<Integer, ArrayList<V>> ceilingEntry(Integer key) {
        return exportEntry(getCeilingEntry(key));
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public Integer ceilingKey(Integer key) {
        return keyOrNull(getCeilingEntry(key));
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public Map.Entry<Integer, ArrayList<V>> higherEntry(Integer key) {
        return exportEntry(getHigherEntry(key));
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public Integer higherKey(Integer key) {
        return keyOrNull(getHigherEntry(key));
    }

    // Views

    /**
     * Fields initialized to contain an instance of the entry set view
     * the first time this view is requested.  Views are stateless, so
     * there's no reason to create more than one.
     */
    private transient EntrySet entrySet;
    private transient KeySet navigableKeySet;
    private transient NavigableMap<Integer, ArrayList<V>> descendingMap;

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     *
     * <p>The set's iterator returns the keys in ascending order.
     * The set's spliterator is
     * <em><a href="Spliterator.html#binding">late-binding</a></em>,
     * <em>fail-fast</em>, and additionally reports {@link Spliterator#SORTED}
     * and {@link Spliterator#ORDERED} with an encounter order that is ascending
     * key order.  The spliterator's comparator (see
     * {@link java.util.Spliterator#getComparator()}) is {@code null} if
     * the tree map's comparator (see {@link #comparator()}) is {@code null}.
     * Otherwise, the spliterator's comparator is the same as or imposes the
     * same total ordering as the tree map's comparator.
     *
     * <p>The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own {@code remove} operation), the results of
     * the iteration are undefined.  The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * {@code Iterator.remove}, {@code Set.remove},
     * {@code removeAll}, {@code retainAll}, and {@code clear}
     * operations.  It does not support the {@code add} or {@code addAll}
     * operations.
     */
    public Set<Integer> keySet() {
        return navigableKeySet();
    }

    /**
     * @since 1.6
     */
    public NavigableSet<Integer> navigableKeySet() {
        KeySet nks = navigableKeySet;
        return (nks != null) ? nks : (navigableKeySet = new KeySet(this));
    }

    /**
     * @since 1.6
     */
    public NavigableSet<Integer> descendingKeySet() {
        return descendingMap().navigableKeySet();
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     *
     * <p>The collection's iterator returns the values in ascending order
     * of the corresponding keys. The collection's spliterator is
     * <em><a href="Spliterator.html#binding">late-binding</a></em>,
     * <em>fail-fast</em>, and additionally reports {@link Spliterator#ORDERED}
     * with an encounter order that is ascending order of the corresponding
     * keys.
     *
     * <p>The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  If the map is
     * modified while an iteration over the collection is in progress
     * (except through the iterator's own {@code remove} operation),
     * the results of the iteration are undefined.  The collection
     * supports element removal, which removes the corresponding
     * mapping from the map, via the {@code Iterator.remove},
     * {@code Collection.remove}, {@code removeAll},
     * {@code retainAll} and {@code clear} operations.  It does not
     * support the {@code add} or {@code addAll} operations.
     */
    public Collection<ArrayList<V>> values() {
        Collection<ArrayList<V>> vs = values;
        if (vs == null) {
            vs = new Values();
            values = vs;
        }
        return vs;
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     *
     * <p>The set's iterator returns the entries in ascending key order. The
     * sets's spliterator is
     * <em><a href="Spliterator.html#binding">late-binding</a></em>,
     * <em>fail-fast</em>, and additionally reports {@link Spliterator#SORTED} and
     * {@link Spliterator#ORDERED} with an encounter order that is ascending key
     * order.
     *
     * <p>The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own {@code remove} operation, or through the
     * {@code setValue} operation on a map entry returned by the
     * iterator) the results of the iteration are undefined.  The set
     * supports element removal, which removes the corresponding
     * mapping from the map, via the {@code Iterator.remove},
     * {@code Set.remove}, {@code removeAll}, {@code retainAll} and
     * {@code clear} operations.  It does not support the
     * {@code add} or {@code addAll} operations.
     */
    public Set<Map.Entry<Integer, ArrayList<V>>> entrySet() {
        EntrySet es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet());
    }

    /**
     * @since 1.6
     */
    public NavigableMap<Integer, ArrayList<V>> descendingMap() {
        NavigableMap<Integer, ArrayList<V>> km = descendingMap;
        return (km != null) ? km :
                (descendingMap = new DescendingSubMap<>(this,
                        true, null, true,
                        true, null, true));
    }

    public NavigableMap<Integer, ArrayList<V>> descendingSubMap(Integer fromKey, boolean fromInclusive, Integer toKey, boolean toInclusive) {
        return new DescendingSubMap<>(this,
                        false, fromKey, fromInclusive,
                        false, toKey, toInclusive);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException if {@code fromKey} or {@code toKey} is
     *         null and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    public NavigableMap<Integer,ArrayList<V>> subMap(Integer fromKey, boolean fromInclusive,
                                    Integer toKey,   boolean toInclusive) {
        return new AscendingSubMap<>(this,
                false, fromKey, fromInclusive,
                false, toKey,   toInclusive);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException if {@code toKey} is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    public NavigableMap<Integer,ArrayList<V>> headMap(Integer toKey, boolean inclusive) {
        return new AscendingSubMap<>(this,
                true,  null,  true,
                false, toKey, inclusive);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException if {@code fromKey} is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    public NavigableMap<Integer,ArrayList<V>> tailMap(Integer fromKey, boolean inclusive) {
        return new AscendingSubMap<>(this,
                false, fromKey, inclusive,
                true,  null,    true);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException if {@code fromKey} or {@code toKey} is
     *         null and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public SortedMap<Integer,ArrayList<V>> subMap(Integer fromKey, Integer toKey) {
        return subMap(fromKey, true, toKey, false);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException if {@code toKey} is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public SortedMap<Integer,ArrayList<V>> headMap(Integer toKey) {
        return headMap(toKey, false);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException if {@code fromKey} is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public SortedMap<Integer,ArrayList<V>> tailMap(Integer fromKey) {
        return tailMap(fromKey, true);
    }

    @Override
    public boolean replace(Integer key, ArrayList<V> oldValue, ArrayList<V> newValue) {
        Entry<V> p = getEntry(key);
        if (p!=null && Objects.equals(oldValue, p.value)) {
            p.value = newValue;
            return true;
        }
        return false;
    }

    @Override
    public ArrayList<V> replace(Integer key, ArrayList<V> value) {
        Entry<V> p = getEntry(key);
        if (p!=null) {
            ArrayList<V> oldValue = p.value;
            p.value = value;
            return oldValue;
        }
        return null;
    }

    @Override
    public void forEach(BiConsumer<? super Integer, ? super ArrayList<V>> action) {
        Objects.requireNonNull(action);
        int expectedModCount = modCount;
        for (Entry<V> e = getFirstEntry(); e != null; e = successor(e)) {
            action.accept(e.key, e.value);

            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    @Override
    public void replaceAll(BiFunction<? super Integer, ? super ArrayList<V>, ? extends ArrayList<V>> function) {
        Objects.requireNonNull(function);
        int expectedModCount = modCount;

        for (Entry<V> e = getFirstEntry(); e != null; e = successor(e)) {
            e.value = function.apply(e.key, e.value);

            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    // View class support

    class Values extends AbstractCollection<ArrayList<V>> {
        public Iterator<ArrayList<V>> iterator() {
            return new ValueIterator(getFirstEntry());
        }

        public int size() {
            return PositionalObjectTreeMap.this.size();
        }

        public boolean contains(Object o) {
            return PositionalObjectTreeMap.this.containsValue(o);
        }

        public boolean remove(Object o) {
            for (Entry<V> e = getFirstEntry(); e != null; e = successor(e)) {
                if (val(e.getValue(), o)) {
                    deleteEntry(e);
                    return true;
                }
            }
            return false;
        }

        public void clear() {
            PositionalObjectTreeMap.this.clear();
        }

        public Spliterator<ArrayList<V>> spliterator() {
            return new ValueSpliterator<>(PositionalObjectTreeMap.this, null, null, 0, -1, 0);
        }
    }

    class EntrySet extends AbstractSet<Map.Entry<Integer, ArrayList<V>>> {
        public Iterator<Map.Entry<Integer, ArrayList<V>>> iterator() {
            return new EntryIterator(getFirstEntry());
        }

        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> entry = (Map.Entry<?,?>) o;
            Object value = entry.getValue();
            Entry<V> p = getEntry(entry.getKey());
            return p != null && val(p.getValue(), value);
        }

        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> entry = (Map.Entry<?,?>) o;
            Object value = entry.getValue();
            Entry<V> p = getEntry(entry.getKey());
            if (p != null && val(p.getValue(), value)) {
                deleteEntry(p);
                return true;
            }
            return false;
        }

        public int size() {
            return PositionalObjectTreeMap.this.size();
        }

        public void clear() {
            PositionalObjectTreeMap.this.clear();
        }

        public Spliterator<Map.Entry<Integer, ArrayList<V>>> spliterator() {
            return new EntrySpliterator<>(PositionalObjectTreeMap.this, null, null, 0, -1, 0);
        }
    }

    /*
     * Unlike Values and EntrySet, the KeySet class is static,
     * delegating to a NavigableMap to allow use by SubMaps, which
     * outweighs the ugliness of needing type-tests for the following
     * Iterator methods that are defined appropriately in main versus
     * submap classes.
     */

    Iterator<Integer> keyIterator() {
        return new KeyIterator(getFirstEntry());
    }

    Iterator<Integer> descendingKeyIterator() {
        return new DescendingKeyIterator(getLastEntry());
    }

    static final class KeySet extends AbstractSet<Integer> implements NavigableSet<Integer> {
        private final NavigableMap<Integer, ?> m;
        KeySet(NavigableMap<Integer,?> map) { m = map; }

        public Iterator<Integer> iterator() {
            if (m instanceof PositionalObjectTreeMap)
                return ((PositionalObjectTreeMap<?>)m).keyIterator();
            else
                return ((NavigableSubMap<?>)m).keyIterator();
        }

        public Iterator<Integer> descendingIterator() {
            if (m instanceof PositionalObjectTreeMap)
                return ((PositionalObjectTreeMap<?>)m).descendingKeyIterator();
            else
                return ((NavigableSubMap<?>)m).descendingKeyIterator();
        }

        public int size() { return m.size(); }
        public boolean isEmpty() { return m.isEmpty(); }
        public boolean contains(Object o) { return m.containsKey(o); }
        public void clear() { m.clear(); }
        public Integer lower(Integer e) { return m.lowerKey(e); }
        public Integer floor(Integer e) { return m.floorKey(e); }
        public Integer ceiling(Integer e) { return m.ceilingKey(e); }
        public Integer higher(Integer e) { return m.higherKey(e); }
        public Integer first() { return m.firstKey(); }
        public Integer last() { return m.lastKey(); }
        public Comparator<? super Integer> comparator() { return m.comparator(); }
        public Integer pollFirst() {
            Map.Entry<Integer,?> e = m.pollFirstEntry();
            return (e == null) ? null : e.getKey();
        }
        public Integer pollLast() {
            Map.Entry<Integer,?> e = m.pollLastEntry();
            return (e == null) ? null : e.getKey();
        }
        public boolean remove(Object o) {
            int oldSize = size();
            m.remove(o);
            return size() != oldSize;
        }
        public NavigableSet<Integer> subSet(Integer fromElement, boolean fromInclusive,
                                      Integer toElement,   boolean toInclusive) {
            return new KeySet(m.subMap(fromElement, fromInclusive,
                    toElement,   toInclusive));
        }
        public NavigableSet<Integer> headSet(Integer toIntegerlement, boolean inclusive) {
            return new KeySet(m.headMap(toIntegerlement, inclusive));
        }
        public NavigableSet<Integer> tailSet(Integer fromIntegerlement, boolean inclusive) {
            return new KeySet(m.tailMap(fromIntegerlement, inclusive));
        }
        public SortedSet<Integer> subSet(Integer fromIntegerlement, Integer toIntegerlement) {
            return subSet(fromIntegerlement, true, toIntegerlement, false);
        }
        public SortedSet<Integer> headSet(Integer toIntegerlement) {
            return headSet(toIntegerlement, false);
        }
        public SortedSet<Integer> tailSet(Integer fromIntegerlement) {
            return tailSet(fromIntegerlement, true);
        }
        public NavigableSet<Integer> descendingSet() {
            return new KeySet(m.descendingMap());
        }

        public Spliterator<Integer> spliterator() {
            return keySpliteratorFor(m);
        }
    }

    /**
     * Base class for TreeMap Iterators
     */
    abstract class PrivateEntryIterator<T> implements Iterator<T> {
        Entry<V> next;
        Entry<V> lastReturned;
        int expectedModCount;

        PrivateEntryIterator(Entry<V> first) {
            expectedModCount = modCount;
            lastReturned = null;
            next = first;
        }

        public final boolean hasNext() {
            return next != null;
        }

        final Entry<V> nextEntry() {
            Entry<V> e = next;
            if (e == null)
                throw new NoSuchElementException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            next = successor(e);
            lastReturned = e;
            return e;
        }

        final Entry<V> prevEntry() {
            Entry<V> e = next;
            if (e == null)
                throw new NoSuchElementException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            next = predecessor(e);
            lastReturned = e;
            return e;
        }

        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            // deleted entries are replaced by their successors
            if (lastReturned.left != null && lastReturned.right != null)
                next = lastReturned;
            deleteEntry(lastReturned);
            expectedModCount = modCount;
            lastReturned = null;
        }
    }

    final class EntryIterator extends PrivateEntryIterator<Map.Entry<Integer, ArrayList<V>>> {
        EntryIterator(Entry<V> first) {
            super(first);
        }
        public Map.Entry<Integer, ArrayList<V>> next() {
            return nextEntry();
        }
    }

    final class ValueIterator extends PrivateEntryIterator<ArrayList<V>> {
        ValueIterator(Entry<V> first) {
            super(first);
        }
        public ArrayList<V> next() {
            return nextEntry().value;
        }
    }

    final class KeyIterator extends PrivateEntryIterator<Integer> {
        KeyIterator(Entry<V> first) {
            super(first);
        }
        public Integer next() {
            return nextEntry().key;
        }
    }

    final class DescendingKeyIterator extends PrivateEntryIterator<Integer> {
        DescendingKeyIterator(Entry<V> first) {
            super(first);
        }
        public Integer next() {
            return prevEntry().key;
        }
        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            deleteEntry(lastReturned);
            lastReturned = null;
            expectedModCount = modCount;
        }
    }

    // Little utilities

    /**
     * Compares two keys using the correct comparison method for this TreeMap.
     */
    @SuppressWarnings("unchecked")
    final int compare(Object k1, Object k2) {
        return comparator==null ? ((Comparable<? super Integer>)k1).compareTo((Integer)k2)
                : comparator.compare((Integer)k1, (Integer)k2);
    }

    /**
     * Test two values for equality.  Differs from o1.equals(o2) only in
     * that it copes with {@code null} o1 properly.
     */
    static boolean val(Object o1, Object o2) {
        return Objects.equals(o1, o2);
    }

    /**
     * Return SimpleImmutableEntry for entry, or null if null
     */
    static <V> Map.Entry<Integer, ArrayList<V>> exportEntry(Entry<V> e) {
        return (e == null) ? null :
                new AbstractMap.SimpleImmutableEntry<>(e);
    }

    /**
     * Return key for entry, or null if null
     */
    static Integer keyOrNull(Entry<?> e) {
        return (e == null) ? null : e.key;
    }

    /**
     * Returns the key corresponding to the specified Entry.
     * @throws NoSuchElementException if the Entry is null
     */
    static Integer key(Entry<?> e) {
        if (e==null)
            throw new NoSuchElementException();
        return e.key;
    }


    // SubMaps

    /**
     * Dummy value serving as unmatchable fence key for unbounded
     * SubMapIterators
     */
    private static final Object UNBOUNDED = new Object();

    /**
     * @serial include
     */
    abstract static class NavigableSubMap<V extends PositionalObject> extends AbstractMap<Integer, ArrayList<V>>
            implements NavigableMap<Integer, ArrayList<V>>, java.io.Serializable {
        private static final long serialVersionUID = -2102997345730753016L;
        /**
         * The backing map.
         */
        final PositionalObjectTreeMap<V> m;

        /**
         * Endpoints are represented as triples (fromStart, lo,
         * loInclusive) and (toEnd, hi, hiInclusive). If fromStart is
         * true, then the low (absolute) bound is the start of the
         * backing map, and the other values are ignored. Otherwise,
         * if loInclusive is true, lo is the inclusive bound, else lo
         * is the exclusive bound. Similarly for the upper bound.
         */
        final Integer lo, hi;
        final boolean fromStart, toEnd;
        final boolean loInclusive, hiInclusive;

        NavigableSubMap(PositionalObjectTreeMap<V> m,
                        boolean fromStart, Integer lo, boolean loInclusive,
                        boolean toEnd, Integer hi, boolean hiInclusive) {
            if (!fromStart && !toEnd) {
                if (m.compare(lo, hi) > 0)
                    throw new IllegalArgumentException("fromKey > toKey");
            } else {
                if (!fromStart) // type check
                    m.compare(lo, lo);
                if (!toEnd)
                    m.compare(hi, hi);
            }

            this.m = m;
            this.fromStart = fromStart;
            this.lo = lo;
            this.loInclusive = loInclusive;
            this.toEnd = toEnd;
            this.hi = hi;
            this.hiInclusive = hiInclusive;
        }

        // internal utilities

        final boolean tooLow(Object key) {
            if (!fromStart) {
                int c = m.compare(key, lo);
                return c < 0 || (c == 0 && !loInclusive);
            }
            return false;
        }

        final boolean tooHigh(Object key) {
            if (!toEnd) {
                int c = m.compare(key, hi);
                return c > 0 || (c == 0 && !hiInclusive);
            }
            return false;
        }

        final boolean inRange(Object key) {
            return !tooLow(key) && !tooHigh(key);
        }

        final boolean inClosedRange(Object key) {
            return (fromStart || m.compare(key, lo) >= 0)
                    && (toEnd || m.compare(hi, key) >= 0);
        }

        final boolean inRange(Object key, boolean inclusive) {
            return inclusive ? inRange(key) : inClosedRange(key);
        }

        /*
         * Absolute versions of relation operations.
         * Subclasses map to these using like-named "sub"
         * versions that invert senses for descending maps
         */

        final PositionalObjectTreeMap.Entry<V> absLowest() {
            PositionalObjectTreeMap.Entry<V> e =
                    (fromStart ?  m.getFirstEntry() :
                            (loInclusive ? m.getCeilingEntry(lo) :
                                    m.getHigherEntry(lo)));
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final PositionalObjectTreeMap.Entry<V> absHighest() {
            PositionalObjectTreeMap.Entry<V> e =
                    (toEnd ?  m.getLastEntry() :
                            (hiInclusive ?  m.getFloorEntry(hi) :
                                    m.getLowerEntry(hi)));
            return (e == null || tooLow(e.key)) ? null : e;
        }

        final PositionalObjectTreeMap.Entry<V> absCeiling(Integer key) {
            if (tooLow(key))
                return absLowest();
            PositionalObjectTreeMap.Entry<V> e = m.getCeilingEntry(key);
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final PositionalObjectTreeMap.Entry<V> absHigher(Integer key) {
            if (tooLow(key))
                return absLowest();
            PositionalObjectTreeMap.Entry<V> e = m.getHigherEntry(key);
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final PositionalObjectTreeMap.Entry<V> absFloor(Integer key) {
            if (tooHigh(key))
                return absHighest();
            PositionalObjectTreeMap.Entry<V> e = m.getFloorEntry(key);
            return (e == null || tooLow(e.key)) ? null : e;
        }

        final PositionalObjectTreeMap.Entry<V> absLower(Integer key) {
            if (tooHigh(key))
                return absHighest();
            PositionalObjectTreeMap.Entry<V> e = m.getLowerEntry(key);
            return (e == null || tooLow(e.key)) ? null : e;
        }

        /** Returns the absolute high fence for ascending traversal */
        final PositionalObjectTreeMap.Entry<V> absHighFence() {
            return (toEnd ? null : (hiInclusive ?
                    m.getHigherEntry(hi) :
                    m.getCeilingEntry(hi)));
        }

        /** Return the absolute low fence for descending traversal  */
        final PositionalObjectTreeMap.Entry<V> absLowFence() {
            return (fromStart ? null : (loInclusive ?
                    m.getLowerEntry(lo) :
                    m.getFloorEntry(lo)));
        }

        // Abstract methods defined in ascending vs descending classes
        // These relay to the appropriate absolute versions

        abstract PositionalObjectTreeMap.Entry<V> subLowest();
        abstract PositionalObjectTreeMap.Entry<V> subHighest();
        abstract PositionalObjectTreeMap.Entry<V> subCeiling(Integer key);
        abstract PositionalObjectTreeMap.Entry<V> subHigher(Integer key);
        abstract PositionalObjectTreeMap.Entry<V> subFloor(Integer key);
        abstract PositionalObjectTreeMap.Entry<V> subLower(Integer key);

        /** Returns ascending iterator from the perspective of this submap */
        abstract Iterator<Integer> keyIterator();

        abstract Spliterator<Integer> keySpliterator();

        /** Returns descending iterator from the perspective of this submap */
        abstract Iterator<Integer> descendingKeyIterator();

        // public methods

        public boolean isEmpty() {
            return (fromStart && toEnd) ? m.isEmpty() : entrySet().isEmpty();
        }

        public int size() {
            return (fromStart && toEnd) ? m.size() : entrySet().size();
        }

        public final boolean containsKey(Object key) {
            return inRange(key) && m.containsKey(key);
        }

        public final ArrayList<V> put(Integer key, ArrayList<V> value) {
            if (!inRange(key))
                throw new IllegalArgumentException("key out of range");
            return m.put(key, value);
        }

        public final ArrayList<V> get(Object key) {
            return !inRange(key) ? null :  m.get(key);
        }

        public final ArrayList<V> remove(Object key) {
            return !inRange(key) ? null : m.remove(key);
        }

        public final Map.Entry<Integer, ArrayList<V>> ceilingEntry(Integer key) {
            return exportEntry(subCeiling(key));
        }

        public final Integer ceilingKey(Integer key) {
            return keyOrNull(subCeiling(key));
        }

        public final Map.Entry<Integer, ArrayList<V>> higherEntry(Integer key) {
            return exportEntry(subHigher(key));
        }

        public final Integer higherKey(Integer key) {
            return keyOrNull(subHigher(key));
        }

        public final Map.Entry<Integer, ArrayList<V>> floorEntry(Integer key) {
            return exportEntry(subFloor(key));
        }

        public final Integer floorKey(Integer key) {
            return keyOrNull(subFloor(key));
        }

        public final Map.Entry<Integer, ArrayList<V>> lowerEntry(Integer key) {
            return exportEntry(subLower(key));
        }

        public final Integer lowerKey(Integer key) {
            return keyOrNull(subLower(key));
        }

        public final Integer firstKey() {
            return key(subLowest());
        }

        public final Integer lastKey() {
            return key(subHighest());
        }

        public final Map.Entry<Integer, ArrayList<V>> firstEntry() {
            return exportEntry(subLowest());
        }

        public final Map.Entry<Integer, ArrayList<V>> lastEntry() {
            return exportEntry(subHighest());
        }

        public final Map.Entry<Integer, ArrayList<V>> pollFirstEntry() {
            PositionalObjectTreeMap.Entry<V> e = subLowest();
            Map.Entry<Integer, ArrayList<V>> result = exportEntry(e);
            if (e != null)
                m.deleteEntry(e);
            return result;
        }

        public final Map.Entry<Integer, ArrayList<V>> pollLastEntry() {
            PositionalObjectTreeMap.Entry<V> e = subHighest();
            Map.Entry<Integer, ArrayList<V>> result = exportEntry(e);
            if (e != null)
                m.deleteEntry(e);
            return result;
        }

        // Views
        transient NavigableMap<Integer,ArrayList<V>> descendingMapView;
        transient NavigableSubMap<V>.EntrySetView entrySetView;
        transient KeySet navigableKeySetView;

        public final NavigableSet<Integer> navigableKeySet() {
            KeySet nksv = navigableKeySetView;
            return (nksv != null) ? nksv :
                    (navigableKeySetView = new KeySet(this));
        }

        public final Set<Integer> keySet() {
            return navigableKeySet();
        }

        public NavigableSet<Integer> descendingKeySet() {
            return descendingMap().navigableKeySet();
        }

        public final SortedMap<Integer,ArrayList<V>> subMap(Integer fromKey, Integer toKey) {
            return subMap(fromKey, true, toKey, false);
        }

        public final SortedMap<Integer,ArrayList<V>> headMap(Integer toKey) {
            return headMap(toKey, false);
        }

        public final SortedMap<Integer,ArrayList<V>> tailMap(Integer fromKey) {
            return tailMap(fromKey, true);
        }

        // View classes

        abstract class EntrySetView extends AbstractSet<Map.Entry<Integer, ArrayList<V>>> {
            private transient int size = -1, sizeModCount;

            public int size() {
                if (fromStart && toEnd)
                    return m.size();
                if (size == -1 || sizeModCount != m.modCount) {
                    sizeModCount = m.modCount;
                    size = 0;
                    for (Entry<Integer, ArrayList<V>> ignored : this) {
                        size++;
                    }
                }
                return size;
            }

            public boolean isEmpty() {
                PositionalObjectTreeMap.Entry<V> n = absLowest();
                return n == null || tooHigh(n.key);
            }

            public boolean contains(Object o) {
                if (!(o instanceof Map.Entry))
                    return false;
                Map.Entry<?,?> entry = (Map.Entry<?,?>) o;
                Object key = entry.getKey();
                if (!inRange(key))
                    return false;
                Entry<?,?> node = m.getEntry(key);
                return node != null &&
                        val(node.getValue(), entry.getValue());
            }

            public boolean remove(Object o) {
                if (!(o instanceof Map.Entry))
                    return false;
                Map.Entry<?,?> entry = (Map.Entry<?,?>) o;
                Object key = entry.getKey();
                if (!inRange(key))
                    return false;
                PositionalObjectTreeMap.Entry<V> node = m.getEntry(key);
                if (node!=null && val(node.getValue(),
                        entry.getValue())) {
                    m.deleteEntry(node);
                    return true;
                }
                return false;
            }
        }

        /**
         * Iterators for SubMaps
         */
        abstract class SubMapIterator<T> implements Iterator<T> {
            PositionalObjectTreeMap.Entry<V> lastReturned;
            PositionalObjectTreeMap.Entry<V> next;
            final Object fenceKey;
            int expectedModCount;

            SubMapIterator(PositionalObjectTreeMap.Entry<V> first,
                           PositionalObjectTreeMap.Entry<V> fence) {
                expectedModCount = m.modCount;
                lastReturned = null;
                next = first;
                fenceKey = fence == null ? UNBOUNDED : fence.key;
            }

            public final boolean hasNext() {
                return next != null && next.key != fenceKey;
            }

            final PositionalObjectTreeMap.Entry<V> nextEntry() {
                PositionalObjectTreeMap.Entry<V> e = next;
                if (e == null || e.key == fenceKey)
                    throw new NoSuchElementException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                next = successor(e);
                lastReturned = e;
                return e;
            }

            final PositionalObjectTreeMap.Entry<V> prevEntry() {
                PositionalObjectTreeMap.Entry<V> e = next;
                if (e == null || e.key == fenceKey)
                    throw new NoSuchElementException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                next = predecessor(e);
                lastReturned = e;
                return e;
            }

            final void removeAscending() {
                if (lastReturned == null)
                    throw new IllegalStateException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                // deleted entries are replaced by their successors
                if (lastReturned.left != null && lastReturned.right != null)
                    next = lastReturned;
                m.deleteEntry(lastReturned);
                lastReturned = null;
                expectedModCount = m.modCount;
            }

            final void removeDescending() {
                if (lastReturned == null)
                    throw new IllegalStateException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                m.deleteEntry(lastReturned);
                lastReturned = null;
                expectedModCount = m.modCount;
            }

        }

        final class SubMapEntryIterator extends SubMapIterator<Map.Entry<Integer, ArrayList<V>>> {
            SubMapEntryIterator(PositionalObjectTreeMap.Entry<V> first,
                                PositionalObjectTreeMap.Entry<V> fence) {
                super(first, fence);
            }
            public Map.Entry<Integer, ArrayList<V>> next() {
                return nextEntry();
            }
            public void remove() {
                removeAscending();
            }
        }

        final class DescendingSubMapEntryIterator extends SubMapIterator<Map.Entry<Integer, ArrayList<V>>> {
            DescendingSubMapEntryIterator(PositionalObjectTreeMap.Entry<V> last,
                                          PositionalObjectTreeMap.Entry<V> fence) {
                super(last, fence);
            }

            public Map.Entry<Integer, ArrayList<V>> next() {
                return prevEntry();
            }
            public void remove() {
                removeDescending();
            }
        }

        // Implement minimal Spliterator as KeySpliterator backup
        final class SubMapKeyIterator extends SubMapIterator<Integer>
                implements Spliterator<Integer> {
            SubMapKeyIterator(PositionalObjectTreeMap.Entry<V> first,
                              PositionalObjectTreeMap.Entry<V> fence) {
                super(first, fence);
            }
            public Integer next() {
                return nextEntry().key;
            }
            public void remove() {
                removeAscending();
            }
            public Spliterator<Integer> trySplit() {
                return null;
            }
            public void forEachRemaining(Consumer<? super Integer> action) {
                while (hasNext())
                    action.accept(next());
            }
            public boolean tryAdvance(Consumer<? super Integer> action) {
                if (hasNext()) {
                    action.accept(next());
                    return true;
                }
                return false;
            }
            public long estimateSize() {
                return Long.MAX_VALUE;
            }
            public int characteristics() {
                return Spliterator.DISTINCT | Spliterator.ORDERED |
                        Spliterator.SORTED;
            }
            public final Comparator<? super Integer>  getComparator() {
                return NavigableSubMap.this.comparator();
            }
        }

        final class DescendingSubMapKeyIterator extends SubMapIterator<Integer>
                implements Spliterator<Integer> {
            DescendingSubMapKeyIterator(PositionalObjectTreeMap.Entry<V> last,
                                        PositionalObjectTreeMap.Entry<V> fence) {
                super(last, fence);
            }
            public Integer next() {
                return prevEntry().key;
            }
            public void remove() {
                removeDescending();
            }
            public Spliterator<Integer> trySplit() {
                return null;
            }
            public void forEachRemaining(Consumer<? super Integer> action) {
                while (hasNext())
                    action.accept(next());
            }
            public boolean tryAdvance(Consumer<? super Integer> action) {
                if (hasNext()) {
                    action.accept(next());
                    return true;
                }
                return false;
            }
            public long estimateSize() {
                return Long.MAX_VALUE;
            }
            public int characteristics() {
                return Spliterator.DISTINCT | Spliterator.ORDERED;
            }
        }
    }

    /**
     * @serial include
     */
    static final class AscendingSubMap<V extends PositionalObject> extends NavigableSubMap<V> {
        private static final long serialVersionUID = 912986545866124060L;

        AscendingSubMap(PositionalObjectTreeMap<V> m,
                        boolean fromStart, Integer lo, boolean loInclusive,
                        boolean toEnd, Integer hi, boolean hiInclusive) {
            super(m, fromStart, lo, loInclusive, toEnd, hi, hiInclusive);
        }

        public Comparator<? super Integer> comparator() {
            return m.comparator();
        }

        public NavigableMap<Integer,ArrayList<V>> subMap(Integer fromKey, boolean fromInclusive,
                                        Integer toKey,   boolean toInclusive) {
            if (!inRange(fromKey, fromInclusive))
                throw new IllegalArgumentException("fromKey out of range");
            if (!inRange(toKey, toInclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new AscendingSubMap<>(m,
                    false, fromKey, fromInclusive,
                    false, toKey,   toInclusive);
        }

        public NavigableMap<Integer,ArrayList<V>> headMap(Integer toKey, boolean inclusive) {
            if (!inRange(toKey, inclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new AscendingSubMap<>(m,
                    fromStart, lo,    loInclusive,
                    false,     toKey, inclusive);
        }

        public NavigableMap<Integer,ArrayList<V>> tailMap(Integer fromKey, boolean inclusive) {
            if (!inRange(fromKey, inclusive))
                throw new IllegalArgumentException("fromKey out of range");
            return new AscendingSubMap<>(m,
                    false, fromKey, inclusive,
                    toEnd, hi,      hiInclusive);
        }

        public NavigableMap<Integer,ArrayList<V>> descendingMap() {
            NavigableMap<Integer,ArrayList<V>> mv = descendingMapView;
            return (mv != null) ? mv :
                    (descendingMapView =
                            new DescendingSubMap<>(m,
                                    fromStart, lo, loInclusive,
                                    toEnd,     hi, hiInclusive));
        }

        Iterator<Integer> keyIterator() {
            return new SubMapKeyIterator(absLowest(), absHighFence());
        }

        Spliterator<Integer> keySpliterator() {
            return new SubMapKeyIterator(absLowest(), absHighFence());
        }

        Iterator<Integer> descendingKeyIterator() {
            return new DescendingSubMapKeyIterator(absHighest(), absLowFence());
        }

        final class AscendingEntrySetView extends EntrySetView {
            public Iterator<Map.Entry<Integer, ArrayList<V>>> iterator() {
                return new SubMapEntryIterator(absLowest(), absHighFence());
            }
        }

        public Set<Map.Entry<Integer, ArrayList<V>>> entrySet() {
            EntrySetView es = entrySetView;
            return (es != null) ? es : (entrySetView = new AscendingSubMap<V>.AscendingEntrySetView());
        }

        PositionalObjectTreeMap.Entry<V> subLowest()       { return absLowest(); }
        PositionalObjectTreeMap.Entry<V> subHighest()      { return absHighest(); }
        PositionalObjectTreeMap.Entry<V> subCeiling(Integer key) { return absCeiling(key); }
        PositionalObjectTreeMap.Entry<V> subHigher(Integer key)  { return absHigher(key); }
        PositionalObjectTreeMap.Entry<V> subFloor(Integer key)   { return absFloor(key); }
        PositionalObjectTreeMap.Entry<V> subLower(Integer key)   { return absLower(key); }
    }

    /**
     * @serial include
     */
    static final class DescendingSubMap<V extends PositionalObject> extends NavigableSubMap<V> {
        private static final long serialVersionUID = 912986545866120460L;
        DescendingSubMap(PositionalObjectTreeMap<V> m,
                         boolean fromStart, Integer lo, boolean loInclusive,
                         boolean toEnd, Integer hi, boolean hiInclusive) {
            super(m, fromStart, lo, loInclusive, toEnd, hi, hiInclusive);
        }

        private final Comparator<? super Integer> reverseComparator =
                Collections.reverseOrder(m.comparator);

        public Comparator<? super Integer> comparator() {
            return reverseComparator;
        }

        public NavigableMap<Integer,ArrayList<V>> subMap(Integer fromKey, boolean fromInclusive,
                                        Integer toKey,   boolean toInclusive) {
            if (!inRange(fromKey, fromInclusive))
                throw new IllegalArgumentException("fromKey out of range");
            if (!inRange(toKey, toInclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new DescendingSubMap<>(m,
                    false, toKey,   toInclusive,
                    false, fromKey, fromInclusive);
        }

        public NavigableMap<Integer,ArrayList<V>> headMap(Integer toKey, boolean inclusive) {
            if (!inRange(toKey, inclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new DescendingSubMap<>(m,
                    false, toKey, inclusive,
                    toEnd, hi,    hiInclusive);
        }

        public NavigableMap<Integer,ArrayList<V>> tailMap(Integer fromKey, boolean inclusive) {
            if (!inRange(fromKey, inclusive))
                throw new IllegalArgumentException("fromKey out of range");
            return new DescendingSubMap<>(m,
                    fromStart, lo, loInclusive,
                    false, fromKey, inclusive);
        }

        public NavigableMap<Integer,ArrayList<V>> descendingMap() {
            NavigableMap<Integer,ArrayList<V>> mv = descendingMapView;
            return (mv != null) ? mv :
                    (descendingMapView =
                            new AscendingSubMap<>(m,
                                    fromStart, lo, loInclusive,
                                    toEnd,     hi, hiInclusive));
        }

        Iterator<Integer> keyIterator() {
            return new DescendingSubMapKeyIterator(absHighest(), absLowFence());
        }

        Spliterator<Integer> keySpliterator() {
            return new DescendingSubMapKeyIterator(absHighest(), absLowFence());
        }

        Iterator<Integer> descendingKeyIterator() {
            return new SubMapKeyIterator(absLowest(), absHighFence());
        }

        final class DescendingEntrySetView extends EntrySetView {
            public Iterator<Map.Entry<Integer, ArrayList<V>>> iterator() {
                return new DescendingSubMapEntryIterator(absHighest(), absLowFence());
            }
        }

        public Set<Map.Entry<Integer, ArrayList<V>>> entrySet() {
            EntrySetView es = entrySetView;
            return (es != null) ? es : (entrySetView = new DescendingSubMap<V>.DescendingEntrySetView());
        }

        PositionalObjectTreeMap.Entry<V> subLowest()       { return absHighest(); }
        PositionalObjectTreeMap.Entry<V> subHighest()      { return absLowest(); }
        PositionalObjectTreeMap.Entry<V> subCeiling(Integer key) { return absFloor(key); }
        PositionalObjectTreeMap.Entry<V> subHigher(Integer key)  { return absLower(key); }
        PositionalObjectTreeMap.Entry<V> subFloor(Integer key)   { return absCeiling(key); }
        PositionalObjectTreeMap.Entry<V> subLower(Integer key)   { return absHigher(key); }
    }

    /**
     * This class exists solely for the sake of serialization
     * compatibility with previous releases of TreeMap that did not
     * support NavigableMap.  It translates an old-version SubMap into
     * a new-version AscendingSubMap. This class is never otherwise
     * used.
     *
     * @serial include
     */
    private class SubMap extends AbstractMap<Integer, ArrayList<V>>
            implements SortedMap<Integer, ArrayList<V>>, java.io.Serializable {
        private static final long serialVersionUID = -6520786458950516097L;
        private boolean fromStart = false, toEnd = false;
        private Integer fromKey, toKey;
        private Object readResolve() {
            return new AscendingSubMap<>(PositionalObjectTreeMap.this,
                    fromStart, fromKey, true,
                    toEnd, toKey, false);
        }
        public Set<Map.Entry<Integer, ArrayList<V>>> entrySet() { throw new InternalError(); }
        public Integer lastKey() { throw new InternalError(); }
        public Integer firstKey() { throw new InternalError(); }
        public SortedMap<Integer,ArrayList<V>> subMap(Integer fromKey, Integer toKey) { throw new InternalError(); }
        public SortedMap<Integer,ArrayList<V>> headMap(Integer toKey) { throw new InternalError(); }
        public SortedMap<Integer,ArrayList<V>> tailMap(Integer fromKey) { throw new InternalError(); }
        public Comparator<? super Integer> comparator() { throw new InternalError(); }
    }


    // Red-black mechanics

    private static final boolean RED   = false;
    private static final boolean BLACK = true;

    /**
     * Node in the Tree.  Doubles as a means to pass key-value pairs back to
     * user (see Map.Entry).
     */

    static final class Entry<V> implements Map.Entry<Integer, ArrayList<V>> {
        Integer key;
        ArrayList<V> value;
        Entry<V> left;
        Entry<V> right;
        Entry<V> parent;
        boolean color = BLACK;

        /**
         * Make a new cell with given key, value, and parent, and with
         * {@code null} child links, and BLACK color.
         */
        Entry(Integer key, ArrayList<V> value, Entry<V> parent) {
            this.key = key;
            this.value = value;
            this.parent = parent;
        }

        /**
         * Make a new cell with given key, value within values, and parent, and with
         * {@code null} child links, and BLACK color.
         */
        Entry(Integer key, V value, Entry<V> parent) {
            this.key = key;
            this.value = new ArrayList<>();
            this.value.add(value);
            this.parent = parent;
        }

        /**
         * Returns the key.
         *
         * @return the key
         */
        public Integer getKey() {
            return key;
        }

        /**
         * Returns the value associated with the key.
         *
         * @return the value associated with the key
         */
        public ArrayList<V> getValue() {
            return value;
        }

        /**
         * Replaces the value currently associated with the key with the given
         * value.
         *
         * @return the value associated with the key before this method was
         *         called
         */
        public ArrayList<V> setValue(ArrayList<V> value) {
            ArrayList<V> oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        public ArrayList<V> addValue(V value) {
            this.value.add(value);
            return this.value;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;

            return val(key,e.getKey()) && val(value,e.getValue());
        }

        public int hashCode() {
            int keyHash = (key==null ? 0 : key.hashCode());
            int valueHash = (value==null ? 0 : value.hashCode());
            return keyHash ^ valueHash;
        }

        public String toString() {
            return key + "=" + value;
        }
    }

    /**
     * Returns the first Entry in the TreeMap (according to the TreeMap's
     * key-sort function).  Returns null if the TreeMap is empty.
     */
    final Entry<V> getFirstEntry() {
        Entry<V> p = root;
        if (p != null)
            while (p.left != null)
                p = p.left;
        return p;
    }

    /**
     * Returns the last Entry in the TreeMap (according to the TreeMap's
     * key-sort function).  Returns null if the TreeMap is empty.
     */
    final Entry<V> getLastEntry() {
        Entry<V> p = root;
        if (p != null)
            while (p.right != null)
                p = p.right;
        return p;
    }

    /**
     * Returns the successor of the specified Entry, or null if no such.
     */
    static <V> Entry<V> successor(Entry<V> t) {
        if (t == null)
            return null;
        else if (t.right != null) {
            Entry<V> p = t.right;
            while (p.left != null)
                p = p.left;
            return p;
        } else {
            Entry<V> p = t.parent;
            Entry<V> ch = t;
            while (p != null && ch == p.right) {
                ch = p;
                p = p.parent;
            }
            return p;
        }
    }

    /**
     * Returns the predecessor of the specified Entry, or null if no such.
     */
    static <V> Entry<V> predecessor(Entry<V> t) {
        if (t == null)
            return null;
        else if (t.left != null) {
            Entry<V> p = t.left;
            while (p.right != null)
                p = p.right;
            return p;
        } else {
            Entry<V> p = t.parent;
            Entry<V> ch = t;
            while (p != null && ch == p.left) {
                ch = p;
                p = p.parent;
            }
            return p;
        }
    }

    /*
     * Balancing operations.
     *
     * Implementations of rebalancings during insertion and deletion are
     * slightly different than the CLR version.  Rather than using dummy
     * nilnodes, we use a set of accessors that deal properly with null.  They
     * are used to avoid messiness surrounding nullness checks in the main
     * algorithms.
     */

    private static <V> boolean colorOf(Entry<V> p) {
        return (p == null ? BLACK : p.color);
    }

    private static <V> Entry<V> parentOf(Entry<V> p) {
        return (p == null ? null: p.parent);
    }

    private static <V> void setColor(Entry<V> p, boolean c) {
        if (p != null)
            p.color = c;
    }

    private static <V> Entry<V> leftOf(Entry<V> p) {
        return (p == null) ? null: p.left;
    }

    private static <V> Entry<V> rightOf(Entry<V> p) {
        return (p == null) ? null: p.right;
    }

    /** From CLR */
    private void rotateLeft(Entry<V> p) {
        if (p != null) {
            Entry<V> r = p.right;
            p.right = r.left;
            if (r.left != null)
                r.left.parent = p;
            r.parent = p.parent;
            if (p.parent == null)
                root = r;
            else if (p.parent.left == p)
                p.parent.left = r;
            else
                p.parent.right = r;
            r.left = p;
            p.parent = r;
        }
    }

    /** From CLR */
    private void rotateRight(Entry<V> p) {
        if (p != null) {
            Entry<V> l = p.left;
            p.left = l.right;
            if (l.right != null) l.right.parent = p;
            l.parent = p.parent;
            if (p.parent == null)
                root = l;
            else if (p.parent.right == p)
                p.parent.right = l;
            else p.parent.left = l;
            l.right = p;
            p.parent = l;
        }
    }

    /** From CLR */
    private void fixAfterInsertion(Entry<V> x) {
        x.color = RED;

        while (x != null && x != root && x.parent.color == RED) {
            if (parentOf(x) == leftOf(parentOf(parentOf(x)))) {
                Entry<V> y = rightOf(parentOf(parentOf(x)));
                if (colorOf(y) == RED) {
                    setColor(parentOf(x), BLACK);
                    setColor(y, BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    x = parentOf(parentOf(x));
                } else {
                    if (x == rightOf(parentOf(x))) {
                        x = parentOf(x);
                        rotateLeft(x);
                    }
                    setColor(parentOf(x), BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    rotateRight(parentOf(parentOf(x)));
                }
            } else {
                Entry<V> y = leftOf(parentOf(parentOf(x)));
                if (colorOf(y) == RED) {
                    setColor(parentOf(x), BLACK);
                    setColor(y, BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    x = parentOf(parentOf(x));
                } else {
                    if (x == leftOf(parentOf(x))) {
                        x = parentOf(x);
                        rotateRight(x);
                    }
                    setColor(parentOf(x), BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    rotateLeft(parentOf(parentOf(x)));
                }
            }
        }
        root.color = BLACK;
    }

    /**
     * Delete node p, and then rebalance the tree.
     */
    private void deleteEntry(Entry<V> p) {
        modCount++;
        size--;
        count -= p.value.size();

        // If strictly internal, copy successor's element to p and then make p
        // point to successor.
        if (p.left != null && p.right != null) {
            Entry<V> s = successor(p);
            p.key = s.key;
            p.value = s.value;
            p = s;
        } // p has 2 children

        // Start fixup at replacement node, if it exists.
        Entry<V> replacement = (p.left != null ? p.left : p.right);

        if (replacement != null) {
            // Link replacement to parent
            replacement.parent = p.parent;
            if (p.parent == null)
                root = replacement;
            else if (p == p.parent.left)
                p.parent.left  = replacement;
            else
                p.parent.right = replacement;

            // Null out links so they are OK to use by fixAfterDeletion.
            p.left = p.right = p.parent = null;

            // Fix replacement
            if (p.color == BLACK)
                fixAfterDeletion(replacement);
        } else if (p.parent == null) { // return if we are the only node.
            root = null;
        } else { //  No children. Use self as phantom replacement and unlink.
            if (p.color == BLACK)
                fixAfterDeletion(p);

            if (p.parent != null) {
                if (p == p.parent.left)
                    p.parent.left = null;
                else if (p == p.parent.right)
                    p.parent.right = null;
                p.parent = null;
            }
        }
    }

    /** From CLR */
    private void fixAfterDeletion(Entry<V> x) {
        while (x != root && colorOf(x) == BLACK) {
            if (x == leftOf(parentOf(x))) {
                Entry<V> sib = rightOf(parentOf(x));

                if (colorOf(sib) == RED) {
                    setColor(sib, BLACK);
                    setColor(parentOf(x), RED);
                    rotateLeft(parentOf(x));
                    sib = rightOf(parentOf(x));
                }

                if (colorOf(leftOf(sib))  == BLACK &&
                        colorOf(rightOf(sib)) == BLACK) {
                    setColor(sib, RED);
                    x = parentOf(x);
                } else {
                    if (colorOf(rightOf(sib)) == BLACK) {
                        setColor(leftOf(sib), BLACK);
                        setColor(sib, RED);
                        rotateRight(sib);
                        sib = rightOf(parentOf(x));
                    }
                    setColor(sib, colorOf(parentOf(x)));
                    setColor(parentOf(x), BLACK);
                    setColor(rightOf(sib), BLACK);
                    rotateLeft(parentOf(x));
                    x = root;
                }
            } else { // symmetric
                Entry<V> sib = leftOf(parentOf(x));

                if (colorOf(sib) == RED) {
                    setColor(sib, BLACK);
                    setColor(parentOf(x), RED);
                    rotateRight(parentOf(x));
                    sib = leftOf(parentOf(x));
                }

                if (colorOf(rightOf(sib)) == BLACK &&
                        colorOf(leftOf(sib)) == BLACK) {
                    setColor(sib, RED);
                    x = parentOf(x);
                } else {
                    if (colorOf(leftOf(sib)) == BLACK) {
                        setColor(rightOf(sib), BLACK);
                        setColor(sib, RED);
                        rotateLeft(sib);
                        sib = leftOf(parentOf(x));
                    }
                    setColor(sib, colorOf(parentOf(x)));
                    setColor(parentOf(x), BLACK);
                    setColor(leftOf(sib), BLACK);
                    rotateRight(parentOf(x));
                    x = root;
                }
            }
        }

        setColor(x, BLACK);
    }

    private static final long serialVersionUID = 919286545866124006L;

    /**
     * Save the state of the {@code TreeMap} instance to a stream (i.e.,
     * serialize it).
     *
     * @serialData The <em>size</em> of the TreeMap (the number of key-value
     *             mappings) is emitted (int), followed by the key (Object)
     *             and value (Object) for each key-value mapping represented
     *             by the TreeMap. The key-value mappings are emitted in
     *             key-order (as determined by the TreeMap's Comparator,
     *             or by the keys' natural ordering if the TreeMap has no
     *             Comparator).
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {
        // Write out the Comparator and any hidden stuff
        s.defaultWriteObject();

        // Write out size (number of Mappings)
        s.writeInt(size);

        // Write out keys and values (alternating)
        for (Map.Entry<Integer, ArrayList<V>> e : entrySet()) {
            s.writeObject(e.getKey());
            s.writeObject(e.getValue());
        }
    }

    /**
     * Reconstitute the {@code TreeMap} instance from a stream (i.e.,
     * deserialize it).
     */
    private void readObject(final java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        // Read in the Comparator and any hidden stuff
        s.defaultReadObject();

        // Read in size
        int size = s.readInt();

        buildFromSorted(size, null, s, null);
    }

    /** Intended to be called only from TreeSet.readObject */
    void readTreeSet(int size, java.io.ObjectInputStream s, V defaultVal)
            throws java.io.IOException, ClassNotFoundException {
        buildFromSorted(size, null, s, defaultVal);
    }

    /** Intended to be called only from TreeSet.addAll */
    void addAllForTreeSet(SortedSet<? extends Integer> set, V defaultVal) {
        try {
            buildFromSorted(set.size(), set.iterator(), null, defaultVal);
        } catch (IOException | ClassNotFoundException cannotHappen) {
        }
    }


    /**
     * Linear time tree building algorithm from sorted data.  Can accept keys
     * and/or values from iterator or stream. This leads to too many
     * parameters, but seems better than alternatives.  The four formats
     * that this method accepts are:
     *
     *    1) An iterator of Map.Entries.  (it != null, defaultVal == null).
     *    2) An iterator of keys.         (it != null, defaultVal != null).
     *    3) A stream of alternating serialized keys and values.
     *                                   (it == null, defaultVal == null).
     *    4) A stream of serialized keys. (it == null, defaultVal != null).
     *
     * It is assumed that the comparator of the TreeMap is already set prior
     * to calling this method.
     *
     * @param size the number of keys (or key-value pairs) to be read from
     *        the iterator or stream
     * @param it If non-null, new entries are created from entries
     *        or keys read from this iterator.
     * @param str If non-null, new entries are created from keys and
     *        possibly values read from this stream in serialized form.
     *        Exactly one of it and str should be non-null.
     * @param defaultVal if non-null, this default value is used for
     *        each value in the map.  If null, each value is read from
     *        iterator or stream, as described above.
     * @throws java.io.IOException propagated from stream reads. This cannot
     *         occur if str is null.
     * @throws ClassNotFoundException propagated from readObject.
     *         This cannot occur if str is null.
     */
    private void buildFromSorted(int size, Iterator<?> it,
                                 java.io.ObjectInputStream str,
                                 V defaultVal)
            throws  java.io.IOException, ClassNotFoundException {
        this.size = size;
        root = buildFromSorted(0, 0, size-1, computeRedLevel(size),
                it, str, defaultVal);
    }

    /**
     * Recursive "helper method" that does the real work of the
     * previous method.  Identically named parameters have
     * identical definitions.  Additional parameters are documented below.
     * It is assumed that the comparator and size fields of the TreeMap are
     * already set prior to calling this method.  (It ignores both fields.)
     *
     * @param level the current level of tree. Initial call should be 0.
     * @param lo the first element index of this subtree. Initial should be 0.
     * @param hi the last element index of this subtree.  Initial should be
     *        size-1.
     * @param redLevel the level at which nodes should be red.
     *        Must be equal to computeRedLevel for tree of this size.
     */
    @SuppressWarnings("unchecked")
    private Entry<V> buildFromSorted(int level, int lo, int hi,
                                                               int redLevel,
                                                               Iterator<?> it,
                                                               java.io.ObjectInputStream str,
                                                               V defaultVal)
            throws  java.io.IOException, ClassNotFoundException {
        /*
         * Strategy: The root is the middlemost element. To get to it, we
         * have to first recursively construct the entire left subtree,
         * so as to grab all of its elements. We can then proceed with right
         * subtree.
         *
         * The lo and hi arguments are the minimum and maximum
         * indices to pull out of the iterator or stream for current subtree.
         * They are not actually indexed, we just proceed sequentially,
         * ensuring that items are extracted in corresponding order.
         */

        if (hi < lo) return null;

        int mid = (lo + hi) >>> 1;

        Entry<V> left  = null;
        if (lo < mid)
            left = buildFromSorted(level+1, lo, mid - 1, redLevel,
                    it, str, defaultVal);

        // extract key and/or value from iterator or stream
        Integer key;
        V value;
        if (it != null) {
            if (defaultVal==null) {
                Map.Entry<?,?> entry = (Map.Entry<?,?>)it.next();
                key = (Integer)entry.getKey();
                value = (V)entry.getValue();
            } else {
                key = (Integer)it.next();
                value = defaultVal;
            }
        } else { // use stream
            key = (Integer) str.readObject();
            value = (defaultVal != null ? defaultVal : (V) str.readObject());
        }

        Entry<V> middle = new Entry<>(key, value, null);
        count += middle.value.size();

        // color nodes in non-full bottommost level red
        if (level == redLevel)
            middle.color = RED;

        if (left != null) {
            middle.left = left;
            left.parent = middle;
        }

        if (mid < hi) {
            Entry<V> right = buildFromSorted(level+1, mid+1, hi, redLevel,
                    it, str, defaultVal);
            middle.right = right;
            if (right != null)
                right.parent = middle;
        }

        return middle;
    }

    /**
     * Find the level down to which to assign all nodes BLACK.  This is the
     * last `full' level of the complete binary tree produced by
     * buildTree. The remaining nodes are colored RED. (This makes a `nice'
     * set of color assignments wrt future insertions.) This level number is
     * computed by finding the number of splits needed to reach the zeroeth
     * node.  (The answer is ~lg(N), but in any case must be computed by same
     * quick O(lg(N)) loop.)
     */
    private static int computeRedLevel(int sz) {
        int level = 0;
        for (int m = sz - 1; m >= 0; m = m / 2 - 1)
            level++;
        return level;
    }

    /**
     * Currently, we support Spliterator-based versions only for the
     * full map, in either plain of descending form, otherwise relying
     * on defaults because size estimation for submaps would dominate
     * costs. The type tests needed to check these for key views are
     * not very nice but avoid disrupting existing class
     * structures. Callers must use plain default spliterators if this
     * returns null.
     */
    static Spliterator<Integer> keySpliteratorFor(NavigableMap<Integer, ?> m) {
        if (m instanceof PositionalObjectTreeMap) {
            @SuppressWarnings("unchecked") PositionalObjectTreeMap<PositionalObject> t =
                    (PositionalObjectTreeMap<PositionalObject>) m;
            return t.keySpliterator();
        }
        if (m instanceof DescendingSubMap) {
            @SuppressWarnings("unchecked") DescendingSubMap<PositionalObject> dm =
                    (DescendingSubMap<PositionalObject>) m;
            PositionalObjectTreeMap<PositionalObject> tm = dm.m;
            if (dm == tm.descendingMap) {
                return tm.descendingKeySpliterator();
            }
        }
        return ((NavigableSubMap<?>) m).keySpliterator();
    }

    final Spliterator<Integer> keySpliterator() {
        return new KeySpliterator<>(this, null, null, 0, -1, 0);
    }

    final Spliterator<Integer> descendingKeySpliterator() {
        return new DescendingKeySpliterator<>(this, null, null, 0, -2, 0);
    }

    /**
     * Base class for spliterators.  Iteration starts at a given
     * origin and continues up to but not including a given fence (or
     * null for end).  At top-level, for ascending cases, the first
     * split uses the root as left-fence/right-origin. From there,
     * right-hand splits replace the current fence with its left
     * child, also serving as origin for the split-off spliterator.
     * Left-hands are symmetric. Descending versions place the origin
     * at the end and invert ascending split rules.  This base class
     * is non-commital about directionality, or whether the top-level
     * spliterator covers the whole tree. This means that the actual
     * split mechanics are located in subclasses. Some of the subclass
     * trySplit methods are identical (except for return types), but
     * not nicely factorable.
     *
     * Currently, subclass versions exist only for the full map
     * (including descending keys via its descendingMap).  Others are
     * possible but currently not worthwhile because submaps require
     * O(n) computations to determine size, which substantially limits
     * potential speed-ups of using custom Spliterators versus default
     * mechanics.
     *
     * To boostrap initialization, external constructors use
     * negative size estimates: -1 for ascend, -2 for descend.
     */
    static class TreeMapSpliterator<V extends PositionalObject> {
        final PositionalObjectTreeMap<V> tree;
        Entry<V> current; // traverser; initially first node in range
        Entry<V> fence;   // one past last, or null
        int side;                   // 0: top, -1: is a left split, +1: right
        int est;                    // size estimate (exact only for top-level)
        int expectedModCount;       // for CME checks

        TreeMapSpliterator(PositionalObjectTreeMap<V> tree,
                           Entry<V> origin, Entry<V> fence,
                           int side, int est, int expectedModCount) {
            this.tree = tree;
            this.current = origin;
            this.fence = fence;
            this.side = side;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        final int getEstimate() { // force initialization
            int s; PositionalObjectTreeMap<V> t;
            if ((s = est) < 0) {
                if ((t = tree) != null) {
                    current = (s == -1) ? t.getFirstEntry() : t.getLastEntry();
                    s = est = t.size;
                    expectedModCount = t.modCount;
                }
                else
                    s = est = 0;
            }
            return s;
        }

        public final long estimateSize() {
            return getEstimate();
        }
    }

    static final class KeySpliterator<V extends PositionalObject>
            extends TreeMapSpliterator<V>
            implements Spliterator<Integer> {
        KeySpliterator(PositionalObjectTreeMap<V> tree,
                       Entry<V> origin, Entry<V> fence,
                       int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public KeySpliterator<V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            Entry<V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0)              ? tree.root : // was top
                                    (d >  0)              ? e.right :   // was right
                                            (f != null) ? f.left :    // was left
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) < 0) {        // e not already past s
                side = 1;
                return new KeySpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super Integer> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            Entry<V> f = fence, e, p, pl;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e.key);
                    if ((p = e.right) != null) {
                        while ((pl = p.left) != null)
                            p = pl;
                    }
                    else {
                        while ((p = e.parent) != null && e == p.right)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super Integer> action) {
            Entry<V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = successor(e);
            action.accept(e.key);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT | Spliterator.SORTED | Spliterator.ORDERED;
        }

        public final Comparator<? super Integer>  getComparator() {
            return tree.comparator;
        }

    }

    static final class DescendingKeySpliterator<V extends PositionalObject>
            extends TreeMapSpliterator<V>
            implements Spliterator<Integer> {
        DescendingKeySpliterator(PositionalObjectTreeMap<V> tree,
                                 Entry<V> origin, Entry<V> fence,
                                 int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public DescendingKeySpliterator<V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            Entry<V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0)              ? tree.root : // was top
                                    (d <  0)              ? e.left :    // was left
                                            (f != null) ? f.right :   // was right
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) > 0) {       // e not already past s
                side = 1;
                return new DescendingKeySpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super Integer> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            Entry<V> f = fence, e, p, pr;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e.key);
                    if ((p = e.left) != null) {
                        while ((pr = p.right) != null)
                            p = pr;
                    }
                    else {
                        while ((p = e.parent) != null && e == p.left)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super Integer> action) {
            Entry<V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = predecessor(e);
            action.accept(e.key);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT | Spliterator.ORDERED;
        }
    }

    static final class ValueSpliterator<V extends PositionalObject>
            extends TreeMapSpliterator<V>
            implements Spliterator<ArrayList<V>> {
        ValueSpliterator(PositionalObjectTreeMap<V> tree,
                         Entry<V> origin, Entry<V> fence,
                         int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public ValueSpliterator<V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            Entry<V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0)              ? tree.root : // was top
                                    (d >  0)              ? e.right :   // was right
                                            (f != null) ? f.left :    // was left
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) < 0) {        // e not already past s
                side = 1;
                return new ValueSpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super ArrayList<V>> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            Entry<V> f = fence, e, p, pl;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e.value);
                    if ((p = e.right) != null) {
                        while ((pl = p.left) != null)
                            p = pl;
                    }
                    else {
                        while ((p = e.parent) != null && e == p.right)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super ArrayList<V>> action) {
            Entry<V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = successor(e);
            action.accept(e.value);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) | Spliterator.ORDERED;
        }
    }

    static final class EntrySpliterator<V extends PositionalObject>
            extends TreeMapSpliterator<V>
            implements Spliterator<Map.Entry<Integer, ArrayList<V>>> {
        EntrySpliterator(PositionalObjectTreeMap<V> tree,
                         Entry<V> origin, Entry<V> fence,
                         int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public EntrySpliterator<V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            Entry<V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0)              ? tree.root : // was top
                                    (d >  0)              ? e.right :   // was right
                                            (f != null) ? f.left :    // was left
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) < 0) {        // e not already past s
                side = 1;
                return new EntrySpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super Map.Entry<Integer, ArrayList<V>>> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            Entry<V> f = fence, e, p, pl;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e);
                    if ((p = e.right) != null) {
                        while ((pl = p.left) != null)
                            p = pl;
                    }
                    else {
                        while ((p = e.parent) != null && e == p.right)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super Map.Entry<Integer, ArrayList<V>>> action) {
            Entry<V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = successor(e);
            action.accept(e);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT | Spliterator.SORTED | Spliterator.ORDERED;
        }

        @Override
        public Comparator<Map.Entry<Integer, ArrayList<V>>> getComparator() {
            // Adapt or create a key-based comparator
            if (tree.comparator != null) {
                return Map.Entry.comparingByKey(tree.comparator);
            }
            else {
                return (Comparator<Map.Entry<Integer, ArrayList<V>>> & Serializable) (e1, e2) -> {
                    Comparable<? super Integer> k1 = (Comparable<? super Integer>) e1.getKey();
                    return k1.compareTo(e2.getKey());
                };
            }
        }
    }
}