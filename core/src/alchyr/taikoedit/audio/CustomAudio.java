package alchyr.taikoedit.audio;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALLwjgl3Audio;
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALMusic;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL11;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT;

public abstract class CustomAudio extends OpenALMusic {
    //Breaking the rules :(
    protected static Method obtainSource;
    protected static Method freeSource;
    protected static Field noDevice;

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

    //Audio stuff
    //constants
    protected static final int bufferSize = 4096 * 4;
    protected static final int bufferCount = 4;
    protected static final int bytesPerSample = 2;
    protected static final byte[] tempBytes = new byte[bufferSize];
    protected static final ByteBuffer tempBuffer = BufferUtils.createByteBuffer(bufferSize);

    //For access
    protected OpenALLwjgl3Audio audio;

    //playback
    protected final int[] bufferIDs = new int[bufferCount];
    protected final Set<Integer> bufferTracker = new TreeSet<>(), activeBuffers = new HashSet<>();
    protected final FloatArray renderedSecondsQueue = new FloatArray(bufferCount);
    protected IntBuffer buffers;
    protected int sourceID = -1;
    protected int format, sampleRate;
    protected boolean isPlaying;
    protected float volume = SettingsMaster.getMusicVolume();
    protected float pan = 0;
    protected float renderedSeconds, maxSecondsPerBuffer;

    protected boolean stoppedAtEnd = false; //For restarting from beginning if playing from end

    protected Music.OnCompletionListener onCompletionListener;


    public boolean hasNoDevice;


    public float tempo = 1.0f;
    public float snapOffset = 0; //Should be used as adjustment to getPosition when seeking while paused


    public CustomAudio(OpenALLwjgl3Audio audio, FileHandle file) {
        super(audio, file);

        this.audio = audio;
    }

    public FileHandle getFile() {
        return file;
    }

    public abstract String getAudioType();

    public abstract void preload();

    public void stop() {
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

    public void pause() {
        if (hasNoDevice) return;
        if (sourceID != -1) alSourcePause(sourceID);
        isPlaying = false;
        snapOffset = 0;
    }

    public boolean isPlaying() {
        if (hasNoDevice) return false;
        if (sourceID == -1) return false;
        return isPlaying;
    }

    public void setVolume(float volume) {
        this.volume = volume;
        if (hasNoDevice) return;
        if (sourceID != -1) alSourcef(sourceID, AL_GAIN, volume);
    }

    public float getVolume() {
        return this.volume;
    }

    public void setPan(float pan, float volume) {
        this.volume = volume;
        this.pan = pan;
        if (hasNoDevice) return;
        if (sourceID == -1) return;
        alSource3f(sourceID, AL_POSITION, MathUtils.cos((pan - 1) * MathUtils.PI / 2), 0,
                MathUtils.sin((pan + 1) * MathUtils.PI / 2));
        alSourcef(sourceID, AL_GAIN, volume);
    }

    @Override
    public void setPosition(float position)
    {
        setPosition(position, true);
    }

    public void setPosition(float position, boolean continuePlay) {
        if (hasNoDevice) return;
        if (sourceID == -1) return;

        boolean wasPlaying = isPlaying && continuePlay;
        isPlaying = false;
        stoppedAtEnd = false;

        //update = false;

        alSourceStop(sourceID);
        alSourcei(sourceID, AL_BUFFER, 0); //Detach all buffers

        renderedSecondsQueue.clear();
        buffers.clear();
        buffers.put(bufferIDs); //Ensure buffer IDs are always the same 4
        buffers.rewind();

        /*
        Original method: move forward until the target position is within the next buffer to be read
        while (renderedSeconds < (position - maxSecondsPerBuffer)) {
            int length = read(tempBytes);
            if (length <= 0) {
                break;
            }
            renderedSeconds += maxSecondsPerBuffer * (float)length / (float)bufferSize; //Calculate the number of seconds this buffer has IGNORING tempo
        }*/

        //New method: Move to the closest frame with custom method
        renderedSeconds = seekTime(position);
        renderedSecondsQueue.add(renderedSeconds);


        boolean filled = false;

        activeBuffers.clear();
        for (int buffer : bufferIDs)
            bufferTracker.add(buffer);

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
                snapOffset = 0;

                //update = true;
            }
        }
    }

    @Override
    protected void setup(int channels, int sampleRate) {
        this.format = channels > 1 ? AL_FORMAT_STEREO16 : AL_FORMAT_MONO16;
        this.sampleRate = sampleRate;
        maxSecondsPerBuffer = (float)bufferSize / (bytesPerSample * channels * sampleRate);
    }

    @Override
    public void play() {
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
            //update = true;
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

                activeBuffers.clear();
                for (int buffer : bufferIDs)
                    bufferTracker.add(buffer);

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


    //same as getPosition method of Music, but should use doubles for math and return a double
    abstract public float getLength();
    abstract protected float seekTime(float pos);


    public void changeTempo(float newTempo, float position)
    {
        tempo = newTempo;

        setPosition(position);
    }

    public void stopAtEnd () {
        pause();
        stoppedAtEnd = true;
    }

    @Override
    public void update () {
        if (hasNoDevice) return;
        if (sourceID == -1) return;
        //if (!update) return;
        if (isPlaying) snapOffset = 0; else return;

        boolean end = false;
        while (alGetSourcei(sourceID, AL_BUFFERS_PROCESSED) > 0) {
            int bufferID = alSourceUnqueueBuffers(sourceID);
            if (bufferID == AL_INVALID_VALUE) break;

            if (renderedSecondsQueue.size > 0) renderedSeconds = renderedSecondsQueue.pop();

            if (activeBuffers.add(bufferID)) {
                if (activeBuffers.size() == bufferCount)
                    activeBuffers.clear();
            }
            else {
                //saw same buffer twice without seeing all buffers.
                //Not all buffers are queued/same buffer queued more than once

                //Same buffer queued more than once = do not requeue this buffer and continue
                //Not all buffers queued = queue the missing buffer
                TaikoEditor.editorLogger.info("Buffer error occurred: " + bufferID);
                continue;
            }

            //TaikoEditor.editorLogger.info("Unenqueued buffer " + bufferID);
            if (end)
                continue; //Stop filling buffers, but continue to unenqueue them all as there is nothing left to play.
            if (fill(bufferID))
            {
                alSourceQueueBuffers(sourceID, bufferID);
                //TaikoEditor.editorLogger.info("Queued buffer " + bufferID);
            }
            else { //no more data
                end = true;
            }
        }

        if (alGetSourcei(sourceID, AL_BUFFERS_QUEUED) == 0) {
            if (end) {
                stopAtEnd();
                if (onCompletionListener != null) onCompletionListener.onCompletion(this);
            }
            else {
                //Weird buffer problem. Re-queue all buffers.
                int i = 0;
                boolean filled = false;
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
                if (!filled) {
                    stopAtEnd();
                    if (onCompletionListener != null) onCompletionListener.onCompletion(this);
                }
            }
        }

        // A buffer underflow will cause the source to stop.
        if (isPlaying && alGetSourcei(sourceID, AL_SOURCE_STATE) != AL_PLAYING) alSourcePlay(sourceID);
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

        if (length > tempBuffer.remaining()) {
            TaikoEditor.editorLogger.error("temp audio buffer not enough space. Needed: " + length + " Remaining: " + tempBuffer.remaining());
        }
        else {
            tempBuffer.put(tempBytes, 0, length).flip();
            alBufferData(bufferID, format, tempBuffer, (int) (sampleRate * tempo));
        }

        return true;
    }
    private void empty (int bufferID) {
        tempBuffer.clear();
        alBufferData(bufferID, format, tempBuffer, (int) (sampleRate * tempo));
    }

    public void dispose () {
        stop();
        if (hasNoDevice) return;
        if (buffers == null) return;
        alDeleteBuffers(buffers);
        buffers = null;
        onCompletionListener = null;
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
        return ((double) renderedSeconds + snapOffset + alGetSourcef(sourceID, AL11.AL_SEC_OFFSET) * (double) tempo);
    }

    public int getChannels () {
        return format == AL_FORMAT_STEREO16 ? 2 : 1;
    }

    public int getRate () {
        return sampleRate;
    }

    public int getSourceId () {
        return sourceID;
    }

    public void setOnCompletionListener (OnCompletionListener listener) {
        onCompletionListener = listener;
    }

    public abstract float loadProgress();
}
