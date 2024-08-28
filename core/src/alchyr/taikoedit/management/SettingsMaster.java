package alchyr.taikoedit.management;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.editor.maps.Mapset;
import alchyr.taikoedit.management.assets.skins.Skins;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static alchyr.taikoedit.TaikoEditor.audioMaster;
import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class SettingsMaster {
    private static int WIDTH, HEIGHT, Y_OFFSET, X_MIDDLE;

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

    public static int waveformOffset = 10; //Displayed waveform position vs reported music position, ignoring editor offset

    public static boolean lazerSnaps = false;

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
    public static int getMiddleX() {
        return X_MIDDLE;
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
    private static FileHandle generalSettingsFile() {
        return Gdx.files.local("settings/settings.cfg");
    }
    public static void loadGeneralSettings()
    {
        //Load settings.
        setLanguage(Language.ENG);

        FileHandle h = generalSettingsFile();

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
                                        case "WaveformOffset":
                                            waveformOffset = Integer.parseInt(keyVal[1]);
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
    public static void saveGeneralSettings() {
        FileHandle h = generalSettingsFile();

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
        X_MIDDLE = WIDTH / 2;
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
                "WaveformOffset:" + waveformOffset + '\n' +
                "Skin:" + Skins.currentSkin.toString();
        //.replace(":", "](}").replace("|", "})]");
    }


    //Mapset-specific settings
    private static final SettingsFileProcessor<Mapset> mapSettingsProcessor = new SettingsFileProcessor<Mapset>("map")
            .addMapping("Offset", Float::parseFloat, (val, params)->TaikoEditor.music.setOffset(val), (set, params)->TaikoEditor.music.getOffset())
            .addMapping("Position", Double::parseDouble, (val, params)->TaikoEditor.music.seekSecond(val), (set, params)->TaikoEditor.music.getSecondTime())
            .addMapping("ViewsetInfo", (s)->s, (val, params)->((EditorLayer) params[0]).loadViewsetInfo(val), (set, params)->((EditorLayer) params[0]).getViewsetInfo());

    private static FileHandle mapSettingsFile(Mapset set) {
        String setDirectory = set.getDirectory().getName();
        if (setDirectory.isEmpty())
            return null;
        if (setDirectory.length() > 32) {
            setDirectory = setDirectory.replace("beatmap-", "");
            setDirectory = setDirectory.replace(" ", "");
        }
        if (setDirectory.length() > 32)
            setDirectory = setDirectory.substring(0, 32);
        return Gdx.files.local("settings/maps/" + setDirectory + ".cfg");
    }
    public static void loadMapSettings(EditorLayer editor, Mapset set) {
        mapSettingsProcessor.readFile(mapSettingsFile(set), editor);
    }
    public static void saveMapSettings(EditorLayer editor, Mapset set) {
        mapSettingsProcessor.writeFile(mapSettingsFile(set), set, editor);
    }


    private static class SettingsFileProcessor<V> {
        private final Map<String, SettingMapping<?, V>> settingMappings;
        private final String typeName;

        public SettingsFileProcessor(String typeName) {
            this.typeName = typeName;
            settingMappings = new HashMap<>();
        }

        public <N> SettingsFileProcessor<V> addMapping(String key, Function<String, N> processor, BiConsumer<N, Object[]> setter, BiFunction<V, Object[], N> provider) {
            settingMappings.put(key, new SettingMapping<>(processor, setter, provider));
            return this;
        }

        public void readFile(FileHandle h, Object... params) {
            if (h == null || !h.exists()) {
                editorLogger.info("No " + typeName + " settings file at " + h);
                return;
            }

            try {
                String full = h.readString();
                String[] entries = full.split("\n"), keyVal;
                for (String entry : entries) {
                    keyVal = entry.split(":", 2);
                    if (keyVal.length == 2) {
                        SettingMapping<?, V> mapping = settingMappings.get(keyVal[0]);
                        if (mapping != null) {
                            mapping.process(keyVal, params);
                        }
                        else {
                            editorLogger.info("Unknown " + typeName + " settings key \"" + keyVal[0] + "\" with value " + keyVal[1]);
                        }
                    } else {
                        editorLogger.error("Invalid " + typeName + " settings entry: " + entry);
                    }
                }
            }
            catch (Exception e) {
                editorLogger.info("Failed to read " + typeName + " settings file " + h, e);
            }
        }
        public void writeFile(FileHandle h, V src, Object... params) {
            try {
                StringBuilder data = new StringBuilder();
                for (Map.Entry<String, SettingMapping<?, V>> mapping : settingMappings.entrySet()) {
                    if (data.length() != 0)
                        data.append('\n');
                    data.append(mapping.getKey()).append(":").append(mapping.getValue().value(src, params));
                }
                h.writeString(data.toString(), false);
                editorLogger.info("Saved " + typeName + " settings to " + h);
            }
            catch (Exception e) {
                editorLogger.info("Failed to save " + typeName + " settings file.", e);
            }
        }

        static class SettingMapping<V, W> {
            final Function<String, V> processor;
            final BiConsumer<V, Object[]> setter;
            final BiFunction<W, Object[], V> provider;

            SettingMapping(Function<String, V> processor, BiConsumer<V, Object[]> setter, BiFunction<W, Object[], V> provider) {
                this.processor = processor;
                this.setter = setter;
                this.provider = provider;
            }

            void process(String[] keyVal, Object... params) {
                setter.accept(processor.apply(keyVal[1]), params);
            }

            String value(W src, Object... params) {
                return String.valueOf(provider.apply(src, params));
            }
        }
    }
}
