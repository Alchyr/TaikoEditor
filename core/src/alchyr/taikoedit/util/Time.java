package alchyr.taikoedit.util;

public class Time implements Comparable<Time> {
    private int hours;
    private int minutes;

    public int getHours() {
        return hours;
    }

    public void setHours(int hours) {
        this.hours = hours;
    }

    public int getMinutes() {
        return minutes;
    }

    public void setMinutes(int minutes) {
        int hours = minutes / 60;
        this.minutes = minutes % 60;
        setHours(hours);
    }

    public int getSeconds() {
        return seconds;
    }

    public int getTotalSeconds() {
        return (hours * 60 + minutes) * 60 + seconds;
    }

    public void setSeconds(int seconds) {
        int minutes = seconds / 60;
        this.seconds = seconds % 60;
        setMinutes(minutes);
    }

    private int seconds;

    public Time(int seconds)
    {
        setSeconds(seconds);
    }
    public Time(int minutes, int seconds)
    {
        this(seconds);
        setMinutes(minutes);
    }
    public Time(int hours, int minutes, int seconds)
    {
        this(minutes, seconds);
        setHours(hours);
    }

    public Time(Time copy) {
        this(copy.hours, copy.minutes, copy.seconds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Time time = (Time) o;
        return getHours() == time.getHours() &&
                getMinutes() == time.getMinutes() &&
                getSeconds() == time.getSeconds();
    }

    @Override
    public int compareTo(Time o) {
        return getTotalSeconds() - o.getTotalSeconds();
    }

    @Override
    public String toString() {
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }
}
