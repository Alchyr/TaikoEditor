package alchyr.diffcalc.live.taiko.difficulty.preprocessing;

import alchyr.diffcalc.live.DifficultyHitObject;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.colour.TaikoColourData;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.rhythm.TaikoRhythmData;
import alchyr.diffcalc.live.taiko.difficulty.utils.HasInterval;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.editor.maps.components.hitobjects.Hit;
import alchyr.taikoedit.util.GeneralUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaikoDifficultyHitObject extends DifficultyHitObject implements HasInterval {
    private final List<TaikoDifficultyHitObject> monoDificultyHitObjects;
    public final int monoIndex;
    private final List<TaikoDifficultyHitObject> noteDifficultyHitObjects;
    public final int noteIndex;
    public final TaikoColourData colourData;
    public final TaikoRhythmData rhythmData;
    public double effectiveBpm;

    public double[] debugData = new double[3];

    public TaikoDifficultyHitObject(HitObject base, HitObject previous, double clockRate, List<DifficultyHitObject> difficultyHitObjects, List<TaikoDifficultyHitObject> centreObjects, List<TaikoDifficultyHitObject> rimObjects, List<TaikoDifficultyHitObject> noteObjects, int index, EditorBeatmap beatmap) {
        super(base, previous, clockRate, difficultyHitObjects, index);

        noteDifficultyHitObjects = noteObjects;

        colourData = new TaikoColourData();
        rhythmData = new TaikoRhythmData(this);

        if (base instanceof Hit) {
            if (((Hit) base).isRim()) {
                monoIndex = rimObjects.size();
                rimObjects.add(this);
                monoDificultyHitObjects = rimObjects;
            }
            else {
                monoIndex = centreObjects.size();
                centreObjects.add(this);
                monoDificultyHitObjects = centreObjects;
            }

            noteIndex = noteObjects.size();
            noteObjects.add(this);
        }
        else {
            monoDificultyHitObjects = null;
            monoIndex = 0;
            noteIndex = 0;
        }

        Map.Entry<Long, ArrayList<TimingPoint>> timing = beatmap.timingPoints.floorEntry(base.getPos());
        Map.Entry<Long, ArrayList<TimingPoint>> sv = beatmap.effectPoints.floorEntry(base.getPos());
        double bpm = timing == null ? 120 : GeneralUtils.listLast(timing.getValue()).getBPM();
        double rate = beatmap.getBaseSV() * (sv == null ? 1 : GeneralUtils.listLast(sv.getValue()).value);
        effectiveBpm = bpm * clockRate * rate;
    }

    @Override
    public double getInterval() {
        return deltaTime;
    }

    public TaikoDifficultyHitObject previousMono(int backwardsIndex) {
        int index = monoIndex - (backwardsIndex + 1);
        if (index < 0 || index >= monoDificultyHitObjects.size()) return null;
        return monoDificultyHitObjects.get(index);
    }

    public TaikoDifficultyHitObject nextMono(int forwardsIndex) {
        int index = monoIndex + (forwardsIndex + 1);
        if (index < 0 || index >= monoDificultyHitObjects.size()) return null;
        return monoDificultyHitObjects.get(index);
    }

    public TaikoDifficultyHitObject previousNote(int backwardsIndex) {
        int index = noteIndex - (backwardsIndex + 1);
        if (index < 0 || index >= noteDifficultyHitObjects.size()) return null;
        return noteDifficultyHitObjects.get(index);
    }

    public TaikoDifficultyHitObject nextNote(int forwardsIndex) {
        int index = noteIndex + (forwardsIndex + 1);
        if (index < 0 || index >= noteDifficultyHitObjects.size()) return null;
        return noteDifficultyHitObjects.get(index);
    }
}