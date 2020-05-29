package alchyr.taikoedit.core.layers;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.GameLayer;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Queue;

import java.util.ArrayList;
import java.util.concurrent.*;

import static alchyr.taikoedit.TaikoEditor.assetMaster;
import static alchyr.taikoedit.TaikoEditor.editorLogger;

//add on top of layer list when loading assets
public class LoadingLayer extends GameLayer {
    private static final float LOAD_SIZE = 420.0f;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Queue<ArrayList<Runnable>> tasks = new Queue<>();
    private final Queue<ArrayList<Runnable>> callbacks = new Queue<>();
    private final ArrayList<Future<?>> activeTasks = new ArrayList<>();
    private boolean assetsLoaded;
    private int taskCount;
    private float taskProgress;

    private GameLayer[] replacementLayers;
    private boolean addToBottom;
    private boolean initializedReplacements;


    //If addToBottom, the last index in the array will end up on the bottom, first index on top (of the bottom)
    //If not addToBottom, the first index will be on the bottom of the top, and the last index will be on the very top.
    public LoadingLayer(String[] assetLists, GameLayer[] replacementLayers, boolean addToBottom)
    {
        for (String assetList : assetLists)
            assetMaster.loadList(assetList);

        this.replacementLayers = replacementLayers;
        this.addToBottom = addToBottom;
        initializedReplacements = false;

        assetsLoaded = false;
        taskProgress = 0;
        taskCount = 0;
    }
    public LoadingLayer(String assetList, GameLayer[] replacementLayers, boolean addToBottom)
    {
        this (new String[] { assetList }, replacementLayers, addToBottom);
    }
    public LoadingLayer(String[] assetLists, GameLayer replacementLayer, boolean addToBottom)
    {
        this(assetLists, new GameLayer[] { replacementLayer }, addToBottom);
    }
    public LoadingLayer(String assetList, GameLayer replacementLayer)
    {
        this(assetList, new GameLayer[] { replacementLayer }, false);
    }
    public LoadingLayer(String assetList, GameLayer[] replacementLayers)
    {
        this(assetList, replacementLayers, false);
    }
    public LoadingLayer(String[] assetLists, GameLayer[] replacementLayers)
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
        }
        tasks.first().add(runnable);
        return this;
    }
    public LoadingLayer addTask(Runnable runnable)
    {
        return addTask(false, runnable);
    }
    public LoadingLayer loadExtra(String key, String file, Class<?> type)
    {
        assetMaster.load(key, file, type);
        return this;
    }


    @Override
    public void update(float elapsed) {
        if (doneLoading())
        {
            //done with current tasks
            activeTasks.clear();

            if (!tasks.isEmpty())
            {
                ArrayList<Runnable> taskSet = tasks.removeLast();
                taskCount = taskSet.size();
                editorLogger.info("Starting next loading task set. Tasks: " + taskCount);
                for (Runnable task : taskSet)
                    activeTasks.add(executor.submit(task));
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

            if (replacementLayers != null && replacementLayers.length != 0)
            {
                editorLogger.info("Loading complete. Adding replacement layers: " + replacementLayers.length);
                for (GameLayer l : replacementLayers) {
                    l.initialize();

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
            }
            else
            {
                editorLogger.info("Loading complete. No replacement layers.");
            }

            TaikoEditor.removeLayer(this);
        }
    }

    private boolean doneLoading()
    {
        if (!assetsLoaded)
            assetsLoaded = assetMaster.update();

        boolean tasksDone = true;

        float completed = 0;

        for (Future<?> f : activeTasks)
            if (!f.isDone())
                tasksDone = false;
            else
                ++completed;

        if (taskCount > 0)
            taskProgress = completed / taskCount;
        else
            taskProgress = 1;

        return assetsLoaded && tasksDone;
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        //progress float from 0 to 1 is assetMaster.getProgress() * taskProgress
        float offset = assetMaster.getProgress() * taskProgress * LOAD_SIZE * SettingsMaster.SCALE;

        //render progress
        //render a nice relaxing loading screen?
        sb.end();

        sr.begin(ShapeRenderer.ShapeType.Line);

        sr.setColor(Color.WHITE);
        sr.circle(SettingsMaster.getWidth() / 2.0f, SettingsMaster.getHeight() - ((LOAD_SIZE + 150.0f) * SettingsMaster.SCALE), LOAD_SIZE * SettingsMaster.SCALE);
        sr.setColor(Color.BLACK.cpy());
        sr.circle(SettingsMaster.getWidth() / 2.0f - offset, SettingsMaster.getHeight() - ((LOAD_SIZE + 150.0f) * SettingsMaster.SCALE), LOAD_SIZE * SettingsMaster.SCALE);

        sr.end();

        sb.begin();
    }

    @Override
    public void dispose() {
        try {
            editorLogger.info("Shutting down LoadingLayer executor.");
            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            editorLogger.error("Tasks interrupted.");
        }
        finally {
            if (!executor.isTerminated()) {
                editorLogger.error("Tasks were unfinished.");
            }
            executor.shutdownNow();
            editorLogger.info("Executor shut down successfully.");
        }
    }
}
