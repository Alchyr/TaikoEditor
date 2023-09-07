package alchyr.taikoedit.core.input;

import alchyr.taikoedit.util.interfaces.functional.VoidMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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


    public String getInputID() {
        return inputID;
    }

    public ArrayList<InputInfo> getInputs() {
        return inputs;
    }

    public void setInputs(List<InputInfo> data) {
        inputs.clear();
        inputs.addAll(data);
    }

    public void removeInput(InputInfo info) {
        inputs.remove(info);
    }

    public void addInput(InputInfo info) {
        inputs.add(info);
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
        private static final int CTRL_INDEX = 256, ALT_INDEX = 257, SHIFT_INDEX = 258;

        public static final int CTRL_ID = 0b1, SHIFT_ID = 0b10, ALT_ID = 0b100;

        public enum Maybe {
            FALSE,
            TRUE,
            MAYBE
        }

        public int code;
        private Maybe ctrl;
        private Maybe shift;
        private Maybe alt;

        public InputInfo(int code, Maybe ctrl, Maybe alt, Maybe shift) {
            this.code = code > 255 ? 0 : code;
            this.ctrl = ctrl;
            this.alt = alt;
            this.shift = shift;
        }
        public InputInfo(int code, boolean ctrl, boolean alt, boolean shift)
        {
            this.code = code > 255 ? 0 : code;
            this.ctrl = ctrl ? Maybe.TRUE : Maybe.FALSE;
            this.alt = alt ? Maybe.TRUE : Maybe.FALSE;
            this.shift = shift ? Maybe.TRUE : Maybe.FALSE;
        }

        public InputInfo(int code, boolean ctrl)
        {
            this(code, ctrl, false, false);
        }
        public InputInfo(int code, Maybe ctrl)
        {
            this(code, ctrl, Maybe.FALSE, Maybe.FALSE);
        }

        public InputInfo(int code)
        {
            this(code, false, false, false);
        }

        public InputInfo(int[] inputSet) {
            this(inputSet[0], Maybe.values()[inputSet[1]], Maybe.values()[inputSet[2]], Maybe.values()[inputSet[3]]);
        }

        public void set(int code, boolean ctrl, boolean alt, boolean shift, boolean overrideMaybe) {
            this.code = code;
            if (overrideMaybe || this.ctrl != Maybe.MAYBE) {
                this.ctrl = ctrl ? Maybe.TRUE : Maybe.FALSE;
            }
            if (overrideMaybe || this.alt != Maybe.MAYBE) {
                this.alt = alt ? Maybe.TRUE : Maybe.FALSE;
            }
            if (overrideMaybe || this.shift != Maybe.MAYBE) {
                this.shift = shift ? Maybe.TRUE : Maybe.FALSE;
            }
        }

        public int getCode()
        {
            return code;
        }

        public Maybe getCtrl() {
            return ctrl;
        }
        public Maybe getAlt() {
            return alt;
        }
        public Maybe getShift() {
            return shift;
        }

        public int[] getModifiers()
        {
            int[] modifiers = new int[(ctrl == Maybe.MAYBE ? 2 : 1) * (alt == Maybe.MAYBE ? 2 : 1) * (shift == Maybe.MAYBE ? 2 : 1)];
            int i = 0;
            boolean c = ctrl == Maybe.TRUE, a = alt == Maybe.TRUE, s = shift == Maybe.TRUE;

            for (int ci = 0; ci < (ctrl == Maybe.MAYBE ? 2 : 1); ++ci) {
                for (int ai = 0; ai < (alt == Maybe.MAYBE ? 2 : 1); ++ai) {
                    for (int si = 0; si < (shift == Maybe.MAYBE ? 2 : 1); ++si) {
                        modifiers[i] = getModifierKey(c, a, s);
                        ++i;
                        s = true;
                    }
                    a = true;
                    s = shift == Maybe.TRUE;
                }
                c = true;
                a = alt == Maybe.TRUE;
            }
            return modifiers;
        }

        public static int getModifierKey(boolean ctrl, boolean alt, boolean shift) {
            return (ctrl ? CTRL_ID : 0) | (shift ? SHIFT_ID : 0) | (alt ? ALT_ID : 0);
        }

        public String toString(String[] keyNameArray) {
            return toString(keyNameArray, code, ctrl == Maybe.TRUE, alt == Maybe.TRUE, shift == Maybe.TRUE);
        }

        public static String toString(String[] keyNameArray, int code, boolean ctrl, boolean alt, boolean shift) {
            if (code >= keyNameArray.length)
                return "";

            return (ctrl ? keyNameArray[CTRL_INDEX] + " " : "") +
                    (alt ? keyNameArray[ALT_INDEX] + " " : "") +
                    (shift ? keyNameArray[SHIFT_INDEX] + " " : "") +
                    (code >= 0 ? keyNameArray[code] : "");
        }

        /*
            An input combination can be represented as:
            int inCode
            bool ctrl, alt, shift
            where a true is input as 0b10 and false is input as 0b01

            A hotkey combination can be represented as:
            int testCode
            int ctrl, alt, shift
            10 = true required, 01 = false required, 11 = doesn't matter

            input is valid if inCode == testCode and
            ctrl & ctrl != 0
            alt & alt != 0
            shift & shift != 0

            This would work, but it requires testing each input, rather than a hashing method which leads directly to the matching input.
         */
    }
}
