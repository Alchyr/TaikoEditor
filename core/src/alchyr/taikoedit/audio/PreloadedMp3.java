package alchyr.taikoedit.audio;

import alchyr.taikoedit.TaikoEditor;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALLwjgl3Audio;
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

import org.lwjgl.openal.AL11;

import static alchyr.taikoedit.TaikoEditor.editorLogger;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT;


//TODO:
//Preload entire mp3 to find length and speed up seek operations? done
//Change the setPosition method. Currently it goes to beginning then reads through ENTIRE song to desired point. Done
//Not good for rapid seeking, which will occur if arrow keys are held. Fixed
//Make a fancier ByteStream (used in read method) to support seek operations? done

public class PreloadedMp3 extends OpenALMusic {
    public static float progress = 0;
    public static float getProgress() {
        return progress;
    }

    static private final int bufferSize = 4096 * 4;
    static private final int bufferCount = 4;
    static private final int bytesPerSample = 2;
    static private final byte[] tempBytes = new byte[bufferSize];
    static private final ByteBuffer tempBuffer = BufferUtils.createByteBuffer(bufferSize);

    private boolean update;
    private final int[] bufferIDs = new int[bufferCount];

    //Breaking the rules :(
    private static Method obtainSource;
    private static Method freeSource;
    private static Field noDevice;

    static {
        try {
            obtainSource = OpenALLwjgl3Audio.class.getDeclaredMethod("obtainSource", boolean.class);
            obtainSource.setAccessible(true);

            freeSource = OpenALLwjgl3Audio.class.getDeclaredMethod("freeSource", int.class);
            freeSource.setAccessible(true);

            noDevice = OpenALLwjgl3Audio.class.getDeclaredField("noDevice");
            noDevice.setAccessible(true);

        } catch (NoSuchMethodException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private final FloatArray renderedSecondsQueue = new FloatArray(bufferCount);

    private PreloadMp3Bitstream bitstream;

    private OpenALLwjgl3Audio audio;
    private IntBuffer buffers;
    private int sourceID = -1;
    private int format, sampleRate;
    private boolean isPlaying;
    private float volume = 1;
    private float pan = 0;
    private float renderedSeconds, maxSecondsPerBuffer;

    private final boolean hasNoDevice;

    private boolean stoppedAtEnd = false;

    public float tempo = 1.0f;

    protected float snapOffset = 0; //Adjustment to getPosition when seeking while paused


    private Music.OnCompletionListener onCompletionListener;

    public PreloadedMp3 (OpenALLwjgl3Audio audio, FileHandle file) {
        super(audio, file);
        this.audio = audio;
        update = false;

        try
        {
            hasNoDevice = (boolean) noDevice.get(audio);
    
            if (hasNoDevice) return;
            bitstream = new PreloadMp3Bitstream(file.read(), file.length());

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
            if (!initialize(0))
                return;
        }

        if (!isPlaying) {
            if (stoppedAtEnd)
            {
                setPosition(0, false);
            }
            alSourcePlay(sourceID);
            isPlaying = true;
            update = true;
            snapOffset = 0;
        }
    }

    public boolean initialize(float offset)
    {
        if (sourceID == -1) {
            try
            {
                if (buffers == null) {
                    int errorCode = alGetError(); //Clear any existing errors
                    buffers = BufferUtils.createIntBuffer(bufferCount);
                    alGenBuffers(buffers);
                    errorCode = alGetError();
                    if (errorCode != AL_NO_ERROR)
                    {
                        buffers = null;
                        throw new GdxRuntimeException("Unable to allocate audio buffers. AL Error: " + errorCode);
                    }

                    //Track buffers
                    for (int i = 0; i < bufferCount; ++i)
                    {
                        bufferIDs[i] = buffers.get(i);
                    }
                    buffers.clear();
                    buffers.put(bufferIDs);
                    buffers.rewind();
                }

                sourceID = (int) obtainSource.invoke(audio, true);
                if (sourceID == -1) return false;

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

            //Successfully initialized
            TaikoEditor.audioMaster.addMusic(this);
            snapOffset = -offset; //Ensure starting position is at 0 to compensate for offset
        }
        return true;
    }

    public void stop () {
        if (hasNoDevice) return;
        if (sourceID == -1) return;
        try {
            freeSource.invoke(audio, sourceID);
            TaikoEditor.audioMaster.removeMusic(this);
            reset();
            sourceID = -1;
            isPlaying = false;
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void pause () {
        if (hasNoDevice) return;
        if (sourceID != -1) alSourcePause(sourceID);
        isPlaying = false;
        snapOffset = 0;
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
        stoppedAtEnd = false;

        update = false;

        alSourceStop(sourceID);
        while (alGetSourcei(sourceID, AL_BUFFERS_PROCESSED) > 0)
        {
            alSourceUnqueueBuffers(sourceID);
        }
        renderedSecondsQueue.clear();
        buffers.clear();
        buffers.put(bufferIDs); //Ensure buffer IDs are always the same 4
        buffers.rewind();

        //Move to closest frame
        renderedSeconds = bitstream.seekTime(position);
        /*
        //Move forward until the target position is within the next buffer to be read
        while (renderedSeconds < (position - maxSecondsPerBuffer)) {
            int length = read(tempBytes);
            if (length <= 0) {
                break;
            }
            renderedSeconds += maxSecondsPerBuffer * (float)length / (float)bufferSize; //Calculate the number of seconds this buffer has IGNORING tempo
        }*/
        renderedSecondsQueue.add(renderedSeconds);


        boolean filled = false;

        int i = 0;
        for (; i < bufferCount; i++) {
            int bufferID = buffers.get(i);
            if (!fill(bufferID)) break;
            filled = true;
            alSourceQueueBuffers(sourceID, bufferID); //Queue buffers as far as possible
        }
        for (; i < bufferCount; i++) {
            int bufferID = buffers.get(i);
            empty(bufferID);
            alSourceQueueBuffers(sourceID, bufferID);
        }


        if (!filled) { //if NO buffers were filled. This is fine.
            snapOffset = position - renderedSeconds;
            if (renderedSeconds + snapOffset > getLength() + maxSecondsPerBuffer)
            {
                snapOffset = getLength() + maxSecondsPerBuffer - renderedSeconds;
            }
            stoppedAtEnd = true;
            if (onCompletionListener != null) onCompletionListener.onCompletion(this);
        }
        else
        {
            snapOffset = position - renderedSeconds;

            renderedSecondsQueue.pop();

            alSourcef(sourceID, AL11.AL_SEC_OFFSET, snapOffset);

            if (wasPlaying) {
                alSourcePlay(sourceID);
                isPlaying = true;

                update = true;
            }
        }
    }

    @Override
    public float getPosition () {
        if (hasNoDevice) return 0;
        if (sourceID == -1) return 0;
        return (renderedSeconds + snapOffset + alGetSourcef(sourceID, AL11.AL_SEC_OFFSET) * tempo);
    }
    public double getPrecisePosition() {
        if (hasNoDevice) return 0;
        if (sourceID == -1) return 0;
        return ((double) renderedSeconds + snapOffset + alGetSourcef(sourceID, AL11.AL_SEC_OFFSET) * tempo);
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
        if (!update) return;
        if (isPlaying) snapOffset = 0;

        boolean end = false;
        int buffers = alGetSourcei(sourceID, AL_BUFFERS_PROCESSED);
        while (buffers-- > 0) {
            int bufferID = alSourceUnqueueBuffers(sourceID);
            if (bufferID == AL_INVALID_VALUE) break;
            //editorLogger.info("Unenqueued buffer " + bufferID);
            if (renderedSecondsQueue.size > 0) renderedSeconds = renderedSecondsQueue.pop();
            if (end)
                continue; //Stop filling buffers, but continue to unenqueue them all as there is nothing left to play.
            if (fill(bufferID))
            {
                alSourceQueueBuffers(sourceID, bufferID);
                //editorLogger.info("Queued buffer " + bufferID);
            }
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
        renderedSecondsQueue.insert(0, previousLoadedSeconds + currentBufferSeconds); //When this buffer is removed in update, time will be updated to the new calculated value.

        tempBuffer.put(tempBytes, 0, length).flip();

        alBufferData(bufferID, format, tempBuffer, (int) (sampleRate * tempo));

        return true;
    }
    private void empty (int bufferID) {
        tempBuffer.clear();
        alBufferData(bufferID, format, tempBuffer, (int) (sampleRate * tempo));
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