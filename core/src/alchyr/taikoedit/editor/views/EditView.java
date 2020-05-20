package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.maps.components.HitObject;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.NavigableMap;

public class EditView extends MapView {
    private int currentPos = 0;
    private int objectY = 0;

    public EditView(EditorLayer parent, EditorBeatmap beatmap) {
        super(0, parent, beatmap, 200);
    }

    @Override
    public int setPos(int y) {
        y = super.setPos(y);

        objectY = this.y - 100;

        return y;
    }

    @Override
    public void update(int pos) {
        currentPos = pos;
    }

    @Override
    public void renderBase(SpriteBatch sb, ShapeRenderer sr) {

    }

    @Override
    public void renderObject(HitObject o, SpriteBatch sb, ShapeRenderer sr) {
        o.render(sb, sr, currentPos, parent.viewScale, SettingsMaster.getWidth() / 2.0f, objectY);
    }

    @Override
    public NavigableMap<Integer, ArrayList<HitObject>> prep(EditorBeatmap map) {
        return map.getEditObjects(currentPos - parent.viewTime, currentPos + parent.viewTime);
    }
}
