package alchyr.taikoedit.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class MusicWrapper {
    private Music music;
    private FileHandle musicFile;

    public float time = -1;
    public float last = -1;

    private float totalElapsed = 0;

    private boolean playing = false;

    public MusicWrapper()
    {
        music = null;
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
        if (musicFile != null)
        {
            try
            {
                this.music = Gdx.audio.newMusic(musicFile);
            }
            catch (Throwable e)
            {
                music = null;

                editorLogger.error("Failed to load music file: " + musicFile.path());
                e.printStackTrace();
            }
        }
    }


    public boolean noTrack()
    {
        return music == null;
    }

    public int getMsTime(float elapsed)
    {
        time = music.getPosition();

        if (playing)
        {
            totalElapsed += elapsed; //Keep track of passing time

            if (time == last) //Music has not updated position, but the song is playing
            {
                return (int) ((time + totalElapsed) * 1000);
            }
        }

        totalElapsed = 0;
        last = time;
        return (int) (time * 1000);
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
            music.pause();
            playing = false;
        }
        else
        {
            music.play();
            playing = true;
            last = time;
        }
    }
}
