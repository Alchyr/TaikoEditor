package alchyr.taikoedit.util.structures;

import java.util.*;

public class CharacterTreeMap<T> {
    private final CharacterTreeNode head;
    private final Map<Character, ArrayList<CharacterTreeNode>> starting;

    public CharacterTreeMap() {
        starting = new HashMap<>();
        head = new CharacterTreeNode();
    }

    public List<T> search(String[] keys, float leniency) {
        //Results are sorted by total score
        Map<T, Float> results = new HashMap<>();
        float max = 0, valueMul;
        for (String key : keys) {
            if (key.isEmpty())
                continue;
            ArrayList<CharacterTreeNode> startPoints = starting.get(key.charAt(0));
            if (startPoints != null) {
                valueMul = Math.min(1, (key.length() / 5.0f)*(key.length() / 5.0f)); //Search term weight increases as length increases
                for (CharacterTreeNode start : startPoints) {
                    for (WeightedEntry result : start.find(key, 1)) {
                        max = Math.max(max, results.merge(result.value, result.weight * valueMul, Float::sum));
                    }
                }
            }
            /*for (WeightedEntry result : head.find(key)) {
                results.merge(result.value, result.weight, Float::sum);
            }*/
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
        final HashMap<Character, CharacterTreeNode> children;
        final List<WeightedEntry> values;

        CharacterTreeNode()
        {
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
            return find(key, 0);
        }
        List<WeightedEntry> find(String key, int index) {
            if (index >= key.length()) {
                return getAllValues();
            }

            CharacterTreeNode child = children.get(key.charAt(index));
            if (child != null) {
                return child.find(key, index + 1);
            }
            return Collections.emptyList();
        }

        List<WeightedEntry> getAllValues()
        {
            List<WeightedEntry> results = new ArrayList<>(values);

            for (CharacterTreeNode t : children.values())
            {
                t.getAllValues(results);
            }

            return results;
        }
        void getAllValues(List<WeightedEntry> results)
        {
            results.addAll(values);

            for (CharacterTreeNode t : children.values())
            {
                t.getAllValues(results);
            }
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
                    n = new CharacterTreeNode();
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
        final T value;
        final float weight;

        WeightedEntry(T value, float weight) {
            this.value = value;
            this.weight = weight;
        }

        @Override
        public int compareTo(WeightedEntry o) {
            return Float.compare(o.weight, weight);
        }
    }
}
