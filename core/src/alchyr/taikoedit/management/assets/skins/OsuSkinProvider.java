package alchyr.taikoedit.management.assets.skins;

import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.layers.EditorLoadingLayer;
import alchyr.taikoedit.core.layers.LoadingLayer;
import alchyr.taikoedit.management.assets.AssetLists;
import alchyr.taikoedit.management.assets.FileHelper;
import alchyr.taikoedit.management.assets.RenderComponent;
import alchyr.taikoedit.util.GeneralUtils;
import alchyr.taikoedit.util.structures.Pair;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

import static alchyr.taikoedit.TaikoEditor.*;

public class OsuSkinProvider extends SkinProvider {
    private final File folder;
    private final String name;
    private final String prefix;

    private final String hitImg, overlayImg, bodyImg, endImg, finisherImg, finisherOverlayImg, spinnerImg, approachCircle, backgroundKey;

    private boolean hasFinisherImg = false, hasSpinner = false, hasApproachCircle = false;

    private SkinRequirements requirements = new SkinRequirements();

    public OsuSkinProvider(File folder) {
        this.folder = folder;
        this.name = folder.getName();

        prefix = ("skin" + getName() + ":").toLowerCase(Locale.ROOT);

        hitImg = prefix("hitbase");
        overlayImg = prefix("hit overlay");
        bodyImg = prefix("body");
        endImg = prefix("end");
        finisherImg = prefix("finisher");
        finisherOverlayImg = prefix("finisher overlay");
        spinnerImg = prefix("spinner");
        approachCircle = prefix("approach");
        backgroundKey = prefix("background");

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
            background = assetMaster.get("menu:background");

            FileHandle base = new FileHandle(folder);

            String file = FileHelper.gdxSeparator(FileHelper.concat(folder.getPath(), requirements.hitBaseFile));
            assetMaster.load(file, Texture.class, AssetLists.linear);
            assetMaster.loadedAssets.put(hitImg, file);

            file = FileHelper.gdxSeparator(FileHelper.concat(folder.getPath(), requirements.hitOverlayFile));
            assetMaster.load(file, Texture.class, AssetLists.linear);
            assetMaster.loadedAssets.put(overlayImg, file);

            file = FileHelper.gdxSeparator(FileHelper.concat(folder.getPath(), requirements.rollMiddle));
            assetMaster.load(file, Texture.class, AssetLists.linear);
            assetMaster.loadedAssets.put(bodyImg, file);

            file = FileHelper.gdxSeparator(FileHelper.concat(folder.getPath(), requirements.rollEnd));
            assetMaster.load(file, Texture.class, AssetLists.linear);
            assetMaster.loadedAssets.put(endImg, file);

            //optional stuff
            FileHandle testHandle = base.child("taiko-normal-hitnormal.wav");
            try {
                if (testHandle.exists()) {
                    sfxDon = prefix("don");
                    audioMaster.addSfx(sfxDon, testHandle.path());
                }
                testHandle = base.child("taiko-normal-hitclap.wav");
                if (testHandle.exists()) {
                    sfxKat = prefix("kat");
                    audioMaster.addSfx(sfxKat, testHandle.path());
                }
                testHandle = base.child("taiko-normal-hitfinish.wav");
                if (testHandle.exists()) {
                    sfxDonFinish = prefix("don finish");
                    audioMaster.addSfx(sfxDonFinish, testHandle.path());
                }
                testHandle = base.child("taiko-normal-hitwhistle.wav");
                if (testHandle.exists()) {
                    sfxKatFinish = prefix("kat finish");
                    audioMaster.addSfx(sfxKatFinish, testHandle.path());
                }
            }
            catch (GdxRuntimeException e) { //Likely due to wav format mismatch, since only 16-bit is compatible.
                int i = 0;
                Throwable cause = e.getCause(), prev = e;
                while (cause != null && i++ < 99) {
                    prev = cause;
                    cause = cause.getCause();
                }
                failMsg = prev.getMessage();
                editorLogger.error(prev.getMessage());
                GeneralUtils.logStackTrace(editorLogger, e);
                sfxDon = "hitsound:don";
                sfxKat = "hitsound:kat";
                sfxDonFinish = "hitsound:don finish";
                sfxKatFinish = "hitsound:kat finish";
            }

            testHandle = base.child(requirements.bigBaseFile);
            if (testHandle.exists()) {
                file = testHandle.path();
                testHandle = base.child(requirements.bigOverlayFile);
                if (testHandle.exists()) {
                    hasFinisherImg = true;
                    assetMaster.load(file, Texture.class, AssetLists.linear);
                    assetMaster.loadedAssets.put(finisherImg, file);

                    file = testHandle.path();
                    assetMaster.load(file, Texture.class, AssetLists.linear);
                    assetMaster.loadedAssets.put(finisherOverlayImg, file);
                }
            }

            testHandle = base.child(requirements.spinner);
            if (testHandle.exists()) {
                hasSpinner = true;
                assetMaster.load(testHandle.path(), Texture.class, AssetLists.linear);
                assetMaster.loadedAssets.put(spinnerImg, testHandle.path());
            }

            testHandle = base.child(requirements.approachCircle);
            if (testHandle.exists()) {
                hasApproachCircle = true;
                assetMaster.load(testHandle.path(), Texture.class, AssetLists.linear);
                assetMaster.loadedAssets.put(approachCircle, testHandle.path());
            }

            if (requirements.hasBg) {
                testHandle = base.child(requirements.bg);
                if (testHandle.exists()) {
                    assetMaster.load(testHandle.path(), Texture.class, AssetLists.linear);
                    assetMaster.loadedAssets.put(backgroundKey, testHandle.path());
                }
                else {
                    requirements.hasBg = false;
                }
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

        if (requirements.hasBg) {
            background = assetMaster.get(backgroundKey);
        }
    }

    public boolean isValid() {
        File[] contents = folder.listFiles(File::isFile);
        if (contents == null || contents.length == 0)
            return false;

        requirements = new SkinRequirements();

        for (File f : contents)
            requirements.test(f.getName());

        return requirements.isValid();
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

    private static class SkinRequirements {
        private static final String ini = "skin.ini";
        private static final String[] noAlt = { };
        //well, actually just the overlay allows for an animated variant.
        private static final String[] hitBase = { "taikohitcircle.png", "taikohitcircle-0.png" };
        private static final String[] hitOverlay = { "taikohitcircleoverlay.png", "taikohitcircleoverlay-0.png" };
        private static final String[] bigBase = { "taikobigcircle.png", "taikobigcircle-0.png" };
        private static final String[] bigOverlay = { "taikobigcircleoverlay.png", "taikobigcircleoverlay-0.png" };
        private static final String[] menuBg = { "menu-background.jpg", "menu-background.jpeg", "menu-background@2x.jpg", "menu-background@2x.jpeg" };

        //img
        String hitBaseFile = hitBase[0];
        String hitOverlayFile = hitOverlay[0];
        String bigBaseFile = bigBase[0];
        String bigOverlayFile = bigOverlay[0];
        String rollMiddle = "taiko-roll-middle.png";
        String rollEnd = "taiko-roll-end.png";
        String spinner = "spinner-warning.png";
        String approachCircle = "approachcircle.png";

        String bg = menuBg[0];
        boolean hasBg = false;

        //sfx

        private Map<String, Pair<String[], Consumer<String>>> required, optional;

        SkinRequirements() {
            required = new HashMap<>();
            optional = new HashMap<>();

            Pair<String[], Consumer<String>> item;

            item = new Pair<>(noAlt, GeneralUtils.noConsumer());
            required.put(ini, item);

            item = new Pair<>(hitBase, (s)->hitBaseFile = s);
            for (String s : hitBase)
                required.put(s, item);

            item = new Pair<>(hitOverlay, (s)->hitOverlayFile = s);
            for (String s : hitOverlay)
                required.put(s, item);

            item = new Pair<>(bigBase, (s)->bigBaseFile = s);
            for (String s : bigBase)
                optional.put(s, item);

            item = new Pair<>(bigOverlay, (s)->bigOverlayFile = s);
            for (String s : bigOverlay)
                optional.put(s, item);

            item = new Pair<>(noAlt, (s)->rollMiddle = s);
            required.put(rollMiddle, item);

            item = new Pair<>(noAlt, (s)->rollEnd = s);
            required.put(rollEnd, item);

            item = new Pair<>(noAlt, (s)->spinner = s);
            optional.put(spinner, item);

            item = new Pair<>(noAlt, (s)->approachCircle = s);
            optional.put(approachCircle, item);

            item = new Pair<>(menuBg, (s)->{ bg = s; hasBg = true; });
            for (String s : menuBg)
                optional.put(s, item);
        }

        public void test(String name) {
            Pair<String[], Consumer<String>> item = required.remove(name.toLowerCase(Locale.ROOT));
            if (item != null) {
                for (String s : item.a)
                    required.remove(s);
                item.b.accept(name);
            }
            else {
                item = optional.remove(name);
                if (item != null) {
                    for (String s : item.a)
                        optional.remove(s);
                    item.b.accept(name);
                }
            }
        }

        public boolean isValid() {
            return required.isEmpty();
        }
    }
}
