package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.management.AssetMaster;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Queue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.editorLogger;

//add on top of layer list when loading assets
public class LoadingLayer extends ProgramLayer {
    private final float circleSize, miniCircleSize, miniCircleDistance;
    private float angleA = 0;
    private float angleB = MathUtils.PI;
    private float dist;
    private final float TURN_RATE = MathUtils.PI2 * 0.5f; //0.5 rotations per second

    private final ExecutorService executor = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>()) {
        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            if (t != null) {
                editorLogger.error(t.getMessage(), t);
            }
            if (r instanceof FutureTask) {
                try {
                    ((FutureTask<?>) r).get();
                } catch (Exception e) {
                    editorLogger.error(e.getMessage(), e);
                }
            }
        }
    };

    private final Queue<ArrayList<Runnable>> tasks = new Queue<>();
    private final Queue<ArrayList<Tracker>> trackers = new Queue<>();
    private final Queue<ArrayList<Runnable>> callbacks = new Queue<>();
    private final ArrayList<Future<?>> activeTasks = new ArrayList<>();
    private final ArrayList<Tracker> activeTrackers = new ArrayList<>();

    private boolean assetsLoaded;
    private int taskCount;
    private float taskProgress;

    protected boolean done;
    protected boolean addedLayers;

    private String[] assetLists;
    private List<LoadInfo> extraLoads;
    private ProgramLayer[] replacementLayers;
    private boolean addToBottom;

    //If addToBottom, the last index in the array will end up on the bottom, first index on top (of the bottom)
    //If not addToBottom, the first index will be on the bottom of the top, and the last index will be on the very top.
    public LoadingLayer()
    {
        this.assetLists = null;
        this.extraLoads = new ArrayList<>();
        this.replacementLayers = null;
        this.addToBottom = false;

        assetsLoaded = false;
        taskProgress = 0;
        taskCount = 0;

        done = false;
        addedLayers = false;

        circleSize = Math.min(SettingsMaster.getHeight() / 6.0f, 80.0f);
        miniCircleSize = circleSize / 8;
        miniCircleDistance = circleSize - miniCircleSize;

        float rnd = MathUtils.random(MathUtils.PI);
        angleA += rnd;
        angleB += rnd;
    }
    public LoadingLayer addLayers(boolean addToBottom, ProgramLayer... replacementLayers) {
        this.replacementLayers = replacementLayers;
        this.addToBottom = addToBottom;
        return this;
    }
    public LoadingLayer loadLists(String... assetLists) {
        this.assetLists = assetLists;
        return this;
    }
    public LoadingLayer newSet() {
        tasks.addFirst(new ArrayList<>());
        trackers.addFirst(new ArrayList<>());
        return this;
    }
    public LoadingLayer addCallback(boolean newSet, Runnable callback)
    {
        if (callbacks.isEmpty() || newSet)
        {
            callbacks.addFirst(new ArrayList<>());
        }
        callbacks.first().add(callback);
        return this;
    }
    public LoadingLayer addCallback(Runnable callback)
    {
        return addCallback(false, callback);
    }
    public LoadingLayer addTask(boolean newSet, Runnable runnable)
    {
        if (tasks.isEmpty() || newSet)
        {
            tasks.addFirst(new ArrayList<>());
            trackers.addFirst(new ArrayList<>());
        }
        tasks.first().add(runnable);
        return this;
    }
    public LoadingLayer addTask(Runnable runnable)
    {
        return addTask(false, runnable);
    }
    public LoadingLayer addTracker(Supplier<Float> tracker)
    {
        return addTracker(tracker, false);
    }
    public LoadingLayer addTracker(Supplier<Float> tracker, boolean mustFinish) {
        return addTracker(tracker, null, mustFinish);
    }
    public LoadingLayer addTracker(Supplier<Float> tracker, Supplier<Boolean> confirmation, boolean mustFinish) {
        if (tasks.isEmpty())
        {
            tasks.addFirst(new ArrayList<>());
            trackers.addFirst(new ArrayList<>());
        }
        trackers.first().add(new Tracker(tracker, confirmation, mustFinish));
        return this;
    }
    public LoadingLayer loadExtra(String key, String file, Class<?> type)
    {
        extraLoads.add(new LoadInfo(key, file, type));
        return this;
    }

    @Override
    public void initialize() {
        if (assetLists != null) {
            for (String assetList : assetLists)
                assetMaster.loadList(assetList);
        }
        for (LoadInfo info : extraLoads)
            assetMaster.load(info.key, info.file, info.clazz);
    }

    @Override
    public void update(float elapsed) {
        angleA = (angleA + elapsed * TURN_RATE) % MathUtils.PI2;
        angleB = (angleB + elapsed * TURN_RATE) % MathUtils.PI2;

        if (!done && updateLoading())
        {
            //done with current tasks
            activeTasks.clear();
            activeTrackers.clear();

            if (!tasks.isEmpty())
            {
                ArrayList<Runnable> taskSet = tasks.removeLast();
                taskCount = taskSet.size();
                editorLogger.info("Starting next loading task set. Tasks: " + taskCount);
                for (Runnable task : taskSet)
                    activeTasks.add(executor.submit(task));

                if (!trackers.isEmpty())
                {
                    ArrayList<Tracker> trackerSet = trackers.removeLast();
                    editorLogger.info("Task set has trackers: " + trackerSet.size());
                    activeTrackers.addAll(trackerSet);
                }
                return;
            }

            done = true;
        }

        if (addLayers()) {
            if (replacementLayers != null)
            {
                editorLogger.info("Loading complete. Adding replacement layers: " + replacementLayers.length);
                for (ProgramLayer l : replacementLayers) {
                    if (addToBottom)
                    {
                        TaikoEditor.addLayerToBottom(l);
                    }
                    else
                    {
                        TaikoEditor.addLayer(l);
                    }
                }
                //use a fancy effect instead later instead of sharp change
                replacementLayers = null;
                return;
            }

            if (!callbacks.isEmpty()) //Trigger callbacks
            {
                ArrayList<Runnable> taskSet = callbacks.removeLast();
                taskCount = taskSet.size();
                editorLogger.info("Starting next callback set. Tasks: " + taskCount);
                for (Runnable task : taskSet)
                    activeTasks.add(executor.submit(task));
                return;
            }

            addedLayers = true;
        }
        if (complete()) {
            TaikoEditor.removeLayer(this);
        }
    }

    private boolean updateLoading()
    {
        assetsLoaded = assetMaster.isDoneLoading();

        boolean tasksDone = true;

        float completed = 0;

        for (Future<?> f : activeTasks)
            if (!f.isDone())
                tasksDone = false;
            else
                ++completed;

        if (!activeTrackers.isEmpty())
        {
            taskProgress = 1;
            for (Tracker tracker : activeTrackers)
            {
                taskProgress *= tracker.getProgress();
                if (!tracker.isComplete())
                    tasksDone = false;
            }
        }
        else if (taskCount > 0)
            taskProgress = completed / taskCount;
        else
            taskProgress = 1;

        return assetsLoaded && tasksDone;
    }

    public float getLoadProgress(AssetMaster assetMaster) {
        return assetMaster.getProgress() * taskProgress;
    }

    public boolean addLayers() {
        return done && !addedLayers;
    }
    public boolean complete() {
        return done;
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        //progress float from 0 to 1 is assetMaster.getProgress() * taskProgress
        float progress = assetMaster.getProgress() * taskProgress;

        //render a nice relaxing loading screen?
        sb.end();
        sr.begin(ShapeRenderer.ShapeType.Filled);

        if (progress <= 1)
        {
            float offset = progress * circleSize * 2;

            sr.setColor(Color.WHITE);
            sr.circle(SettingsMaster.getMiddle(), SettingsMaster.getHeight() / 2.0f, circleSize);
            sr.setColor(Color.BLACK);
            sr.circle(SettingsMaster.getMiddle() + offset - circleSize * 2, SettingsMaster.getHeight() / 2.0f, circleSize);
        }

        float xOffset = MathUtils.cos(angleA) * miniCircleDistance;
        float yOffset = MathUtils.sin(angleA) * miniCircleDistance;
        sr.setColor(Color.WHITE);
        sr.circle(SettingsMaster.getMiddle() + xOffset, SettingsMaster.getHeight() / 2.0f + yOffset, miniCircleSize);

        xOffset = MathUtils.cos(angleB) * miniCircleDistance;
        yOffset = MathUtils.sin(angleB) * miniCircleDistance;
        sr.setColor(Color.BLACK);
        sr.circle(SettingsMaster.getMiddle() + xOffset, SettingsMaster.getHeight() / 2.0f + yOffset, miniCircleSize);

        sr.end();
        sb.begin();
    }

    @Override
    public void dispose() {
        try {
            editorLogger.info("Shutting down LoadingLayer executor.");
            executor.shutdown();
            if (executor.awaitTermination(2, TimeUnit.SECONDS)) {
                editorLogger.info("Executor shut down successfully.");
            }
            else {
                if (!executor.isTerminated()) {
                    editorLogger.error("Tasks were unfinished. Shutting down forcefully.");
                    executor.shutdownNow();
                }
            }
        }
        catch (InterruptedException e) {
            editorLogger.error("Tasks interrupted.");
        }
    }

    private static class LoadInfo {
        final String key;
        final String file;
        final Class<?> clazz;

        public LoadInfo(String s, String file, Class<?> clazz) {
            this.key = s;
            this.file = file;
            this.clazz = clazz;
        }
    }

    private static class Tracker {
        private final Supplier<Float> progress;
        private final Supplier<Boolean> confirmation;
        private final boolean mustFinish;

        public Tracker(Supplier<Float> progress, Supplier<Boolean> confirmation, boolean mustFinish) {
            this.progress = progress;
            this.confirmation = confirmation;
            this.mustFinish = mustFinish;
        }

        public float getProgress() {
            return progress.get();
        }

        public boolean isComplete() {
            if (mustFinish) {
                return confirmation == null ? progress.get() >= 1 : confirmation.get();
            }
            return true;
        }
    }
}
