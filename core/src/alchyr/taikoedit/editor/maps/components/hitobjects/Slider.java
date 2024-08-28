package alchyr.taikoedit.editor.maps.components.hitobjects;

import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.maps.components.ILongObject;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.structures.Pair;
import alchyr.taikoedit.util.structures.MapObject;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import static alchyr.taikoedit.TaikoEditor.osuDecimalFormat;
import static alchyr.taikoedit.management.assets.skins.Skins.currentSkin;

public class Slider extends HitObject implements ILongObject {
    private static final Color slider = Color.GOLDENROD.cpy();

    //x,y,time,type,hitSound,curveType|curvePoints,slides,length,edgeSounds,edgeSets,hitSample

    private SliderProperties sliderProperties;
    private long duration;
    private double preciseDuration;
    private long endPos;
    private String[] edgeSounds, edgeSets;

    public Slider(long start, long duration)
    {
        this.type = HitObjectType.SLIDER;

        super.setPos(start);
        this.preciseDuration = this.duration = duration;
        this.endPos = start + this.duration;
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
        edgeSounds = null;
        edgeSets = null;
    }

    public Slider(long start, double duration)
    {
        this.type = HitObjectType.SLIDER;

        super.setPos(start);
        this.preciseDuration = duration;
        this.duration = Math.round(this.preciseDuration);
        this.endPos = start + this.duration;
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
        edgeSounds = null;
        edgeSets = null;
    }

    public Slider(Slider base)
    {
        this.type = HitObjectType.SLIDER;
        setPos(base.getPrecisePos());
        this.x = base.x;
        this.y = base.y;
        this.newCombo = base.newCombo;
        this.colorSkip = base.colorSkip;
        this.normal = base.normal;
        this.whistle = base.whistle;
        this.finish = base.finish;
        this.clap = base.clap;
        this.duration = base.duration;
        this.preciseDuration = base.preciseDuration;
        this.endPos = base.endPos;

        if (base.edgeSounds != null)
        {
            this.edgeSounds = new String[base.edgeSounds.length];
            System.arraycopy(base.edgeSounds, 0, edgeSounds, 0, base.edgeSounds.length);
        }
        else
        {
            this.edgeSounds = null;
        }

        if (base.edgeSets != null)
        {
            this.edgeSets = new String[base.edgeSets.length];
            System.arraycopy(base.edgeSets, 0, edgeSets, 0, base.edgeSets.length);
        }
        else
        {
            this.edgeSets = null;
        }

        if (base.hitSample != null)
        {
            this.hitSample = new int[base.hitSample.length];
            System.arraycopy(base.hitSample, 0, hitSample, 0, base.hitSample.length);
        }
        else
        {
            this.hitSample = null;
        }

        this.sliderProperties = base.sliderProperties.copy();
    }

    public Slider(String[] params)
    {
        type = HitObjectType.SLIDER;
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
                    setPos(Double.parseDouble(params[i]));
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
                    hitSample = new int[Math.min(samples.length, 4)];
                    for (int n = 0; n < hitSample.length; ++n)
                    {
                        hitSample[n] = Integer.parseInt(samples[n]);
                    }
                    if (samples.length > 4) {
                        StringBuilder sb = new StringBuilder();
                        for (int s = 4; s < samples.length; ++s) {
                            sb.append(samples[s]);
                        }
                        sampleFile = sb.toString();
                    }
            }
        }
    }

    @Override
    public void setPos(long newPos) {
        super.setPos(newPos);
        endPos = getPos() + duration;
        //ALSO: Update sv based on sv at new position to recalculate duration
        //EDIT: ALSO, DON'T do this. Duration will be fixed. Having to mess with sv to keep sliders the right duration is a pain.
    }

    @Override
    public long getDuration() {
        return duration;
    }
    @Override
    public long getEndPos() {
        return endPos;
    }
    @Override
    public void setDuration(long duration) {
        this.duration = duration;
        this.endPos = this.getPos() + this.duration;
        this.preciseDuration = this.endPos - this.getPrecisePos();
    }
    @Override
    public void setEndPos(long endPos) {
        this.endPos = endPos;
        this.duration = this.endPos - this.getPos();
        this.preciseDuration = this.endPos - this.getPrecisePos();
    }

    public void calculateDuration(double beatLength, double sliderMultiplier) //Should only be used when reading a map from file.
    {
        this.preciseDuration = (sliderProperties.length / (sliderMultiplier * 100.0) * beatLength * sliderProperties.repeatCount);
        this.duration = Math.round(preciseDuration);
        this.endPos = this.getPos() + duration;
    }
    private double calculateLength(double beatLength, double sliderMultiplier) //Should only be used when reading a map from file.
    {
        return (this.preciseDuration * sliderMultiplier * 100.0) / (sliderProperties.repeatCount * beatLength);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y, float alpha) {
        if (testHidden()) return;

        slider.a = alpha;
        float startX = 1 + x + (float) (this.getPos() - pos) * viewScale;
        float endX = 1 + x + (float) (this.endPos - pos) * viewScale;
        sb.setColor(slider);
        slider.a = 1;
        float scale = finish ? currentSkin.largeScale : currentSkin.normalScale;

        if ((selected && duration < 0) || duration > 0)
        {
            float bodyStart = Math.max(0, startX), bodyEnd = Math.min(SettingsMaster.getWidth(), endX);
            currentSkin.body.renderC(sb, sr, (bodyStart + bodyEnd) / 2f, y, (bodyEnd - bodyStart) / currentSkin.body.getWidth(), scale, 0, slider);
            //sb.draw(body, startX, y - (CIRCLE_OFFSET * scale), endX - startX, CIRCLE_SIZE * scale);
            currentSkin.end.renderC(sb, sr, endX, y, scale, slider);
            /*sb.draw(circle, endX - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                    scale, scale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);*/
        }
        currentSkin.hit.renderC(sb, sr, startX, y, scale, slider);
        /*sb.draw(circle, startX - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                scale, scale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);*/

        if (selected)
        {
            renderSelection(sb, sr, pos, viewScale, x, y);
        }
    }

    @Override
    public void gameplayRender(SpriteBatch sb, ShapeRenderer sr, float sv, float baseX, float x, int y, float alpha) {
        x += baseX;

        slider.a = alpha;
        float startX = 1 + x;
        float endX = 1 + x + (float) (this.endPos - getPos()) * sv;
        sb.setColor(slider);
        float scale = finish ? currentSkin.largeScale : currentSkin.normalScale;

        if (duration > 0)
        {
            float bodyStart = Math.max(0, startX), bodyEnd = Math.min(SettingsMaster.getWidth(), endX);
            currentSkin.body.renderC(sb, sr, (bodyStart + bodyEnd) / 2f, y, (bodyEnd - bodyStart) / currentSkin.body.getWidth(), scale, 0, slider);
            //sb.draw(body, startX, y - (CIRCLE_OFFSET * scale), endX - startX, CIRCLE_SIZE * scale);
        }
        currentSkin.end.renderC(sb, sr, endX, y, scale, slider);
        currentSkin.hit.renderC(sb, sr, startX, y, scale, slider);
        /*sb.draw(circle, endX - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                scale, scale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);
        sb.draw(circle, startX - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                scale, scale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);*/

        if (selected)
        {
            renderSelection(sb, sr, getPos(), sv, x, y);
        }
    }

    @Override
    public void renderSelection(SpriteBatch sb, ShapeRenderer sr, double pos, float viewScale, float x, float y) {
        if (testHidden()) return;

        sb.setColor(Color.WHITE);
        float scale = finish ? currentSkin.largeScale : currentSkin.normalScale;

        currentSkin.selection.renderC(sb, sr, 1 + x + (float) (this.endPos - pos) * viewScale, y, scale);
        currentSkin.selection.renderC(sb, sr, 1 + x + (float) (this.getPos() - pos) * viewScale, y, scale);
        /*sb.draw(currentSkin.selection, (x + (float) (this.endPos - pos) * viewScale) - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                scale, scale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);*/
        /*sb.draw(currentSkin.selection, (x + (float) (this.getPos() - pos) * viewScale) - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                scale, scale, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);*/
    }

    @Override
    public String toString() {
        return x + "," + y + "," + limitedDecimals.format(getPrecisePos()) + "," + getTypeFlag() + "," + getHitsoundFlag() + "," + sliderProperties.toString() + (edgeSounds == null || edgeSounds.length == 0 ? "" : (
               edgeSamples() + "," + getHitSamples()));
    }
    public String toString(double beatLength, double sliderMultiplier) {
        return x + "," + y + "," + limitedDecimals.format(getPrecisePos()) + "," + getTypeFlag() + "," + getHitsoundFlag() + "," + sliderProperties.toString(calculateLength(beatLength, sliderMultiplier)) + (edgeSounds == null || edgeSounds.length == 0 ? "" : (
                edgeSamples() + "," + getHitSamples()));
    }

    private String edgeSamples() {
        StringBuilder sb = new StringBuilder(",");
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

        private SliderProperties(char curveType)
        {
            this.curveType = curveType;
            sliderPoints = new ArrayList<>();
        }
        private SliderProperties copy()
        {
            SliderProperties copy = new SliderProperties(this.curveType);
            copy.sliderPoints.clear();
            copy.sliderPoints.addAll(this.sliderPoints);
            copy.length = this.length;
            copy.repeatCount = this.repeatCount;
            return copy;
        }

        public void setLength(double length)
        {
            this.length = length;
        }

        //Cuts off like one decimal places from doubles, as due to the method of storage for sliders they are innately imprecise
        private static final DecimalFormat limitedDecimals = new DecimalFormat("##0.############", osuDecimalFormat);
        @Override
        public String toString()
        {
            return curveType + "|" + curvePoints() + "," + repeatCount + "," + limitedDecimals.format(length);
        }

        public String toString(double actualLength)
        {

            return curveType + "|" + curvePoints() + "," + repeatCount + "," + limitedDecimals.format(actualLength);
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

    @Override
    public MapObject shiftedCopy(long newPos) {
        Slider copy = new Slider(this);
        copy.setPos(newPos);
        return copy;
    }
}
