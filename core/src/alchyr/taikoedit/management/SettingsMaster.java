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

    public static boolean lazerSnaps = true;

    public static long roundPos(double pos) {
        return lazerSnaps ? Math.round(pos) : (long) pos;
    }

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


    private static final String settingVersion = "0";
    public static void load()
    {
        //Load settings.
        setLanguage(Language.ENG);

        FileHandle h = Gdx.files.local("settings/settings.cfg");

        if (h.exists()) {
            try {
                String full = h.readString();
                if (full.startsWith("v")) {
                    full = full.substring(1);
                    String[] params = full.split("\n"), keyVal;
                    switch (params[0]) {
                        case "0":
                        default:
                            for (int i = 1; i < params.length; ++i) {
                                keyVal = params[i].split(":", 2);
                                if (keyVal.length == 2) {
                                    switch (keyVal[0]) {
                                        case "Music":
                                            musicVolume = Float.parseFloat(keyVal[1]);
                                            break;
                                        case "Effects":
                                            effectVolume = Float.parseFloat(keyVal[1]);
                                            break;
                                        case "dX":
                                            donX = Integer.parseInt(keyVal[1]);
                                            break;
                                        case "dY":
                                            donY = Integer.parseInt(keyVal[1]);
                                            break;
                                        case "kX":
                                            katX = Integer.parseInt(keyVal[1]);
                                            break;
                                        case "kY":
                                            katY = Integer.parseInt(keyVal[1]);
                                            break;
                                        case "DX":
                                            bigDonX = Integer.parseInt(keyVal[1]);
                                            break;
                                        case "DY":
                                            bigDonY = Integer.parseInt(keyVal[1]);
                                            break;
                                        case "KX":
                                            bigKatX = Integer.parseInt(keyVal[1]);
                                            break;
                                        case "KY":
                                            bigKatY = Integer.parseInt(keyVal[1]);
                                            break;
                                        case "Skin":
                                            Skins.loadedSkin = keyVal[1];
                                            break;
                                        case "LazerSnaps":
                                            lazerSnaps = Boolean.parseBoolean(keyVal[1]);
                                            break;
                                        default:
                                            editorLogger.info("Unknown setting key \"" + keyVal[0] + "\" with value " + keyVal[1]);
                                    }
                                }
                                else {
                                    editorLogger.error("Invalid setting entry: " + params[i]);
                                }
                            }
                            break;
                    }
                }
                else {
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
                                Skins.loadedSkin = params[i].replace("](}", ":").replace("})]", "|");
                                break;
                        }
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
        return "v" + settingVersion + '\n' +
                "Music:" + musicVolume + '\n' +
                "Effects:" + effectVolume + '\n' +
                "dX:" + donX + '\n' +
                "dY:" + donY + '\n' +
                "kX:" + katX + '\n' +
                "kY:" + katY + '\n' +
                "DX:" + bigDonX + '\n' +
                "DY:" + bigDonY + '\n' +
                "KX:" + bigKatX + '\n' +
                "KY:" + bigKatY + '\n' +
                "LazerSnaps:" + lazerSnaps + '\n' +
                "Skin:" + Skins.currentSkin.toString();
        //.replace(":", "](}").replace("|", "})]");
    }
}
