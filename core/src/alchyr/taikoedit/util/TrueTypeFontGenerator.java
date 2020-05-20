package alchyr.taikoedit.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class TrueTypeFontGenerator {
    private static HashMap<String, BitmapFont> loadedFonts = new HashMap<>();

    private static FreeTypeFontGenerator.FreeTypeFontParameter parameters;

    private static String[] systemFontPaths = getSystemFontsPaths();

    public static boolean loadedFont(String name)
    {
        return loadedFonts.containsKey(name);
    }

    static {
        resetParameters();
    }

    public static void resetParameters()
    {
        parameters = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameters.size = 32;
        parameters.minFilter = Texture.TextureFilter.Linear;
        parameters.magFilter = Texture.TextureFilter.Linear;
        parameters.hinting = FreeTypeFontGenerator.Hinting.Slight;
    }
    public static FreeTypeFontGenerator.FreeTypeFontParameter getParameters() {
        return parameters;
    }
    public static BitmapFont generateFont(FileHandle file)
    {
        FreeTypeFontGenerator g = new FreeTypeFontGenerator(file);

        editorLogger.info("Generating TrueTypeFont from file " + file.path());

        BitmapFont f = g.generateFont(parameters);

        editorLogger.info("Complete.");

        g.dispose();

        return f;
    }

    public static BitmapFont generateSystemFont(String systemFontFileName)
    {
        FileHandle systemFontFile = getSystemFontHandle(systemFontFileName);

        if (systemFontFile == null)
        {
            editorLogger.error("Failed to load system font " + systemFontFileName + ".");
            return null;
        }

        return generateFont(systemFontFile);
    }



    private static FileHandle getSystemFontHandle(String file)
    {
        if (systemFontPaths != null)
        {
            for (String s : systemFontPaths)
            {
                FileHandle h = Gdx.files.absolute(s + File.separatorChar + file);

                if (h.exists())
                    return h;
            }
        }
        return null;
    }

    private static String[] getSystemFontsPaths() {
        String[] result;
        switch (SystemUtils.getSystemType())
        {
            case WINDOWS:
                result = new String[1];
                String path = System.getenv("WINDIR");
                result[0] = path + "\\" + "Fonts";

                return result;
            case MAC:
                result = new String[3];
                result[0] = System.getProperty("user.home") + File.separator + "Library/Fonts";
                result[1] = "/Library/Fonts";
                result[2] = "/System/Library/Fonts";

                return result;
            case LINUX:
            case UNSUPPORTED: //just try it I guess
                String[] pathsToCheck = {
                        System.getProperty("user.home") + File.separator + ".fonts",
                        "/usr/share/fonts/truetype",
                        "/usr/share/fonts/TTF"
                };

                ArrayList<String> resultList = new ArrayList<>();

                for (int i = pathsToCheck.length - 1; i >= 0; i--) {
                    String check = pathsToCheck[i];
                    File tmp = new File(check);
                    if (tmp.exists() && tmp.isDirectory() && tmp.canRead()) {
                        resultList.add(check);
                    }
                }

                if (resultList.isEmpty()) {
                    result = new String[0]; //failure
                }
                else {
                    result = new String[resultList.size()];
                    result = resultList.toArray(result);
                }

                return result;
        }

        return null;
    }
}
