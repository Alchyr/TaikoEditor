package alchyr.taikoedit.editor.views;

import alchyr.diffcalc.live.taiko.TaikoDifficultyCalculator;
import alchyr.diffcalc.live.taiko.TaikoDifficultyAttributes;
import alchyr.diffcalc.live.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.editor.tools.Toolset;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.structures.MapObject;
import alchyr.taikoedit.util.structures.MapObjectTreeMap;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;

import static alchyr.taikoedit.TaikoEditor.*;
import static alchyr.taikoedit.core.layers.EditorLayer.viewScale;

public class DifficultyView extends MapView {
    public static final String ID = "diff";
    @Override
    public String typeString() {
        return ID;
    }

    public static final int HEIGHT = 220;
    private static final int MARGIN = 40;
    public static final int GRAPH_AREA = HEIGHT - MARGIN;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.7f);
    private static final Color lineColor = new Color(0.35f, 0.35f, 0.38f, 0.4f);

    //Base position values
    private int baseTextY = 0;

    //Offset
    private final BitmapFont font;

    private double lastSounded;

    private long maxPos = 1000;

    private final Map<HitObject, TaikoDifficultyHitObject> difficultyInfo = new HashMap<>();
    private TaikoDifficultyAttributes attributes;

    private static double maxDensity = 10;
    public double localMaxDensity = 10;
    private String maxDensityString = "Max Objects Per Second: 10";
    private final double[] density;
    private final Color densityColor = new Color(0f, 0f, 0.15f, 1f);


    private enum DifficultyMode {
        STRAINS,
        DENSITY
    }

    private DifficultyMode mode = DifficultyMode.STRAINS;


    public DifficultyView(EditorLayer parent, EditorBeatmap beatmap) {
        super(ViewType.DIFFICULTY_VIEW, parent, beatmap, HEIGHT);
        lastSounded = 0;

        font = assetMaster.getFont("base:aller small");

        addOverlayButton(new ImageButton(assetMaster.get("editor:exit"), assetMaster.get("editor:exith")).setClick(this::close).setAction("Close View"));
        addOverlayButton(new ImageButton(assetMaster.get("editor:mode"), assetMaster.get("editor:modeh")).setClick(this::swapMode).setAction("Swap Modes"));

        int sections = SettingsMaster.getWidth() / 20;
        density = new double[sections];

        calculateDifficulty();
    }

    @Override
    public void onGameplayChange() {
        TaikoEditor.onMain(this::calculateDifficulty);
    }

    private void calculateDifficulty() {
        attributes = (TaikoDifficultyAttributes) TaikoDifficultyCalculator.calculateDifficulty(map, difficultyInfo);

        maxPos = (long) Math.ceil(music.getSecondLength() * 1000);
        Long last = map.objects.lastKey();
        if (last != null && last > maxPos) maxPos = last;

        double step = (double) maxPos / density.length;
        double nextStep = step;
        int sectionIndex = 0;
        int objectCount = 0;

        maxDensity = 10;
        for (EditorBeatmap map : parent.getActiveMaps()) {
            ViewSet set = parent.getViewSet(map);
            if (set == null) continue;
            for (MapView view : set.getViews()) {
                if (view instanceof DifficultyView && ((DifficultyView) view).localMaxDensity > maxDensity)
                    maxDensity = ((DifficultyView) view).localMaxDensity;
            }
        }

        localMaxDensity = 0;

        outer:
        for (Map.Entry<Long, ArrayList<HitObject>> entry : map.objects.entrySet()) {
            while (entry.getKey() > nextStep) {
                density[sectionIndex] = objectCount * 1000 / step;
                if (density[sectionIndex] > localMaxDensity) localMaxDensity = density[sectionIndex];

                objectCount = 0;
                ++sectionIndex;
                nextStep += step;
                if (sectionIndex >= density.length) break outer; //shouldn't happen, but just in case
            }

            objectCount += entry.getValue().size();
        }

        if (localMaxDensity > maxDensity) maxDensity = localMaxDensity;
        maxDensityString = "Max Objects Per Second: " + df.format(localMaxDensity);
        if (localMaxDensity != maxDensity) {
            maxDensityString += " | Graph Max: " + df.format(maxDensity);
        }


        //showText(attributes.StarRating + " : " + attributes.ContinuousRating + " : " + attributes.BurstRating);

        //owner.addView(new DifficultyView(owner, map, difficultyInfo), true);
    }

    public void close(int button)
    {
        if (button == Input.Buttons.LEFT)
        {
            parent.removeView(this);
        }
    }

    public void swapMode(int button)
    {
        switch (mode) {
            case STRAINS:
                mode = DifficultyMode.DENSITY;
                parent.showText("Density (objects per second)");
                break;
            case DENSITY:
                mode = DifficultyMode.STRAINS;
                parent.showText("Difficulty");
                break;
        }
    }

    @Override
    public int setPos(int y) {
        super.setPos(y);

        baseTextY = this.y + 50;

        return this.y;
    }

    @Override
    public void primaryUpdate(boolean isPlaying) {
        if (isPrimary && lockOffset == 0 && isPlaying && lastSounded < time && time - lastSounded < 25
                && parent.getViewSet(map).contains((o)->o.type == ViewType.OBJECT_VIEW))
        {

            //To play ALL hitobjects passed.
            for (ArrayList<HitObject> objects : map.objects.subMap((long) lastSounded, false, time, true).values())
            {
                for (HitObject o : objects)
                {
                    o.playSound();
                }
            }
        }
        lastSounded = preciseTime;
    }

    @Override
    public void renderBase(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, bottom, SettingsMaster.getWidth(), height);

        sb.setColor(lineColor);
        sb.draw(pix, 0, bottom + GRAPH_AREA, SettingsMaster.getWidth(), 1);
    }

    @Override
    public void renderOverlay(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(Color.WHITE);
        float y = bottom + GRAPH_AREA - 5;
        textRenderer.setFont(font).renderText(sb, "Star Rating: " + df.format(attributes.StarRating), 0, y, Color.WHITE);
        y -= 17;
        textRenderer.setFont(font).renderText(sb, "Color: " + df.format(attributes.ColourDifficulty), 0, y, Color.WHITE);
        y -= 17;
        textRenderer.setFont(font).renderText(sb, "Rhythm: " + df.format(attributes.RhythmDifficulty), 0, y, Color.WHITE);
        y -= 17;
        textRenderer.setFont(font).renderText(sb, "Stamina: " + df.format(attributes.StaminaDifficulty), 0, y, Color.WHITE);
        y -= 17;
        textRenderer.setFont(font).renderText(sb, "Reading: " + df.format(attributes.ReadingDifficulty), 0, y, Color.WHITE);
        y -= 17;
        textRenderer.setFont(font).renderText(sb, "Mechanical: " + df.format(attributes.MechanicalDifficulty), 0, y, Color.WHITE);
        y -= 17;

        switch (mode) {
            case STRAINS:
                y = bottom + 13;
                textRenderer.renderText(sb, "Object Stamina: ", 0, y);
                y += 17;
                textRenderer.renderText(sb, "Object Rhythm: ", 0, y);
                y += 17;
                textRenderer.renderText(sb, "Object Color: ", 0, y);
                y += 17;
                break;
            case DENSITY:
                double colorCap = Math.max(10, maxDensity * 0.83f);
                for (int i = 0; i < density.length; ++i) {
                    float coloring = (float) MathUtils.clamp(density[i] / colorCap, 0.01, 1);
                    densityColor.r = coloring;
                    densityColor.g = 1 - coloring;
                    sb.setColor(densityColor);
                    sb.draw(pix, 1 + (i * 20), bottom, 18, (float) (GRAPH_AREA * (density[i] / maxDensity)));
                }

                sb.setColor(Color.WHITE);
                textRenderer.setFont(font).renderText(sb, maxDensityString, 0, bottom + GRAPH_AREA + 10, Color.WHITE);
                break;
        }

        super.renderOverlay(sb, sr);
    }

    private final DecimalFormat df = new DecimalFormat("#0.##", osuDecimalFormat);

    @Override
    public void renderObject(MapObject o, SpriteBatch sb, ShapeRenderer sr, float alpha) {
        switch (mode) {
            case STRAINS:
                float x = SettingsMaster.getMiddleX() + (float) (o.getPos() - preciseTime) * viewScale;
                if (x < 100) return;

                sb.setColor(Color.WHITE);
                textRenderer.setFont(font);

                TaikoDifficultyHitObject difficultyObj = difficultyInfo.get(o);

                if (difficultyObj != null) {
                    int y = bottom + 13;
                    for (int i = 0; i < difficultyObj.debugData.length; ++i) {
                        textRenderer.renderText(sb, df.format(difficultyObj.debugData[i]), x, y);
                        y += 17;
                    }
                }

                break;
            case DENSITY:
                break;
        }
    }
    @Override
    public void renderSelection(MapObject o, SpriteBatch sb, ShapeRenderer sr) {
        //o.renderSelection(sb, sr, time, viewScale, SettingsMaster.getMiddleX(), objectY);
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends MapObject>> prep() {
        return map.getEditObjects(time - EditorLayer.viewTime, time + EditorLayer.viewTime);
    }

    @Override
    public void selectAll() {

    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends MapObject>> getVisibleRange(long start, long end) {
        NavigableMap<Long, ? extends ArrayList<? extends MapObject>> source = map.getEditObjects(time - EditorLayer.viewTime, time + EditorLayer.viewTime);

        if (source.isEmpty())
            return null;

        start = Math.max(start, source.lastKey());
        end = Math.min(end, source.firstKey());

        if (start >= end)
            return null;

        return source.subMap(end, true, start, true);
    }

    @Override
    public int getBasePosition() {
        return mode == DifficultyMode.DENSITY ? getPositionFromTime(preciseTime) : -1;
    }

    @Override
    public int getPositionFromTime(double time) {
        return getPositionFromTime(time, 0);
    }

    @Override
    public int getPositionFromTime(double time, int offset)
    {
        return (int) ((time / maxPos) * SettingsMaster.getWidth());
    }

    @Override
    public String getSelectionString() {
        return "";
    }

    @Override
    public void addSelectionRange(long startTime, long endTime)
    {

    }

    @Override
    public MapObject getObjectAt(float x, float y) {
        return null;
    }

    @Override
    public MouseHoldObject click(float x, float y, int button) {
        if (mode == DifficultyMode.DENSITY && music.lock(this))
        {
            music.seekMs(convertPosition(x));
            music.unlock(this);
        }

        return null;
    }

    private double convertPosition(float position)
    {
        return MathUtils.clamp(position * maxPos / SettingsMaster.getWidth(), 0, maxPos);
    }

    @Override
    public boolean clickedEnd(MapObject o, float x) {
        return false;
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    @Override
    public void pasteObjects(ViewType copyType, MapObjectTreeMap<MapObject> copyObjects) {
    }

    @Override
    public void reverse() {
    }

    @Override
    public void invert() {
    }
    @Override
    public void deleteSelection() {

    }

    @Override
    public void registerMove(long totalMovement) {

    }

    private static final Toolset toolset = new Toolset();
    public Toolset getToolset()
    {
        return toolset;
    }
}