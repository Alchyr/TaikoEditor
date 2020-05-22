package alchyr.taikoedit.editor.views;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.maps.EditorBeatmap;
import alchyr.taikoedit.maps.components.HitObject;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.*;

public class ViewSet {
    private final ArrayList<MapView> views;
    private final TreeMap<Integer, ArrayList<MapView>> organizedViews;
    private final HashMap<Integer, NavigableMap<Integer, ArrayList<HitObject>>> viewObjects;
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

        for (Map.Entry<Integer, ArrayList<MapView>> viewSet : organizedViews.entrySet()) {
            ArrayList<MapView> views = viewSet.getValue();
            int index;
            for (ArrayList<HitObject> objects : viewObjects.get(viewSet.getKey()).values()) {
                for (HitObject o : objects) {
                    for (index = 0; index < views.size(); ++index) {
                        views.get(0).renderObject(o, sb, sr);
                    }
                }
            }
        }
    }

    public boolean click(EditorLayer source, int x, int y, int pointer, int button)
    {
        for (MapView view : views) {
            if (y < view.topY && y >= view.y)
            {
                if (view.click(x, y, pointer, button))
                {
                    if (source.primaryView != null)
                        source.primaryView.isPrimary = false;
                    source.primaryView = view;
                }
                return true;
            }
        }
        return false;
    }

    public MapView first()
    {
        if (views.isEmpty())
            return null;
        return views.get(0);
    }

    public void prep(int pos)
    {
        for (Map.Entry<Integer, ArrayList<MapView>> viewSet : organizedViews.entrySet())
        {
            viewObjects.put(viewSet.getKey(), viewSet.getValue().get(0).prep(pos)); //Each type should only be prepped once
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
}
