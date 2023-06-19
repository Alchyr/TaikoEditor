package alchyr.taikoedit.audio.ogg;

//Ogg Vorbis.

import alchyr.taikoedit.audio.CustomAudio;
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALLwjgl3Audio;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class PreloadOgg extends CustomAudio {
    private PreloadOggStream data;
    private PreloadOggStream previousInput;

    public PreloadOgg(OpenALLwjgl3Audio audio, FileHandle file) {
        super(audio, file);

        PreloadOggStream.progress = 0;

        try
        {
            hasNoDevice = (boolean) noDevice.get(audio);
        } catch (Exception e) {
            throw new GdxRuntimeException("error while preloading ogg", e);
        }
    }

    @Override
    public String getAudioType() {
        return "ogg";
    }

    @Override
    public void preload() {
        try
        {
            data = new PreloadOggStream(file.read());
            setup(data.getChannels(), data.getSampleRate());
        } catch (Exception e) {
            throw new GdxRuntimeException("error while preloading ogg", e);
        }
    }

    @Override
    public float getLength() {
        return data.getLength();
    }

    @Override
    protected float seekTime(float pos) {
        return data.seekTime(pos);
    }

    @Override
    public float loadProgress() {
        return PreloadOggStream.progress;
    }

    public int read(byte[] buffer) {
        if (data == null) {
            data = new PreloadOggStream(file.read(), previousInput);
            setup(data.getChannels(), data.getSampleRate());
            previousInput = null; // release this reference
        }
        return data.read(buffer);
    }

    public void reset() {
        data.restart();
    }
}
