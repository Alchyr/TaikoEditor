package alchyr.taikoedit.management;

import com.badlogic.gdx.Input;

import java.util.HashMap;

public class SettingsMaster {
    private static boolean fullscreen;

    private static int WIDTH, HEIGHT, MIDDLE, GAMEPLAY_HEIGHT;

    public static boolean autoSprint = true;

    public static float SCALE = 1.0f;

    private static Language language;

    public static Language getLanguage() {
        return language;
    }

    public static String osuFolder = "";

    public static float musicVolume = 1.0f;
    public static float effectVolume = 1.0f;

    public static void setLanguage(Language language) {
        boolean loadText = SettingsMaster.language != language;
        SettingsMaster.language = language;
        if (loadText)
            LocalizationMaster.updateLocalization();
    }

    public enum Language {
        ENG
    }


    public static int getWidth() {
        return WIDTH;
    }
    public static int getMiddle() {
        return MIDDLE;
    }

    public static int getHeight() {
        return GAMEPLAY_HEIGHT;
    }

    public static void load()
    {
        //Load settings.
        setLanguage(Language.ENG);
    }

    public static void updateDimensions(int width, int height)
    {
        WIDTH = width;
        MIDDLE = WIDTH / 2;
        HEIGHT = height;
        GAMEPLAY_HEIGHT = HEIGHT - (fullscreen ? 20 : 0);

        //All things that are based on screen size should be recalculated here. A loading layer should be placed on top of the screen until this is complete.
    }
    public static void useFullscreenOffset(boolean fullscreen)
    {
        if (SettingsMaster.fullscreen = fullscreen)
        {
            GAMEPLAY_HEIGHT = HEIGHT;
        }
        else
        {
            GAMEPLAY_HEIGHT = HEIGHT - 20;
        }
    }


    public static class KeyBindings
    {
        //Input Type
        public static final int KEYBOARD = 0;
        public static final int MOUSE = 1;
        public static final int CONTROLLER = 2;

        //Bindings
        public static final int UNBOUND = -1;

        //player
        public static final int SPEED_MODIFIER = 0;
        public static final int LEFT = 1;
        public static final int RIGHT = 2;
        public static final int UP = 3;
        public static final int DOWN = 4;
        public static final int FIRE = 5;


        public static HashMap<InputBinding, Integer> playerInputBindings;

        static
        {
            playerInputBindings = new HashMap<>();

            playerInputBindings.put(binding(KEYBOARD, Input.Keys.SHIFT_LEFT), SPEED_MODIFIER);
            playerInputBindings.put(binding(KEYBOARD, Input.Keys.SHIFT_RIGHT), SPEED_MODIFIER);
            playerInputBindings.put(binding(KEYBOARD, Input.Keys.LEFT), LEFT);
            playerInputBindings.put(binding(KEYBOARD, Input.Keys.RIGHT), RIGHT);
            playerInputBindings.put(binding(KEYBOARD, Input.Keys.UP), UP);
            playerInputBindings.put(binding(KEYBOARD, Input.Keys.DOWN), DOWN);
            playerInputBindings.put(binding(KEYBOARD, Input.Keys.A), LEFT);
            playerInputBindings.put(binding(KEYBOARD, Input.Keys.D), RIGHT);
            playerInputBindings.put(binding(KEYBOARD, Input.Keys.W), UP);
            playerInputBindings.put(binding(KEYBOARD, Input.Keys.S), DOWN);
        }

        public static int getBinding(HashMap<InputBinding, Integer> bindings, Integer type, int key)
        {
            return bindings.getOrDefault(InputBinding.getBinding(type, key), UNBOUND);
        }
        public static int getPlayerBinding(Integer type, int key)
        {
            return getBinding(playerInputBindings, type, key);
        }

        private static InputBinding binding(Integer type, int key)
        {
            return InputBinding.getBinding(type, key) == null ? new InputBinding(type, key) : InputBinding.getBinding(type, key);
        }

        public static class InputBinding
        {
            private static HashMap<Integer, HashMap<Integer, InputBinding>> bindings = new HashMap<>();
            private static HashMap<Integer, InputBinding> failMap = new HashMap<>();

            private InputBinding(Integer type, int key)
            {
                bindings.computeIfAbsent(type, k -> new HashMap<>()).put(key, this);
            }

            private static InputBinding getBinding(Integer type, int key)
            {
                return bindings.getOrDefault(type, failMap).getOrDefault(key, null);
            }
        }
    }
}
