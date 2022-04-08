package alchyr.taikoedit.management.assets.skins;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.layers.LoadingLayer;
import alchyr.taikoedit.management.assets.RenderComponent;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

public abstract class SkinProvider {
    public String sfxDon = "hitsound:don", sfxKat = "hitsound:kat",
            sfxDonFinish = "hitsound:don finish", sfxKatFinish = "hitsound:kat finish";
    protected Texture hitBase, hitOverlay;

    public float largeScale = 1.5f, normalScale = 1, gameplaySpinnerScale = 1.25f;

    public Texture background;

    public RenderComponent<?> hit;
    public RenderComponent<?> finisher;
    private Texture selectionTexture; //Gotta Dispose
    public RenderComponent<?> selection;
    public RenderComponent<?> body;
    public RenderComponent<?> end;

    public RenderComponent<?> hitArea; //gameplay
    public Color gameplayHitAreaColor = new Color(1f, 1f, 1f, 0.6f);
    public RenderComponent<?> gameplaySpinner; //used purely for gameplay since spinners need to have a body in edit view
    public Color gameplaySpinnerColor = Color.LIGHT_GRAY.cpy();

    public LoadState state = LoadState.UNLOADED;
    public String failMsg = null;

    public enum LoadState {
        UNLOADED,
        FAILED,
        LOADED
    }

    abstract String getName();

    /*
        Should ensure all textures are loaded.
     */
    public LoadingLayer getLoader(ProgramLayer followupLayer) {
        return new LoadingLayer()
                .addTask(this::generateSelection)
                .addLayers(false, followupLayer);
    }
    //Generates a selection texture based on the overlay texture.
    public void generateSelection() {
        if (hitOverlay == null || selection != null)
            return;

        if (!hitOverlay.getTextureData().isPrepared())
            hitOverlay.getTextureData().prepare();
        Pixmap base = hitOverlay.getTextureData().consumePixmap();

        Pixmap selectionPixmap = new Pixmap(base.getWidth(), base.getHeight(), Pixmap.Format.RGBA8888);

        int color;
        for (int x = 0; x < base.getWidth(); ++x) {
            for (int y = 0; y < base.getHeight(); ++y) {
                color = base.getPixel(x, y); //rgba8888 format. 0xrrggbbaa, 0brrrrrrrr_gggggggg_bbbbbbbb_aaaaaaaa
                color = ((((color >>> 24) / 2) + 127) << 24) |
                        (((((color >>> 16) & 0b11111111) / 4) + 65) << 16) |
                        ((((color >>> 8) & 0b11111111) / 16) << 8) |
                        (color & 0x000000ff);

                selectionPixmap.drawPixel(x, y, color);
            }
        }

        if (hitOverlay.getTextureData().disposePixmap()) {
            base.dispose();
        }


        Object wait = new Object();
        TaikoEditor.onMain(()->{
            selectionTexture = new Texture(selectionPixmap);
            selectionTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            selection = new RenderComponent<>(selectionTexture, Texture.class);
            synchronized (wait) {
                wait.notify();
            }
        });
        try {
            synchronized (wait) {
                wait.wait(500);
            }
        }
        catch (InterruptedException ignored) { }
    }
    public void unload() {
        if (state == LoadState.LOADED)
            state = LoadState.UNLOADED;
        if (selectionTexture != null) {
            selectionTexture.dispose();
            selection = null;
            selectionTexture = null;
        }
    }

    public void dispose() {
        if (selectionTexture != null) {
            selectionTexture.dispose();
            selection = null;
            selectionTexture = null;
        }
    }

    @Override
    public String toString() {
        return getName();
    }
}
