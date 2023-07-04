package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.input.AdjustedInputProcessor;
import alchyr.taikoedit.util.Sync;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Queue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static alchyr.taikoedit.TaikoEditor.*;

//add on top of layer list when loading assets
public class LoadingLayer extends ProgramLayer implements InputLayer {
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
    private Supplier<ProgramLayer[]> replacementLayers;
    private boolean addToBottom;

    private LoadingInputProcessor processor;


    //Debug
    private long assetLoadTime = 0;
    private long taskSetTime = 0;

    //If addToBottom, the last index in the array will end up on the bottom, first index on top (of the bottom)
    //If not addToBottom, the first index will be on the bottom of the top, and the last index will be on the very top.
    public LoadingLayer()
    {
        this.type = LAYER_TYPE.NORMAL;

        this.assetLists = null;
        this.extraLoads = new ArrayList<>();
        this.replacementLayers = null;
        this.addToBottom = false;

        assetsLoaded = false;
        taskProgress = 0;
        taskCount = 0;

        done = false;
        addedLayers = false;
    }
    public LoadingLayer addLayers(boolean addToBottom, ProgramLayer... replacementLayers) {
        this.replacementLayers = ()->replacementLayers;
        this.addToBottom = addToBottom;
        return this;
    }
    public LoadingLayer addLayers(boolean addToBottom, Supplier<ProgramLayer[]> replacementLayers) {
        this.replacementLayers = replacementLayers;
        this.addToBottom = addToBottom;
        return this;
    }
    public LoadingLayer loadLists(String... assetLists) {
        this.assetLists = assetLists;
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
    public LoadingLayer newSet() {
        tasks.addFirst(new ArrayList<>());
        trackers.addFirst(new ArrayList<>());
        return this;
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
    public LoadingLayer addWait(float time) {
        return addTask(()->{
            try {
                Thread.sleep((long) (time * 1000));
            } catch (InterruptedException ignored) {
            }
        });
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
    public LoadingLayer addFailure(Supplier<Boolean> isFailed) {
        tasks.addFirst(new ArrayList<>());
        trackers.addFirst(new ArrayList<>());
        tasks.first().add(
                ()->{
                    if (isFailed.get()) {
                        tasks.clear();
                        trackers.clear();
                    }
                }
        );
        return this;
    }

    public LoadingLayer loadExtra(String key, String file, Class<?> type)
    {
        extraLoads.add(new LoadInfo(key, file, type));
        return this;
    }

    @Override
    public InputProcessor getProcessor() {
        if (processor == null) {
            processor = new LoadingInputProcessor(type == LAYER_TYPE.UPDATE_STOP || type == LAYER_TYPE.FULL_STOP);
        }
        return processor;
    }

    @Override
    public void initialize() {
        if (assetLists != null || !extraLoads.isEmpty())
            assetLoadTime = System.nanoTime();

        if (assetLists != null) {
            for (String assetList : assetLists)
                assetMaster.loadList(assetList);
        }
        for (LoadInfo info : extraLoads)
            assetMaster.load(info.key, info.file, info.clazz);
    }

    @Override
    public void update(float elapsed) {
        if (!done && updateLoading())
        {
            if (taskSetTime != 0) {
                taskSetTime = System.nanoTime() - taskSetTime;
                editorLogger.debug("Task set complete: took " + (1.0 * taskSetTime / Sync.NANOS_IN_SECOND) + " seconds.");
            }
            //done with current tasks
            activeTasks.clear();
            activeTrackers.clear();

            if (!tasks.isEmpty())
            {
                taskSetTime = System.nanoTime();

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
                ProgramLayer[] layers = replacementLayers.get();
                editorLogger.info("Loading complete. Adding replacement layers: " + layers.length);
                for (ProgramLayer l : layers) {
                    if (l != null) {
                        if (addToBottom)
                        {
                            addLayerToBottom(l);
                        }
                        else
                        {
                            addLayer(l);
                        }
                    }
                }
                //use a fancy effect instead later instead of sharp change
                replacementLayers = null;
                addedLayers = true;
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
        }
        if (complete()) {
            removeLayer(this);
        }
    }

    private boolean updateLoading()
    {
        if (!assetsLoaded && assetMaster.isDoneLoading()) {
            if (assetLoadTime != 0) {
                assetLoadTime = System.nanoTime() - assetLoadTime;
                editorLogger.debug("Asset loading complete: took " + (1.0 * assetLoadTime / Sync.NANOS_IN_SECOND) + " seconds.");
            }
            assetsLoaded = true;
        }

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

    public float getLoadProgress() {
        return MathUtils.clamp(assetMaster.getProgress() * taskProgress, 0f, 1f);
    }

    public boolean addLayers() {
        return done;
    }
    public boolean complete() {
        return done;
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

    private static class LoadingInputProcessor extends AdjustedInputProcessor {
        private final boolean blocking;

        public LoadingInputProcessor(boolean blocking) {
            super();

            this.blocking = blocking;
        }

        @Override
        public boolean keyDown(int keycode) {
            return blocking;
        }

        @Override
        public boolean keyUp(int keycode) {
            return blocking;
        }

        @Override
        public boolean keyTyped(char character) {
            return blocking;
        }

        @Override
        public boolean onTouchDown(int gameX, int gameY, int pointer, int button) {
            return blocking;
        }

        @Override
        public boolean onTouchDragged(int gameX, int gameY, int pointer) {
            return blocking;
        }

        @Override
        public boolean onTouchUp(int gameX, int gameY, int pointer, int button) {
            return blocking;
        }

        @Override
        public boolean onMouseMoved(int gameX, int gameY) {
            return blocking;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            return blocking;
        }
    }
}
