package alchyr.taikoedit.core.layers.sub;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.input.AdjustedInputProcessor;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.core.ui.Button;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.editor.maps.FullMapInfo;
import alchyr.taikoedit.editor.maps.Mapset;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.List;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class DifficultySettingsLayer extends ProgramLayer implements InputLayer {
    private static DifficultySettingsProcessor processor;

    //Rendering
    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 0.83f);

    //Textures
    private final Texture pix;

    //Positions
    private final int middleY = SettingsMaster.getHeight() / 2;
    private final int minRenderY = -(middleY + 30);

    //For smoothness

    //Parts
    private final List<Button> difficultyOptions;
    private final List<Button> viewOptions;
    private final Button saveButton;

    //Scroll
    private int difficultyIndex = 0, viewIndex = 0;

    //Other data
    private final EditorLayer sourceLayer;


    //Editing existing difficulty
    public DifficultySettingsLayer(EditorLayer editor, Mapset set, FullMapInfo difficulty)
    {
        this.type = LAYER_TYPE.UPDATE_STOP;

        sourceLayer = editor;

        processor = new DifficultySettingsProcessor(this);
        pix = assetMaster.get("ui:pixel");

        int difficultyOptionX = SettingsMaster.getWidth() / 4;
        int viewOptionX = SettingsMaster.getWidth() / 2;
        int openButtonX = difficultyOptionX * 3;

        difficultyOptions = new ArrayList<>();

        difficultyOptions.add(new Button(difficultyOptionX, middleY, "Create New", assetMaster.getFont("aller medium"), null).setAction("NEW"));

        viewOptions = new ArrayList<>();
        viewOptions.add(new Button(viewOptionX, middleY, "Object Editor", assetMaster.getFont("aller medium"), null).setAction("objects"));
        viewOptions.add(new Button(viewOptionX, middleY, "SV Editor", assetMaster.getFont("aller medium"), null).setAction("sv"));
        viewOptions.add(new Button(viewOptionX, middleY, "Gameplay View", assetMaster.getFont("aller medium"), null).setAction("gameplay"));

        saveButton = new Button(openButtonX, middleY, "Save", assetMaster.getFont("aller medium"), this::save);
    }

    private void save(int button)
    {
        TaikoEditor.removeLayer(this);
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
        saveButton.update();
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        sb.setColor(backColor);
        sb.draw(pix, 0, 0, SettingsMaster.getWidth(), SettingsMaster.getHeight());

        /*int index = 0;
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
        }*/

        saveButton.render(sb, sr);

        sb.setColor(Color.WHITE);
        //sb.draw(pix, linePos, middleY - 1, lineWidth, 3);
        //sb.draw(pix, linePosB, middleY - 1, lineWidthB, 3);
    }

    private void cancel()
    {
        TaikoEditor.removeLayer(this);
    }


    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    private static class DifficultySettingsProcessor extends AdjustedInputProcessor {
        private final DifficultySettingsLayer sourceLayer;

        public DifficultySettingsProcessor(DifficultySettingsLayer source)
        {
            this.sourceLayer = source;
        }

        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) //Process input using the current primary view? TODO: Add keybindings? SettingsMaster has some keybind code.
            {
                case Input.Keys.ESCAPE:
                    sourceLayer.cancel();
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

            sourceLayer.saveButton.click(gameX, gameY, button);
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