package alchyr.taikoedit.editor.views;

import alchyr.diffcalc.TaikoDifficultyCalculator;
import alchyr.diffcalc.taiko.difficulty.TaikoDifficultyAttributes;
import alchyr.diffcalc.taiko.difficulty.preprocessing.TaikoDifficultyHitObject;
import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.layers.sub.DifficultySettingsLayer;
import alchyr.taikoedit.core.ui.ImageButton;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.components.HitObject;
import alchyr.taikoedit.core.input.MouseHoldObject;
import alchyr.taikoedit.util.structures.PositionalObject;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.*;
import java.util.function.Predicate;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class ViewSet {
    private final EditorLayer owner;
    private final List<MapView> views;
    private final HashMap<MapView, NavigableMap<Long, ? extends ArrayList<? extends PositionalObject>>> viewObjects;
    private final EditorBeatmap map;

    private final HashMap<HitObject, TaikoDifficultyHitObject> difficultyInfo = new HashMap<>();

    private final BitmapFont difficultyFont;
    private float difficultyX;
    private int yOffset;

    public ViewSet(EditorLayer owner, EditorBeatmap map)
    {
        this.owner = owner;
        this.map = map;

        difficultyFont = assetMaster.getFont("aller medium");
        difficultyX = SettingsMaster.getWidth() - (TaikoEditor.textRenderer.setFont(difficultyFont).getWidth(map.getName()) + 10);

        views = new ArrayList<>();
        viewObjects = new HashMap<>();
    }
    public void updateDiffNamePosition() {
        difficultyX = SettingsMaster.getWidth() - (TaikoEditor.textRenderer.setFont(difficultyFont).getWidth(map.getName()) + 10);
    }

    public void update(double exactPos, long pos, float elapsed, boolean isPlaying, boolean canHover)
    {
        for (MapView view : views)
        {
            view.update(exactPos, pos, elapsed, canHover);
            viewObjects.put(view, view.prep());
            view.primaryUpdate(isPlaying);
        }
    }

    public void render(SpriteBatch sb, ShapeRenderer sr)
    {
        for (MapView view : views) {
            view.renderBase(sb, sr); //render the stuff that goes under objects
        }

        for (MapView view : views) {
            for (ArrayList<? extends PositionalObject> objects : viewObjects.getOrDefault(view, Collections.emptyNavigableMap()).values()) {
                view.renderStack(objects, sb, sr);
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

    private void properties() {
        owner.clean();
        TaikoEditor.addLayer(new DifficultySettingsLayer(owner, map));
    }

    public void setOffset(int offset)
    {
        yOffset = offset;
        for (MapView view : views)
        {
            view.setOffset(offset);
        }
    }

    public boolean containsY(float y)
    {
        if (views.isEmpty())
            return false;

        return y >= views.get(views.size() - 1).bottom;
    }

    public List<MapView> getViews() {
        return views;
    }

    public MapView getView(float y)
    {
        for (MapView view : views) {
            if (y >= view.bottom) //Since views are positioned by their index in this array, there's no need to check that y < view.topY
            {
                return view;
            }
        }
        return null;
    }
    public MouseHoldObject click(float x, float y, int button, int modifiers)
    {
        for (MapView view : views) {
            if (y >= view.bottom) //Since views are positioned by their index in this array, there's no need to check that y < view.topY
            {
                MouseHoldObject returnVal = view.clickOverlay(x, y, button);

                if (returnVal == null) { //Clicking an overlay button cancels pretty much everything else
                    if (view.select()) {
                        owner.setPrimaryView(view);
                    }

                    if (button == Input.Buttons.RIGHT && (owner.tools.getCurrentTool() == null || !owner.tools.getCurrentTool().consumesRightClick())) {
                        if (view.rightClick(x, y)) {
                            returnVal = MouseHoldObject.nothing;
                        }
                    }

                    if (returnVal == null) {
                        returnVal = view.click(x, y, button);
                    }

                    if (owner.tools.changeToolset(view) && returnVal == null)
                    {
                        //If the current tool is valid for the new toolset, use it immediately
                        returnVal = owner.tools.getCurrentTool().supportsView(view) ? owner.tools.getCurrentTool().click(view, x, y, button, modifiers) : null;
                    }
                }

                return returnVal == MouseHoldObject.nothing ? null : returnVal;
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

    public int reposition(int y)
    {
        for (MapView view : views)
        {
            y = view.setPos(y);
        }
        return y;
    }

    private final ImageButton propertiesButton = new ImageButton(assetMaster.get("editor:properties"), assetMaster.get("editor:propertiesh")).setClick(this::properties).setAction("Properties");
    public void addView(MapView toAdd)
    {
        if (toAdd.map.equals(map))
        {
            if (views.isEmpty()) { //When adding the first view, and then also when removing the first view, the properties button must be added, which can be a single instance based in this class.
                toAdd.addOverlayButton(propertiesButton);
            }
            views.add(toAdd);

            //Causes updates to occur on map changes
            if (toAdd instanceof EffectView) {
                map.bindEffectView((EffectView) toAdd);
            }
            else if (toAdd instanceof GameplayView) {
                map.bindGameplayView((GameplayView) toAdd);
            }
        }
        else
        {
            throw new IllegalArgumentException("Attempted to add a view of the wrong map to ViewSet.");
        }
    }
    public void removeView(MapView toRemove)
    {
        if (views.indexOf(toRemove) == 0 && views.size() > 1) {
            views.get(1).addOverlayButton(propertiesButton);
        }
        toRemove.clearSelection();
        views.remove(toRemove);
        viewObjects.remove(toRemove);

        if (toRemove instanceof EffectView) {
            map.removeEffectView((EffectView) toRemove);
        }
        else if (toRemove instanceof GameplayView) {
            map.removeGameplayView((GameplayView) toRemove);
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

    public boolean objectExists(int x, int y) {
        return contains((view)->view.getObjectAt(x, y) != null);
    }

    public void dispose()
    {
        for (MapView view : views)
        {
            view.dispose();
        }
        views.clear();
        viewObjects.clear();
    }

    public void calculateDifficulty() {
        TaikoDifficultyAttributes attributes = (TaikoDifficultyAttributes) TaikoDifficultyCalculator.calculateDifficulty(map, difficultyInfo);
        owner.showText(attributes.StarRating + " : " + attributes.ContinuousRating + " : " + attributes.BurstRating);

        owner.addView(new DifficultyView(owner, map, difficultyInfo), true);
    }
}
