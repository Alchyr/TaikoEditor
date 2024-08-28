package alchyr.taikoedit.util.structures;

import java.util.*;

public class CharacterTreeMap<T> {
    private final CharacterTreeNode head;
    private final Map<Character, ArrayList<CharacterTreeNode>> starting;

    public CharacterTreeMap() {
        starting = new HashMap<>();
        head = new CharacterTreeNode(false);
    }

    public void clear() {
        starting.clear();
        head.clear();
    }

    public List<T> search(String[] keys, float leniency) {
        //Results are sorted by total score
        Map<T, Float> results = new HashMap<>();
        Set<T> notFound = new HashSet<>();

        float max = 0, valueMul;
        for (String key : keys) {
            if (key.isEmpty())
                continue;

            notFound.clear();
            notFound.addAll(results.keySet());
            ArrayList<CharacterTreeNode> startPoints = starting.get(key.charAt(0));
            valueMul = Math.min(1, (key.length() / 5.0f)*(key.length() / 5.0f)); //Search term weight increases as length increases

            if (startPoints != null) {
                for (CharacterTreeNode start : startPoints) {
                    for (WeightedEntry result : start.find(key)) {
                        if (start.isTop && result.exactMatch) {
                            max = Math.max(max, results.merge(result.entry, result.weight * 2.5f, Float::sum));
                        }
                        else {
                            max = Math.max(max, results.merge(result.entry, result.weight * valueMul, Float::sum));
                        }
                        notFound.remove(result.entry);
                    }
                }
            }

            for (T entry : notFound) {
                //Each search term with no match cuts a result's value
                results.merge(entry, -2f * valueMul, Float::sum);
            }
        }

        float requirement = max * leniency;
        return results.entrySet().stream()
                .filter((a)->a.getValue() > requirement)
                .sorted((a, b)->Float.compare(b.getValue(), a.getValue()))
                .collect(ArrayList::new, (l, v)->l.add(v.getKey()), ArrayList::addAll);
    }

    public void put(String[] keys, T value, float weight) {
        for (String key : keys) {
            key = key.trim();
            if (!key.isEmpty())
                head.put(key, new WeightedEntry(value, weight));
        }
    }
    public void put(String key, T value, float weight) {
        key = key.trim();
        if (!key.isEmpty())
            head.put(key, new WeightedEntry(value, weight));
    }

    private class CharacterTreeNode {
        final boolean isTop;
        final HashMap<Character, CharacterTreeNode> children;
        final List<WeightedEntry> values;

        CharacterTreeNode(boolean isTop)
        {
            this.isTop = isTop;
            children = new HashMap<>();
            values = new ArrayList<>();
        }

        /*List<T> get(String key)
        {
            if (key.isEmpty())
            {
                return new ArrayList<>(values);
            }

            if (children.containsKey(key.charAt(0)))
                return children.get(key.charAt(0)).get(key.substring(1));

            return new ArrayList<>();
        }*/

        List<WeightedEntry> find(String key)
        {
            return find(key, 1);
        }
        List<WeightedEntry> find(String key, int index) {
            if (index >= key.length()) {
                //This is the last letter of the term, return all values (which includes all children's values)
                return getAllValues();
            }

            //Still going, move to child
            CharacterTreeNode child = children.get(key.charAt(index));
            if (child != null) {
                return child.find(key, index + 1);
            }
            //No match
            return Collections.emptyList();
        }

        List<WeightedEntry> getAllValues()
        {
            List<WeightedEntry> results = new ArrayList<>();
            for (WeightedEntry entry : values) {
                entry.exactMatch = true;
                results.add(entry);
            }

            for (CharacterTreeNode t : children.values())
            {
                t.getAllValues(results);
            }

            return results;
        }
        void getAllValues(List<WeightedEntry> results)
        {
            for (WeightedEntry entry : values) {
                entry.exactMatch = false;
                results.add(entry);
            }

            for (CharacterTreeNode t : children.values())
            {
                t.getAllValues(results);
            }
        }

        void clear() {
            values.clear();
            children.clear();
        }

        void put(String key, WeightedEntry value)
        {
            if (key.length() > 64) { //heck off
                key = key.substring(0, 64);
            }
            put(key, value, 0);
        }

        void put(String key, WeightedEntry value, int index) {
            if (index < key.length())
            {
                char c = key.charAt(index);
                CharacterTreeNode n = children.get(c);
                if (n == null)
                {
                    n = new CharacterTreeNode(head == this);
                    starting.computeIfAbsent(c, k -> new ArrayList<>()).add(n);
                    children.put(c, n);
                }
                n.put(key, value, index + 1);
            }
            else
            {
                values.add(value);
            }
        }
    }

    private class WeightedEntry implements Comparable<WeightedEntry> {
        final T entry;
        final float weight;
        boolean exactMatch = false;

        WeightedEntry(T entry, float weight) {
            this.entry = entry;
            this.weight = weight;
        }

        @Override
        public int compareTo(WeightedEntry o) {
            return Float.compare(o.weight, weight);
        }
    }
}
