package alchyr.taikoedit.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class MusicWrapper implements Music.OnCompletionListener {
    public static float OFFSET = -0.085f;

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
                return precise + OFFSET;
            }
        }

        totalElapsed = 0;
        precise = last = time;
        return time + OFFSET;
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
        if (lockKey != null)
        {
            seekMs(newPos);
            return;
        }
        music.setPosition(newPos / 1000.0f);
    }
    public void seekMs(int newPos, boolean continuePlaying)
    {
        if (!continuePlaying)
            playing = false;
        music.setPosition(newPos / 1000.0f, continuePlaying);
    }
    public void seekSecond(float newPos)
    {
        music.setPosition(newPos);
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
        music.setPosition(newPos, continuePlaying);
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

    @Override
    public void onCompletion(Music music) {
        playing = false;
        //music.setPosition(); set position to end? Cancel the reset? hm
    }
}
