package com.badlogic.gdx.backends.lwjgl3.audio;

import alchyr.taikoedit.TaikoEditor;
import com.badlogic.gdx.Gdx;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTDisconnect;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class DeviceSwapping {
    private static OpenALLwjgl3Audio audio = null;
    private static boolean enabled = false;
    private static double wait = 0;

    private static String currentDeviceIdentifier;

    static {
        if (Gdx.audio instanceof OpenALLwjgl3Audio) {
            audio = (OpenALLwjgl3Audio) Gdx.audio;

            //ALC_DEVICE_SPECIFIER 4101 is suggested to use but does not give the necessary info. 4115 is used by lwjgl.
            currentDeviceIdentifier = ALC10.alcGetString(audio.device, 4115);
            editorLogger.info("Current audio device: " + currentDeviceIdentifier);

            editorLogger.info("Available: ");
            for (String s : audio.getAvailableOutputDevices()) {
                editorLogger.info(s);
            }

            enabled = true;
        }
    }

    public static void updateActiveDevice(float elapsed) {
        wait -= elapsed;
        if (enabled && wait < 0 && audio != null) {
            wait = 3;

            String sysDefault = ALC10.alcGetString(0, 4115);
            if (!currentDeviceIdentifier.equals(sysDefault)) {
                editorLogger.info("Swapped default device: " + sysDefault);
                currentDeviceIdentifier = sysDefault;
                audio.switchOutputDevice(currentDeviceIdentifier);
            }
        }
    }
}
