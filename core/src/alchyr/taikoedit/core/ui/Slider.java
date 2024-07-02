package alchyr.taikoedit.core.ui;

import alchyr.taikoedit.core.input.BindingGroup;
import alchyr.taikoedit.core.input.MouseHoldObject;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static alchyr.taikoedit.TaikoEditor.*;
import static alchyr.taikoedit.util.GeneralUtils.oneDecimal;

public class Slider {
    private static final float HEIGHT = 20, MARK_WIDTH = 12;
    private final float left, markX, centerX, right, y, lineY, textY, bottom, width;

    private final float min, max;
    private final String minText, middleText, maxText;
    private final TreeMap<Float, Float> impreciseValues, preciseValues;

    private float value;

    private Texture pix, slider;
    private BitmapFont font;

    private Consumer<Float> onValueChange = null;

    public Slider(float centerX, float y, float width, float min, float max, float imprecise, float precise)
    {
        this.centerX = centerX;
        this.y = y;
        this.lineY = y - 1;
        this.bottom = y - HEIGHT / 2;
        this.textY = bottom - 8;
        this.width = width;
        this.left = centerX - width / 2;
        this.right = left + width;
        this.markX = left - MARK_WIDTH / 2;
        this.max = max;
        this.min = min;

        this.value = (max + min) / 2.0f;
        minText = oneDecimal.format(min);
        middleText = oneDecimal.format(this.value);
        maxText = oneDecimal.format(max);

        impreciseValues = new TreeMap<>();
        preciseValues = new TreeMap<>();

        int count = Math.round((max - min) / precise);
        for (int i = 0; i <= count; ++i) {
            float f = min + (((float)i / count) * max);
            preciseValues.put(left + getOffset(f), f);
        }

        count = Math.round((max - min) / imprecise);
        for (int i = 0; i <= count; ++i) {
            float f = min + (((float)i / count) * max);
            impreciseValues.put(left + getOffset(f), f);
        }

        pix = assetMaster.get("ui:pixel");
        slider = assetMaster.get("ui:slider");
        font = assetMaster.getFont("base:aller small");
    }
    public Slider onValueChange(Consumer<Float> receiver) {
        onValueChange = receiver;
        if (onValueChange != null)
            onValueChange.accept(this.value);
        return this;
    }

    public void setValue(float val)
    {
        if (val < min)
            val = min;
        if (val > max)
            val = max;
        this.value = val;

        if (onValueChange != null)
            onValueChange.accept(this.value);
    }
    public void setPos(float newPos) {
        TreeMap<Float, Float> src = BindingGroup.shift() ? preciseValues : impreciseValues;

        Map.Entry<Float, Float> higher = src.ceilingEntry(newPos);
        Map.Entry<Float, Float> lower = src.floorEntry(newPos);

        if (higher == null && lower == null)
            return;

        if (higher == null) {
            setValue(lower.getValue());
            return;
        }

        if (lower == null) {
            setValue(higher.getValue());
            return;
        }

        float highDist = higher.getKey() - newPos;
        float lowDist = newPos - lower.getKey();

        if (lowDist <= highDist) {
            setValue(lower.getValue());
        }
        else {
            setValue(higher.getValue());
        }
    }
    private float getOffset() {
        return getOffset(value);
    }
    private float getOffset(float val) {
        return ((val - min) / (max - min)) * width;
    }

    public void render(SpriteBatch sb, ShapeRenderer sr)
    {
        sb.setColor(Color.WHITE);
        sb.draw(pix, left, y, width, 3);

        sb.draw(slider, markX + getOffset(), bottom, 12, 20);

        textRenderer.renderTextCentered(sb, minText, left, textY);
        textRenderer.renderTextCentered(sb, middleText, centerX, textY);
        textRenderer.renderTextCentered(sb, maxText, right, textY);
    }

    private final SliderHold hold = new SliderHold(this);
    public MouseHoldObject click(float x, float y) {
        //if clicking on draggy thingy, return drag mouse hold object thing
        if (y < bottom || y > bottom + HEIGHT)
            return null;

        float f = left + getOffset() - MARK_WIDTH * 0.75f;
        if (x >= f && x <= f + MARK_WIDTH * 1.5f) {
            return hold.prep(x, left + getOffset());
        }

        //Otherwise, set value and return null
        setPos(x);
        return null;
    }



    private static class SliderHold extends MouseHoldObject
    {
        private final Slider s;
        private float dragStart, sliderStart;

        public SliderHold(Slider s)
        {
            super(null, null);

            this.s = s;
            dragStart = 0;
            sliderStart = 0;
        }
        public SliderHold prep(float mouseStart, float sliderStart) {
            this.dragStart = mouseStart;
            this.sliderStart = sliderStart;
            return this;
        }

        @Override
        public void update(float elapsed) {
            float targetPoint = sliderStart + (Gdx.input.getX() - dragStart);
            //Find closest snap to this new offset
            s.setPos(targetPoint);
        }
    }
}
