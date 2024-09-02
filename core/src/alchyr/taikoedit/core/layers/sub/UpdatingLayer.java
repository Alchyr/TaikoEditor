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

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
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

    private static final boolean openInBrowser;
    private static final String FAIL_MESSAGE;
    static {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            openInBrowser = true;
            FAIL_MESSAGE = "Failed to update.\nPress any key to return to editor and\nopen the file page in a web browser.";
        }
        else {
            openInBrowser = false;
            FAIL_MESSAGE = "Failed to update.\nPress any key to return to editor.";
        }
    }

    private static UpdatingLayerProcessor processor;

    //Rendering
    private static final Color backColor = new Color(0.0f, 0.0f, 0.0f, 1.0f);

    //Positions
    private final int middleY = SettingsMaster.getHeight() / 2;

    //Parts
    private String text;
    private boolean openInBrowserThisTime = openInBrowser;

    private URL downloadURL;
    private final byte[] hash;

    private final Path destination;
    private final Path updatePath;

    private int state;

    public UpdatingLayer(String url, byte[] hash, String location) {
        this.type = LAYER_TYPE.FULL_STOP;

        processor = new UpdatingLayerProcessor(this);

        this.hash = hash;

        this.text = "Downloading update. Do not end process.";
        state = 0;

        try {
            downloadURL = new URL(url);
        }
        catch (MalformedURLException e) {
            openInBrowserThisTime = false;
            editorLogger.error("Invalid update URL", e);
            text = "Update URL was invalid.\nPress any button to return to editor.";
            state = -1;
        }

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
                            catch (IOException e) {
                                e.printStackTrace();
                                text = "ERROR: Target folder did not exist and could not be created.\nPress any key to return to editor and\nopen the file page in a web browser.";
                                state = -1;
                                return;
                            }
                        }

                        state = 2;
                    }
                    catch (Exception e) {
                        editorLogger.info("Update process failed.", e);
                        text = FAIL_MESSAGE;
                        state = - 1;
                    }
                });
                break;
            case 2:
                state = 3;
                executor.submit(()->{
                    try (FileOutputStream out = new FileOutputStream(updatePath.toFile())) {
                        FileChannel outChannel = out.getChannel();
                        this.text = "Downloading update. Do not end process.\nConnecting...";
                        URLConnection connection = downloadURL.openConnection();

                        MessageDigest md = MessageDigest.getInstance("MD5");
                        DigestInputStream mdInputReader = new DigestInputStream(connection.getInputStream(), md);

                        ReadableByteChannel readableByteChannel = Channels.newChannel(mdInputReader);
                        this.text = "Downloading update. Do not end process.\nDownloading...";

                        long written = outChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);

                        editorLogger.info("Wrote " + written + " bytes");

                        byte[] digest = md.digest();

                        if (!Arrays.equals(hash, digest)) {
                            editorLogger.warn("Download file hash does not match.");
                            state = -1;
                            openInBrowserThisTime = false;
                            mdInputReader.close();

                            text = "Downloaded file's hash did not match.\nPress any button to return to editor.";
                            Thread.sleep(1000);
                            Files.delete(updatePath);
                            return;
                        }

                        state = 4;
                        text = "Checking validity of file...";
                    }
                    catch (UnknownHostException e) {
                        openInBrowserThisTime = false;
                        editorLogger.info("Probably invalid URL.", e);
                        text = "Update URL is invalid.\nPress any button to return to editor.";
                        state = -1;
                    }
                    catch (NoSuchFileException e) {
                        editorLogger.info("Target location was invalid.", e);
                        text = FAIL_MESSAGE;
                        state = -1;
                    }
                    catch (Exception e) {
                        editorLogger.info("Update process failed.", e);
                        text = FAIL_MESSAGE;
                        state = - 1;
                    }
                });
                break;
            case 4:
                state = 5;

                executor.submit(()->{
                    try (JarFile editorJar = new JarFile(updatePath.toFile(), false)) {
                        JarEntry mainFile = editorJar.getJarEntry("alchyr/taikoedit/TaikoEditor.class");
                        if (mainFile == null) {
                            throw new RuntimeException("Downloaded jar is invalid.");
                        }

                        text = "Update downloaded successfully. Press any key to exit and complete update.";
                        state = 6;
                    }
                    catch (Exception e) {
                        editorLogger.info("Update process failed.", e);
                        openInBrowserThisTime = false;
                        text = "Downloaded file does not seem to be a valid .jar file.\nPress any button to return to editor.";
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
            if (openInBrowserThisTime) {
                try {
                    Desktop.getDesktop().browse(downloadURL.toURI());
                }
                catch (Exception e) {
                    editorLogger.error("Failed to open page in browser", e);
                }
            }
            executor.shutdownNow();
            close();
        }
        else if (state == 6) {
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