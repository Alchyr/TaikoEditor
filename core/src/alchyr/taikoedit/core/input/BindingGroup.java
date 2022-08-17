package alchyr.taikoedit.core.input;

import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.interfaces.functional.TriFunction;
import alchyr.taikoedit.util.interfaces.functional.VoidMethod;
import alchyr.taikoedit.util.structures.Pair;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BindingGroup {
    private final String ID;
    private final boolean modifiable;
    public String getID() {
        return ID;
    }
    public boolean isModifiable() {
        return modifiable;
    }

    private final Map<String, InputBinding> allBindings = new HashMap<>();
    private final List<InputBinding> orderedBindings = new ArrayList<>();

    //Outer key: Modifier key state (ctrl = 1, shift = 2, alt = 4)
    //Inner key: keycode
    private final Map<Integer, HashMap<Integer, InputBinding>> keyInputs = new HashMap<>(); //used to quickly translate key input to the correct bindings
    private final Map<Integer, InputBinding> heldKeyInputs = new HashMap<>(); //currently held keys : binding linked to key
    private final Map<InputBinding, Integer> activeBindings = new HashMap<>(); //currently active bindings : number of active inputs
    private final Map<InputBinding, Pair<Integer, List<Integer>>> blockedBindings = new HashMap<>(); //bindings that are currently disabled by conflicts : blocking bindings,held key inputs

    private final Map<Integer, MouseHoldObject> mouseHolds = new ConcurrentHashMap<>(); //button : current active hold
    private final List<MouseInputInfo> mouseInputs = new ArrayList<>();

    private final Queue<VoidMethod> actionQueue = new ConcurrentLinkedQueue<>();

    private final InputBinding anyInput = InputBinding.create("any");

    private final List<BindingGroup> copies = new ArrayList<>();

    private final ReentrantLock inputLock = new ReentrantLock();

    public BindingGroup(String ID, boolean modifiable)
    {
        this.ID = ID;
        this.modifiable = modifiable;
        allBindings.put("any", anyInput);
    }

    public void addBinding(InputBinding binding)
    {
        allBindings.put(binding.getInputID(), binding);
        orderedBindings.add(binding);
    }
    public InputBinding getBinding(String key) {
        return allBindings.get(key);
    }
    public String getBindingInputString(String key, String[] keyNameArray) {
        InputBinding binding = allBindings.get(key);
        if (binding != null) {
            if (binding.getInputs().isEmpty()) {
                return null;
            }
            return binding.getInputs().get(0).toString(keyNameArray);
        }
        return null;
    }

    public void initialize(Map<String, BindingGroup> bindingMap, Map<String, List<InputBinding.InputInfo>> bindingData) {
        //Load data
        if (bindingData != null)
            loadBindings(bindingData);

        //Prep inputs
        updateInputMap();

        //Register
        bindingMap.put(getID(), this);
    }
    private void loadBindings(Map<String, List<InputBinding.InputInfo>> bindingData) {
        for (InputBinding binding : orderedBindings) {
            List<InputBinding.InputInfo> data = bindingData.get(binding.getInputID());
            if (data != null) {
                binding.setInputs(data);
            }
        }
    }
    public void updateInputMap()
    {
        keyInputs.clear();
        for (InputBinding binding : allBindings.values())
        {
            for (InputBinding.InputInfo i : binding.getInputs())
            {
                int[] modifiers = i.getModifiers();
                for (int modifierKey : modifiers) {
                    if (!keyInputs.containsKey(modifierKey))
                        keyInputs.put(modifierKey, new HashMap<>());

                    keyInputs.get(modifierKey).put(i.getCode(), binding);
                }
            }
        }

        for (BindingGroup copy : copies) {
            copy.updateInputMap();
        }
    }

    public Map<String, InputBinding> getBindings() {
        return allBindings;
    }
    public List<InputBinding> getOrderedBindings() {
        return orderedBindings;
    }

    //Previously used to reset whenever something requests this group
    public BindingGroup resetBindings()
    {
        heldKeyInputs.clear();
        activeBindings.clear();
        blockedBindings.clear();
        mouseHolds.clear();
        for (InputBinding binding : allBindings.values())
        {
            binding.clearBinding();
        }
        mouseInputs.clear();
        actionQueue.clear();
        return this;
    }
    public BindingGroup copy()
    {
        BindingGroup copy = new BindingGroup(ID, this.modifiable);

        for (InputBinding binding : allBindings.values()) {
            copy.addBinding(binding.copy());
        }

        copy.updateInputMap();
        copies.add(copy);
        return copy;
    }
    public void releaseCopy(BindingGroup copy) {
        copies.remove(copy);
    }

    public void bind(String bindingKey, Supplier<VoidMethod> onDown, KeyHoldObject hold)
    {
        InputBinding binding = allBindings.get(bindingKey);

        if (binding != null)
            binding.bind(onDown, hold);
    }
    public void bind(String bindingKey, VoidMethod onDown, KeyHoldObject hold)
    {
        InputBinding binding = allBindings.get(bindingKey);

        if (binding != null)
            binding.bind(()->onDown, hold);
    }
    public void bind(String bindingKey, Supplier<VoidMethod> onDown)
    {
        if (allBindings.containsKey(bindingKey))
            allBindings.get(bindingKey).bind(onDown);
    }
    public void bind(String bindingKey, VoidMethod onDown)
    {
        if (allBindings.containsKey(bindingKey))
            allBindings.get(bindingKey).bind(()->onDown);
    }

    //Parameters of the function are: x, y, button (0 = left click, 1 = right click), return value of boolean for whether or not this click is valid
    public void addMouseBind(TriFunction<Integer, Integer, Integer, Boolean> isValidClick, BiFunction<Vector2, Integer, MouseHoldObject> onPress) {
        mouseInputs.add(new MouseInputInfo(isValidClick, onPress));
    }
    public void addMouseBind(BiFunction<Integer, Integer, Boolean> isValidClick, BiFunction<Vector2, Integer, MouseHoldObject> onPress) {
        mouseInputs.add(new MouseInputInfo((x, y, b)->isValidClick.apply(x, y), onPress));
    }
    public void addMouseBind(TriFunction<Integer, Integer, Integer, Boolean> isValidClick, VoidMethod onPress) {
        mouseInputs.add(new MouseInputInfo(isValidClick, (pos, button)->{onPress.run(); return null;}));
    }
    public void addMouseBind(BiFunction<Integer, Integer, Boolean> isValidClick, VoidMethod onPress) {
        mouseInputs.add(new MouseInputInfo((x, y, b)->isValidClick.apply(x, y), (pos, button)->{onPress.run(); return null;}));
    }
    public void addMouseBind(TriFunction<Integer, Integer, Integer, Boolean> isValidClick, Consumer<Integer> onPress) {
        mouseInputs.add(new MouseInputInfo(isValidClick, (pos, button)->{onPress.accept(button); return null;}));
    }
    public void addMouseBind(BiFunction<Integer, Integer, Boolean> isValidClick, Consumer<Integer> onPress) {
        mouseInputs.add(new MouseInputInfo((x, y, b)->isValidClick.apply(x, y), (pos, button)->{onPress.accept(button); return null;}));
    }

    public ArrayList<InputBinding.InputInfo> bindingInputs(String bindingKey)
    {
        if (allBindings.containsKey(bindingKey))
            return allBindings.get(bindingKey).getInputs();

        return null;
    }

    public void releaseInput(boolean safe) {
        releaseKeys.clear();
        for (Map.Entry<Integer, InputBinding> bindingEntry : heldKeyInputs.entrySet()) {
            releaseKeys.add(bindingEntry.getKey());
        }
        for (int key : releaseKeys) {
            releaseKey(key, safe ? null : actionQueue);
        }
        heldKeyInputs.clear();
        activeBindings.clear();
        blockedBindings.clear();

        releaseMouse(safe);
    }
    public void releaseMouse(boolean safe) {
        if (safe) {
            for (MouseHoldObject o : mouseHolds.values()) {
                o.onRelease(Gdx.input.getX(), SettingsMaster.gameY());
            }
        }
        else {
            for (MouseHoldObject o : mouseHolds.values()) {
                actionQueue.add(()-> o.onRelease(Gdx.input.getX(), SettingsMaster.gameY()));
            }
        }
        mouseHolds.clear();
    }

    HashSet<InputBinding> stillHeldBindings = new HashSet<>();
    HashSet<Integer> releaseKeys = new HashSet<>();
    public void update(float elapsed)
    {
        //Check currently held keys. If they are not held, release them.
        //This is due to keyUp events possibly being missed if something else consumes them, another layer is created, focus is lost, etc.
        //Many possibilities.

        inputLock.lock();

        VoidMethod m;
        while (!actionQueue.isEmpty()) {
            m = actionQueue.poll();
            if (m != null)
                m.run();
        }

        stillHeldBindings.clear();
        releaseKeys.clear();

        for (Map.Entry<Integer, InputBinding> bindingEntry : heldKeyInputs.entrySet()) {
            if (Gdx.input.isKeyPressed(bindingEntry.getKey())) //key is still pressed
            {
                if (bindingEntry.getValue().hasHold() && !stillHeldBindings.contains(bindingEntry.getValue())) {
                    bindingEntry.getValue().getHold().update(elapsed); //ensures only one update
                }
                stillHeldBindings.add(bindingEntry.getValue());
            } else {
                releaseKeys.add(bindingEntry.getKey());
            }
        }

        for (int key : releaseKeys) {
            releaseKey(key, null); //no action queue used since this is on the main thread
        }

        Iterator<Map.Entry<Integer, MouseHoldObject>> mouseHoldIterator = mouseHolds.entrySet().iterator();
        Map.Entry<Integer, MouseHoldObject> mouseEntry;
        while (mouseHoldIterator.hasNext()) {
            mouseEntry = mouseHoldIterator.next();
            if (Gdx.input.isButtonPressed(mouseEntry.getKey())) {
                mouseEntry.getValue().update(elapsed);
            }
            else {
                mouseEntry.getValue().onRelease(Gdx.input.getX(), SettingsMaster.gameY());
                mouseHoldIterator.remove();
            }
        }

        inputLock.unlock();
    }

    public boolean receiveKeyDown(int keycode)
    {
        HashMap<Integer, InputBinding> keyBindings = keyInputs.get(modifierState());

        if (keyBindings != null)
        {
            InputBinding binding = keyBindings.get(keycode);

            if (binding != null)
            {
                return activateBinding(binding, keycode);
            }
        }

        //Input was not consumed.
        return anyInput.onDown(actionQueue);
    }
    List<Pair<InputBinding, List<Integer>>> reactivateBlockedInputs = new ArrayList<>();
    public boolean receiveKeyUp(int keycode)
    {
        if (heldKeyInputs.containsKey(keycode))
        {
            inputLock.lock();

            InputBinding binding = heldKeyInputs.remove(keycode);

            int heldCount = activeBindings.getOrDefault(binding, 0) - 1;
            if (heldCount == 0)
            {
                if (binding.hasRelease())
                {
                    binding.onRelease(actionQueue);
                }

                activeBindings.remove(binding);

                //When binding is released, check for blocked conflicting bindings
                Map.Entry<InputBinding, Pair<Integer, List<Integer>>> blockedBinding;
                Iterator<Map.Entry<InputBinding, Pair<Integer, List<Integer>>>> blockedIterator = blockedBindings.entrySet().iterator();
                reactivateBlockedInputs.clear();
                while (blockedIterator.hasNext()) {
                    blockedBinding = blockedIterator.next();
                    if (binding.isConflict.test(blockedBinding.getKey().getInputID())) {
                        if (--blockedBinding.getValue().a <= 0) { //no more blocking inputs are active
                            blockedIterator.remove();
                            reactivateBlockedInputs.add(new Pair<>(blockedBinding.getKey(), blockedBinding.getValue().b));
                        }
                    }
                }

                for (Pair<InputBinding, List<Integer>> reactivateBinding : reactivateBlockedInputs) {
                    for (int key : reactivateBinding.b) {
                        activateBinding(binding, key);
                    }
                }
            }
            else if (heldCount > 0)
            {
                activeBindings.put(binding, heldCount);
            }

            inputLock.unlock();
        }
        return false;
    }

    private boolean activateBinding(InputBinding binding, int keycode) {
        if (activeBindings.containsKey(binding)) //Binding already active, perform stacking depending on binding type
        {
            //already held down (probably using another supported key)
            switch (binding.bindingType) {
                case SINGLE: //Input "remains" held, with +1 input, allowing you to swap between inputs with no impact.
                    activeBindings.merge(binding, 1, Integer::sum);
                    inputLock.lock();
                    heldKeyInputs.put(keycode, binding);
                    inputLock.unlock();
                    return true;
                case REPRESS: //Triggers release and press events. Remove previously held inputs and trigger their release events
                    inputLock.lock();

                    for (InputBinding.InputInfo info : binding.getInputs()) {
                        InputBinding held = heldKeyInputs.get(info.getCode());

                        if (held == binding) {
                            heldKeyInputs.remove(info.getCode());
                            if (binding.hasRelease())
                            {
                                binding.onRelease(actionQueue);
                            }

                            activeBindings.remove(binding);
                        }
                    }
                    inputLock.unlock();
                    //continue to receive press code
                    break;
                case OVERWRITE: //Remove previously held inputs without triggering release events, but does trigger press events.
                    inputLock.lock();

                    for (InputBinding.InputInfo info : binding.getInputs()) {
                        InputBinding held = heldKeyInputs.get(info.getCode());

                        if (held == binding) {
                            heldKeyInputs.remove(info.getCode());
                            activeBindings.remove(binding);
                        }
                    }
                    inputLock.unlock();
                    break;
            }
        }
        else { //Check for blocked input
            for (InputBinding activeBinding : activeBindings.keySet()) {
                if (activeBinding.blockLock && activeBinding.isConflict.test(binding.getInputID())) {
                    return false;
                }
            }
        }

        if (binding.onDown(actionQueue))
        {
            //Binding triggers press event
            inputLock.lock();
            if (binding.hasHold())
            {
                binding.getHold().reset();
            }

            //Conflicts
            Iterator<InputBinding> inputIterator;
            InputBinding testBinding;

            //Increment blocking count of already blocked bindings
            if (binding.conflictType == InputBinding.ConflictType.BLOCKING) {
                inputIterator = blockedBindings.keySet().iterator();
                while (inputIterator.hasNext()) {
                    testBinding = inputIterator.next();

                    if (binding.isConflict.test(testBinding.getInputID())) {
                        Pair<Integer, List<Integer>> blockData = blockedBindings.get(testBinding);
                        if (blockData != null) {
                            blockData.a += 1;
                        }
                    }
                }
            }

            //Block or cancel active conflicting inputs
            inputIterator = activeBindings.keySet().iterator();
            while (inputIterator.hasNext()) {
                testBinding = inputIterator.next();

                if (binding.isConflict.test(testBinding.getInputID())) {
                    inputIterator.remove();

                    switch (binding.conflictType) {
                        case BLOCKING: //Releasing blocking keys will result in the unblocked input becoming active again.
                            List<Integer> bindingKeys = new ArrayList<>();
                            for (Map.Entry<Integer, InputBinding> heldKey : heldKeyInputs.entrySet()) { //Track the current held keys for this input
                                if (heldKey.getValue().equals(testBinding)) {
                                    bindingKeys.add(heldKey.getKey());
                                }
                            }
                            blockedBindings.put(testBinding, new Pair<>(1, bindingKeys));
                            break;
                        case CANCELLING: //Cancelled input must be pressed again.
                            for (InputBinding.InputInfo inputInfo : testBinding.getInputs())
                                heldKeyInputs.remove(inputInfo.getCode());
                            break;
                    }
                    if (testBinding.hasRelease())
                        testBinding.onRelease(actionQueue);
                }
            }

            activeBindings.put(binding, 1);
            heldKeyInputs.put(keycode, binding);

            inputLock.unlock();

            return true;
        }
        return false;
    }
    private void releaseKey(int keycode, Queue<VoidMethod> queue) {
        if (heldKeyInputs.containsKey(keycode))
        {
            inputLock.lock();

            InputBinding binding = heldKeyInputs.remove(keycode);

            int heldCount = activeBindings.getOrDefault(binding, -1);
            if (heldCount == -1) {
                //Not in active bindings
                Pair<Integer, List<Integer>> blockInfo = blockedBindings.get(binding);
                if (blockInfo != null) {
                    blockInfo.b.remove(keycode); //This blocked binding input is removed
                    if (blockInfo.b.isEmpty()) { //No more inputs on blocked binding, stop tracking it
                        blockedBindings.remove(binding);
                    }
                }
            }
            else if (heldCount <= 1) {
                if (binding.hasRelease())
                {
                    binding.onRelease(queue);
                }

                activeBindings.remove(binding);

                //When binding is released, check for blocked conflicting bindings

                //Maybe: Blocking is tracked in a two-way map?
                //Each blocking binding points to the bindings they block
                //Each blocked binding points to the bindings blocking them?
                //Using just one of the two is more efficient if blocking is expected to be uncommon.
                //Two way map makes tracking/removal easier, but also makes tracking a bit more complicated.

                for (Map.Entry<InputBinding, Pair<Integer, List<Integer>>> blockedBinding : blockedBindings.entrySet()) {
                    if (binding.isConflict.test(blockedBinding.getKey().getInputID())) {
                        int amt = blockedBinding.getValue().a - 1;

                        if (amt <= 0) { //no longer blocked

                        }
                    }
                }
            }
            else {
                activeBindings.put(binding, heldCount - 1); //decrement held count
            }

            inputLock.unlock();
        }
    }

    //----------- MOUSE -----------
    public boolean receiveTouchDown(int gameX, int gameY, int button) {
        MouseHoldObject hold = mouseHolds.remove(button);
        if (hold != null) {
            actionQueue.add(()->hold.onRelease(gameX, gameY));
        }

        for (MouseInputInfo info : mouseInputs)
        {
            if (info.condition.apply(gameX, gameY, button)) {
                actionQueue.add(()->finalizeTouch(info, gameX, gameY, button));
                return true;
            }
        }

        return anyInput.onDown(actionQueue);
    }
    private void finalizeTouch(MouseInputInfo info, int gameX, int gameY, int button) {
        MouseHoldObject hold = mouseHolds.remove(button);
        if (hold != null) {
            hold.onRelease(gameX, gameY);
        }
        hold = info.onPress.apply(new Vector2(gameX, gameY), button);
        if (hold != null)
            mouseHolds.put(button, hold);
    }
    public boolean receiveTouchUp(int gameX, int gameY, int button) {
        MouseHoldObject hold = mouseHolds.remove(button);
        if (hold != null) {
            actionQueue.add(()->hold.onRelease(gameX, gameY));
            return true;
        }
        return false;
    }
    public boolean receiveTouchDragged(int gameX, int gameY) {
        for (MouseHoldObject hold : mouseHolds.values())
            actionQueue.add(()->hold.onDrag(gameX, gameY));
        return !mouseHolds.isEmpty();
    }

    public void cancelMouseHold(MouseHoldObject hold) {
        Iterator<Map.Entry<Integer, MouseHoldObject>> holdIterator = mouseHolds.entrySet().iterator();
        MouseHoldObject o;
        while (holdIterator.hasNext()) {
            o = holdIterator.next().getValue();
            if (o.equals(hold)) {
                holdIterator.remove();
                return;
            }
        }
    }

    public boolean isBindingActive(String key) {
        InputBinding b = allBindings.get(key);
        if (b == null)
            return false;
        return activeBindings.containsKey(b);
    }

    public void queue(VoidMethod action) {
        if (action != null)
            actionQueue.add(action);
    }

    public static int modifierState()
    {
        return InputBinding.InputInfo.getModifierKey(ctrl(), alt(), shift());
    }

    public static boolean ctrl()
    {
        return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
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
