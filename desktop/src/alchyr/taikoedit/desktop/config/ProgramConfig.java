package alchyr.taikoedit.desktop.config;

import com.badlogic.gdx.Graphics;

import java.io.File;
import java.nio.file.Paths;

public class ProgramConfig {
    public boolean complete = false;

    public boolean fullscreen;
    public int width;
    public int height;

    public String osuFolder;
    public boolean useFastMenu;

    public int fpsMode;
    public int fps;

    public ProgramConfig(Graphics.DisplayMode primaryMode)
    {
        fullscreen = false;

        width = primaryMode.width;
        height = primaryMode.height;

        osuFolder = getSongFolder();
        useFastMenu = true;

        fpsMode = 1;
        fps = 120;
    }

    public ProgramConfig(String source)
    {
        String[] settings = source.split("\n");

        fullscreen = settings[0].equals("true");
        width = Integer.parseInt(settings[1]);
        height = Integer.parseInt(settings[2]);

        osuFolder = settings[3];

        fpsMode = Integer.parseInt(settings[4]);

        fps = Integer.parseInt(settings[5]);

        if (settings.length > 6) {
            useFastMenu = Boolean.parseBoolean(settings[6]);
            complete = true;
        }
    }

    @Override
    public String toString() {
        return fullscreen +
                "\n" + width +
                "\n" + height +
                "\n" + osuFolder +
                "\n" + fpsMode +
                "\n" + fps +
                "\n" + useFastMenu;
    }
    private static String getSongFolder()
    {
        String location;

        String OS = (System.getProperty("os.name")).toUpperCase();

        if (OS.contains("WIN"))
        {
            location = System.getenv("LOCALAPPDATA");
            if (location != null && testSongFolder(location = location + File.separatorChar + "osu!"))
            {
                return location;
            }
            location = System.getenv("APPDATA");
            if (location != null && testSongFolder(location = location + File.separatorChar + "osu!"))
            {
                return location;
            }
            location = System.getenv("ProgramFiles(x86)");
            if (location != null && testSongFolder(location = location + File.separatorChar + "osu!"))
            {
                return location;
            }
            location = System.getenv("ProgramFiles");
            if (location != null && testSongFolder(location = location + File.separatorChar + "osu!"))
            {
                return location;
            }
        }
        else
        {
            //Eh I'm too lazy to deal with other operating systems. Locate it yourself.
        }
        return "";
    }

    private static boolean testSongFolder(String location)
    {
        File f = Paths.get(location).toFile();

        return testSongFolder(f);
    }
    private static boolean testSongFolder(File f)
    {
        return f != null && f.isDirectory() && songsExists(f);
    }
    private static boolean songsExists(File directory)
    {
        return new File(directory, "Songs").exists();
    }
}
