package alchyr.taikoedit.audio;

import alchyr.taikoedit.util.structures.Pair;

import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

public class Waveform {
    //Given time x, where x is milliseconds, the floor entry in changePoints is the values at that point.
    private final TreeMap<Integer, Pair<Float, Float>> values; //min, max

    public Waveform(Iterator<byte[]> data, double samplesPerChunk, int channels) {
        values = new TreeMap<>();

        Pair<Float, Float> lastChunk = new Pair<>(0f, 0f), currentChunk = new Pair<>(0f, 0f);
        values.put(-1, lastChunk);

        //2 bytes per point
        //one point per channel per sample (2 channels = 2 points for 1 sample)
        //sampleRate = number of samples in one second
        int time = 0; //ms position of current chunk

        double chunkCounter = 0;
        float sample = 0;
        int channelCounter = 0;
        float overallMax = 0;
        short point = 0;

        boolean pos = true;
        while (data.hasNext()) {
            byte[] chunk = data.next();

            for (byte b : chunk) {
                if (pos) {
                    point = 0;
                    point |= b;
                }
                else {
                    point |= b << 8;

                    sample += point;
                    ++channelCounter;

                    if (channelCounter >= channels) {
                        channelCounter = 0;

                        sample /= channels;
                        if (sample > 0) {
                            currentChunk.b = Math.max(currentChunk.b, sample);
                        }
                        else {
                            currentChunk.a = Math.min(currentChunk.a, sample);
                        }
                        sample = 0;
                        ++chunkCounter;

                        if (chunkCounter >= samplesPerChunk) { //Done with chunk.
                            if (currentChunk.a != lastChunk.a || currentChunk.b != lastChunk.b) {
                                values.put(time, currentChunk);

                                overallMax = Math.max(overallMax, currentChunk.b);
                                overallMax = Math.max(overallMax, Math.abs(currentChunk.a));

                                lastChunk = currentChunk;
                                currentChunk = new Pair<>(0f, 0f);
                            }
                            else {
                                currentChunk.a = 0f;
                                currentChunk.b = 0f;
                            }

                            chunkCounter -= samplesPerChunk;
                            ++time;
                        }
                    }
                }
                pos = !pos;
            }
        }

        if (chunkCounter > 0) { //Leftover data
            values.put(time, currentChunk);
            overallMax = Math.max(overallMax, currentChunk.b);
            overallMax = Math.max(overallMax, Math.abs(currentChunk.a));
        }

        for (Pair<Float, Float> maxMin : values.values()) { //Limit to a 0-1 scale
            maxMin.a /= overallMax;
            maxMin.b /= overallMax;
        }
    }

    //start and end time in ms
    private int lastStart = -1, lastEnd = -1;
    private SortedMap<Integer, Pair<Float, Float>> currentView = null;
    public SortedMap<Integer, Pair<Float, Float>> getSection(int start, int end) {
        Integer floor = values.floorKey(start);
        if (floor != null) start = floor;

        if (start != lastStart || end != lastEnd)
        {
            lastStart = start;
            lastEnd = end;
            currentView = values.subMap(start, true, end, true);
        }
        return currentView;
    }

    public boolean isEmpty() {
        return values == null || values.isEmpty();
    }
}
