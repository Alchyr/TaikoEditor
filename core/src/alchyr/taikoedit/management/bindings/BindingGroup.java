package alchyr.taikoedit.management.bindings;

import alchyr.taikoedit.util.VoidMethod;
import alchyr.taikoedit.util.input.KeyHoldObject;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class BindingGroup {
    private final String ID;
    public String getID() {
        return ID;
    }

    //how to link bindinggroup to actual game layer?



    //Outer key: Modifier key state (ctrl = 1, shift = 2, alt = 4)
    //Inner key: keycode
    private final HashMap<Integer, HashMap<Integer, InputBinding>> keyInputs = new HashMap<>();
    private final HashMap<String, InputBinding> allBindings = new HashMap<>();
    private final HashMap<Integer, InputBinding> heldKeyInputs = new HashMap<>();


    public BindingGroup(String ID)
    {
        this.ID = ID;
    }

    public void addBinding(InputBinding binding)
    {
        allBindings.put(binding.getInputID(), binding);
    }

    public void createInputMap()
    {
        keyInputs.clear();
        for (InputBinding binding : allBindings.values())
        {
            for (InputBinding.InputInfo i : binding.getInputs())
            {
                int modifiers = i.getModifiers();
                if (!keyInputs.containsKey(modifiers))
                    keyInputs.put(modifiers, new HashMap<>());

                keyInputs.get(modifiers).put(i.getCode(), binding);
            }
        }
    }

    public BindingGroup resetBindings()
    {
        for (InputBinding binding : allBindings.values())
        {
            binding.clearBinding();
        }
        return this;
    }

    public void bind(String bindingKey, BooleanSupplier onDown, KeyHoldObject hold)
    {
        InputBinding binding = allBindings.get(bindingKey);

        if (binding != null)
            binding.bind(onDown, hold);
    }
    public void bind(String bindingKey, VoidMethod onDown, KeyHoldObject hold)
    {
        InputBinding binding = allBindings.get(bindingKey);

        if (binding != null)
            binding.bind(()->{
                onDown.run();
                return true;
            }, hold);
    }
    public void bind(String bindingKey, BooleanSupplier onDown)
    {
        if (allBindings.containsKey(bindingKey))
            allBindings.get(bindingKey).bind(onDown);
    }
    public void bind(String bindingKey, VoidMethod onDown)
    {
        if (allBindings.containsKey(bindingKey))
            allBindings.get(bindingKey).bind(()->{
                onDown.run();
                return true;
            });
    }

    public ArrayList<InputBinding.InputInfo> bindingInputs(String bindingKey)
    {
        if (allBindings.containsKey(bindingKey))
            return allBindings.get(bindingKey).getInputs();

        return null;
    }

    public void clearInput() {
        heldKeyInputs.clear();
    }

    public void update(float elapsed)
    {
        //Check currently held keys. If they are not held, release them.
        //This is due to keyUp events possibly being missed if something else consumes them, another layer is created, focus is lost, etc.
        //Many possibilities.

        Iterator<Map.Entry<Integer, InputBinding>> inputIterator = heldKeyInputs.entrySet().iterator();
        while (inputIterator.hasNext())
        {
            Map.Entry<Integer, InputBinding> next = inputIterator.next();

            if (Gdx.input.isKeyPressed(next.getKey()))
            {
                if (next.getValue().hasHold())
                {
                    next.getValue().getHold().update(elapsed);
                }
            }
            else
            {
                if (next.getValue().hasRelease())
                {
                    next.getValue().onRelease();
                }
                inputIterator.remove();
            }
        }
    }

    public boolean receiveKeyDown(int keycode)
    {
        HashMap<Integer, InputBinding> keyBindings = keyInputs.get(modifierState());

        if (keyBindings != null)
        {
            InputBinding binding = keyBindings.get(keycode);

            if (binding != null)
            {
                boolean result = binding.onDown();

                if (result)
                {
                    if (binding.hasHold())
                    {
                        binding.getHold().reset();

                        for (int conflict : binding.getHold().conflictingKeys) //remove conflicting held keys
                        {
                            if (heldKeyInputs.containsKey(conflict))
                            {
                                InputBinding b = heldKeyInputs.get(conflict);
                                if (b.hasRelease())
                                {
                                    b.onRelease();
                                }
                                heldKeyInputs.remove(conflict);
                            }
                        }
                    }

                    heldKeyInputs.put(keycode, binding);
                }

                return result;
            }
        }

        return false;
    }
    public boolean receiveKeyUp(int keycode)
    {
        if (heldKeyInputs.containsKey(keycode))
        {
            InputBinding binding = heldKeyInputs.remove(keycode);

            if (binding.hasRelease())
            {
                return binding.onRelease();
            }
        }
        return false;
    }

    public static int modifierState()
    {
        return (ctrl() ? 1 : 0) |
                (shift() ? 2 : 0) |
                (alt() ? 4 : 0);
    }

    public static boolean ctrl()
    {
        return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT);
    }
    public static boolean shift()
    {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }
    public static boolean alt()
    {
        return Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
    }
}
