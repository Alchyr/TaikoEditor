package alchyr.taikoedit.audio;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALAudio;
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALMusic;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.GdxRuntimeException;
import javazoom.jl.decoder.OutputBuffer;
import org.lwjgl.BufferUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT;


//TODO:
//Preload entire mp3 to find length and speed up seek operations?
//Change the setPosition method. Currently it goes to beginning then reads through ENTIRE song to desired point.
//Not good for rapid seeking, which will occur if arrow keys are held.
//Make a fancier ByteStream (used in read method) to support seek operations?

public class PreloadedMp3 extends OpenALMusic {
    static private final int bufferSize = 4096 * 10;
    static private final int bufferCount = 3;
    static private final int bytesPerSample = 2;
    static private final byte[] tempBytes = new byte[bufferSize];
    static private final ByteBuffer tempBuffer = BufferUtils.createByteBuffer(bufferSize);

    //Breaking the rules. This is Bad!
    private static Method obtainSource;
    private static Method freeSource;
    private static Field noDevice;
    private static Field music;

    static {
        try {
            obtainSource = OpenALAudio.class.getDeclaredMethod("obtainSource", boolean.class);
            obtainSource.setAccessible(true);

            freeSource = OpenALAudio.class.getDeclaredMethod("freeSource", int.class);
            freeSource.setAccessible(true);

            noDevice = OpenALAudio.class.getDeclaredField("noDevice");
            noDevice.setAccessible(true);

            music = OpenALAudio.class.getDeclaredField("music");
            music.setAccessible(true);

        } catch (NoSuchMethodException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private FloatArray renderedSecondsQueue = new FloatArray(bufferCount);

    private PreloadMp3Bitstream bitstream;

    private OpenALAudio audio;
    private IntBuffer buffers;
    private int sourceID = -1;
    private int format, sampleRate;
    private boolean isPlaying;
    private float volume = 1;
    private float pan = 0;
    private float renderedSeconds, maxSecondsPerBuffer;

    private final Array<OpenALMusic> audioMusic;
    private final boolean hasNoDevice;

    private boolean stoppedAtEnd = false;

    public float tempo = 1.0f;


    private Music.OnCompletionListener onCompletionListener;

    @SuppressWarnings("unchecked")
    public PreloadedMp3 (OpenALAudio audio, FileHandle file) {
        super(audio, file);
        this.audio = audio;

        try
        {
            hasNoDevice = (boolean) noDevice.get(audio);
            audioMusic = (Array<OpenALMusic>) music.get(audio);
    
            if (hasNoDevice) return;
            bitstream = new PreloadMp3Bitstream(file.read());

            setup(bitstream.channels, bitstream.sampleRate);
        } catch (Exception e) {
            this.audio = null;
            throw new GdxRuntimeException("error while preloading mp3", e);
        }
    }

    @Override
    protected void setup (int channels, int sampleRate) {
        this.format = channels > 1 ? AL_FORMAT_STEREO16 : AL_FORMAT_MONO16;
        this.sampleRate = sampleRate;
        maxSecondsPerBuffer = (float)bufferSize / (bytesPerSample * channels * sampleRate);
    }

    @Override
    public void play () {
        if (hasNoDevice) return;
        if (sourceID == -1) {
            if (!initialize())
                return;
        }

        if (!isPlaying) {
            if (stoppedAtEnd)
            {
                reset();
            }
            alSourcePlay(sourceID);
            isPlaying = true;
        }
    }

    private boolean initialize()
    {
        if (sourceID == -1) {
            try
            {
                sourceID = (int) obtainSource.invoke(audio, true);
                if (sourceID == -1) return false;

                audioMusic.add(this);

                if (buffers == null) {
                    buffers = BufferUtils.createIntBuffer(bufferCount);
                    alGenBuffers(buffers);
                    int errorCode = alGetError();
                    if (errorCode != AL_NO_ERROR)
                        throw new GdxRuntimeException("Unable to allocate audio buffers. AL Error: " + errorCode);
                }

                alSourcei(sourceID, AL_DIRECT_CHANNELS_SOFT, AL_TRUE);
                alSourcei(sourceID, AL_LOOPING, AL_FALSE);
                setPan(pan, volume);

                boolean filled = false; // Check if there's anything to actually play.
                for (int i = 0; i < bufferCount; i++) { //Fill and queue buffers until there is nothing left to queue or no more buffers
                    int bufferID = buffers.get(i);
                    if (!fill(bufferID)) break;
                    filled = true;
                    alSourceQueueBuffers(sourceID, bufferID);
                }
                if (!filled && onCompletionListener != null) onCompletionListener.onCompletion(this);

                if (alGetError() != AL_NO_ERROR) {
                    stop();
                    return false;
                }
            }
            catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
                return false;
            }
        }

        return true;
    }

    public void stop () {
        if (hasNoDevice) return;
        if (sourceID == -1) return;
        try {
            freeSource.invoke(audio, sourceID);
            audioMusic.removeValue(this, true);
            reset();
            sourceID = -1;
            renderedSeconds = 0;
            renderedSecondsQueue.clear();
            isPlaying = false;
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void pause () {
        if (hasNoDevice) return;
        if (sourceID != -1) alSourcePause(sourceID);
        isPlaying = false;
    }

    public boolean isPlaying () {
        if (hasNoDevice) return false;
        if (sourceID == -1) return false;
        return isPlaying;
    }

    public void setVolume (float volume) {
        this.volume = volume;
        if (hasNoDevice) return;
        if (sourceID != -1) alSourcef(sourceID, AL_GAIN, volume);
    }

    public float getVolume () {
        return this.volume;
    }

    public void setPan (float pan, float volume) {
        this.volume = volume;
        this.pan = pan;
        if (hasNoDevice) return;
        if (sourceID == -1) return;
        alSource3f(sourceID, AL_POSITION, MathUtils.cos((pan - 1) * MathUtils.PI / 2), 0,
                MathUtils.sin((pan + 1) * MathUtils.PI / 2));
        alSourcef(sourceID, AL_GAIN, volume);
    }

    public void setPosition(float position)
    {
        setPosition(position, true);
    }

    public void setPosition (float position, boolean continuePlay) {
        if (hasNoDevice) return;
        if (sourceID == -1) return;

        boolean wasPlaying = isPlaying && continuePlay;
        isPlaying = false;
        alSourceStop(sourceID);
        alSourceUnqueueBuffers(sourceID, buffers);

        renderedSecondsQueue.clear();

        //Move to closest frame
        renderedSeconds = bitstream.seekTime(position);

        //Move forward until the target position is within the next buffer to be read
        while (renderedSeconds < (position - maxSecondsPerBuffer)) {
            if (read(tempBytes) <= 0) break;
            renderedSeconds += maxSecondsPerBuffer;
        }

        renderedSecondsQueue.add(renderedSeconds);
        boolean filled = false;
        for (int i = 0; i < bufferCount; i++) {
            int bufferID = buffers.get(i);
            if (!fill(bufferID)) break;
            filled = true;
            alSourceQueueBuffers(sourceID, bufferID);
        }
        renderedSecondsQueue.pop();
        if (!filled) {
            stopAtEnd();
            if (onCompletionListener != null) onCompletionListener.onCompletion(this);
        }
        alSourcef(sourceID, AL11.AL_SEC_OFFSET, position - renderedSeconds);
        if (wasPlaying) {
            alSourcePlay(sourceID);
            isPlaying = true;
        }
    }

    @Override
    public float getPosition () {
        if (hasNoDevice) return 0;
        if (sourceID == -1) return 0;
        return (renderedSeconds + alGetSourcef(sourceID, AL11.AL_SEC_OFFSET) * tempo);
    }

    public float getLength() {
        return bitstream.length;
    }

    @Override
    public int read (byte[] buffer) {
        try {
            int totalLength = 0;
            int minRequiredLength = buffer.length - OutputBuffer.BUFFERSIZE * 2;
            while (totalLength <= minRequiredLength) {
                byte[] frame = bitstream.getNextFrame();
                if (frame == null) break;

                System.arraycopy(frame, 0, buffer, totalLength, frame.length);
                totalLength += frame.length;
            }
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

    public int getChannels () {
        return format == AL_FORMAT_STEREO16 ? 2 : 1;
    }

    public int getRate () {
        return sampleRate;
    }

    @Override
    public void update () {
        if (hasNoDevice) return;
        if (sourceID == -1) return;
        if (!isPlaying) return; //It's already decoded, stop here

        boolean end = false;
        int buffers = alGetSourcei(sourceID, AL_BUFFERS_PROCESSED);
        while (buffers-- > 0) {
            int bufferID = alSourceUnqueueBuffers(sourceID);
            if (bufferID == AL_INVALID_VALUE) break;
            if (renderedSecondsQueue.size > 0) renderedSeconds = renderedSecondsQueue.pop();
            if (end) continue;
            if (fill(bufferID))
                alSourceQueueBuffers(sourceID, bufferID);
            else
                end = true;
        }
        if (end && alGetSourcei(sourceID, AL_BUFFERS_QUEUED) == 0) {
            stopAtEnd();
            if (onCompletionListener != null) onCompletionListener.onCompletion(this);
        }

        // A buffer underflow will cause the source to stop.
        if (isPlaying && alGetSourcei(sourceID, AL_SOURCE_STATE) != AL_PLAYING) alSourcePlay(sourceID);
    }

    public void stopAtEnd () {
        pause();
        stoppedAtEnd = true;
    }

    private boolean fill (int bufferID) {
        tempBuffer.clear();
        int length = read(tempBytes);
        if (length <= 0) {
            return false;
        }
        float previousLoadedSeconds = renderedSecondsQueue.size > 0 ? renderedSecondsQueue.first() : 0;
        float currentBufferSeconds = maxSecondsPerBuffer * (float)length / (float)bufferSize; //Calculate the number of seconds this buffer has IGNORING tempo
        renderedSecondsQueue.insert(0, previousLoadedSeconds + currentBufferSeconds);

        tempBuffer.put(tempBytes, 0, length).flip();

        alBufferData(bufferID, format, tempBuffer, (int) (sampleRate * tempo));

        return true;
    }

    public void changeTempo(float newTempo, float position)
    {
        tempo = newTempo;

        setPosition(position);
        //AL10.alSourcef(sourceID, AL10.AL_PITCH, getPitch(tempo)); Results in no change in speed. It seems as though this method utilizes sample rate?
    }
    /*private float getPitch(float tempo)
    {
        //low tempo, higher pitch
        return 1 / tempo;
    }*/

    public void dispose () {
        stop();
        if (hasNoDevice) return;
        if (buffers == null) return;
        alDeleteBuffers(buffers);
        bitstream.clear();
        buffers = null;
        onCompletionListener = null;
    }

    public void setOnCompletionListener (OnCompletionListener listener) {
        onCompletionListener = listener;
    }

    public int getSourceId () {
        return sourceID;
    }
}