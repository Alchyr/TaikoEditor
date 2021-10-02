package alchyr.taikoedit.core.layers.sub;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.input.AdjustedInputProcessor;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.Button;
import alchyr.taikoedit.editor.views.GameplayView;
import alchyr.taikoedit.editor.views.SvView;
import alchyr.taikoedit.editor.views.ObjectView;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.EditorBeatmap;
import alchyr.taikoedit.editor.maps.MapInfo;
import alchyr.taikoedit.editor.maps.Mapset;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;

import java.util.ArrayList;
import java.util.List;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class DifficultyMenuLayer extends ProgramLayer implements InputLayer {
    private static DifficultyMenuProcessor processor;

    //Rendering
    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.83f);

    //Textures
    private final Texture pix;

    //Positions
    private final int middleY = SettingsMaster.getHeight() / 2;
    private final int minRenderY = -(middleY + 30);

        //For smoothness
        private float optionY = 0, viewY = 0, targetOptionY, targetViewY, startOptionY, startViewY;
        private float targetLineWidth, startLineWidth, lineWidth = 0, targetLineWidthB, startLineWidthB, lineWidthB = 0;
        private float targetLinePos, startLinePos, linePos = 0, targetLinePosB, startLinePosB, linePosB = SettingsMaster.getWidth();
        private float firstLineProgress = 0, secondLineProgress = 0, difficultyProgress = 0, viewProgress = 0;

    //Parts
    private final List<Button> difficultyOptions;
    private final List<Button> viewOptions;
    private final Button openButton;

    //Scroll
    private int difficultyIndex = 0, viewIndex = 0;

    //Other data
    private final EditorLayer sourceLayer;
    private final List<MapInfo> maps;

    public DifficultyMenuLayer(EditorLayer editor, Mapset set)
    {
        this.type = LAYER_TYPE.UPDATE_STOP;

        sourceLayer = editor;

        processor = new DifficultyMenuProcessor(this);
        pix = assetMaster.get("ui:pixel");

        int difficultyOptionX = SettingsMaster.getWidth() / 4;
        int viewOptionX = SettingsMaster.getWidth() / 2;
        int openButtonX = difficultyOptionX * 3;

        difficultyOptions = new ArrayList<>();
        maps = set.getMaps();
        for (MapInfo info : maps)
        {
            difficultyOptions.add(new Button(difficultyOptionX, middleY, info.difficultyName, assetMaster.getFont("aller medium"), null));
        }

        viewOptions = new ArrayList<>();
        viewOptions.add(new Button(viewOptionX, middleY, "Object Editor", assetMaster.getFont("aller medium"), null).setAction("objects"));
        viewOptions.add(new Button(viewOptionX, middleY, "SV Editor", assetMaster.getFont("aller medium"), null).setAction("sv"));
        viewOptions.add(new Button(viewOptionX, middleY, "Gameplay View", assetMaster.getFont("aller medium"), null).setAction("gameplay"));
        viewOptions.add(new Button(viewOptionX, middleY, "Create New", assetMaster.getFont("aller medium"), null).setAction("NEW"));

        openButton = new Button(openButtonX, middleY, "Open", assetMaster.getFont("aller medium"), this::open);

        startLinePos = SettingsMaster.getMiddle() / 3.0f;
        calculateFirstLine();
        calculateSecondLine();
        calculateDifficulties();
        calculateViews();
    }

    private void open(int button)
    {
        EditorBeatmap b = sourceLayer.getEditorBeatmap(maps.get(difficultyIndex));
        switch (viewOptions.get(viewIndex).action)
        {
            case "objects":
                sourceLayer.addView(new ObjectView(sourceLayer, b), true);
                break;
            case "sv":
                sourceLayer.addView(new SvView(sourceLayer, b), true);
                break;
            case "gameplay":
                sourceLayer.addView(new GameplayView(sourceLayer, b), true);
                break;
            case "NEW":

                break;
        }


        TaikoEditor.removeLayer(this);
    }

    private void calculateFirstLine()
    {
        targetLinePos = difficultyOptions.get(difficultyIndex).endX() + 10;
        startLinePos = linePos;
        float end = viewOptions.get(viewIndex).startX() - 10;
        targetLineWidth = end - targetLinePos;
        startLineWidth = lineWidth;
        firstLineProgress = 0;
    }
    private void calculateSecondLine()
    {
        targetLinePosB = viewOptions.get(viewIndex).endX() + 10;
        startLinePosB = linePosB;
        float end = openButton.startX() - 10;
        targetLineWidthB = end - targetLinePosB;
        startLineWidthB = lineWidthB;
        secondLineProgress = 0;
    }
    private void calculateDifficulties()
    {
        targetOptionY = 30 * difficultyIndex;
        startOptionY = optionY;
        difficultyProgress = 0;
    }
    private void calculateViews()
    {
        targetViewY = 30 * viewIndex;
        startViewY = viewY;
        viewProgress = 0;
    }

    private void updateLines(float elapsed)
    {
        if (firstLineProgress < 1)
            firstLineProgress += elapsed * 4; //Just gotta make sure it doesn't overflow if you wait 5000 years or so

        if (firstLineProgress < 1)
            lineWidth = Interpolation.circle.apply(startLineWidth, targetLineWidth, firstLineProgress);
        else
            lineWidth = targetLineWidth;

        if (firstLineProgress < 1)
            linePos = Interpolation.circle.apply(startLinePos, targetLinePos, firstLineProgress);
        else
            linePos = targetLinePos;


        if (secondLineProgress < 1)
            secondLineProgress += elapsed * 4; //Just gotta make sure it doesn't overflow if you wait 5000 years or so

        if (secondLineProgress < 1)
            lineWidthB = Interpolation.circle.apply(startLineWidthB, targetLineWidthB, secondLineProgress);
        else
            lineWidthB = targetLineWidthB;

        if (secondLineProgress < 1)
            linePosB = Interpolation.circle.apply(startLinePosB, targetLinePosB, secondLineProgress);
        else
            linePosB = targetLinePosB;
    }
    private void updateButtons(float elapsed)
    {
        if (viewProgress < 1)
            viewProgress += elapsed * 4; //Just gotta make sure it doesn't overflow if you wait 5000 years or so

        if (viewProgress < 1)
            viewY = Interpolation.circle.apply(startViewY, targetViewY, viewProgress);
        else
            viewY = targetViewY;

        if (difficultyProgress < 1)
            difficultyProgress += elapsed * 4;

        if (difficultyProgress < 1)
            optionY = Interpolation.circle.apply(startOptionY, targetOptionY, difficultyProgress);
        else
            optionY = targetOptionY;
    }

    @Override
    public void update(float elapsed) {
        for (Button b : difficultyOptions)
        {
            b.update();
        }
        for (Button b : viewOptions)
        {
            b.update();
        }
        openButton.update();

        updateLines(elapsed);
        updateButtons(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight());

        int index = 0;
        for (float y = optionY; y > minRenderY && index < difficultyOptions.size(); y -= 30)
        {
            difficultyOptions.get(index).render(sb, sr, 0, y);
            ++index;
        }
        index = 0;
        for (float y = viewY; y > minRenderY && index < viewOptions.size(); y -= 30)
        {
            viewOptions.get(index).render(sb, sr, 0, y);
            ++index;
        }

        openButton.render(sb, sr);

        sb.setColor(Color.WHITE);
        sb.draw(pix, linePos, middleY - 1, lineWidth, 3);
        sb.draw(pix, linePosB, middleY - 1, lineWidthB, 3);
    }

    private void close()
    {
        TaikoEditor.removeLayer(this);
    }

    private void chooseDifficulty(int buttonIndex)
    {
        difficultyIndex = buttonIndex;
        calculateFirstLine();
        calculateDifficulties();
    }
    private void chooseType(int buttonIndex)
    {
        boolean wasCreate = viewOptions.get(viewIndex).action.equals("NEW");
        viewIndex = buttonIndex;

        if (!wasCreate && viewOptions.get(viewIndex).action.equals("NEW"))
        {
            openButton.setText("Create", true);
        }
        else if (wasCreate && !viewOptions.get(viewIndex).action.equals("NEW"))
        {
            openButton.setText("Open", true);
        }

        calculateFirstLine();
        calculateSecondLine();
        calculateViews();
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    private static class DifficultyMenuProcessor extends AdjustedInputProcessor {
        private final DifficultyMenuLayer sourceLayer;

        public DifficultyMenuProcessor(DifficultyMenuLayer source)
        {
            this.sourceLayer = source;
        }

        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) //Process input using the current primary view? TODO: Add keybindings? SettingsMaster has some keybind code.
            {
                case Input.Keys.ESCAPE:
                    sourceLayer.close();
                    return true;
                case Input.Keys.RIGHT:
                    break;
            }
            return true;
        }

        @Override
        public boolean keyUp(int keycode) {
            return true;
        }

        @Override
        public boolean keyTyped(char character) {
            /*if (sourceLayer.searchInput.keyTyped(character)) {
                sourceLayer.mapOptions.clear();

                if (sourceLayer.searchInput.text.isEmpty()) {
                    for (String key : MapMaster.mapDatabase.keys) {
                        sourceLayer.mapOptions.add(sourceLayer.hashedMapOptions.get(key));
                    }
                }
                else {
                    MapMaster.search(sourceLayer.searchInput.text).forEach((m)->{
                        sourceLayer.mapOptions.add(sourceLayer.hashedMapOptions.get(m.key));
                    });
                }
                return true;
            }*/

            return true;
        }

        @Override
        public boolean onTouchDown(int gameX, int gameY, int pointer, int button) {
            if (button != 0 && button != 1)
                return true; //i only care about left and right click.

            for (int i = 0; i < sourceLayer.difficultyOptions.size(); ++i)
            {
                if (sourceLayer.difficultyOptions.get(i).click(gameX, gameY, button))
                {
                    sourceLayer.chooseDifficulty(i);
                    return true;
                }
            }
            for (int i = 0; i < sourceLayer.viewOptions.size(); ++i)
            {
                if (sourceLayer.viewOptions.get(i).click(gameX, gameY, button))
                {
                    sourceLayer.chooseType(i);
                    return true;
                }
            }
            sourceLayer.openButton.click(gameX, gameY, button);
            return true;
        }

        @Override
        public boolean onTouchUp(int gameX, int gameY, int pointer, int button) {
            return true;
        }

        @Override
        public boolean onTouchDragged(int gameX, int gameY, int pointer) {
            return true;
        }

        @Override
        public boolean onMouseMoved(int gameX, int gameY) {
            return true;
        }

        @Override
        public boolean scrolled(int amount) {
            return true;
        }
    }
}
