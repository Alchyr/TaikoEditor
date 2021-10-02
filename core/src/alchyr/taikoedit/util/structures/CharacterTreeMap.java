package alchyr.taikoedit.util.structures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CharacterTreeMap<T> {
    private final HashMap<Character, CharacterTreeMap<T>> base;
    private final List<T> values;

    public CharacterTreeMap()
    {
        base = new HashMap<>();
        values = new ArrayList<>();
    }

    public List<T> get(String key)
    {
        if (key.isEmpty())
        {
            return new ArrayList<>(values);
        }

        if (base.containsKey(key.charAt(0)))
            return base.get(key.charAt(0)).get(key.substring(1));

        return new ArrayList<>();
    }

    public List<T> find(String key)
    {
        if (key.isEmpty())
        {
            return getAllValues();
        }

        if (base.containsKey(key.charAt(0)))
            return base.get(key.charAt(0)).find(key.substring(1));

        return new ArrayList<>();
    }

    public List<T> getAllValues()
    {
        ArrayList<T> result = new ArrayList<>(values);

        for (CharacterTreeMap<T> t : base.values())
        {
            result.addAll(t.getAllValues());
        }

        return result;
    }

    public void put(String[] keys, T value)
    {
        for (String key : keys)
        {
            put(key, value);
        }
    }

    public void put(String key, T value)
    {
        if (key.length() > 64) { //heck off
            key = key.substring(0, 64);
        }
        if (key.isEmpty())
        {
            values.add(value);
        }
        else
        {
            if (!base.containsKey(key.charAt(0)))
            {
                base.put(key.charAt(0), new CharacterTreeMap<>());
            }
            base.get(key.charAt(0)).put(key.substring(1), value);
        }
    }
}
