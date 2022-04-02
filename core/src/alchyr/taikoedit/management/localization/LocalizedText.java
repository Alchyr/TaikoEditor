package alchyr.taikoedit.management.localization;

import java.util.HashMap;

public class LocalizedText {
    public String key;
    private HashMap<String, String[]> text;

    public LocalizedText(String key)
    {
        this.key = key;
        this.text = new HashMap<>();
    }

    public void setData(LocalizedText copy)
    {
        this.key = copy.key;
        this.text = copy.text;
    }

    public String[] get(String key)
    {
        return text.get(key);
    }
    public String get(String key, int index) {
        return text.get(key)[index];
    }
    public String getOrDefault(String key, int index) {
        return getOrDefault(key, index, key);
    }
    public String getOrDefault(String key, int index, String defaultValue) {
        String[] t = text.get(key);
        if (t == null || index >= t.length)
            return defaultValue;
        return t[index];
    }

    public void put(String key, String[] text)
    {
        this.text.put(key, text);
    }

}
