package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.changes.MapChange;
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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.NavigableMap;

import static alchyr.taikoedit.TaikoEditor.*;

public class ChangelogView extends MapView {
    public static final String ID = "changelog";
    @Override
    public String typeString() {
        return ID;
    }

    public static final int HEIGHT = 50;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.7f);

    private static final int midPos = SettingsMaster.getWidth() / 2;

    private final BitmapFont font;

    //Base position values
    private int baseTextY = 0;

    //Offset
    private int textY = 0;

    private long lastSounded;

    public ChangelogView(EditorLayer parent, EditorBeatmap beatmap) {
        super(ViewType.CHANGELOG_VIEW, parent, beatmap, HEIGHT);
        lastSounded = 0;
        font = assetMaster.getFont("aller medium");

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

        baseTextY = this.y + HEIGHT / 2;

        return this.y;
    }

    public void setOffset(int offset)
    {
        super.setOffset(offset);

        textY = baseTextY + yOffset;
    }

    @Override
    public void primaryUpdate(boolean isPlaying) {
        if (isPrimary && lockOffset == 0 && isPlaying && lastSounded < time && time - lastSounded < 25
                && parent.getViewSet(map).contains((o)->o.type == ViewType.OBJECT_VIEW))
        {
            for (ArrayList<HitObject> objects : map.objects.subMap(lastSounded, false, time, true).values())
            {
                for (HitObject o : objects)
                {
                    o.playSound();
                }
            }
        }
        lastSounded = time;
    }

    @Override
    public void update(double exactPos, long msPos, float elapsed, boolean canHover) {
        super.update(exactPos, msPos, elapsed, canHover);

        //if changelog index or branch is not the same as it was last update: update display buttons (buttons will "jump" to selected state)
    }

    @Override
    public MouseHoldObject click(float x, float y, int button) {
        return super.click(x, y, button);
    }

    @Override
    public void renderBase(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, bottom, SettingsMaster.getWidth(), height);

        textRenderer.setFont(font);

        MapChange change = map.lastChange();
        if (change != null) {
            textRenderer.renderTextCentered(sb, change.getName(), SettingsMaster.getMiddleX(), textY);
        }
    }

    private final DecimalFormat df = new DecimalFormat("#0.##", osuDecimalFormat);

    @Override
    public void renderObject(MapObject o, SpriteBatch sb, ShapeRenderer sr, float alpha) {
    }
    @Override
    public void renderSelection(MapObject o, SpriteBatch sb, ShapeRenderer sr) {

    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends MapObject>> prep() {
        return Collections.emptyNavigableMap();
    }

    @Override
    public void selectAll() {
        clearSelection();
    }

    @Override
    public NavigableMap<Long, ? extends ArrayList<? extends MapObject>> getVisibleRange(long start, long end) {
        return null;
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