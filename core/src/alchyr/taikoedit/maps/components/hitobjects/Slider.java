package alchyr.taikoedit.maps.components.hitobjects;

import alchyr.taikoedit.maps.components.HitObject;
import alchyr.taikoedit.util.structures.Pair;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.List;

public class Slider extends HitObject {
    public static Texture headTexture;

    private static final Color slider = Color.GOLDENROD.cpy();

    //x,y,time,type,hitSound,curveType|curvePoints,slides,length,edgeSounds,edgeSets,hitSample

    private SliderProperties sliderProperties;

    private String[] edgeSounds, edgeSets;

    public Slider(String[] params)
    {
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


    private static final class SliderProperties
    {
        private final char curveType;
        private final List<Pair<Integer, Integer>> sliderPoints;
        private int repeatCount = 1;
        private double length = 140;

        //length / (SliderMultiplier * 100) * beatLength * repeatCount = duration

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
        public int getDuration(double beatLength, double sliderMultiplier)
        {
            return (int) (length / sliderMultiplier * 100 * beatLength * repeatCount);
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr, float pos, float viewScale, float x, float y) {
        sb.setColor(slider);
        if (finish)
        {
            sb.draw(headTexture, x + (this.pos - pos) * viewScale - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                    LARGE_SCALE, LARGE_SCALE, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);
        }
        else
        {
            sb.draw(headTexture, x + (this.pos - pos) * viewScale - CIRCLE_OFFSET, y - CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_OFFSET, CIRCLE_SIZE, CIRCLE_SIZE,
                    1.0f, 1.0f, 0, 0, 0, CIRCLE_SIZE, CIRCLE_SIZE, false, false);
        }
    }
}
