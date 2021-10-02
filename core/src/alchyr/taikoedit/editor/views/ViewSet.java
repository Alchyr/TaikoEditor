package alchyr.taikoedit.editor.views;

import alchyr.diffcalc.TaikoDifficultyCalculator;
import alchyr.diffcalc.taiko.difficulty.TaikoDifficultyAttributes;
import alchyr.diffcalc.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.util.input.MouseHoldObject;
import alchyr.taikoedit.util.structures.PositionalObject;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.*;
import java.util.function.Predicate;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class ViewSet {
    private final EditorLayer owner;
    private final ArrayList<MapView> views;
    private final TreeMap<MapView.ViewType, ArrayList<MapView>> organizedViews;
    private final HashMap<MapView.ViewType, NavigableMap<Long, ArrayList<? extends PositionalObject>>> viewObjects;
    private final EditorBeatmap map;

    private final HashMap<HitObject, TaikoDifficultyHitObject> difficultyInfo = new HashMap<>();

    private final BitmapFont difficultyFont;
    private final float difficultyX;
    private int yOffset;

    public ViewSet(EditorLayer owner, EditorBeatmap map)
    {
        this.owner = owner;
        this.map = map;

        difficultyFont = assetMaster.getFont("aller medium");
        difficultyX = SettingsMaster.getWidth() - (TaikoEditor.textRenderer.setFont(difficultyFont).getWidth(map.getName()) + 10);

        views = new ArrayList<>();
        organizedViews = new TreeMap<>();
        viewObjects = new HashMap<>();
    }

    public void update(double exactPos, long pos, boolean isPlaying)
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
        boolean first = true;
        for (MapView view : views) {
            view.renderOverlay(sb, sr);
            if (first)
            {
                textRenderer.setFont(difficultyFont).renderText(sb, map.getName(), difficultyX, view.top - 10);
                first = false;
            }
        }
    }

    public void setOffset(int offset)
    {
        yOffset = offset;
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

    public ArrayList<MapView> getViews() {
        return views;
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
    public MouseHoldObject click(int x, int y, int pointer, int button, int modifiers)
    {
        for (MapView view : views) {
            if (y >= view.bottom) //Since views are positioned by their index in this array, there's no need to check that y < view.topY
            {
                if (view.clickOverlay(x, y, button))
                {
                    return null;
                }
                MouseHoldObject returnVal = null;
                if (owner.tools.changeToolset(view)) //If the current tool is valid for the new toolset, use it immediately
                {
                    returnVal = owner.tools.getCurrentTool().supportsView(view) ? owner.tools.getCurrentTool().click(view, x, y, button, modifiers) : null;
                }

                if (view.click(x, y, pointer, button))
                {
                    owner.setPrimaryView(view);
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
    public void prep(long pos)
    {
        for (Map.Entry<MapView.ViewType, ArrayList<MapView>> viewSet : organizedViews.entrySet())
        {
            viewObjects.put(viewSet.getKey(), (NavigableMap<Long, ArrayList<? extends PositionalObject>>) viewSet.getValue().get(0).prep(pos)); //Each type should only be prepped once
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

            switch (toAdd.type) //Bind to certain hooks
            {
                case EFFECT_VIEW:
                    map.bindEffectView((SvView) toAdd);
                    break;
            }
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

            switch (toRemove.type)
            {
                case EFFECT_VIEW:
                    map.removeEffectView((SvView) toRemove);
                    break;
            }
        }
    }

    public boolean contains(Predicate<MapView> condition)
    {
        for (MapView view : views)
        {
            if (condition.test(view))
            {
                return true;
            }
        }
        return false;
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

    public void calculateDifficulty() {
        TaikoDifficultyAttributes attributes = (TaikoDifficultyAttributes) TaikoDifficultyCalculator.calculateDifficulty(map, difficultyInfo);
        owner.showText(attributes.StarRating + " : " + attributes.ContinuousRating + " : " + attributes.BurstRating);

        owner.addView(new DifficultyView(owner, map, difficultyInfo), true);
    }
}
