package alchyr.taikoedit.editor;

import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.Button;
import alchyr.taikoedit.editor.tools.EditorTool;
import alchyr.taikoedit.editor.tools.SelectionTool;
import alchyr.taikoedit.editor.tools.Toolset;
import alchyr.taikoedit.editor.views.*;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class Tools  {
    public static final int HEIGHT = 50;

    private static final int BUTTON_Y = 0;
    private static final int BUTTON_X = 0;
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 50;

    private static final Color windowColor = new Color(0.0f, 0.0f, 0.0f, 0.85f);

    private static final Toolset defaultTools = new Toolset(SelectionTool.get());

    private EditorLayer owner;

    private final BitmapFont font;
    private final Texture pix;

    private Toolset currentToolset;
    private EditorTool currentTool;

    private ArrayList<Button> toolButtons = new ArrayList<>();
    private int visibleButtons = 0;

    public Tools(EditorLayer owner)
    {
        this.owner = owner;

        font = assetMaster.getFont("aller medium");
        pix = assetMaster.get("ui:pixel");
        setToolset(defaultTools);
    }

    public EditorTool getCurrentTool()
    {
        if (currentTool != null) currentTool.activate();
        return currentTool;
    }

    public void update(int viewsTop, int viewsBottom, List<EditorBeatmap> activeMaps, HashMap<EditorBeatmap, ViewSet> views, float elapsed)
    {
        if (currentTool != null)
            currentTool.update(viewsTop, viewsBottom, activeMaps, views, elapsed);

        for (int i = 0; i < visibleButtons; ++i)
        {
            toolButtons.get(i).update(elapsed);
        }
    }

    public void render(SpriteBatch sb, ShapeRenderer sr)
    {
        sb.setColor(windowColor);
        sb.draw(pix, 0, 0, SettingsMaster.getWidth(), HEIGHT);

        for (int i = 0; i < visibleButtons; ++i)
        {
            toolButtons.get(i).render(sb, sr);
        }

        /*int y = TEXT_Y;
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
        }*/
    }

    public boolean click(float gameX, float gameY, int button) {
        for (int i = 0; i < visibleButtons; ++i)
        {
            if (toolButtons.get(i).click(gameX, gameY, button))
            {
                selectToolIndex(i);
                return true;
            }
        }
        return false;
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
                currentTool.onSelected(owner);

                for (int i = 0; i < toolButtons.size(); ++i)
                {
                    toolButtons.get(i).renderBorder = i == index;
                }
            }
            return true;
        }
        return false;
    }
    public boolean instantUse(int index, MapView view) //uses tool *if* tool supports instant use, and maybe selects it. Otherwise, just selects
    {
        if (currentToolset.size() > index)
        {
            EditorTool tool = currentToolset.getTool(index);
            if (currentTool != null && !currentTool.equals(tool))
            {
                currentTool.cancel();
            }

            if (tool != null && tool.supportsView(view)) {
                if (tool.instantUse(view)) {
                    if (!tool.equals(currentTool)) {
                        currentTool = tool;
                        currentTool.onSelected(owner);
                        for (int i = 0; i < toolButtons.size(); ++i)
                        {
                            toolButtons.get(i).renderBorder = i == index;
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    public boolean changeToolset(MapView view)
    {
        return setToolset(view.getToolset());
    }

    private boolean setToolset(Toolset set)
    {
        currentToolset = set;

        //Update buttons
        visibleButtons = currentToolset.size();
        for (int i = 0; i < visibleButtons; ++i)
        {
            if (toolButtons.size() < i + 1)
            {
                /*toolButtons.add(new Button(BUTTON_X + (i / 6 * BUTTON_WIDTH),
                        BUTTON_Y - (i % 6 * BUTTON_HEIGHT),
                        BUTTON_WIDTH, BUTTON_HEIGHT, (i + 1) + ": " + currentToolset.getTool(i).name, font, null).useBorderRendering());*/
                toolButtons.add(new Button(BUTTON_X + (i * BUTTON_WIDTH),
                        BUTTON_Y,
                        BUTTON_WIDTH, BUTTON_HEIGHT, (i + 1) + ": " + currentToolset.getTool(i).name, font).useBorderRendering());
            }
            else
            {
                toolButtons.get(i).setText((i + 1) + ": " + currentToolset.getTool(i).name);
            }
        }

        //Swap tools or stick with current one
        if (!currentToolset.containsTool(currentTool))
        {
            if (currentTool != null)
                currentTool.cancel();
            currentTool = currentToolset.getDefaultTool();

            for (int i = 0; i < toolButtons.size(); ++i)
            {
                toolButtons.get(i).renderBorder = i == 0;
            }

            //Does not trigger onSelected
            return currentTool == null;
        }
        return currentTool != null;
    }
}