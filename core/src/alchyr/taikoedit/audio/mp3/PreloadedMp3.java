package alchyr.taikoedit.audio.mp3;

import alchyr.taikoedit.audio.CustomAudio;
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALLwjgl3Audio;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import javazoom.jl.decoder.OutputBuffer;


//Changes from original:
//Preload entire mp3 to determine exact length and speed up seek operations? done
//Change the setPos method. Currently it goes to beginning then reads through ENTIRE song to desired point. Done
//Not good for rapid seeking, which will occur if arrow keys are held. Fixed
//Make a fancier ByteStream (used in read method) to support seek operations? done

public class PreloadedMp3 extends CustomAudio {
    private PreloadMp3Bitstream bitstream;

    public PreloadedMp3 (OpenALLwjgl3Audio audio, FileHandle file) {
        super(audio, file);

        PreloadMp3Bitstream.progress = 0;

        try
        {
            hasNoDevice = (boolean) noDevice.get(audio);
        } catch (Exception e) {
            this.audio = null;
            throw new GdxRuntimeException("error while preloading mp3", e);
        }
    }

    @Override
    public void preload() {
        try
        {
            bitstream = new PreloadMp3Bitstream(file.read(), file.length());
            setup(bitstream.channels, bitstream.sampleRate);
        } catch (Exception e) {
            this.audio = null;
            throw new GdxRuntimeException("error while preloading mp3", e);
        }
    }

    @Override
    public float loadProgress() {
        return PreloadMp3Bitstream.progress;
    }

    @Override
    public float getLength() {
        return bitstream.length;
    }

    @Override
    protected float seekTime(float pos) {
        return bitstream.seekTime(pos);
    }

    @Override
    public int read(byte[] buffer) {
        try {
            int totalLength = 0;
            int minRequiredLength = buffer.length - OutputBuffer.BUFFERSIZE * 2;
            while (totalLength <= minRequiredLength) {
                byte[] frame = bitstream.getNextFrame();
                if (frame == null) break;

                System.arraycopy(frame, 0, buffer, totalLength, frame.length);
                totalLength += frame.length;
            }
            /*if (totalLength == 0)
            {
                editorLogger.info("Reached 0 length buffer.");
            }*/
            return totalLength;
        } catch (Throwable ex) {
            reset();
            throw new GdxRuntimeException("Error reading audio data.", ex);
        }
    }

    @Override
    public void reset () {
        if (bitstream == null) return;

        bitstream.setFrame(0);
    }

    public void dispose () {
        super.dispose();
        if (bitstream != null)
            bitstream.clear();
    }
}