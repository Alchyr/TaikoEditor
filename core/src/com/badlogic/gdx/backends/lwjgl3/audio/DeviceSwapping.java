package com.badlogic.gdx.backends.lwjgl3.audio;

import com.badlogic.gdx.Gdx;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTDisconnect;

public class DeviceSwapping {
    private static OpenALLwjgl3Audio audio = null;
    private static boolean enabled = false;
    private static double wait = 0;

    static {
        if (Gdx.audio instanceof OpenALLwjgl3Audio) {
            audio = (OpenALLwjgl3Audio) Gdx.audio;


            enabled = true;
        }
    }

    public static void updateActiveDevice(double elapsed) {
        wait -= elapsed;
        if (enabled && wait < 0 && audio != null) {
            wait = 1;

            if (ALC10.alcGetInteger(audio.device, EXTDisconnect.ALC_CONNECTED) == 0) {

            }
        }
    }
}
