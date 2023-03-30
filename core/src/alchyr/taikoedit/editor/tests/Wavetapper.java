package alchyr.taikoedit.editor.tests;

import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.util.GeneralUtils;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import static alchyr.taikoedit.TaikoEditor.editorLogger;

public class Wavetapper {
    private static final long DON_TIME = 158195, KAT_TIME = 156588;

    //Step 1:
    //Formula for position of the don/kat relative to time.
    //This is easy; already have this in gameplay view.
    //Next, formula for a barline that has that position at the given time, with sv equal to the sv at the time.



    /* Gimmicks
     * First - Introduce common gimmick. Single lines don when it reaches the don.
     * Thick lines kat when it reaches the kat.
     *
     * Phone dialing - sliderhead stuff?
     *
     * Slow section - A second don and kat appears. The slow background rhythm uses barlines that line up with both.
     * For the slow don/kat themselves, use two more faster barlines to mark their timing more readably.
     */

    private static final double ARBITRARY_FACTOR = 100;

    public static void generate(EditorBeatmap map) {
        try {
            float baseSV = map.getBaseSV();
            double svRate = baseSV, currentBPM;


            final TreeMap<Long, Float> svMap = new TreeMap<>();
            genSVMap(svMap, map);


            HitObject don = map.objects.get(DON_TIME).get(0);
            HitObject kat = map.objects.get(KAT_TIME).get(0);
            double donSv = svAtTime(map, DON_TIME);
            double katSv = svAtTime(map, KAT_TIME);
        }
        catch (Exception e) {
            GeneralUtils.logStackTrace(editorLogger, e);
        }
    }

    private static void genSVMap(TreeMap<Long, Float> svMap, EditorBeatmap map) {

    }

    private static double svAtTime(EditorBeatmap map, long time) {
        Map.Entry<Long, ArrayList<TimingPoint>> timing = map.timingPoints.floorEntry(time);
        if (timing == null)
            return map.getBaseSV();

        Map.Entry<Long, ArrayList<TimingPoint>> effect = map.effectPoints.floorEntry(time);
        if (effect == null || timing.getKey() > effect.getKey()) {
            return map.getBaseSV() / timing.getValue().get(timing.getValue().size() - 1).value;
        }
        else {
            return (map.getBaseSV() * effect.getValue().get(effect.getValue().size() - 1).value) / timing.getValue().get(timing.getValue().size() - 1).value;
        }
    }
}
