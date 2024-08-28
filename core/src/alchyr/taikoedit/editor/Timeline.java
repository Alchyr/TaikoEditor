package alchyr.taikoedit.editor;

import alchyr.taikoedit.editor.maps.BreakInfo;
import alchyr.taikoedit.editor.maps.components.TimingPoint;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.util.EditorTime;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.util.structures.Pair;
import alchyr.taikoedit.util.structures.StackingTreeMap;
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
    private static final int MARK_HEIGHT = 9, SECTION_HEIGHT = MARK_HEIGHT * 2;
    private static final int TIMELINE_START = 150, TIMELINE_END = SettingsMaster.getWidth() - 50, TIMELINE_LENGTH = TIMELINE_END - TIMELINE_START;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.85f);
    private static final Color kiaiColor = new Color(240.0f/255.0f, 164.0f/255.0f, 66.0f/255.0f, 0.6f);
    private static final Color breakColor = new Color(0.7f, 0.7f, 0.7f, 0.5f);
    private static final Color bookmarkColor = new Color(0.25f, 0.4f, 1.0f, 1.0f);

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
    private DecimalFormat percentFormat = new DecimalFormat("##0.#%", osuDecimalFormat);

    private EditorBeatmap currentMap;
    private Set<Integer> bookmarks;
    private final List<Integer> bookmarkRenderPositions;

    private final Map<EditorBeatmap, Map<Long, MarkInfo>> timingPointPositions;
    private final Map<EditorBeatmap, StackingTreeMap.ExtendedStackingTreeMap<Integer, MarkInfo>> visiblePointPositions;

    public Timeline(int y, float length)
    {
        font = assetMaster.getFont("base:aller small");

        this.y = y;
        this.timeX = 20;
        this.textY = y + 18;
        this.lineY = y + HEIGHT / 2.0f - LINE_THICKNESS / 2.0f;
        this.tickY = y + HEIGHT / 2.0f - TICK_HEIGHT / 2.0f;
        this.bookmarkY = lineY - MARK_HEIGHT;
        this.length = length;

        minClickY = y + 3;
        maxClickY = y + HEIGHT - 3;

        time = new EditorTime();
        currentMap = null;
        bookmarks = null;
        bookmarkRenderPositions = new ArrayList<>();

        timingPointPositions = new HashMap<>();
        visiblePointPositions = new HashMap<>();

        this.pos = TIMELINE_START;
    }

    public MouseHoldObject click(float gameX, float gameY)
    {
        //if (button == Gdx)
        //{
        if (gameX >= TIMELINE_START && gameX <= TIMELINE_END && gameY >= minClickY && gameY <= maxClickY)
        {
            if (music.lock(this))
            {
                music.seekSecond(convertPosition(gameX));

                return new MouseHoldObject(this::drag, this::release);
            } //If it cannot be locked, something else has locked it. Timeline drag should probably not work in this situation.
        }
        //}
        return null;
    }

    private void drag(float gameX, float gameY)
    {
        music.seekSecond(convertPosition(gameX));
    }

    private boolean release(float gameX, float gameY)
    {
        music.unlock(this);
        return true;
    }

    public void update(double pos)
    {
        this.time.setMilliseconds(Math.round(pos * 1000));
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

        //Kiai/breaks, timing points
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
                    sb.draw(pix, start, bookmarkY, convertPercent((kiaiEntry.getKey() / 1000.0) / length) - start, SECTION_HEIGHT);
                }
            }
            kiaiIterator = null;

            sb.setColor(breakColor);
            for (BreakInfo breakSection : currentMap.getBreaks()) {
                start = convertPercent((breakSection.start / 1000.0) / length);
                sb.draw(pix, start, bookmarkY, convertPercent((breakSection.end / 1000.0) / length) - start, SECTION_HEIGHT);
            }

            for (List<MarkInfo> info : visiblePointPositions.get(currentMap).values()) {
                sb.setColor(info.get(0).c);
                sb.draw(pix, info.get(0).pos, lineY, 1, MARK_HEIGHT);
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
            currentMap.setTimeline(this);
            bookmarks = currentMap.getBookmarks(); //Same list. Changes will be reflected in the FullMapInfo itself.
            bookmarkRenderPositions.clear();
            for (int bookmark : bookmarks)
                bookmarkRenderPositions.add(convertPercent((bookmark / 1000.0) / length));

            if (!timingPointPositions.containsKey(map)) {
                calculateTimingPoints(map);
            }
        }
        else
        {
            bookmarks = null;
            bookmarkRenderPositions.clear();
        }
    }
    public void closeMap(EditorBeatmap map) {
        if (map.equals(currentMap)) {
            setMap(null);
        }
        timingPointPositions.remove(map);
        visiblePointPositions.remove(map);
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

    private void calculateTimingPoints(EditorBeatmap map) {
        TreeMap<Long, MarkInfo> linePositions = new TreeMap<>();
        StackingTreeMap.ExtendedStackingTreeMap<Integer, MarkInfo> visible = new StackingTreeMap.ExtendedStackingTreeMap<>();
        timingPointPositions.put(map, linePositions);
        visiblePointPositions.put(map, visible);
        int green, red;
        MarkInfo info;

        for (Map.Entry<Long, ArrayList<TimingPoint>> stack : map.allPoints.entrySet()) {
            red = 0;
            for (TimingPoint p : stack.getValue()) {
                if (p.uninherited) {
                    ++red;
                }
            }
            green = stack.getValue().size() - red;

            info = new MarkInfo(convertPercent((stack.getKey() / 1000.0) / length), red, green);
            linePositions.put(stack.getKey(), info);
            visible.add(info);
        }
    }

    public void updateTimingPoints(EditorBeatmap map,
                                   Iterable<? extends Map.Entry<Long,? extends List<?>>> added,
                                   Iterable<? extends Map.Entry<Long,? extends List<?>>> removed) {
        Map<Long, MarkInfo> mapInfo = timingPointPositions.get(map);
        MarkInfo info;
        int red, green;

        if (mapInfo != null) {
            StackingTreeMap.ExtendedStackingTreeMap<Integer, MarkInfo> visible = visiblePointPositions.get(map);

            if (added != null) {
                for (Map.Entry<Long, ? extends List<?>> stack : added) {
                    info = mapInfo.get(stack.getKey());
                    if (info == null) {
                        info = new MarkInfo(convertPercent((stack.getKey() / 1000.0) / length), 0, 0);
                        mapInfo.put(stack.getKey(), info);
                        visible.add(info);
                    }

                    red = 0;
                    for (Object o : stack.getValue()) {
                        if (((TimingPoint) o).uninherited) {
                            ++red;
                        }
                    }
                    green = stack.getValue().size() - red;

                    info.add(red, green);
                }
            }

            if (removed != null) {
                for (Map.Entry<Long, ? extends List<?>> stack : removed) {
                    info = mapInfo.get(stack.getKey());
                    if (info == null) {
                        continue;
                    }

                    red = 0;
                    for (Object o : stack.getValue()) {
                        if (((TimingPoint) o).uninherited) {
                            ++red;
                        }
                    }
                    green = stack.getValue().size() - red;

                    info.remove(red, green);

                    if (info.empty()) {
                        mapInfo.remove(stack.getKey());
                        visible.removeObject(info);
                    }
                }
            }
        }
    }

    private static class MarkInfo implements Comparable<MarkInfo>, StackingTreeMap.StackableComparable<Integer> {
        static final int RED = 1, GREEN = 2, YELLOW = 3;
        final int pos;
        private int red;
        private int green;
        Color c;

        MarkInfo(int pos, int red, int green) {
            this.pos = pos;
            this.red = red;
            this.green = green;
            updateColor();
        }

        void add(int red, int green) {
            this.red += red;
            this.green += green;
            updateColor();
        }
        void remove(int red, int green) {
            this.red -= red;
            this.green -= green;
            updateColor();
        }
        boolean empty() {
            return red + green <= 0;
        }

        private void updateColor() {
            switch ((red > 0 ? RED : 0) | (green > 0 ? GREEN : 0)) {
                case RED:
                    c = TimingPoint.RED;
                    break;
                case YELLOW:
                    c = TimingPoint.YELLOW;
                    break;
                default:
                    c = TimingPoint.GREEN;
                    break;
            }
        }

        @Override
        public int compareTo(MarkInfo o) {
            return Integer.compare(pos, o.pos);
        }

        @Override
        public Integer getKey() {
            return pos;
        }
    }
}
