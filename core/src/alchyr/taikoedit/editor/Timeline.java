package alchyr.taikoedit.editor;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.EditorTime;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.util.structures.Pair;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.text.DecimalFormat;
import java.util.*;

import static alchyr.taikoedit.TaikoEditor.*;

public class Timeline {
    public static final int HEIGHT = 30;

    private static final int LINE_THICKNESS = 1;
    private static final int TICK_HEIGHT = 19;
    private static final int MARK_HEIGHT = 9;
    private static final int TIMELINE_START = 150, TIMELINE_END = SettingsMaster.getWidth() - 50, TIMELINE_LENGTH = TIMELINE_END - TIMELINE_START;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.85f);
    private static final Color kiaiColor = new Color(240.0f/255.0f, 164.0f/255.0f, 66.0f/255.0f, 0.8f);
    private static final Color breakColor = new Color(0.7f, 0.7f, 0.7f, 0.5f);
    private static final Color bookmarkColor = new Color(0.25f, 0.25f, 0.95f, 1.0f);

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
    private DecimalFormat percentFormat = new DecimalFormat("##0.#%", osuSafe);

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

    public MouseHoldObject click(float gameX, float gameY)
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

    private void drag(float gameX, float gameY)
    {
        EditorLayer.music.seekSecond(convertPosition(gameX));
    }

    private boolean release(float gameX, float gameY)
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
    private double convertPosition(float position)
    {
        return MathUtils.clamp((position - TIMELINE_START) * length / TIMELINE_LENGTH, 0, length);
    }

    public String getTimeString()
    {
        return time.toString();
    }


    private Iterator<Map.Entry<Long, Boolean>> kiaiIterator;
    private Map.Entry<Long, Boolean> kiaiEntry;
    public void render(SpriteBatch sb, ShapeRenderer sr)
    {
        sb.setColor(backColor);
        sb.draw(pix, 0, y, SettingsMaster.getWidth(), HEIGHT);

        //Kiai/breaks
        if (currentMap != null) {
            sb.setColor(kiaiColor);
            kiaiIterator = currentMap.getKiai().entrySet().iterator();
            boolean kiai = false;
            int start = TIMELINE_START;
            while (kiaiIterator.hasNext()) {
                kiaiEntry = kiaiIterator.next();

                if (!kiai && (kiai = kiaiEntry.getValue())) {
                    //kiai started
                    start = convertPercent((kiaiEntry.getKey() / 1000.0) / length);
                }
                else if (kiai && !(kiai = kiaiEntry.getValue())) {
                    sb.draw(pix, start, lineY, convertPercent((kiaiEntry.getKey() / 1000.0) / length) - start, MARK_HEIGHT);
                }
            }
            kiaiIterator = null;

            sb.setColor(breakColor);
            for (Pair<Long, Long> breakSection : currentMap.getBreaks()) {
                start = convertPercent((breakSection.a / 1000.0) / length);
                sb.draw(pix, start, lineY, convertPercent((breakSection.b / 1000.0) / length) - start, MARK_HEIGHT);
            }
        }

        //Timeline
        sb.setColor(Color.WHITE);
        sb.draw(pix, TIMELINE_START, lineY, TIMELINE_LENGTH, LINE_THICKNESS);

        //Bookmarks
        sb.setColor(bookmarkColor);
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
