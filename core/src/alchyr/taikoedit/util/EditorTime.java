package alchyr.taikoedit.util;

public class EditorTime implements Comparable<EditorTime> {
    private int minutes;
    private int seconds;
    private int milliseconds;

    public int getMinutes() {
        return minutes;
    }

    public void setMinutes(int minutes) {
        //int hours = minutes / 60;
        this.minutes = minutes;
    }

    public int getSeconds() {
        return seconds;
    }

    public void setSeconds(int seconds) {
        int minutes = seconds / 60;
        this.seconds = seconds % 60;
        setMinutes(minutes);
    }

    public int getMilliseconds() {
        return milliseconds;
    }

    public void setMilliseconds(int milliseconds) {
        int seconds = milliseconds / 1000;
        this.milliseconds = milliseconds % 1000;
        setSeconds(seconds);
    }

    public int getTotalMs() {
        return (minutes * 60 + seconds) * 1000 + milliseconds;
    }

    public EditorTime()
    {
        milliseconds = 0;
        seconds = 0;
        minutes = 0;
    }

    public EditorTime(int milliseconds)
    {
        setMilliseconds(milliseconds);
    }

    public EditorTime(int seconds, int milliseconds)
    {
        this(seconds);
        setMinutes(minutes);
    }
    public EditorTime(int minutes, int seconds, int milliseconds)
    {
        this(seconds, milliseconds);
        setMinutes(minutes);
    }

    public EditorTime(EditorTime copy) {
        this(copy.minutes, copy.seconds, copy.milliseconds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EditorTime time = (EditorTime) o;
        return getMinutes() == time.getMinutes() &&
                getSeconds() == time.getSeconds() &&
                getMilliseconds() == time.getMilliseconds();
    }

    @Override
    public int compareTo(EditorTime o) {
        return getTotalMs() - o.getTotalMs();
    }

    @Override
    public String toString() {
        return String.format("%02d:%02d:%03d", minutes, seconds, milliseconds);
    }
}
