package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.util.input.KeyHoldManager;
import alchyr.taikoedit.util.input.MouseHoldObject;
import alchyr.taikoedit.util.structures.PositionalObject;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.*;

public class ViewSet {
    private final ArrayList<MapView> views;
    private final TreeMap<MapView.ViewType, ArrayList<MapView>> organizedViews;
    private final HashMap<MapView.ViewType, NavigableMap<Integer, ArrayList<? extends PositionalObject>>> viewObjects;
    private final EditorBeatmap map;

    public ViewSet(EditorBeatmap map)
    {
        this.map = map;

        views = new ArrayList<>();
        organizedViews = new TreeMap<>();
        viewObjects = new HashMap<>();
    }

    public void update(float exactPos, int pos, boolean isPlaying)
    {
        prep(pos);
        for (MapView view : views)
        {
            view.update(exactPos, pos);
            view.primaryUpdate(isPlaying);
        }
    }

    public void render(SpriteBatch sb, ShapeRenderer sr)
    {
        for (MapView view : views) {
            view.renderBase(sb, sr); //render the stuff that goes under objects
        }

        for (Map.Entry<MapView.ViewType, ArrayList<MapView>> viewSet : organizedViews.entrySet()) {
            ArrayList<MapView> views = viewSet.getValue();
            int index;
            for (ArrayList<? extends PositionalObject> objects : viewObjects.get(viewSet.getKey()).values()) {
                for (PositionalObject o : objects) {
                    for (index = 0; index < views.size(); ++index) {
                        views.get(0).renderObject(o, sb, sr);
                    }
                }
            }
        }
    }

    public void renderOverlays(SpriteBatch sb, ShapeRenderer sr)
    {
        for (MapView view : views) {
            view.renderOverlay(sb, sr);
        }
    }

    public void setOffset(int offset)
    {
        for (MapView view : views)
        {
            view.setOffset(offset);
        }
    }

    public boolean containsY(int y)
    {
        if (views.isEmpty())
            return false;

        return y >= views.get(views.size() - 1).bottom;
    }
    public MapView getView(int y)
    {
        for (MapView view : views) {
            if (y >= view.bottom) //Since views are positioned by their index in this array, there's no need to check that y < view.topY
            {
                return view;
            }
        }
        return null;
    }
    public MouseHoldObject click(EditorLayer source, int x, int y, int pointer, int button, KeyHoldManager keyHolds)
    {
        for (MapView view : views) {
            if (y >= view.bottom) //Since views are positioned by their index in this array, there's no need to check that y < view.topY
            {
                if (view.clickOverlay(x, y, button))
                {
                    return null;
                }
                MouseHoldObject returnVal = null;
                if (source.tools.changeToolset(view)) //If the current tool is valid for the new toolset, use it immediately
                {
                    returnVal = source.tools.getCurrentTool().click(view, x, y, button, keyHolds);
                }

                if (view.click(x, y, pointer, button))
                {
                    if (source.primaryView != null)
                        source.primaryView.isPrimary = false;
                    source.primaryView = view;
                }

                return returnVal;
            }
        }
        return null;
    }

    public MapView first()
    {
        if (views.isEmpty())
            return null;
        return views.get(0);
    }

    @SuppressWarnings("unchecked")
    public void prep(int pos)
    {
        for (Map.Entry<MapView.ViewType, ArrayList<MapView>> viewSet : organizedViews.entrySet())
        {
            viewObjects.put(viewSet.getKey(), (NavigableMap<Integer, ArrayList<? extends PositionalObject>>) viewSet.getValue().get(0).prep(pos)); //Each type should only be prepped once
        }
    }

    public int reposition(int y)
    {
        for (MapView view : views)
        {
            y = view.setPos(y);
        }
        return y;
    }

    public void addView(MapView toAdd)
    {
        if (toAdd.map.equals(map))
        {
            views.add(toAdd);
            if (!organizedViews.containsKey(toAdd.type))
                organizedViews.put(toAdd.type, new ArrayList<>());

            organizedViews.get(toAdd.type).add(toAdd);
        }
        else
        {
            throw new IllegalArgumentException("Attempted to add a view of the wrong map to ViewSet.");
        }
    }
    public void removeView(MapView toRemove)
    {
        views.remove(toRemove);
        if (organizedViews.containsKey(toRemove.type))
        {
            organizedViews.get(toRemove.type).remove(toRemove);
            if (organizedViews.get(toRemove.type).isEmpty())
            {
                organizedViews.remove(toRemove.type);
                viewObjects.remove(toRemove.type);
            }
        }
    }

    public boolean isEmpty()
    {
        return views.isEmpty();
    }

    public void dispose()
    {
        for (MapView view : views)
        {
            view.dispose();
        }
        views.clear();
        organizedViews.clear();
        viewObjects.clear();
    }
}
