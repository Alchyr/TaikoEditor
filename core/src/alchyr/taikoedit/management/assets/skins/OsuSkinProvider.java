package alchyr.taikoedit.management.assets.skins;

import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.layers.EditorLoadingLayer;
import alchyr.taikoedit.core.layers.LoadingLayer;
import alchyr.taikoedit.management.assets.AssetLists;
import alchyr.taikoedit.management.assets.FileHelper;
import alchyr.taikoedit.management.assets.RenderComponent;
import alchyr.taikoedit.util.GeneralUtils;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static alchyr.taikoedit.TaikoEditor.*;

public class OsuSkinProvider extends SkinProvider {
    private final File folder;
    private final String name;
    private final String prefix;

    private final String hitImg, overlayImg, bodyImg, endImg, finisherImg, finisherOverlayImg, spinnerImg, approachCircle;

    private boolean hasFinisherImg = false, hasSpinner = false, hasApproachCircle = false;

    public OsuSkinProvider(File folder) {
        this.folder = folder;
        this.name = folder.getName();

        prefix = ("skin" + getName() + ":").toLowerCase();

        hitImg = prefix("hitbase");
        overlayImg = prefix("hit overlay");

        bodyImg = prefix("body");
        endImg = prefix("end");

        finisherImg = prefix("finisher");
        finisherOverlayImg = prefix("finisher overlay");

        spinnerImg = prefix("spinner");

        approachCircle = prefix("approach");

        gameplayHitAreaColor = new Color(0.7f, 0.7f, 0.7f, 0.7f);

        largeScale *= 0.75f;
        normalScale *= 0.75f;
    }

    private String prefix(String post) {
        return prefix + post;
    }
    @Override
    public String getName() {
        return name;
    }

    @Override
    public LoadingLayer getLoader(ProgramLayer followupLayer) {
        return new EditorLoadingLayer() {
            @Override
            public void initialize() {
                super.initialize();
                loadAssets();
            }
        }.addLayers(false,
                ()->new ProgramLayer[] {
                        new LoadingLayer()
                                .addTask(this::prepRenderables)
                                .addTask(true, this::generateSelection)
                                .addTask(true, ()->{
                                    if (state != LoadState.FAILED) state = LoadState.LOADED;
                                })
                                .addLayers(false, followupLayer)
                }
        );
    }

    private void loadAssets() {
        try {
            assetMaster.markLoading();
            String file = FileHelper.gdxSeparator(FileHelper.concat(folder.getPath(), "taikohitcircle.png"));
            assetMaster.load(file, Texture.class, AssetLists.linear);
            assetMaster.loadedAssets.put(hitImg, file);

            file = FileHelper.gdxSeparator(FileHelper.concat(folder.getPath(), "taikohitcircleoverlay.png"));
            assetMaster.load(file, Texture.class, AssetLists.linear);
            assetMaster.loadedAssets.put(overlayImg, file);

            file = FileHelper.gdxSeparator(FileHelper.concat(folder.getPath(), "taiko-roll-middle.png"));
            assetMaster.load(file, Texture.class, AssetLists.linear);
            assetMaster.loadedAssets.put(bodyImg, file);

            file = FileHelper.gdxSeparator(FileHelper.concat(folder.getPath(), "taiko-roll-end.png"));
            assetMaster.load(file, Texture.class, AssetLists.linear);
            assetMaster.loadedAssets.put(endImg, file);

            //optional stuff
            FileHandle testHandle = new FileHandle(folder).child("taiko-normal-hitnormal.wav");
            if (testHandle.exists()) {
                sfxDon = prefix("don");
                audioMaster.addSfx(sfxDon, testHandle.path());
            }
            testHandle = new FileHandle(folder).child("taiko-normal-hitclap.wav");
            if (testHandle.exists()) {
                sfxKat = prefix("kat");
                audioMaster.addSfx(sfxKat, testHandle.path());
            }
            testHandle = new FileHandle(folder).child("taiko-normal-hitfinish.wav");
            if (testHandle.exists()) {
                sfxDonFinish = prefix("don finish");
                audioMaster.addSfx(sfxDonFinish, testHandle.path());
            }
            testHandle = new FileHandle(folder).child("taiko-normal-hitwhistle.wav");
            if (testHandle.exists()) {
                sfxKatFinish = prefix("kat finish");
                audioMaster.addSfx(sfxKatFinish, testHandle.path());
            }

            testHandle = new FileHandle(folder).child("taikobigcircle.png");
            if (testHandle.exists()) {
                file = testHandle.path();
                testHandle = new FileHandle(folder).child("taikobigcircleoverlay.png");
                if (testHandle.exists()) {
                    hasFinisherImg = true;
                    assetMaster.load(file, Texture.class, AssetLists.linear);
                    assetMaster.loadedAssets.put(finisherImg, file);

                    file = testHandle.path();
                    assetMaster.load(file, Texture.class, AssetLists.linear);
                    assetMaster.loadedAssets.put(finisherOverlayImg, file);
                }
            }

            testHandle = new FileHandle(folder).child("spinner-warning.png");
            if (testHandle.exists()) {
                hasSpinner = true;
                assetMaster.load(testHandle.path(), Texture.class, AssetLists.linear);
                assetMaster.loadedAssets.put(spinnerImg, testHandle.path());
            }

            testHandle = new FileHandle(folder).child("approachcircle.png");
            if (testHandle.exists()) {
                hasApproachCircle = true;
                assetMaster.load(testHandle.path(), Texture.class, AssetLists.linear);
                assetMaster.loadedAssets.put(approachCircle, testHandle.path());
            }
        }
        catch (GdxRuntimeException e) {
            int i = 0;
            Throwable cause = e.getCause(), prev = e;
            while (cause != null && i++ < 99) {
                prev = cause;
                cause = cause.getCause();
            }
            failMsg = prev.getMessage();
            editorLogger.error(prev.getMessage());
            GeneralUtils.logStackTrace(editorLogger, e);
            state = LoadState.FAILED;
        }
    }

    private void prepRenderables() {
        hitBase = assetMaster.get(hitImg);
        hitOverlay = assetMaster.get(overlayImg);

        hit = new RenderComponent<>(hitBase, Texture.class).chain(new RenderComponent<>(hitOverlay, new RenderComponent.FixedColorTexture(Color.WHITE)));

        hitArea = new RenderComponent<>(hitBase, Texture.class);
        if (hasApproachCircle) {
            Texture approachCircleTexture = assetMaster.get(approachCircle);
            hitArea.chain(new RenderComponent<>(approachCircleTexture, Texture.class));
        }

        body = assetMaster.getRenderComponent(bodyImg, Texture.class);
        Texture endTexture = assetMaster.get(endImg);
        end = new RenderComponent<>(endTexture, new RenderComponent.TextureRenderer(Align.left));

        if (hasFinisherImg) {
            Texture finisherBase = assetMaster.get(finisherImg);
            Texture finisherOverlay = assetMaster.get(finisherOverlayImg);

            finisher = new RenderComponent<>(finisherBase, Texture.class).chain(new RenderComponent<>(finisherOverlay, new RenderComponent.FixedColorTexture(Color.WHITE)));
        }
        else {
            finisher = hit;
        }

        if (hasSpinner) {
            gameplaySpinnerColor = Color.WHITE.cpy();
            gameplaySpinner = assetMaster.getRenderComponent(spinnerImg, Texture.class);

            float targetSize = finisher.getWidth() * largeScale;
            gameplaySpinnerScale = targetSize / gameplaySpinner.getWidth();
        }
        else {
            gameplaySpinnerScale = largeScale;
            gameplaySpinner = hit;
        }
    }

    public static boolean isValid(File folder) {
        Set<String> requirements = getRequirements();
        File[] contents = folder.listFiles(File::isFile);
        if (contents == null || contents.length == 0)
            return false;

        for (File file : contents) {
            requirements.remove(file.getName());
        }

        return requirements.isEmpty();
    }

    @Override
    public void unload() {
        super.unload();

        assetMaster.unload(hitImg);
        assetMaster.unload(overlayImg);
        assetMaster.unload(bodyImg);
        assetMaster.unload(endImg);

        if (hasFinisherImg) {
            assetMaster.unload(finisherImg);
            assetMaster.unload(finisherOverlayImg);
        }

        if (hasSpinner)
            assetMaster.unload(spinnerImg);

        if (hasApproachCircle)
            assetMaster.unload(approachCircle);
    }

    private static final Set<String> skinRequirements;
    static {
        skinRequirements = new HashSet<>();
        skinRequirements.add("skin.ini");
        /*skinRequirements.add("taiko-normal-hitnormal.wav");
        skinRequirements.add("taiko-normal-hitfinish.wav");
        skinRequirements.add("taiko-normal-hitclap.wav");
        skinRequirements.add("taiko-normal-hitwhistle.wav");*/

        skinRequirements.add("taikohitcircle.png");
        skinRequirements.add("taikohitcircleoverlay.png");

        skinRequirements.add("taiko-roll-middle.png");
        skinRequirements.add("taiko-roll-end.png");
        //skinRequirements.add("");
    }
    private static Set<String> getRequirements() {
        return new HashSet<>(skinRequirements);
    }
}
