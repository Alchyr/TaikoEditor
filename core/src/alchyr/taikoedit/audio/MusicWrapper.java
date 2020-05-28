package alchyr.taikoedit.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class MusicWrapper implements Music.OnCompletionListener {
    private static final float BASE_OFFSET = -0.083f;
    public float offset = BASE_OFFSET;

    private PreloadedMp3 music;
    private FileHandle musicFile;

    public float precise = -1; //The last value returned
    public float time = -1; //Refresher to see if music has give a new value
    public float last = -1; //The last updated value obtained from music

    private float totalElapsed = 0;

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

    public void setMusic(FileHandle handle)
    {
        if (this.music != null)
        {
            music.stop();

            this.music.dispose();
            this.music = null;
        }

        playing = false;

        musicFile = handle;

        time = last = 0;
    }

    public void prep()
    {
        if (musicFile != null && musicFile.extension().equals("mp3"))
        {
            try
            {
                this.music = (PreloadedMp3) Gdx.audio.newMusic(musicFile);
                music.setOnCompletionListener(this);
            }
            catch (Throwable e)
            {
                music = null;

                editorLogger.error("Failed to load music file: " + musicFile.path());
                e.printStackTrace();
            }
        }
        else if (musicFile != null)
        {
            editorLogger.error("Attempted to load a non-mp3 music file with MusicWrapper.");
        }
    }

    public boolean initialize() throws InterruptedException {
        if (this.music != null)
        {
            if (this.music.getSourceId() == -1)
            {
                int retry = 5;

                while (retry > 0)
                {
                    try
                    {
                        if (this.music.initialize(offset))
                        {
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
            return true;
        }
        return false;
    }

    public boolean isPlaying()
    {
        return playing = music.isPlaying();
    }

    public boolean noTrack()
    {
        return music == null;
    }

    public int getMsTime(float elapsed)
    {
        return (int) (getSecondTime(elapsed) * 1000);
    }
    public float getSecondTime(float elapsed)
    {
        time = music.getPosition();

        if (playing)
        {
            totalElapsed += elapsed; //Keep track of passing time

            if (time == last) //Music has not updated position, but the song is playing
            {
                precise = (time + (totalElapsed) * music.tempo);
                return precise + offset;
            }
        }

        totalElapsed = 0;
        precise = last = time;
        return time + offset;
    }

    public int getMsLength()
    {
        return (int) (music.getLength() * 1000);
    }
    public float getSecondLength()
    {
        return music.getLength();
    }

    public void dispose()
    {
        if (music != null)
        {
            music.stop();

            music.dispose();
            music = null;
            editorLogger.info("Music disposed.");
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
        if (lockKey != null)
            return;
        music.pause();
        playing = false;
    }
    public void play()
    {
        if (lockKey != null)
            return;
        music.play();
        playing = true;
        last = time;
    }

    public void seekMs(int newPos)
    {
        seekSecond(newPos / 1000.0f);
    }
    public void seekMs(int newPos, boolean continuePlaying)
    {
        if (!continuePlaying)
            playing = false;
        seekSecond(newPos / 1000.0f, continuePlaying);
    }
    public void seekSecond(float newPos)
    {
        music.setPosition(Math.max(0, newPos) - offset);
    }
    public void seekSecond(float newPos, boolean continuePlaying)
    {
        if (lockKey != null)
        {
            seekSecond(newPos);
            return;
        }
        if (!continuePlaying)
            playing = false;
        music.setPosition(Math.max(0, newPos) - offset, continuePlaying);
    }

    public void setTempo(float newTempo)
    {
        if (newTempo < 0.5f)
            newTempo = 0.5f;
        if (newTempo > 1.5f)
            newTempo = 1.5f;

        music.changeTempo(newTempo, precise);
    }
    public void changeTempo(float add)
    {
        setTempo(music.tempo + add);
    }

    public void modifyOffset(float change)
    {
        offset += change;
        if (music != null)
            music.snapOffset -= change;
    }
    public int getDisplayOffset()
    {
        return (int) ((offset - BASE_OFFSET) * 1000);
    }

    @Override
    public void onCompletion(Music music) {
        playing = false;
        //music.setPosition(); set position to end? Cancel the reset? hm
    }
}
