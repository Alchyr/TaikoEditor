package alchyr.taikoedit.core.layers.sub;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.InputLayer;
import alchyr.taikoedit.core.ProgramLayer;
import alchyr.taikoedit.core.input.AdjustedInputProcessor;
import alchyr.taikoedit.management.SettingsMaster;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static alchyr.taikoedit.TaikoEditor.*;

public class UpdatingLayer extends ProgramLayer implements InputLayer {
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private static final String OLD = "oldVer.jar";
    private static final String ACTIVE = "desktop-1.0.jar";
    private static final String TEMP = "temp.jar";

    private static UpdatingLayerProcessor processor;

    //Rendering
    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 1.0f);

    //Positions
    private final int middleY = SettingsMaster.getHeight() / 2;

    //Parts
    private String text;

    private final String id, location;

    private final Path destination;
    private final Path updatePath;

    private int state = 0;

    public UpdatingLayer(String id, String location) {
        this.type = LAYER_TYPE.FULL_STOP;

        processor = new UpdatingLayerProcessor(this);

        this.text = "Downloading update. Do not end process.";

        this.id = id;
        this.location = location;

        destination = Paths.get(location);
        updatePath = Paths.get(location, TEMP);
    }

    @Override
    public void update(float elapsed) {
        switch (state) {
            case 0: //hasn't started yet.
                state = 1;
                executor.submit(()->{
                    try {
                        if (!Files.exists(destination)) {
                            try {
                                Files.createDirectory(destination);
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                                text = "ERROR: Target folder did not exist and could not be created. Press any key to return to editor.";
                                state = -1;
                                return;
                            }
                        }
                        state = 2;
                    }
                    catch (Exception e) {
                        System.out.println("Update process failed.");
                        e.printStackTrace();
                        text = "Failed to update. Press any key to return to editor.";
                        state = - 1;
                    }
                });
                break;
            case 2:
                state = 3;
                executor.submit(()->{
                    try {
                        InputStream in = new URI("https://drive.google.com/uc?export=download&id=" + id).toURL().openStream();

                        Files.copy(in, updatePath, StandardCopyOption.REPLACE_EXISTING);
                        //Download file to temporary location, as to not try to replace currently running jar.
                        in.close();

                        state = 4;
                        text = "Checking validity of file...";
                    }
                    catch (NoSuchFileException e) {
                        System.out.println("Target folder did not exist.");
                        e.printStackTrace();
                        text = "Failed to update. Press any key to return to editor.";
                        state = -1;
                    }
                    catch (Exception e) {
                        System.out.println("Update process failed.");
                        e.printStackTrace();
                        text = "Failed to update. Press any key to return to editor.";
                        state = - 1;
                    }
                });
                break;
            case 4:
                executor.submit(()->{
                    try (JarFile editorJar = new JarFile(updatePath.toFile(), false)) {
                        JarEntry mainFile = editorJar.getJarEntry("alchyr/taikoedit/TaikoEditor.class");
                        if (mainFile == null) {
                            throw new RuntimeException("Downloaded jar is invalid.");
                        }

                        state = 5;
                        text = "Update downloaded successfully. Press any key to exit and complete update.";
                    }
                    catch (Exception e) {
                        System.out.println("Update process failed.");
                        e.printStackTrace();
                        text = "Failed to update. Press any key to return to editor.";
                        state = - 1;
                    }
                });
                break;
        }
    }

    @Override
    public void render(SpriteBatch sb, ShapeRenderer sr) {
        Gdx.gl.glClearColor(backColor.r, backColor.g, backColor.b, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        textRenderer.renderTextCentered(sb, text, SettingsMaster.getMiddleX(), middleY, Color.WHITE);
    }

    private void receiveInput() {
        if (state == -1) {
            //Failure.
            executor.shutdownNow();
            close();
        }
        else if (state == 5) {
            //Success.
            try {
                Path p = destination.resolve("update");
                String classpath = p + File.separator;

                Process process = new ProcessBuilder(
                        System.getProperty("java.home") + "/bin/java",
                        "-classpath",
                        classpath,
                        "alchyr.Updater",
                        new File("lib").getAbsolutePath()
                )
                        .start();

                executor.shutdownNow();
                end();
            }
            catch (Exception e) {
                editorLogger.error("Failed to start update process.");
                e.printStackTrace();
                state = -2;
                text = "Failed to start update file copy process.\nYou can manually replace the desktop-1.0.jar file with the temp.jar file in the lib folder.\nPress any key to exit.";
            }
        }
        else if (state == -2) {
            executor.shutdownNow();
            end();
        }
    }

    private void close() {
        TaikoEditor.removeLayer(this);
    }

    @Override
    public void dispose() {
        super.dispose();
        executor.shutdownNow();
    }

    @Override
    public InputProcessor getProcessor() {
        return processor;
    }

    private static class UpdatingLayerProcessor extends AdjustedInputProcessor {
        private final UpdatingLayer sourceLayer;

        public UpdatingLayerProcessor(UpdatingLayer source)
        {
            this.sourceLayer = source;
        }

        @Override
        public boolean onTouchDown(int gameX, int gameY, int pointer, int button) {
            if (button != 0 && button != 1)
                return false; //left or right click only

            sourceLayer.receiveInput();

            return true;
        }

        @Override
        public boolean onTouchUp(int gameX, int gameY, int pointer, int button) {
            return true;
        }

        @Override
        public boolean onTouchDragged(int gameX, int gameY, int pointer) {
            return true;
        }

        @Override
        public boolean onMouseMoved(int gameX, int gameY) {
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            sourceLayer.receiveInput();
            return true;
        }

        @Override
        public boolean keyUp(int keycode) {
            return true;
        }

        @Override
        public boolean keyTyped(char character) {
            return true;
        }

        @Override
        public boolean scrolled(float x, float y) {
            return true;
        }
    }
}
