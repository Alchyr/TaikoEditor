package alchyr.taikoedit.util.input;

import java.util.HashMap;

public class KeyHoldManager {
    private HashMap<Integer, KeyHoldObject> holds;

    public KeyHoldManager()
    {
        holds = new HashMap<>();
    }

    public void update(float elapsed)
    {
        for (KeyHoldObject hold : holds.values())
        {
            hold.update(elapsed);
        }
    }

    public void clear()
    {
        holds.clear();
    }

    public void add(KeyHoldObject hold)
    {
        KeyHoldObject replaced = holds.put(hold.keycode, hold);
        //Should be impossible, but good to be careful.
        if (replaced != null)
            replaced.onRelease();

        for (int keycode : hold.conflictingKeys)
        {
            release(keycode);
        }
    }

    public boolean release(int keycode)
    {
        KeyHoldObject released = holds.remove(keycode);

        if (released != null)
        {
            released.reset();
            return released.onRelease();
        }

        return false;
    }

    public boolean isHeld(int keycode)
    {
        return holds.containsKey(keycode);
    }
}
