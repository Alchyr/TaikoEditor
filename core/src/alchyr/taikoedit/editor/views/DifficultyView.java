package alchyr.taikoedit.editor.views;

import alchyr.diffcalc.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
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
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.text.DecimalFormat;
import java.util.ArrayList;
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

    public static final int HEIGHT = 350;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.7f);

    private static final int midPos = SettingsMaster.getWidth() / 2;

    //Base position values
    private int baseTextY = 0;

    //Offset
    private int textY = 0;

    private double lastSounded;

    private final Map<HitObject, TaikoDifficultyHitObject> difficultyInfo;

    public DifficultyView(EditorLayer parent, EditorBeatmap beatmap, Map<HitObject, TaikoDifficultyHitObject> difficultyInfo) {
        super(ViewType.DIFFICULTY_VIEW, parent, beatmap, HEIGHT);
        lastSounded = 0;

        this.difficultyInfo = difficultyInfo;

        addOverlayButton(new ImageButton(assetMaster.get("editor:exit"), assetMaster.get("editor:exith")).setClick(this::close).setAction("Close View"));
    }

    public void close(int button)
    {
        if (button == Input.Buttons.LEFT)
        {
            parent.removeView(this);
        }
    }

    @Override
    public int setPos(int y) {
        super.setPos(y);

        baseTextY = this.y + 50;

        return this.y;
    }

    public void setOffset(int offset)
    {
        super.setOffset(offset);

        textY = baseTextY + yOffset;
    }

    @Override
    public void primaryUpdate(boolean isPlaying) {
        if (isPrimary && lockOffset == 0 && isPlaying && lastSounded < preciseTime && preciseTime - lastSounded < 25) //might have skipped backwards
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
    }

    private final DecimalFormat df = new DecimalFormat("#0.##", osuDecimalFormat);

    @Override
    public void renderObject(MapObject o, SpriteBatch sb, ShapeRenderer sr, float alpha) {
        textRenderer.renderText(sb, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).BASE_COLOR_DEBUG), SettingsMaster.getMiddleX() + (float) (o.getPos() - preciseTime) * viewScale, textY + 250, Color.WHITE);
        textRenderer.renderText(sb, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).SWAP_BONUS_DEBUG), SettingsMaster.getMiddleX() + (float) (o.getPos() - preciseTime) * viewScale, textY + 200);
        textRenderer.renderText(sb, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).RHYTHM_BONUS_DEBUG), SettingsMaster.getMiddleX() + (float) (o.getPos() - preciseTime) * viewScale, textY + 150);
        textRenderer.renderText(sb, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).COMBINED_DEBUG), SettingsMaster.getMiddleX() + (float) (o.getPos() - preciseTime) * viewScale, textY + 100);
        textRenderer.renderText(sb, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).BURST_BASE), SettingsMaster.getMiddleX() + (float) (o.getPos() - preciseTime) * viewScale, textY + 50);
        textRenderer.renderText(sb, df.format(difficultyInfo.getOrDefault(o, TaikoDifficultyHitObject.defaultInfo).BURST_DEBUG), SettingsMaster.getMiddleX() + (float) (o.getPos() - preciseTime) * viewScale, textY);
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