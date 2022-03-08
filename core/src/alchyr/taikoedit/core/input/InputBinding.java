package alchyr.taikoedit.core.input;

import alchyr.taikoedit.util.interfaces.functional.VoidMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Queue;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class InputBinding {
    public static final VoidMethod noEffect = ()->{};
    private static final Supplier<VoidMethod> notSet = ()->null;

    private final String inputID;

    private final ArrayList<InputInfo> inputs;

    private Supplier<VoidMethod> onDown = notSet;
    private boolean hasHold;
    private KeyHoldObject hold;
    private boolean hasRelease;
    private Supplier<VoidMethod> onRelease;

    private static final Predicate<String> defaultConflict = (s)->false;
    public Predicate<String> isConflict = defaultConflict;
    public ConflictType conflictType = ConflictType.CANCELLING;
    public boolean blockLock = false; //When blocking, the conflicting inputs are completely blocked.

    public BindingType bindingType = BindingType.SINGLE;

    //To complete implementation of blocking:
    //Add blocked inputs to a list, tracking how many inputs are currently blocking them
    //Their key holds remain in the active list; when removed, a blocked input is also cancelled.
    //When all blocking inputs are removed, the key acts as if it was pressed again.
    //Blocking is intended to be used only for inputs focused on holding, as inputs focused on pressing would work strangely.

    public enum ConflictType {
        BLOCKING, //UNIMPLEMENTED
        CANCELLING
    }

    public enum BindingType {
        SINGLE, //The normal mode. Multiple inputs of the same type will not re-press or overwrite previous inputs.
        OVERWRITE, //With multiple inputs, each one can overwrite the other, causing a new press event but NOT a release
        REPRESS //with multiple inputs, will cause old input to be released and new one to be pressed
    }


    static InputBinding create(String ID)
    {
        InputBinding binding = new InputBinding(ID);
        binding.clearBinding();
        return binding;
    }
    public static InputBinding create(String ID, InputInfo... input)
    {
        InputBinding binding = new InputBinding(ID);

        binding.inputs.addAll(Arrays.asList(input));
        binding.clearBinding();

        return binding;
    }
    private InputBinding(String ID) {
        this.inputID = ID;
        this.inputs = new ArrayList<>();
    }
    public InputBinding copy() {
        InputBinding copy = create(this.inputID);
        copy.inputs.addAll(inputs);

        copy.isConflict = isConflict;
        copy.conflictType = conflictType;

        copy.bindingType = bindingType;

        copy.blockLock = blockLock;

        return copy;
    }

    public InputBinding setConflicts(Predicate<String> conflicts, ConflictType type)
    {
        this.isConflict = conflicts;
        this.conflictType = type;

        return this;
    }
    public InputBinding setType(BindingType bindingType)
    {
        this.bindingType = bindingType;

        return this;
    }

    public String getInputID() {
        return inputID;
    }

    public ArrayList<InputInfo> getInputs() {
        return inputs;
    }

    public void clearBinding() {
        onDown = notSet;
        onRelease = null;
        hold = null;

        hasRelease = false;
        hasHold = false;
    }

    public boolean onDown(Queue<VoidMethod> actionQueue)
    {
        if (hasHold)
            hold.reset();

        VoidMethod action = onDown.get();
        if (action == null)
            return false;

        if (action != noEffect) {
            if (actionQueue == null) {
                action.run();
            }
            else {
                actionQueue.add(action);
            }
        }

        return true;
    }
    public boolean onRelease(Queue<VoidMethod> actionQueue)
    {
        if (hasRelease) {
            VoidMethod action = onRelease.get();
            if (action == null)
                return false;

            if (action != noEffect) {
                if (actionQueue == null) {
                    action.run();
                }
                else {
                    actionQueue.add(action);
                }
            }

            return true;
        }
        return false;
    }
    public boolean hasRelease()
    {
        return hasRelease;
    }
    public boolean hasHold()
    {
        return hasHold;
    }
    public KeyHoldObject getHold()
    {
        return hold;
    }

    public void bind(Supplier<VoidMethod> onDown) {
        bind(onDown, null, null);
    }
    public void bind(Supplier<VoidMethod> onDown, KeyHoldObject hold) {
        bind(onDown, hold, null);
    }
    public void bind(Supplier<VoidMethod> onDown, Supplier<VoidMethod> onRelease) {
        bind(onDown, null, onRelease);
    }
    public void bind(Supplier<VoidMethod> onDown, KeyHoldObject hold, Supplier<VoidMethod> onRelease) {
        if (onDown != null)
        {
            this.onDown = onDown;
        }

        this.hold = hold;
        this.hasHold = hold != null;

        this.onRelease = onRelease;
        this.hasRelease = onRelease != null;
    }


    public static class InputInfo {
        public int code;
        public boolean ctrl;
        public boolean shift;
        public boolean alt;

        public InputInfo(int code, boolean ctrl, boolean shift, boolean alt)
        {
            this.code = code;
            this.ctrl = ctrl;
            this.shift = shift;
            this.alt = alt;
        }

        public InputInfo(int code, boolean ctrl)
        {
            this(code, ctrl, false, false);
        }

        public InputInfo(int code)
        {
            this(code, false, false, false);
        }

        public int getCode()
        {
            return code;
        }
        public int getModifiers()
        {
            return (ctrl ? 1 : 0) | (shift ? 2 : 0) | (alt ? 4 : 0);
        }
    }
}
