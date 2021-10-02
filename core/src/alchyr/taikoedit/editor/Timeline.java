package alchyr.taikoedit.editor;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.EditorTime;
import alchyr.taikoedit.util.input.MouseHoldObject;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class Timeline {
    public static final int HEIGHT = 30;

    private static final int LINE_THICKNESS = 1;
    private static final int TICK_HEIGHT = 19;
    private static final int MARK_HEIGHT = 9;
    private static final int TIMELINE_START = 150, TIMELINE_END = SettingsMaster.getWidth() - 50, TIMELINE_LENGTH = TIMELINE_END - TIMELINE_START;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.85f);

    private BitmapFont font;
    private Texture pix = assetMaster.get("ui:pixel");

    private final int y;
    private final int timeX;
    private final int textY;
    private final double length;
    private final float lineY;
    private final float bookmarkY;
    private float tickY;

    private float minClickY, maxClickY, percentage;

    private EditorTime time;
    private int pos;
    private DecimalFormat percentFormat = new DecimalFormat("##0.#%");

    private MouseHoldObject holdObject;

    private EditorBeatmap currentMap;
    private Set<Integer> bookmarks;
    private final List<Integer> bookmarkRenderPositions;

    public Timeline(int y, float length)
    {
        font = assetMaster.getFont("aller small");

        this.y = y;
        this.timeX = 20;
        this.textY = y + 18;
        this.lineY = y + HEIGHT / 2.0f - LINE_THICKNESS / 2.0f;
        this.tickY = y + HEIGHT / 2.0f - TICK_HEIGHT / 2.0f;
        this.bookmarkY = lineY - MARK_HEIGHT;
        this.length = length;

        minClickY = y + 3;
        maxClickY = y + HEIGHT - 3;

        holdObject = new MouseHoldObject(this::drag, this::release);

        time = new EditorTime();
        currentMap = null;
        bookmarks = null;
        bookmarkRenderPositions = new ArrayList<>();

        this.pos = TIMELINE_START;
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

    public void update(double pos)
    {
        this.time.setMilliseconds((int) (pos * 1000));
        this.percentage = (float) (pos / length);
        this.pos = convertPercent(this.percentage);
    }

    private int convertPercent(double percentage)
    {
        return TIMELINE_START + (int) (percentage * (TIMELINE_LENGTH));
    }
    private double convertPosition(int position)
    {
        return MathUtils.clamp((float) (position - TIMELINE_START) * length / TIMELINE_LENGTH, 0, length);
    }

    public String getTimeString()
    {
        return time.toString();
    }

    public void render(SpriteBatch sb, ShapeRenderer sr)
    {
        sb.setColor(backColor);
        sb.draw(pix, 0, y, SettingsMaster.getWidth(), HEIGHT);

        //Timeline
        sb.setColor(Color.WHITE);
        sb.draw(pix, TIMELINE_START, lineY, TIMELINE_LENGTH, LINE_THICKNESS);

        //Bookmarks
        sb.setColor(Color.BLUE);
        for (int i : bookmarkRenderPositions)
        {
            sb.draw(pix, i, bookmarkY, 1, MARK_HEIGHT);
        }

        //Current time marker
        sb.setColor(Color.WHITE);
        sb.draw(pix, pos - 1, tickY, 2, TICK_HEIGHT);

        textRenderer.setFont(font).renderText(sb, time.toString(), 20, textY).renderText(sb, percentFormat.format(percentage), 100, textY);
    }

    public void setMap(EditorBeatmap map) {
        currentMap = map;
        if (currentMap != null)
        {
            bookmarks = currentMap.getBookmarks(); //Same list. Changes will be reflected in the FullMapInfo itself.
            bookmarkRenderPositions.clear();
            for (int bookmark : bookmarks)
                bookmarkRenderPositions.add(convertPercent((bookmark / 1000.0) / length));
        }
        else
        {
            bookmarks = null;
            bookmarkRenderPositions.clear();
        }
    }

    public void recalculateBookmarks()
    {
        if (bookmarks != null)
        {
            bookmarkRenderPositions.clear();
            for (int bookmark : bookmarks)
                bookmarkRenderPositions.add(convertPercent((bookmark / 1000.0) / length));
        }
    }
}
