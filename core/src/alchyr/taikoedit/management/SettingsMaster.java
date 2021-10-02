package alchyr.taikoedit.management;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

import java.util.HashMap;

public class SettingsMaster {
    private static boolean fullscreen;

    private static int WIDTH, HEIGHT, Y_OFFSET, MIDDLE;

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
        return HEIGHT;
    }
    public static int screenToGameY(int screenY) {
        return Y_OFFSET - screenY;
    }

    public static void load()
    {
        //Load settings.
        setLanguage(Language.ENG);
    }

    public static void updateDimensions()
    {
        WIDTH = Gdx.graphics.getWidth();
        MIDDLE = WIDTH / 2;
        HEIGHT = Gdx.graphics.getHeight();
        Y_OFFSET = HEIGHT - 1;

        /*if (HEIGHT == Gdx.graphics.getDisplayMode().height + 1)
            --HEIGHT;*/

        //All things that are based on screen size should be recalculated here. A loading layer should be placed on top of the screen until this is complete.
        SCALE = Math.min(HEIGHT / 1080.0f, WIDTH / 1920.0f);
    }
}
