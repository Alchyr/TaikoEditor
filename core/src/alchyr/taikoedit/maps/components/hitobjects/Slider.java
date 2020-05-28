package alchyr.taikoedit.maps.components.hitobjects;

import alchyr.taikoedit.maps.components.HitObject;
import alchyr.taikoedit.maps.components.ILongObject;
import alchyr.taikoedit.util.structures.Pair;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.List;

public class Slider extends HitObject implements ILongObject {
    private static final Color slider = Color.GOLDENROD.cpy();

    //x,y,time,type,hitSound,curveType|curvePoints,slides,length,edgeSounds,edgeSets,hitSample

    private SliderProperties sliderProperties;
    private int duration;
    private int endPos;
    private String[] edgeSounds, edgeSets;

    public Slider(int start, int duration)
    {
        this.type = HitType.SLIDER;

        this.pos = start;
        this.duration = duration;
        this.endPos = this.pos + this.duration;
        this.x = 0;
        this.y = 384;
        this.newCombo = true;

        this.sliderProperties = new SliderProperties();

        colorSkip = 0;

        normal = false;
        whistle = false;
        finish = false;
        clap = false;

        hitSample = null;
    }

    public Slider(String[] params)
    {
        type = HitType.SLIDER;
        for (int i = 0; i < params.length; ++i) //to avoid out of bounds.
        {
            switch (i)
            {
                case 0:
                    x = Integer.parseInt(params[i]);
                    break;
                case 1:
                    y = Integer.parseInt(params[i]);
                    break;
                case 2:
                    pos = Integer.parseInt(params[i]);
                    break;
                case 3:
                    int objectType = Integer.parseInt(params[i]);

                    newCombo = (objectType & NEWCOMBO) > 0;
                    colorSkip = (objectType & COLORSKIP) >>> 4;
                    break;
                case 4:
                    int hitSound = Integer.parseInt(params[i]);
                    normal = (hitSound & NORMAL) > 0;
                    whistle = (hitSound & WHISTLE) > 0;
                    finish = (hitSound & FINISH) > 0;
                    clap = (hitSound & CLAP) > 0;
                    break;
                case 5:
                    //Object parameters
                    String[] sliderData = params[i].split("\\|");
                    sliderProperties = new SliderProperties(sliderData);
                    break;
                case 6:
                    sliderProperties.repeatCount = Integer.parseInt(params[i]);
                    break;
                case 7:
                    sliderProperties.setLength(Double.parseDouble(params[i]));
                    //TODO: Use getDuration of sliderProperties to get slider duration
                    break;
                case 8:
                    //Edge sounds
                    edgeSounds = params[i].split("\\|");
                    break;
                case 9:
                    //Edge sets
                    edgeSets = params[i].split("\\|");
                    break;
                default:
                    //Should be the last index, which *should* be colon separated list for hitsamples
                    String[] samples = params[i].split(":");
                    hitSample = new int[samples.length];
                    for (int n = 0; n < samples.length; ++n)
                    {
                        hitSample[n] = Integer.parseInt(samples[n]);
                    }
            }
        }
    }

    @Override
    public void setPosition(int newPos) {
        super.setPosition(newPos);
        endPos = pos + duration;
        //ALSO: Update sv based on sv at new position to recalculate duration
        //EDIT: ALSO, DON'T do this. Duration will be fixed. Having to mess with sv to keep sliders the right duration is a pain.
    }

    @Override
    public int getDuration() {
        return duration;
    }
    @Override
    public int getEndPos() {
        return endPos;
    }
    @Override
    public void setDuration(int duration) {
        this.duration = duration;
        this.endPos = this.pos + this.duration;
    }
    @Override
    public void setEndPos(int endPos) {
        this.endPos = endPos;
        this.duration = this.endPos - this.pos;
    }

    public void calculateDuration(double beatLength, double sliderMultiplier) //Should only be used when reading a map from file.
    {
        this.duration = (int) (sliderProperties.length / (sliderMultiplier * 100.0) * beatLength * sliderProperties.repeatCount);
        this.endPos = this.pos + duration;
    }
    private double calculateLength(double beatLength, double sliderMultiplier) //Should only be used when reading a map from file.
    {
        return (this.duration * sliderMultiplier * 100.0) / (sliderProperties.repeatCount * beatLength);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr, int pos, float viewScale, float x, float y, float alpha) {
        slider.a = alpha;
        float startX = x + (this.pos - pos) * viewScale;
        float endX = x + (this.endPos - pos) * viewScale;
        sb.setColor(slider);
        float scale = finish ? LARGE_SCALE : 1.0f;

        if (duration > 0)
        {
            sb.draw(body, startX, y - (CIRCLE_OFFSET * scale), endX - startX, CIRCLE_SIZE * scale);
        }
        sb.draw(circle, endX - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                scale, scale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);
        sb.draw(circle, startX - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                scale, scale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);

        if (selected)
        {
            renderSelection(sb, sr, pos, viewScale, x, y);
        }
    }

    @Override
    public void renderSelection(SpriteBatch sb, ShapeRenderer sr, int pos, float viewScale, float x, float y) {
        sb.setColor(Color.WHITE);
        float scale = finish ? LARGE_SCALE : 1.0f;

        sb.draw(selection, (x + (this.endPos - pos) * viewScale) - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                scale, scale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);
        sb.draw(selection, (x + (this.pos - pos) * viewScale) - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                scale, scale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);
    }

    @Override
    public String toString() {
        return x + "," + y + "," + pos + "," + getTypeFlag() + "," + getHitsoundFlag() + "," + sliderProperties.toString() + (edgeSounds == null || edgeSounds.length == 0 ? "" : (
               edgeSamples() + "," + getHitSamples()));
    }
    public String toString(double beatLength, double sliderMultiplier) {
        return x + "," + y + "," + pos + "," + getTypeFlag() + "," + getHitsoundFlag() + "," + sliderProperties.toString(calculateLength(beatLength, sliderMultiplier)) + (edgeSounds == null || edgeSounds.length == 0 ? "" : (
                edgeSamples() + "," + getHitSamples()));
    }

    private String edgeSamples() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (; i < edgeSounds.length - 1; ++i)
        {
            sb.append(edgeSounds[i]).append("|");
        }
        sb.append(edgeSounds[i]).append(",");
        for (i = 0; i < edgeSets.length - 1; ++i)
        {
            sb.append(edgeSets[i]).append("|");
        }
        sb.append(edgeSets[i]);

        return sb.toString();
    }




    private static final class SliderProperties
    {
        //curveType|curvePoints,slides,length,edgeSounds,edgeSets
        private final char curveType;
        private final List<Pair<Integer, Integer>> sliderPoints;
        private int repeatCount = 1;
        private double length = 140;

        //length / (SliderMultiplier * 100) * beatLength * repeatCount = duration

        public SliderProperties()
        {
            curveType = 'L';

            sliderPoints = new ArrayList<>();
            sliderPoints.add(new Pair<>(512, 384));
        }
        public SliderProperties(String[] data)
        {
            curveType = data[0].charAt(0);

            sliderPoints = new ArrayList<>();

            for (int i = 1; i < data.length; ++i)
            {
                String[] point = data[i].split(":");
                sliderPoints.add(new Pair<>(Integer.parseInt(point[0]), Integer.parseInt(point[1])));
            }
        }

        public void setLength(double length)
        {
            this.length = length;
        }

        @Override
        public String toString()
        {
            return curveType + "|" + curvePoints() + "," + repeatCount + "," + length;
        }

        public String toString(double actualLength)
        {

            return curveType + "|" + curvePoints() + "," + repeatCount + "," + actualLength;
        }

        private String curvePoints()
        {
            StringBuilder sb = new StringBuilder();

            int i = 0;
            for (; i < sliderPoints.size() - 1; ++i)
            {
                sb.append(sliderPoints.get(i).a).append(":").append(sliderPoints.get(i).b).append("|");
            }
            sb.append(sliderPoints.get(i).a).append(":").append(sliderPoints.get(i).b);

            return sb.toString();
        }
    }
}
