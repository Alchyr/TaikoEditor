package alchyr.taikoedit.management;

//There should be binding Groups.
//A class "BoundInputLayer" that automatically conveys its input to a binding group.
//Binding keys should be easily modifiable.
//Binding to a key already bound within a group should unbind that key.

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.core.input.InputBinding;
import alchyr.taikoedit.util.GeneralUtils;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Collections;

//One input binding is a linkage of a single input to a single result.
//Bindings are bound to methods?
public class BindingMaster {
    private static final String hotkeyFile = "settings/hotkeys.cfg";
    private static final Logger logger = LogManager.getLogger("Hotkeys");

    private static final HashMap<String, BindingGroup> bindingGroups = new HashMap<>();

    public static void initialize()
    {
        FileHandle h = Gdx.files.local(hotkeyFile);

        Map<String, Map<String, List<InputBinding.InputInfo>>> loadedData = load(h);

        BindingGroup creating;

        //Main Menu
        creating = new BindingGroup("Menu", false);

        creating.initialize(bindingGroups, loadedData.get(creating.getID()));

        //Editor
        creating = new BindingGroup("Editor", true);
        creating.addBinding(InputBinding.create("Undo", new InputBinding.InputInfo(Input.Keys.Z, true)));
        creating.addBinding(InputBinding.create("Redo", new InputBinding.InputInfo(Input.Keys.Y, true)));
        creating.addBinding(InputBinding.create("SelectAll", new InputBinding.InputInfo(Input.Keys.A, true)));
        creating.addBinding(InputBinding.create("Delete", new InputBinding.InputInfo(Input.Keys.FORWARD_DEL), new InputBinding.InputInfo(Input.Keys.BACKSPACE)));
        creating.addBinding(InputBinding.create("Copy", new InputBinding.InputInfo(Input.Keys.C, true)));
        creating.addBinding(InputBinding.create("Cut", new InputBinding.InputInfo(Input.Keys.X, true)));
        creating.addBinding(InputBinding.create("Paste", new InputBinding.InputInfo(Input.Keys.V, true)));
        creating.addBinding(InputBinding.create("Reverse", new InputBinding.InputInfo(Input.Keys.G, true)));
        creating.addBinding(InputBinding.create("ClearSelect", new InputBinding.InputInfo(Input.Keys.GRAVE)));
        creating.addBinding(InputBinding.create("Bookmark", new InputBinding.InputInfo(Input.Keys.B, true)));
        creating.addBinding(InputBinding.create("OpenView", new InputBinding.InputInfo(Input.Keys.N, true)));
        creating.addBinding(InputBinding.create("Save", new InputBinding.InputInfo(Input.Keys.S, true)));
        creating.addBinding(InputBinding.create("SaveAll", new InputBinding.InputInfo(Input.Keys.S, true, false, true)));
        creating.addBinding(InputBinding.create("TJASave", new InputBinding.InputInfo(Input.Keys.S, true, true, true)));

        creating.addBinding(InputBinding.create("TogglePlayback", new InputBinding.InputInfo(Input.Keys.SPACE)));
        creating.addBinding(InputBinding.create("SeekRight", new InputBinding.InputInfo(Input.Keys.RIGHT, InputBinding.InputInfo.Maybe.MAYBE), new InputBinding.InputInfo(Input.Keys.X, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.MAYBE)));
        creating.addBinding(InputBinding.create("SeekLeft", new InputBinding.InputInfo(Input.Keys.LEFT, InputBinding.InputInfo.Maybe.MAYBE), new InputBinding.InputInfo(Input.Keys.Z, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.MAYBE)));
        creating.addBinding(InputBinding.create("RateUp", new InputBinding.InputInfo(Input.Keys.UP)));
        creating.addBinding(InputBinding.create("RateDown", new InputBinding.InputInfo(Input.Keys.DOWN)));
        creating.addBinding(InputBinding.create("ZoomIn", new InputBinding.InputInfo(Input.Keys.UP, false, false, true)));
        creating.addBinding(InputBinding.create("ZoomOut", new InputBinding.InputInfo(Input.Keys.DOWN, false, false, true)));
        creating.addBinding(InputBinding.create("SnapUp", new InputBinding.InputInfo(Input.Keys.UP, true)));
        creating.addBinding(InputBinding.create("SnapDown", new InputBinding.InputInfo(Input.Keys.DOWN, true)));
        creating.addBinding(InputBinding.create("IncreaseOffset", new InputBinding.InputInfo(Input.Keys.EQUALS, InputBinding.InputInfo.Maybe.MAYBE)));
        creating.addBinding(InputBinding.create("DecreaseOffset", new InputBinding.InputInfo(Input.Keys.MINUS, InputBinding.InputInfo.Maybe.MAYBE)));

        creating.addBinding(InputBinding.create("FinishLock", new InputBinding.InputInfo(Input.Keys.Q)));
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
        creating.addBinding(InputBinding.create("i2",
                new InputBinding.InputInfo(Input.Keys.W, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.MAYBE),
                new InputBinding.InputInfo(Input.Keys.F, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.MAYBE),
                new InputBinding.InputInfo(Input.Keys.K, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.MAYBE)));
        creating.addBinding(InputBinding.create("i3",
                new InputBinding.InputInfo(Input.Keys.E, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.MAYBE),
                new InputBinding.InputInfo(Input.Keys.D, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.MAYBE),
                new InputBinding.InputInfo(Input.Keys.L, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.FALSE, InputBinding.InputInfo.Maybe.MAYBE)));
        creating.addBinding(InputBinding.create("i4", new InputBinding.InputInfo(Input.Keys.R)));
        creating.addBinding(InputBinding.create("i5", new InputBinding.InputInfo(Input.Keys.T)));
        creating.addBinding(InputBinding.create("i6", new InputBinding.InputInfo(Input.Keys.Y)));
        creating.addBinding(InputBinding.create("i7", new InputBinding.InputInfo(Input.Keys.U)));
        creating.addBinding(InputBinding.create("i8", new InputBinding.InputInfo(Input.Keys.I)));
        creating.addBinding(InputBinding.create("i9", new InputBinding.InputInfo(Input.Keys.O)));
        creating.addBinding(InputBinding.create("i0", new InputBinding.InputInfo(Input.Keys.P)));
        creating.addBinding(InputBinding.create("Exit", new InputBinding.InputInfo(Input.Keys.ESCAPE)));
        creating.addBinding(InputBinding.create("Messy", new InputBinding.InputInfo(Input.Keys.H, true)));
        creating.addBinding(InputBinding.create("Resnap", new InputBinding.InputInfo(Input.Keys.R, true, true, false)));

        //used for testing.
        //creating.addBinding(InputBinding.create("DEBUG", new InputBinding.InputInfo(Input.Keys.Q, true, true, true)));

        if (TaikoEditor.DIFFCALC)
            creating.addBinding(InputBinding.create("DIFFCALC", new InputBinding.InputInfo(Input.Keys.D, true, true, false)));

        creating.initialize(bindingGroups, loadedData.get(creating.getID()));

        creating = new BindingGroup("Basic", false);

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
        creating.addBinding(InputBinding.create("Up", new InputBinding.InputInfo(Input.Keys.UP)));
        creating.addBinding(InputBinding.create("Down", new InputBinding.InputInfo(Input.Keys.DOWN)));

        creating.initialize(bindingGroups, loadedData.get(creating.getID()));


        //Test
        creating = new BindingGroup("test", false);

        creating.addBinding(InputBinding.create("Exit", new InputBinding.InputInfo(Input.Keys.ESCAPE)));

        creating.initialize(bindingGroups, loadedData.get(creating.getID()));

        save(h);
    }


    public static BindingGroup getBindingGroupCopy(String key)
    {
        return bindingGroups.get(key).copy();
    }
    public static void releaseCopy(BindingGroup copy) {
        BindingGroup base = bindingGroups.get(copy.getID());
        if (base != null && !base.equals(copy)) {
            base.releaseCopy(copy);
        }
    }

    public static Map<String, BindingGroup> getBindingGroups() {
        return bindingGroups;
    }

    public static void save() {
        save(Gdx.files.local(hotkeyFile));
    }
    private static void save(FileHandle h) {
        File f = h.file();
        Writer writer = null;
        Json json = new Json(JsonWriter.OutputType.json);

        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
            json.setWriter(writer);

            json.writeObjectStart();
            for (Map.Entry<String, BindingGroup> bindingGroup : bindingGroups.entrySet()) {
                BindingGroup group = bindingGroup.getValue();
                json.writeObjectStart(group.getID());

                for (InputBinding binding : group.getOrderedBindings()) {
                    json.writeArrayStart(binding.getInputID());

                    for (InputBinding.InputInfo input : binding.getInputs()) {
                        json.writeArrayStart();
                        json.writeValue(input.getCode());
                        json.writeValue(input.getCtrl().ordinal());
                        json.writeValue(input.getAlt().ordinal());
                        json.writeValue(input.getShift().ordinal());
                        json.writeArrayEnd();
                    }

                    json.writeArrayEnd();
                }
                json.writeObjectEnd();
            }
            json.writeObjectEnd();

            logger.info("Saved hotkeys.");
        }
        catch (Exception e) {
            logger.error("Failed to save hotkeys.");
            GeneralUtils.logStackTrace(logger, e);
        }
        finally {
            StreamUtils.closeQuietly(writer);
        }
    }

    private static Map<String, Map<String, List<InputBinding.InputInfo>>> load(FileHandle h) {
        FileInputStream in = null;
        try {
            if (h.exists()) {
                File f = h.file();

                if (f.isFile() && f.canRead()) {
                    in = new FileInputStream(f);
                    JsonValue data = new JsonReader().parse(in), bindingData, inputData;

                    if (data == null) {
                        logger.info("No hotkey data in file.");
                        return Collections.emptyMap();
                    }

                    Map<String, Map<String, List<InputBinding.InputInfo>>> bindingGroups = new HashMap<>();

                    data = data.child(); //Binding Groups

                    while (data != null) {
                        HashMap<String, List<InputBinding.InputInfo>> bindingGroup = new HashMap<>();
                        bindingGroups.put(data.name(), bindingGroup);

                        bindingData = data.child(); //Bindings
                        while (bindingData != null) {
                            //One Binding, array of arrays
                            try {
                                List<InputBinding.InputInfo> inputs = new ArrayList<>();

                                inputData = bindingData.child();
                                while (inputData != null) {
                                    int[] inputSet = inputData.asIntArray();
                                    if (inputSet.length == 4) {
                                        inputs.add(new InputBinding.InputInfo(inputSet));
                                    }
                                    else {
                                        logger.warn("Skipped incorrectly sized input set");
                                    }
                                    inputData = inputData.next();
                                }

                                bindingGroup.put(bindingData.name(), inputs);
                            }
                            catch (Exception e) {
                                logger.error("Failed to load binding inputs.");
                                GeneralUtils.logStackTrace(logger, e);
                            }
                            bindingData = bindingData.next();
                        }


                        data = data.next();
                    }

                    logger.info("Loaded hotkeys.");
                    return bindingGroups;
                }
            }
        }
        catch (Exception e) {
            logger.error("Failed to load hotkey data.");
            GeneralUtils.logStackTrace(logger, e);
        }
        finally {
            StreamUtils.closeQuietly(in);
        }

        return Collections.emptyMap();
    }
}
