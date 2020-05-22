package alchyr.taikoedit.editor;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.input.MouseHoldObject;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class Timeline {
    public static final int HEIGHT = 30;

    private static final int LINE_THICKNESS = 3;
    private static final int TICK_HEIGHT = 19;
    private static final int TIMELINE_START = 150, TIMELINE_END = SettingsMaster.getWidth() - 50, TIMELINE_LENGTH = TIMELINE_END - TIMELINE_START;

    private BitmapFont font;
    private Texture pix = assetMaster.get("ui:pixel");

    private final float length;
    private final float lineY;
    private float tickY;

    private float minClickY, maxClickY;

    private int pos;

    private MouseHoldObject holdObject;

    public Timeline(int y, float length)
    {
        font = assetMaster.getFont("aller small");

        this.lineY = y + HEIGHT / 2.0f - 1.5f;
        this.tickY = y + HEIGHT / 2.0f - TICK_HEIGHT / 2.0f;
        this.length = length;

        minClickY = y + 5;
        maxClickY = y + HEIGHT - 5;

        holdObject = new MouseHoldObject(this::drag, this::release);
    }

    public MouseHoldObject click(int gameX, int gameY, int pointer, int button)
    {
        //if (button == Gdx)
        //{
        if (gameX >= TIMELINE_START && gameX <= TIMELINE_END && gameY >= minClickY && gameY <= maxClickY)
        {
            if (EditorLayer.music.lock(this))
            {
                EditorLayer.music.seekSecond(convertPosition(gameX));

                return holdObject;
            } //If it cannot be locked, something else has locked it. Timeline drag should probably not work in this situation.
        }
        //}
        return null;
    }

    private void drag(int gameX, int gameY)
    {
        EditorLayer.music.seekSecond(convertPosition(gameX));
    }

    private boolean release(int gameX, int gameY)
    {
        EditorLayer.music.unlock(this);
        return true;
    }

    public void update(float pos)
    {
        this.pos = convertPercent(pos / length);
    }

    private int convertPercent(float percentage)
    {
        return TIMELINE_START + (int) (percentage * (TIMELINE_LENGTH));
    }
    private float convertPosition(int position)
    {
        return MathUtils.clamp((float) (position - TIMELINE_START) * length / TIMELINE_LENGTH, 0, length);
    }

    public void render(SpriteBatch sb, ShapeRenderer sr)
    {
        sb.setColor(Color.WHITE.cpy());
        sb.draw(pix, TIMELINE_START, lineY, TIMELINE_LENGTH, LINE_THICKNESS);
        sb.draw(pix, pos - 1, tickY, 2, TICK_HEIGHT);
    }
}
