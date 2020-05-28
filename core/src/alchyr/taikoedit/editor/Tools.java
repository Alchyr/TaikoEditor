package alchyr.taikoedit.editor;

import alchyr.taikoedit.editor.tools.EditorTool;
import alchyr.taikoedit.editor.tools.SelectionTool;
import alchyr.taikoedit.editor.tools.Toolset;
import alchyr.taikoedit.editor.views.MapView;
import alchyr.taikoedit.editor.views.ObjectView;
import alchyr.taikoedit.editor.views.ViewSet;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.maps.EditorBeatmap;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.HashMap;
import java.util.List;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class Tools {
    public static final int HEIGHT = 300;

    private static final int TEXT_Y = 270;
    private static final int TEXT_X = 30;
    private static final int TEXT_OFFSET = 30;

    private static final Color windowColor = new Color(0.0f, 0.0f, 0.0f, 0.85f);

    private static final Toolset defaultTools = new Toolset(SelectionTool.get());
    private static final HashMap<MapView.ViewType, Toolset> toolsets;
    static {
        toolsets = new HashMap<>();
        toolsets.put(MapView.ViewType.OBJECT_VIEW, ObjectView.getToolset());
    }

    private final BitmapFont font;
    private final Texture pix;

    private Toolset currentToolset;
    private EditorTool currentTool;

    public Tools()
    {
        font = assetMaster.getFont("aller small");
        pix = assetMaster.get("ui:pixel");
        setToolset(defaultTools);
    }

    public EditorTool getCurrentTool()
    {
        return currentTool;
    }

    public void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed)
    {
        if (currentTool != null)
            currentTool.update(viewsTop, viewsBottom, activeMaps, views, elapsed);
    }

    public void render(SpriteBatch sb, ShapeRenderer sr)
    {
        sb.setColor(windowColor);
        sb.draw(pix, 0, 0, SettingsMaster.getWidth(), HEIGHT);

        int y = TEXT_Y;
        for (int i = 0; i < currentToolset.size(); ++i)
        {
            if (currentToolset.getTool(i).equals(currentTool))
            {
                textRenderer.setFont(font).renderText(sb, Color.GOLD, (i + 1) + ": " + currentToolset.getTool(i).name, TEXT_X, y);
            }
            else
            {
                textRenderer.setFont(font).renderText(sb, Color.WHITE, (i + 1) + ": " + currentToolset.getTool(i).name, TEXT_X, y);
            }
            y -= TEXT_OFFSET;
        }
    }

    public void renderCurrentTool(SpriteBatch sb, ShapeRenderer sr)
    {
        if (currentTool != null)
            currentTool.render(sb, sr);
    }

    public boolean selectToolIndex(int index)
    {
        if (currentToolset.size() > index)
        {
            if (currentTool != null && currentTool != currentToolset.getTool(index))
            {
                currentTool.cancel();
                currentTool = currentToolset.getTool(index);
            }
            return true;
        }
        return false;
    }

    public boolean changeToolset(MapView view)
    {
        return setToolset(toolsets.getOrDefault(view.type, defaultTools));
    }

    private boolean setToolset(Toolset set)
    {
        currentToolset = set;
        if (!currentToolset.containsTool(currentTool))
        {
            if (currentTool != null)
                currentTool.cancel();
            currentTool = currentToolset.getDefaultTool();
            return false;
        }
        return true;
    }
}