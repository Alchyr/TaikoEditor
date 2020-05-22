package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.maps.components.HitObject;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedSet;

public class ObjectView extends MapView {
    public static final int HEIGHT = 200;
    private static final int BIG_HEIGHT = 50;
    public static final int MEDIUM_HEIGHT = 30;
    public static final int SMALL_HEIGHT = 15;

    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.7f);

    private static final int midPos = SettingsMaster.getWidth() / 2;

    private int currentPos = 0;
    private int objectY = 0;
    private int topBigY = 0;

    private int lastSounded;

    private SortedSet<Snap> activeSnaps;

    public ObjectView(EditorLayer parent, EditorBeatmap beatmap) {
        super(0, parent, beatmap, HEIGHT);
        lastSounded = 0;
    }

    @Override
    public int setPos(int y) {
        super.setPos(y);

        objectY = this.y + 100;
        topBigY = this.y + HEIGHT - BIG_HEIGHT;

        return this.y;
    }

    @Override
    public void primaryUpdate(boolean isPlaying) {
        if (isPrimary && isPlaying && lastSounded < currentPos) //might have skipped backwards
        {
            //Play only the most recently passed HitObject list. This avoids spamming a bunch of sounds if fps is too low.
            /*Map.Entry<Integer, ArrayList<HitObject>> entry = map.getEditObjects().higherEntry(currentPos);
            if (entry != null && entry.getKey() > lastSounded)
            {
                for (HitObject o : entry.getValue())
                    o.playSound();
            }*/

            //To play ALL hitobjects passed.
            for (ArrayList<HitObject> objects : map.objects.subMap(lastSounded, false, currentPos, true).values())
            {
                for (HitObject o : objects)
                {
                    o.playSound();
                }
            }
        }
        lastSounded = currentPos;
    }

    @Override
    public void update(float exactPos, int msPos) {
        currentPos = msPos;

        activeSnaps = map.getActiveSnaps(currentPos - parent.viewTime, currentPos + parent.viewTime);
    }

    @Override
    public void renderBase(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, y, SettingsMaster.getWidth(), height);

        //Divisors.
        for (Snap s : activeSnaps)
        {
            s.render(sb, sr, currentPos, parent.viewScale, SettingsMaster.getMiddle(), y);
        }

        //Replace Fat White Midpoint with something a little less obnoxious
        //Small triangle on top and bottom like my skin? It would look nice tesselated as well, I think.
        sb.setColor(Color.WHITE);
        sb.draw(pix, midPos, y, 2, BIG_HEIGHT);
        sb.draw(pix, midPos, topBigY, 2, BIG_HEIGHT);
    }

    @Override
    public void renderObject(HitObject o, SpriteBatch sb, ShapeRenderer sr) {
        o.render(sb, sr, currentPos, parent.viewScale, SettingsMaster.getMiddle(), objectY);
    }

    @Override
    public NavigableMap<Integer, ArrayList<HitObject>> prep(int pos) {
        return map.getEditObjects(pos - parent.viewTime, pos + parent.viewTime);
    }
}
