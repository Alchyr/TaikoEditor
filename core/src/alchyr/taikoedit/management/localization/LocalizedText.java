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

    public void put(String key, String[] text)
    {
        this.text.put(key, text);
    }
}
