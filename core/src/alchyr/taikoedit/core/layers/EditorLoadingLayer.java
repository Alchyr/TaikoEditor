package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

//add on top of layer list when loading assets
public class EditorLoadingLayer extends LoadingLayer {
    private static final int WIDTH = 128, HEIGHT = 256, MID_X = 64, MID_Y = 128, SCALE_Y = 14;

    private float x, y;

    private Texture area, bar;

    @Override
    public void initialize() {
        area = assetMaster.get("base:load_area");
        bar = assetMaster.get("base:load_bar");

        x = SettingsMaster.getMiddleX() - MID_X;
        y = SettingsMaster.getHeight() / 2f - MID_Y;

        super.initialize();
    }

    @Override
    public void update(float elapsed) {
        super.update(elapsed);
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        if (!done) {
            sb.setColor(Color.WHITE);
            sb.draw(area, x, y);
            sb.draw(bar, x, y, MID_X, SCALE_Y, WIDTH, HEIGHT, 1, getLoadProgress(), 0, 0, 0, WIDTH, HEIGHT, false, false);
        }
    }
}
