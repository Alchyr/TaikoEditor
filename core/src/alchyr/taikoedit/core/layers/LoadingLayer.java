package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.ProgramLayer;
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

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Queue<ArrayList<Runnable>> tasks = new Queue<>();
    private final Queue<ArrayList<Supplier<Float>>> trackers = new Queue<>();
    private final Queue<ArrayList<Runnable>> callbacks = new Queue<>();
    private final ArrayList<Future<?>> activeTasks = new ArrayList<>();
    private final ArrayList<Supplier<Float>> activeTrackers = new ArrayList<>();
    private boolean assetsLoaded;
    private int taskCount;
    private float taskProgress;

    private final ProgramLayer[] replacementLayers;
    private final boolean addToBottom;

    private boolean complete = false;

    //If addToBottom, the last index in the array will end up on the bottom, first index on top (of the bottom)
    //If not addToBottom, the first index will be on the bottom of the top, and the last index will be on the very top.
    public LoadingLayer(String[] assetLists, ProgramLayer[] replacementLayers, boolean addToBottom)
    {
        for (String assetList : assetLists)
            if (assetList != null)
                assetMaster.loadList(assetList);

        this.replacementLayers = replacementLayers;
        this.addToBottom = addToBottom;

        assetsLoaded = false;
        taskProgress = 0;
        taskCount = 0;

        circleSize = Math.min(SettingsMaster.getHeight() / 6.0f, 80.0f);
        miniCircleSize = circleSize / 8;
        miniCircleDistance = circleSize - miniCircleSize;

        float rnd = MathUtils.random(MathUtils.PI);
        angleA += rnd;
        angleB += rnd;
    }
    public LoadingLayer(String assetList, ProgramLayer[] replacementLayers, boolean addToBottom)
    {
        this (new String[] { assetList }, replacementLayers, addToBottom);
    }
    public LoadingLayer(String[] assetLists, ProgramLayer replacementLayer, boolean addToBottom)
    {
        this(assetLists, new ProgramLayer[] { replacementLayer }, addToBottom);
    }
    public LoadingLayer(String assetList, ProgramLayer replacementLayer)
    {
        this(assetList, new ProgramLayer[] { replacementLayer }, false);
    }
    public LoadingLayer(String assetList, ProgramLayer[] replacementLayers)
    {
        this(assetList, replacementLayers, false);
    }
    public LoadingLayer(String[] assetLists, ProgramLayer[] replacementLayers)
    {
        this(assetLists, replacementLayers, false);
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
        trackers.first().add(tracker);
        return this;
    }

    public LoadingLayer loadExtra(String key, String file, Class<?> type)
    {
        assetMaster.load(key, file, type);
        return this;
    }


    @Override
    public void update(float elapsed) {
        angleA = (angleA + elapsed * TURN_RATE) % MathUtils.PI2;
        angleB = (angleB + elapsed * TURN_RATE) % MathUtils.PI2;

        if (doneLoading())
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
                    ArrayList<Supplier<Float>> trackerSet = trackers.removeLast();
                    editorLogger.info("Task set has trackers: " + trackerSet.size());
                    activeTrackers.addAll(trackerSet);
                }
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

            complete = true;
        }
    }

    private boolean doneLoading()
    {
        /*if (!assetsLoaded)
        {
            assetsLoaded = assetMaster.update();
        }*/
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
            for (Supplier<Float> tracker : activeTrackers)
            {
                taskProgress *= tracker.get();
            }
        }
        else if (taskCount > 0)
            taskProgress = completed / taskCount;
        else
            taskProgress = 1;

        return assetsLoaded && tasksDone;
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

        if (complete) {
            if (replacementLayers != null && replacementLayers.length != 0)
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
                    l.initialize();
                }
                //use a fancy effect instead later instead of sharp change
            }
            else
            {
                editorLogger.info("Loading complete. No replacement layers.");
            }

            TaikoEditor.removeLayer(this);
        }
    }

    @Override
    public void dispose() {
        try {
            editorLogger.info("Shutting down LoadingLayer executor.");
            List<Runnable> unfinished = executor.shutdownNow();
            if (!unfinished.isEmpty())
                editorLogger.info(unfinished.size() + " tasks were unfinished.");
        } finally {
            editorLogger.info("Executor shut down successfully.");
        }
    }
}
