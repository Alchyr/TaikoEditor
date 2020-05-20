package alchyr.taikoedit.desktop.config;

import com.badlogic.gdx.Graphics;

public class ProgramConfig {
    public boolean fullscreen;
    public int width;
    public int height;

    public String osuFolder;

    public ProgramConfig(Graphics.DisplayMode primaryMode)
    {
        fullscreen = true;

        width = primaryMode.width;
        height = primaryMode.height;

        osuFolder = "";
    }

    public ProgramConfig(String source)
    {
        String[] settings = source.split("\n");

        fullscreen = settings[0].equals("true");
        width = Integer.parseInt(settings[1]);
        height = Integer.parseInt(settings[2]);

        osuFolder = settings[3];
    }

    @Override
    public String toString() {
        return fullscreen +
                "\n" + width +
                "\n" + height +
                "\n" + osuFolder;
    }
}
