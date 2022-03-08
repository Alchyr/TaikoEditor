package alchyr.taikoedit.management;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALLwjgl3Audio;
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALMusic;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;

// play sounds.
public class AudioMaster {
    private static final Logger logger = LogManager.getLogger("SoundMaster");

    private final HashMap<String, Sfx> map = new HashMap<>();
    //If a bunch of sound effect are played in a very short period (fractions of a second) reduce the volume of successive plays.
    private float audioFade = 1.0f; //multiplies volume.

    private static Field music;

    private HashSet<OpenALMusic> queuedAddMusic = new HashSet<>();
    private HashSet<OpenALMusic> queuedRemoveMusic = new HashSet<>();

    static {
        try {
            music = OpenALLwjgl3Audio.class.getDeclaredField("music");
            music.setAccessible(true);

        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    public AudioMaster()
    {

    }

    public void addSfx(String key, String file)
    {
        map.put(key, new Sfx(file));
    }
    public void removeSfx(String key)
    {
        Sfx s = map.remove(key);
        if (s != null)
            s.dispose();
    }

    public void addMusic(OpenALMusic track) {
        queuedAddMusic.add(track);
    }
    public void removeMusic(OpenALMusic track) {
        queuedRemoveMusic.add(track);
    }

    @SuppressWarnings("unchecked")
    public void setMusicVolume(float newVolume) {
        for (OpenALMusic m : queuedAddMusic)
            m.setVolume(newVolume);

        try {
            for (OpenALMusic m : (Array<OpenALMusic>) music.get(Gdx.audio))
                m.setVolume(newVolume);
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
    @SuppressWarnings("unchecked")
    public void update(float elapsed)
    {
        audioFade = Math.min(1, audioFade + elapsed * 50);
        if (!queuedAddMusic.isEmpty() || !queuedRemoveMusic.isEmpty())
        {
            try {
                Array<OpenALMusic> audioMusic = (Array<OpenALMusic>) music.get(Gdx.audio);

                for (OpenALMusic m : queuedAddMusic)
                    audioMusic.add(m);

                for (OpenALMusic m : queuedRemoveMusic)
                    audioMusic.removeValue(m, true);

                queuedAddMusic.clear();
                queuedRemoveMusic.clear();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * @param key key of the sound to play
     * @param pitch pitch multiplication, from 0.5 to 2
     * @param pan -1 to 1, panning left to right. 0 is center.
     * @param diminish whether or not this sound should cause audio to fade (and be affected by fade). Should be enabled for sounds that may play rapidly.
     */
    public long playSfx(String key, float volume, float pitch, float pan, boolean diminish)
    {
        long k = -1;
        Sfx s = map.get(key);
        if (s != null)
        {
            volume *= SettingsMaster.effectVolume * (diminish ? audioFade : 1);
            if (volume > 0)
            {
                k = s.play(volume, pitch, pan);
            }
            if (diminish)
                audioFade *= 0.6f;
        }
        else
        {
            logger.info("Sound " + key + " not found. Perhaps the key is misspelled or it is not loaded?");
        }
        return k;
    }

    public long playSfx(String key, float volume, float pitch, boolean diminish)
    {
        return playSfx(key, volume, pitch, 0, diminish);
    }

    public long playSfx(String key, float volume, boolean diminish)
    {
        return playSfx(key, volume, 1, 0, diminish);
    }

    public long playSfx(String key, float volume)
    {
        return playSfx(key, volume, 1, 0, false);
    }
    public long playSfx(String key)
    {
        return playSfx(key, 1, 1, 0, false);
    }


    public static class Sfx {
        private static final Logger logger = LogManager.getLogger(Sfx.class.getName());
        private String url;
        private Sound sound;

        public Sfx(String url) {
            this.sound = this.initSound(Gdx.files.internal(url));
        }

        public long play(float volume) {
            return this.sound != null ? this.sound.play(volume) : 0L;
        }

        public long play(float volume, float y, float z) {
            return this.sound != null ? this.sound.play(volume, y, z) : 0L;
        }

        public long loop(float volume) {
            return this.sound != null ? this.sound.loop(volume) : 0L;
        }

        public void setVolume(long id, float volume) {
            if (this.sound != null) {
                this.sound.setVolume(id, volume);
            }

        }// 61

        public void stop() {
            logger.info("stopping");
            if (this.sound != null) {
                this.sound.stop();
            }

        }// 68

        public void stop(long id) {
            if (this.sound != null) {
                this.sound.stop(id);
            }
        }

        private Sound initSound(FileHandle file) {
            if (this.sound == null) {
                if (file != null) {
                    if (Gdx.audio != null) {
                        return Gdx.audio.newSound(file);
                    } else {
                        logger.info("WARNING: Gdx.audio is null");
                        return null;
                    }
                } else {
                    logger.info("File: " + this.url + " was not found.");
                    return null;
                }
            } else {
                return this.sound;
            }
        }

        public void dispose()
        {
            if (this.sound != null)
            {
                this.sound.dispose();
                this.sound = null;
            }
        }
    }

}