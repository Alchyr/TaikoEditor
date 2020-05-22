package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.maps.components.HitObject;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.NavigableMap;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public abstract class MapView {
    private static final Color selectionGlow = new Color(0.8f, 0.7f, 0.4f, 1.0f);
    private static final float GLOW_THICKNESS = 2.0f * SettingsMaster.SCALE;

    EditorLayer parent;
    public EditorBeatmap map;
    public int type; //Views of the same time should use the same set of objects in the same order

    public boolean isPrimary;

    public int y, topY;
    protected int height;
    private float topGlowY;

    protected Texture pix = assetMaster.get("ui:pixel");

    public MapView(int viewType, EditorLayer parent, EditorBeatmap beatmap, int height)
    {
        this.type = viewType;
        this.parent = parent;
        this.map = beatmap;

        this.y = 0;
        this.topY = this.y + height;
        this.height = height;
        this.topGlowY = height - GLOW_THICKNESS;

        isPrimary = false;
    }

    public int setPos(int y)
    {
        this.y = y - height;
        this.topY = this.y + height;

        return this.y;
    }

    //If this method returns true, make it the primary view
    public boolean click(int x, int y, int pointer, int button)
    {
        isPrimary = true;
        return true;
    }

    //Prep -> update -> rendering

    //Ensure map is ready for rendering. Exact details will depend on the view.
    public abstract NavigableMap<Integer, ArrayList<HitObject>> prep(int pos);
    public abstract void update(float exactPos, int msPos);
    public abstract void renderBase(SpriteBatch sb, ShapeRenderer sr);
    public abstract void renderObject(HitObject o, SpriteBatch sb, ShapeRenderer sr);

    //Rendering done to show the currently active MapView.
    public void primaryRender(SpriteBatch sb, ShapeRenderer sr)
    {
        sb.setColor(selectionGlow);
        sb.draw(pix, 0, this.y + this.topGlowY, SettingsMaster.getWidth(), GLOW_THICKNESS);
        sb.draw(pix, 0, this.y, SettingsMaster.getWidth(), GLOW_THICKNESS);
    }

    public void primaryUpdate(boolean isPlaying)
    {
    }
}
