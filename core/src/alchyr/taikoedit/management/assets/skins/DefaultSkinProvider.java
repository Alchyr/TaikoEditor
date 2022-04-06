package alchyr.taikoedit.management.assets.skins;

import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.layers.LoadingLayer;
import alchyr.taikoedit.management.assets.RenderComponent;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;

import static alchyr.taikoedit.TaikoEditor.assetMaster;

public class DefaultSkinProvider extends SkinProvider {
    public DefaultSkinProvider() {

    }

    @Override
    public LoadingLayer getLoader(ProgramLayer followupLayer) {
        return new LoadingLayer()
                .addTask(this::prepRenderables)
                .addTask(true, this::generateSelection)
                .addLayers(false, followupLayer);
    }

    private void prepRenderables() {
        hitBase = assetMaster.get("editor:hit");
        hitOverlay = assetMaster.get("editor:overlay");

        hit = new RenderComponent<>(hitBase, Texture.class).chain(new RenderComponent<>(hitOverlay, new RenderComponent.FixedColorTexture(Color.WHITE)));
        finisher = hit;
        body = assetMaster.getRenderComponent("editor:body", Texture.class);
        end = hit;
        gameplaySpinner = hit;
        hitArea = assetMaster.getRenderComponent("editor:hitarea", Texture.class);

        state = LoadState.LOADED;
    }

    @Override
    public String getName() {
        return "Default";
    }
}
