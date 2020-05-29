package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.editor.Snap;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.util.structures.PositionalObject;
import alchyr.taikoedit.util.structures.PositionalObjectTreeMap;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.*;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public abstract class MapView {
    public enum ViewType {
        OBJECT_VIEW,
        TIMING_VIEW,
        EFFECT_VIEW,
        GAMEPLAY_VIEW
    }


    private static final Color selectionGlow = new Color(0.8f, 0.7f, 0.4f, 1.0f);
    private static final float GLOW_THICKNESS = 2.0f * SettingsMaster.SCALE;

    EditorLayer parent;
    public EditorBeatmap map;
    public final ViewType type; //Views of the same time should use the same set of objects in the same order

    public boolean isPrimary;

    //Position within song.
    protected int time = 0;

    //Base position values
    public int y;

    public int yOffset = 0;
    //Post-Offset values
    public int bottom; //y + offset
    public int topY;

    //Un-Offset values
    public float topGlowY; //Relative to Bottom.
    public int overlayY; //Relative to Bottom.

    public int overlayWidth;

    protected int height;
    protected Texture pix = assetMaster.get("ui:pixel");
    //protected Texture overlayEnd = assetMaster.get("editor:overlay end");

    //Selection
    protected PositionalObjectTreeMap<PositionalObject> selectedObjects;

    private final List<ImageButton> overlayButtons;

    public MapView(ViewType viewType, EditorLayer parent, EditorBeatmap beatmap, int height)
    {
        this.type = viewType;
        this.parent = parent;
        this.map = beatmap;

        this.y = 0;
        this.height = height;
        this.topGlowY = height - GLOW_THICKNESS;
        this.overlayY = height - 30;
        this.overlayWidth = 0;

        setOffset(0);

        isPrimary = false;

        overlayButtons = new ArrayList<>();
    }

    protected void addOverlayButton(ImageButton b)
    {
        overlayButtons.add(b);
        overlayWidth += b.getWidth();
    }

    public int getTime()
    {
        return time;
    }
    public abstract int getTimeFromPosition(int x);
    protected int getTimeFromPosition(int x, int offset)
    {
        return (int) (time + (x - offset) * EditorLayer.viewScale);
    }
    public int getPositionFromTime(int time, int offset)
    {
        return (int) ((time - this.time) / EditorLayer.viewScale + offset);
    }

    public int setPos(int y)
    {
        this.y = y - height;

        return this.y;
    }

    //If this method returns true, make it the primary view
    public boolean click(int x, int y, int pointer, int button)
    {
        if (!isPrimary)
        {
            isPrimary = true;
            return true;
        }
        return false;
    }
    public boolean clickOverlay(int x, int y, int button)
    {
        if (x <= overlayWidth && y >= overlayY)
        {
            for (ImageButton b : overlayButtons)
            {
                if (b.click(x, y, button))
                {
                    return true;
                }
            }
        }
        return false;
    }

    //Prep -> update -> rendering

    //Ensure map is ready for rendering. Exact details will depend on the view.
    public abstract NavigableMap<Integer, ? extends ArrayList<? extends PositionalObject>> prep(int pos);
    public void setOffset(int offset)
    {
        this.yOffset = offset;

        this.topY = this.y + height + offset;
        this.bottom = this.y + offset;
    }
    public void update(float exactPos, int msPos)
    {
        time = msPos;
        for (ImageButton b : overlayButtons)
            b.update();
    }
    public abstract void renderBase(SpriteBatch sb, ShapeRenderer sr);
    public void renderObject(PositionalObject o, SpriteBatch sb, ShapeRenderer sr)
    {
        renderObject(o, sb, sr, 1.0f);
    }
    public abstract void renderObject(PositionalObject o, SpriteBatch sb, ShapeRenderer sr, float alpha);
    public abstract void renderSelection(PositionalObject o, SpriteBatch sb, ShapeRenderer sr);

    //Rendering done to show the currently active MapView.
    public void primaryRender(SpriteBatch sb, ShapeRenderer sr)
    {
        sb.setColor(selectionGlow);
        sb.draw(pix, 0, this.bottom + this.topGlowY, SettingsMaster.getWidth(), GLOW_THICKNESS);
        sb.draw(pix, 0, this.bottom, SettingsMaster.getWidth(), GLOW_THICKNESS);
    }

    public void renderOverlay(SpriteBatch sb, ShapeRenderer sr)
    {
        if (!overlayButtons.isEmpty())
        {
            /*sb.setColor(Color.BLACK);
            sb.draw(pix, 0, y, overlayWidth, 30);
            sb.draw(overlayEnd, overlayWidth, y);*/

            sb.setColor(Color.WHITE);
            float y = this.bottom + this.overlayY;
            float x = 0;
            for (ImageButton b : overlayButtons)
            {
                b.render(sb, sr, (int)x, (int)y);
                x += b.getWidth();
            }
        }
    }

    public void primaryUpdate(boolean isPlaying)
    {
    }

    public abstract Snap getPreviousSnap();
    public abstract Snap getNextSnap();
    public abstract Snap getClosestSnap(int time, int limit);
    public abstract boolean noSnaps();

    //Other methods
    public abstract void deleteObject(PositionalObject o);
    public abstract void pasteObjects(PositionalObjectTreeMap<PositionalObject> copyObjects);

    //Selection logic
    public abstract NavigableMap<Integer, ? extends ArrayList<? extends PositionalObject>> getVisisbleRange(int start, int end);
    public PositionalObjectTreeMap<PositionalObject> getSelection() {
        return selectedObjects;
    }
    public abstract String getSelectionString();

    public abstract void deleteSelection();
    public abstract void registerMove(int totalMovement); //Registers a movement of selected objects with underlying map for undo/redo support

    public boolean hasSelection()
    {
        return selectedObjects != null && !selectedObjects.isEmpty();
    }

    public void clearSelection()
    {
        if (selectedObjects != null)
        {
            for (List<? extends PositionalObject> stuff : selectedObjects.values())
            {
                for (PositionalObject o : stuff)
                    o.selected = false;
            }
            selectedObjects = null;
        }
    }

    public abstract void selectAll();

    public abstract void addSelectionRange(int startTime, int endTime);

    //Perform Click selection.
    public abstract PositionalObject clickObject(int x, int y);
    public abstract PositionalObject clickSelection(int x, int y);
    public abstract boolean clickedEnd(PositionalObject o, int x); //assuming this object was returned by clickObject, y should already be confirmed to be in range.
    public void select(PositionalObject p) //Add a single object to selection.
    {
        p.selected = true;
        if (selectedObjects == null)
            selectedObjects = new PositionalObjectTreeMap<>();

        selectedObjects.add(p);
    }
    public void deselect(PositionalObject p)
    {
        p.selected = false;
        if (selectedObjects != null)
        {
            selectedObjects.removeObject(p);
        }
    }


    public void dispose()
    {
        parent = null;
        map = null;
        pix = null; //no need to dispose textures, asset master handles them.
    }
}
