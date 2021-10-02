package alchyr.taikoedit.desktop.config;

import com.badlogic.gdx.Graphics;

public class ProgramConfig {
    public boolean fullscreen;
    public int width;
    public int height;

    public String osuFolder;

    public int fpsMode;
    public int fps;

    public ProgramConfig(Graphics.DisplayMode primaryMode)
    {
        fullscreen = true;

        width = primaryMode.width;
        height = primaryMode.height;

        osuFolder = "";

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
    }

    @Override
    public String toString() {
        return fullscreen +
                "\n" + width +
                "\n" + height +
                "\n" + osuFolder +
                "\n" + fpsMode +
                "\n" + fps;
    }
}
