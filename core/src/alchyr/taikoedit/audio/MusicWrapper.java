package alchyr.taikoedit.audio;

import alchyr.taikoedit.audio.mp3.PreloadedMp3;
import alchyr.taikoedit.audio.ogg.PreloadOgg;
import alchyr.taikoedit.util.RunningAverage;
import alchyr.taikoedit.util.TrackedThread;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class MusicWrapper implements Music.OnCompletionListener {
    public float getProgress() {
        if (music == null || music.hasNoDevice)
            return 1;

        return music.loadProgress();
    }

    private static final Map<String, Float> baseOffsetMap = new HashMap<>();
    static {
        baseOffsetMap.put("mp3", -0.083f);
        baseOffsetMap.put("ogg", 0f);
    }
    public float activeOffset = 0;
    private static float getBaseOffset(CustomAudio music) {
        return music == null ? 0 : baseOffsetMap.getOrDefault(music.getAudioType(), 0f);
    }

    private transient boolean hasMusic = false; //has a successfully loaded track

    private CustomAudio music;
    private FileHandle musicFile;

    public double precise = -1; //The last value returned
    private double minimum = -1; //Minimum time, to prevent back-skipping on new time given

    //For mitigating forward-skipping
    private boolean initGap = false;
    private RunningAverage timeGap = new RunningAverage(1);
    private RunningAverage updateGap = new RunningAverage(16);
    private double catchup;

    private double time = -1; //Refresher to see if music has give a new value
    private double last = -1; //The last updated value obtained from music
    private boolean seeked = false;

    private double totalElapsed = 0, baseElapsed = 0;

    private boolean playing = false;

    private Object lockKey = null;

    public MusicWrapper()
    {
        music = null;
    }

    public boolean lock(Object key)
    {
        if (lockKey == null) //can't lock when something else already has
        {
            lockKey = key;
            return true;
        }
        return false;
    }
    public boolean unlock(Object key)
    {
        if (key.equals(lockKey))
        {
            lockKey = null;
            return true;
        }
        return false;
    }

    private final LinkedList<SuccessFailThread> loadingThreads = new LinkedList<>();
    public SuccessFailThread loadAsync(String songFile, Consumer<SuccessFailThread> followup) {
        loadingThreads.removeIf((t)->!t.isAlive());

        List<SuccessFailThread> old = Collections.emptyList();
        if (!loadingThreads.isEmpty()) {
            for (SuccessFailThread t : loadingThreads) {
                t.interrupt();
            }
            old = new ArrayList<>(loadingThreads);
        }

        List<SuccessFailThread> finalOld = old;
        SuccessFailThread loadingThread = new SuccessFailThread(()->{
            try {
                for (SuccessFailThread sibling : finalOld) {
                    if (sibling.isAlive()) {
                        editorLogger.info("Waiting for old load attempt to end.");
                        synchronized (sibling) {
                            sibling.setFollowup((t)->{
                                synchronized (sibling) {
                                    sibling.notify();
                                }
                            }, true);
                            while (sibling.isAlive()) {
                                sibling.wait(1000);
                            }
                        }
                    }
                }

                FileHandle file = Gdx.files.absolute(songFile);
                setMusic(file);
                if (file.exists()) {
                    prep();

                    if (!initialize()) {
                        dispose();
                    }
                }
                else {
                    dispose();
                }
            }
            catch (InterruptedException ignored) {
                editorLogger.info("Cancelled loading " + songFile);
                dispose();
            }
            catch (Exception e) {
                e.printStackTrace();
                dispose();
            }
        }, this::getProgress, ()->hasMusic);
        loadingThreads.add(loadingThread);
        loadingThread.setName("Loading " + songFile);
        loadingThread.setDaemon(true);
        loadingThread.setFollowup(followup, false);
        loadingThread.start();

        return loadingThread;
    }
    public void cancelAsyncFollowup() {
        for (SuccessFailThread t : loadingThreads)
            t.setFollowup(null, false);
    }

    public static class SuccessFailThread extends TrackedThread {
        final Supplier<Boolean> success;
        private Consumer<SuccessFailThread> followup;
        public SuccessFailThread(Runnable r, Supplier<Float> tracker, Supplier<Boolean> success) {
            super(r, tracker);

            this.success = success;
        }

        @Override
        public void run() {
            super.run();

            if (this.followup != null) {
                this.followup.accept(this);
            }
        }

        public boolean success() {
            return success.get();
        }
        public void setFollowup(Consumer<SuccessFailThread> r, boolean started) {
            if (started) {
                if (isAlive()) {
                    this.followup = r;
                }
                else if (r != null) {
                    r.accept(this);
                }
            }
            else {
                this.followup = r;
            }
        }
    }

    public boolean hasMusic() {
        return hasMusic;
    }
    public void setMusic(FileHandle handle)
    {
        if (this.music != null)
        {
            dispose();
        }

        playing = false;

        musicFile = handle;

        time = last = 0;
    }

    public void prep()
    {
        if (musicFile != null)
        {
            editorLogger.info("Prepping music.");
            try
            {
                switch (musicFile.extension().toLowerCase(Locale.ROOT)) {
                    case "mp3":
                        this.music = (PreloadedMp3) Gdx.audio.newMusic(musicFile);
                        music.setOnCompletionListener(this);
                        break;
                    case "ogg":
                        this.music = (PreloadOgg) Gdx.audio.newMusic(musicFile);
                        music.setOnCompletionListener(this);
                        break;
                    default:
                        editorLogger.error("Attempted to load unsupported audio type " + musicFile.extension() + ".");
                        return;
                }
                if (music == null) {
                    editorLogger.error("Failed to load music file: " + musicFile.path());
                }
                else if (music.hasNoDevice) {
                    editorLogger.error("No Audio Device");
                }
                else {
                    activeOffset = getBaseOffset(music);
                    music.preload();
                }
            }
            catch (Throwable e)
            {
                music = null;

                editorLogger.error("Failed to load music file: " + musicFile.path());
                e.printStackTrace();
            }
        }
        else {
            editorLogger.info("Attempted to prep no music file.");
        }
    }

    public boolean initialize() throws InterruptedException {
        if (this.music != null)
        {
            editorLogger.info("Initializing music.");
            CustomAudio tempMusic = this.music;
            if (tempMusic.getSourceId() == -1)
            {
                int retry = 5;

                while (retry > 0)
                {
                    try
                    {
                        if (tempMusic.initialize(activeOffset))
                        {
                            hasMusic = true;
                            initGap = true;
                            timeGap = new RunningAverage(64);
                            updateGap.init(0);
                            return true;
                        }
                        else
                        {
                            if (this.music.getSourceId() == -1) //Failed to get audio source.
                            {
                                editorLogger.info("Failed to obtain audio source.");
                            }
                            return false;
                        }
                        //Returning false means a non-audio buffer failure.
                    }
                    catch (GdxRuntimeException e)
                    {
                        //Failed to allocate audio buffers.
                        --retry;
                        editorLogger.info("Failed to allocate audio buffers. Attempts remaining: " + retry);
                        Thread.sleep(100);
                    }
                }
                return false;
            }
            hasMusic = true;
            return true;
        }
        editorLogger.info("Attempted to initialize null music.");
        return false;
    }

    public boolean isPlaying()
    {
        return playing = music != null && music.isPlaying();
    }

    public boolean noTrack()
    {
        return music == null;
    }

    public boolean update(double elapsed) {
        if (music != null) {
            time = music.getPrecisePosition();

            if (playing)
            {
                if (elapsed < 0.5f) {
                    totalElapsed += elapsed * music.tempo * (catchup + 1); //Keep track of passing time
                    baseElapsed += elapsed * music.tempo;
                    //editorLogger.info("Total elapsed time: " + baseElapsed);

                    precise = Math.max(last + totalElapsed, minimum);
                }
                if (time == last) //Music has not updated position, but the song is playing
                {
                    return false;
                }
                //editorLogger.info("Music updated position.");
                if (!seeked) {
                    double gap = time - last;
                    if (gap > 0 && gap < 0.1) {
                        if (initGap) {
                            if (updateGap.avg() == 0) {
                                updateGap.add(gap);
                            }
                            else {
                                updateGap.init(gap); //fill based on the second gap, which will occur during continuous play and is thus more reliable
                                initGap = false;
                            }
                        }
                        else {
                            updateGap.add(gap);
                        }
                    }

                    gap = time - (last + baseElapsed);
                    if (gap < 0.1 && gap > -0.1)
                        timeGap.add(gap);
                }
            }
            baseElapsed = 0;
            totalElapsed = 0;
            minimum = Long.MIN_VALUE;

            if (playing) {
                double avg = updateGap.avg();
                //editorLogger.debug("UpdateGap: " + avg + " | TimeGap: " + timeGap.avg());
                if (precise > time && precise - time < 0.1) {
                    minimum = precise;
                    if (!initGap && avg != 0) {
                        catchup = timeGap.avg() / avg;
                    }
                    //editorLogger.debug("A bit ahead. Catchup: " + catchup);
                }
                else if (precise < time && time - precise < 0.1) {
                    if (!initGap && avg != 0)
                        catchup = timeGap.avg() / avg;
                    //editorLogger.debug("A bit behind. Catchup: " + catchup);
                }
                else {
                    catchup = 0;
                    //editorLogger.debug("Very far off.");
                }
            }

            last = time;
            precise = Math.max(last, minimum);
        }
        seeked = false;
        return true;
    }
    public double getMsTime()
    {
        return getSecondTime() * 1000.0f;
    }
    public double getSecondTime()
    {
        return precise + activeOffset;
    }

    public double getMsLength()
    {
        return music.getLength() * 1000.0;
    }
    public float getSecondLength()
    {
        return music.getLength();
    }

    public void dispose()
    {
        if (music != null)
        {
            hasMusic = false;
            music.stop();

            music.dispose();
            music = null;
            editorLogger.info("Disposed music.");
        }

        playing = false;
    }

    public void toggle()
    {
        if (music.isPlaying())
        {
            pause();
            /*music.changeTempo(music.tempo + 0.5f, pos);*/
        }
        else
        {
            play();
        }
    }

    public void pause()
    {
        if (lockKey != null || music == null)
            return;
        music.pause();
        playing = false;
    }
    public void play()
    {
        if (lockKey != null || music == null)
            return;
        music.play();
        playing = true;
        last = time;
    }

    public void seekMs(double newPos)
    {
        seekSecond(newPos / 1000.0);
    }
    public void seekMs(double newPos, boolean continuePlaying)
    {
        if (!continuePlaying)
            playing = false;
        seekSecond(newPos / 1000.0, continuePlaying);
    }
    public void seekSecond(double newPos)
    {
        music.setPosition((float) Math.max(0, newPos) - activeOffset);
        seeked = true;
    }
    public void seekSecond(double newPos, boolean continuePlaying)
    {
        if (lockKey != null)
        {
            seekSecond(newPos);
            return;
        }
        if (!continuePlaying)
            playing = false;
        music.setPosition((float) Math.max(0, newPos) - activeOffset, continuePlaying);
        seeked = true;
    }

    public float setTempo(float newRate)
    {
        if (newRate < 0.1f)
            newRate = 0.1f;
        if (newRate > 2.0f)
            newRate = 2.0f;

        if (Math.abs(1 - newRate) < 0.001f) //clear out rounding errors whenever you return to 1
            newRate = 1;

        music.changeTempo(newRate, (float) precise);
        return newRate;
    }
    public float changeTempo(float add)
    {
        return setTempo(music.tempo + add);
    }
    public float getTempo()
    {
        return music.tempo;
    }

    public void modifyOffset(float change)
    {
        activeOffset += change;
        if (music != null)
            music.snapOffset -= change;
    }
    public void setOffset(float value) {
        float diff = activeOffset;
        activeOffset = getBaseOffset(music) + value;
        diff = activeOffset - diff;
        if (music != null)
            music.snapOffset += diff;
    }

    public float getOffset() {
        return activeOffset - getBaseOffset(music);
    }
    public int getDisplayOffset()
    {
        return Math.round((activeOffset - getBaseOffset(music)) * 1000);
    }

    @Override
    public void onCompletion(Music music) {
        playing = false;
    }
}
