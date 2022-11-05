package alchyr.taikoedit.management;

import alchyr.taikoedit.management.assets.skins.Skins;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;

import java.util.HashMap;

import static alchyr.taikoedit.TaikoEditor.audioMaster;
import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class SettingsMaster {
    private static int WIDTH, HEIGHT, Y_OFFSET, MIDDLE;

    public static float SCALE = 1.0f;

    private static Language language;

    public static Language getLanguage() {
        return language;
    }

    public static String osuFolder = "";

    private static float musicVolume = 0.5f;
    public static float getMusicVolume() {
        return musicVolume;
    }
    public static void setMusicVolume(float f) {
        musicVolume = Math.max(0, Math.min(1.0f, f));
        audioMaster.setMusicVolume(musicVolume);
    }
    public static float effectVolume = 0.5f;

    //Controls position of dons and kats.
    //Is applied on newly placed objects and objects that have their properties changed (finisher, change color)
    //May also be applied to all objects in future with a "reposition all" option?
    public static int donX = 64, donY = 64, bigDonX = 64, bigDonY = 320, katX = 448, katY = 64, bigKatX = 448, bigKatY = 320;

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
    public static float gameY() {
        return screenToGameY(Gdx.input.getY());
    }
    public static float screenToGameY(float screenY) {
        return Y_OFFSET - screenY;
    }

    public static void load()
    {
        //Load settings.
        setLanguage(Language.ENG);

        FileHandle h = Gdx.files.local("settings/settings.cfg");

        if (h.exists()) {
            try {
                String full = h.readString();
                String[] params = full.split(":");

                for (int i = 0; i < params.length; ++i) {
                    switch (i) {
                        case 0:
                            musicVolume = Float.parseFloat(params[i]);
                            break;
                        case 1:
                            effectVolume = Float.parseFloat(params[i]);
                            break;
                        case 2:
                            donX = Integer.parseInt(params[i]);
                            break;
                        case 3:
                            donY = Integer.parseInt(params[i]);
                            break;
                        case 4:
                            katX = Integer.parseInt(params[i]);
                            break;
                        case 5:
                            katY = Integer.parseInt(params[i]);
                            break;
                        case 6:
                            bigDonX = Integer.parseInt(params[i]);
                            break;
                        case 7:
                            bigDonY = Integer.parseInt(params[i]);
                            break;
                        case 8:
                            bigKatX = Integer.parseInt(params[i]);
                            break;
                        case 9:
                            bigKatY = Integer.parseInt(params[i]);
                            break;
                        case 10:
                            Skins.loadedSkin = params[i].replace("](}", ":");
                            break;
                    }
                }
            }
            catch (Exception e) {
                editorLogger.info("Failed to read editor settings file.");
            }
        }
    }
    public static void save() {
        FileHandle h = Gdx.files.local("settings/settings.cfg");

        try {
            h.writeString(settingsString(), false);
            editorLogger.info("Saved editor settings.");
        }
        catch (Exception e) {
            editorLogger.info("Failed to save editor settings file.");
        }
    }

    public static void updateDimensions()
    {
        WIDTH = Gdx.graphics.getWidth();
        MIDDLE = WIDTH / 2;
        HEIGHT = Gdx.graphics.getHeight();
        if (HEIGHT == Gdx.graphics.getDisplayMode().height + 1) //borderless fullscreen workaround
            --HEIGHT;

        Y_OFFSET = HEIGHT - 1;


        //All things that are based on screen size should be recalculated here. A loading layer should be placed on top of the screen until this is complete.
        SCALE = Math.min(HEIGHT / 1080.0f, WIDTH / 1920.0f);
    }

    private static String settingsString() {
        return musicVolume + ":" + effectVolume + ":" +
                donX + ":" + donY + ":" +
                katX + ":" + katY + ":" +
                bigDonX + ":" + bigDonY + ":" +
                bigKatX + ":" + bigKatY + ":" +
                Skins.currentSkin.toString().replace(":", "](}");
    }
}
