package alchyr.taikoedit.util;

public class EditorTime implements Comparable<EditorTime> {
    private long minutes;
    private long seconds;
    private long milliseconds;

    public long getMinutes() {
        return minutes;
    }

    public void setMinutes(long minutes) {
        //int hours = minutes / 60;
        this.minutes = minutes;
    }

    public long getSeconds() {
        return seconds;
    }

    public void setSeconds(long seconds) {
        long minutes = seconds / 60;
        this.seconds = seconds % 60;
        setMinutes(minutes);
    }

    public long getMilliseconds() {
        return milliseconds;
    }

    public void setMilliseconds(long milliseconds) {
        long seconds = milliseconds / 1000;
        this.milliseconds = milliseconds % 1000;
        setSeconds(seconds);
    }

    public long getTotalMs() {
        return (minutes * 60 + seconds) * 1000 + milliseconds;
    }

    public EditorTime()
    {
        milliseconds = 0;
        seconds = 0;
        minutes = 0;
    }

    public EditorTime(long milliseconds)
    {
        setMilliseconds(milliseconds);
    }

    public EditorTime(long seconds, long milliseconds)
    {
        this(milliseconds);
        setSeconds(seconds);
    }
    public EditorTime(long minutes, long seconds, long milliseconds)
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
        return Long.compare(getTotalMs(), o.getTotalMs());
    }

    @Override
    public String toString() {
        return String.format("%02d:%02d:%03d", minutes, seconds, milliseconds);
    }
}
