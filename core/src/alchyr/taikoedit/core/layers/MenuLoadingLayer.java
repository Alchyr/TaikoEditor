package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.editor.maps.BeatmapDatabase;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.textRenderer;

public class MenuLoadingLayer extends EditorLoadingLayer {
    private BitmapFont font;
    private float textY;

    @Override
    public void initialize() {
        super.initialize();
        font = assetMaster.getFont("base:aller small");
        textY = SettingsMaster.getHeight() / 2f - 160;
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        super.render(sb, sr);
        textRenderer.setFont(font)
                .renderTextCentered(sb, "Maps: ~" + BeatmapDatabase.mapCount, SettingsMaster.getMiddleX(), textY);
    }
}
