package alchyr.taikoedit.management;

//There should be binding Groups.
//A class "BoundInputLayer" that automatically conveys its input to a binding group.
//Binding keys should be easily modifiable.
//Binding to a key already bound within a group should unbind that key.

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.core.input.InputBinding;
import com.badlogic.gdx.Input;

import java.util.HashMap;

//One input binding is a linkage of a single input to a single result.
//Bindings are bound to methods?
public class BindingMaster {
    private static final HashMap<String, BindingGroup> bindingGroups = new HashMap<>();

    public static void initialize()
    {
        //todo: load/save bindings

        BindingGroup creating;

        //Main Menu
        creating = new BindingGroup("Menu");
        //creating.addBinding();

        creating.createInputMap();

        bindingGroups.put(creating.getID(), creating);

        //Editor
        creating = new BindingGroup("Editor");
        creating.addBinding(InputBinding.create("SeekRight", new InputBinding.InputInfo(Input.Keys.RIGHT), new InputBinding.InputInfo(Input.Keys.RIGHT, true), new InputBinding.InputInfo(Input.Keys.X), new InputBinding.InputInfo(Input.Keys.X, false, true, false)));
        creating.addBinding(InputBinding.create("SeekLeft", new InputBinding.InputInfo(Input.Keys.LEFT), new InputBinding.InputInfo(Input.Keys.LEFT, true), new InputBinding.InputInfo(Input.Keys.Z), new InputBinding.InputInfo(Input.Keys.Z, false, true, false)));
        creating.addBinding(InputBinding.create("RateUp", new InputBinding.InputInfo(Input.Keys.UP)));
        creating.addBinding(InputBinding.create("RateDown", new InputBinding.InputInfo(Input.Keys.DOWN)));
        creating.addBinding(InputBinding.create("ZoomIn", new InputBinding.InputInfo(Input.Keys.UP, false, true, false)));
        creating.addBinding(InputBinding.create("ZoomOut", new InputBinding.InputInfo(Input.Keys.DOWN, false, true, false)));
        creating.addBinding(InputBinding.create("SnapUp", new InputBinding.InputInfo(Input.Keys.UP, true)));
        creating.addBinding(InputBinding.create("SnapDown", new InputBinding.InputInfo(Input.Keys.DOWN, true)));
        creating.addBinding(InputBinding.create("SelectAll", new InputBinding.InputInfo(Input.Keys.A, true)));
        creating.addBinding(InputBinding.create("Bookmark", new InputBinding.InputInfo(Input.Keys.B, true)));
        creating.addBinding(InputBinding.create("Copy", new InputBinding.InputInfo(Input.Keys.C, true)));
        creating.addBinding(InputBinding.create("Reverse", new InputBinding.InputInfo(Input.Keys.G, true)));
        creating.addBinding(InputBinding.create("Resnap", new InputBinding.InputInfo(Input.Keys.R, true, false, true)));
        creating.addBinding(InputBinding.create("FUCK", new InputBinding.InputInfo(Input.Keys.H, true)));
        creating.addBinding(InputBinding.create("Cut", new InputBinding.InputInfo(Input.Keys.X, true)));
        creating.addBinding(InputBinding.create("Paste", new InputBinding.InputInfo(Input.Keys.V, true)));
        creating.addBinding(InputBinding.create("ClearSelect", new InputBinding.InputInfo(Input.Keys.GRAVE)));
        creating.addBinding(InputBinding.create("OpenView", new InputBinding.InputInfo(Input.Keys.N, true)));
        creating.addBinding(InputBinding.create("Save", new InputBinding.InputInfo(Input.Keys.S, true)));
        creating.addBinding(InputBinding.create("SaveAll", new InputBinding.InputInfo(Input.Keys.S, true, true, false)));
        creating.addBinding(InputBinding.create("TJASave", new InputBinding.InputInfo(Input.Keys.S, true, true, true)));
        creating.addBinding(InputBinding.create("FinishLock", new InputBinding.InputInfo(Input.Keys.Q)));
        creating.addBinding(InputBinding.create("Redo", new InputBinding.InputInfo(Input.Keys.Y, true)));
        creating.addBinding(InputBinding.create("Undo", new InputBinding.InputInfo(Input.Keys.Z, true)));
        creating.addBinding(InputBinding.create("Delete", new InputBinding.InputInfo(Input.Keys.FORWARD_DEL), new InputBinding.InputInfo(Input.Keys.BACKSPACE)));
        creating.addBinding(InputBinding.create("IncreaseOffset", new InputBinding.InputInfo(Input.Keys.EQUALS), new InputBinding.InputInfo(Input.Keys.EQUALS, true)));
        creating.addBinding(InputBinding.create("DecreaseOffset", new InputBinding.InputInfo(Input.Keys.MINUS), new InputBinding.InputInfo(Input.Keys.MINUS, true)));
        creating.addBinding(InputBinding.create("TogglePlayback", new InputBinding.InputInfo(Input.Keys.SPACE)));
        creating.addBinding(InputBinding.create("Exit", new InputBinding.InputInfo(Input.Keys.ESCAPE)));
        creating.addBinding(InputBinding.create("1", new InputBinding.InputInfo(Input.Keys.NUM_1)));
        creating.addBinding(InputBinding.create("2", new InputBinding.InputInfo(Input.Keys.NUM_2)));
        creating.addBinding(InputBinding.create("3", new InputBinding.InputInfo(Input.Keys.NUM_3)));
        creating.addBinding(InputBinding.create("4", new InputBinding.InputInfo(Input.Keys.NUM_4)));
        creating.addBinding(InputBinding.create("5", new InputBinding.InputInfo(Input.Keys.NUM_5)));
        creating.addBinding(InputBinding.create("6", new InputBinding.InputInfo(Input.Keys.NUM_6)));
        creating.addBinding(InputBinding.create("7", new InputBinding.InputInfo(Input.Keys.NUM_7)));
        creating.addBinding(InputBinding.create("8", new InputBinding.InputInfo(Input.Keys.NUM_8)));
        creating.addBinding(InputBinding.create("9", new InputBinding.InputInfo(Input.Keys.NUM_9)));
        creating.addBinding(InputBinding.create("0", new InputBinding.InputInfo(Input.Keys.NUM_0)));
        creating.addBinding(InputBinding.create("i2", new InputBinding.InputInfo(Input.Keys.W), new InputBinding.InputInfo(Input.Keys.F), new InputBinding.InputInfo(Input.Keys.K),
                new InputBinding.InputInfo(Input.Keys.W, false, true, false), new InputBinding.InputInfo(Input.Keys.F, false, true, false), new InputBinding.InputInfo(Input.Keys.K, false, true, false)));
        creating.addBinding(InputBinding.create("i3", new InputBinding.InputInfo(Input.Keys.E), new InputBinding.InputInfo(Input.Keys.D), new InputBinding.InputInfo(Input.Keys.L),
                new InputBinding.InputInfo(Input.Keys.E, false, true, false), new InputBinding.InputInfo(Input.Keys.D, false, true, false), new InputBinding.InputInfo(Input.Keys.L, false, true, false)));
        creating.addBinding(InputBinding.create("i4", new InputBinding.InputInfo(Input.Keys.R)));
        creating.addBinding(InputBinding.create("i5", new InputBinding.InputInfo(Input.Keys.T)));
        creating.addBinding(InputBinding.create("i6", new InputBinding.InputInfo(Input.Keys.Y)));
        creating.addBinding(InputBinding.create("i7", new InputBinding.InputInfo(Input.Keys.U)));
        creating.addBinding(InputBinding.create("i8", new InputBinding.InputInfo(Input.Keys.I)));
        creating.addBinding(InputBinding.create("i9", new InputBinding.InputInfo(Input.Keys.O)));
        creating.addBinding(InputBinding.create("i0", new InputBinding.InputInfo(Input.Keys.P)));

        //used for testing.
        //creating.addBinding(InputBinding.create("DEBUG", new InputBinding.InputInfo(Input.Keys.Q, true, true, true)));

        if (TaikoEditor.DIFFCALC)
            creating.addBinding(InputBinding.create("DIFFCALC", new InputBinding.InputInfo(Input.Keys.D, true, false, true)));

        creating.createInputMap();

        bindingGroups.put(creating.getID(), creating);

        creating = new BindingGroup("Basic");

        creating.addBinding(InputBinding.create("Exit", new InputBinding.InputInfo(Input.Keys.ESCAPE)));
        creating.addBinding(InputBinding.create("Confirm", new InputBinding.InputInfo(Input.Keys.ENTER)));
        creating.addBinding(InputBinding.create("1", new InputBinding.InputInfo(Input.Keys.NUM_1)));
        creating.addBinding(InputBinding.create("2", new InputBinding.InputInfo(Input.Keys.NUM_2)));
        creating.addBinding(InputBinding.create("3", new InputBinding.InputInfo(Input.Keys.NUM_3)));
        creating.addBinding(InputBinding.create("4", new InputBinding.InputInfo(Input.Keys.NUM_4)));
        creating.addBinding(InputBinding.create("5", new InputBinding.InputInfo(Input.Keys.NUM_5)));
        creating.addBinding(InputBinding.create("6", new InputBinding.InputInfo(Input.Keys.NUM_6)));
        creating.addBinding(InputBinding.create("7", new InputBinding.InputInfo(Input.Keys.NUM_7)));
        creating.addBinding(InputBinding.create("8", new InputBinding.InputInfo(Input.Keys.NUM_8)));
        creating.addBinding(InputBinding.create("9", new InputBinding.InputInfo(Input.Keys.NUM_9)));
        creating.addBinding(InputBinding.create("0", new InputBinding.InputInfo(Input.Keys.NUM_0)));
        creating.addBinding(InputBinding.create("TAB", new InputBinding.InputInfo(Input.Keys.TAB)));

        creating.createInputMap();

        bindingGroups.put(creating.getID(), creating);


        //Test
        creating = new BindingGroup("test");

        creating.addBinding(InputBinding.create("Exit", new InputBinding.InputInfo(Input.Keys.ESCAPE)));

        creating.createInputMap();

        bindingGroups.put(creating.getID(), creating);
    }


    public static BindingGroup getBindingGroup(String key)
    {
        return bindingGroups.get(key).copy();
    }
}
