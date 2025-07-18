package alchyr.taikoedit.desktop;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.core.layers.EditorLayer;
import alchyr.taikoedit.desktop.config.ConfigMenu;
import alchyr.taikoedit.desktop.config.ProgramConfig;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.EventWindowListener;
import alchyr.taikoedit.util.FileDropHandler;
import alchyr.taikoedit.util.SystemUtils;
import alchyr.taikoedit.management.assets.FileHelper;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import com.badlogic.gdx.utils.StreamUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/*
 * Display modes:
 * Fullscreen. Works as expected. Great, except swing popups will not be visible, and will be covered but uninteractable. So, don't use them.
 * Windowed. Works as expected.
 * Windowed Fullscreen -
 * If the taskbar is shown, totally fucked. Reports window size of 1061, while the maximum value input is 1041, effectively cutting off 20 pixels.
 *
 * Fix: Initialize window at a smaller size, then resize to windowed fullscreen after. Works as expected whether taskbar is shown or hidden.
 */

public class DesktopLauncher {
	public static final Logger logger = LogManager.getLogger("DesktopLauncher");

	private static final String settingsPrefix = "settings" + File.separator;

	private static int width = -1, height = -1;
	private static boolean borderless = false;
	private static int fps = 0; //0 = vsync, <0 = unlimited

	private static boolean fast = false;
	private static String directOpen = null;

	public static void main(String[] args) {
		SystemUtils.log(logger);

		initCommandlineArgs(args);
		processCommandlineArgs(args);

		Lwjgl3ApplicationConfiguration config = initialize();
		if (config != null) {
			launch(config);
		}
	}

	private static void initCommandlineArgs(String [] args) {
		if (boolify(System.getProperty("l5j.encargs"))) {
			final String enc = StandardCharsets.UTF_8.name();
			for (int i = 0; i < args.length; ++i) {
				try {
					//JOptionPane.showMessageDialog(null, "Encoded: " + args[i]);
					args[i] = URLDecoder.decode(args[i], enc);
					//JOptionPane.showMessageDialog(null, "Decoded: " + args[i]);
				} catch (Exception ignored) { }
			}
		}
	}

	private static boolean boolify(String s) {
		if (s == null)
			return false;
		return s.equals("1") || s.equals("true");
	}

	private static void processCommandlineArgs(String[] args) {
		int startIndex = 0, currentIndex = 0;
		for (String arg : args) {
			++currentIndex;
			switch (arg) {
				case "-fast":
					//logger.info("Loading fast menu.");
					fast = true;
					startIndex = currentIndex;
					break;
				default:
					if (arg.endsWith(".osu")) {
						String path = String.join(" ", Arrays.copyOfRange(args, startIndex, currentIndex));
						logger.info("Maybe beatmap file: " + path);
						directOpen = path;
					}
					break;
			}
		}
	}

	public static void launch(Lwjgl3ApplicationConfiguration config)
	{
		try {
			//MAYBE:
			//Add this if people run into pixelformat exceptions.
			//Catch that exception and set this before attempting launch again.
			//System.setProperty("org.lwjgl.opengl.Display.allowSoftwareOpenGL", "true");

			logger.info("Launching.");
			logger.info(" - Resolution: " + (width == -1 && height == -1 ? "Fullscreen" : width + "x" + height));
			logger.info(" - Borderless: " + (borderless ? "Yes" : "No"));
			logger.info(" - FPS Setting: " + (fps == 0 ? "VSync" : (fps < 0 ? "Unlimited" : fps)));
			logger.info(" - Menu: " + (fast ? "Fast" : "Normal"));
			new Lwjgl3Application(new TaikoEditor(width, height, fps, borderless, fast, directOpen), config);
		}
		catch (Exception e)
		{
			logger.error(e);
			e.printStackTrace();

			try {
				File f = new File("error.txt");
				PrintWriter pWriter = null;

				try {
					pWriter = new PrintWriter(f);
					pWriter.println("Version: " + TaikoEditor.VERSION);
					pWriter.println("Error occurred on main thread:");
					e.printStackTrace(pWriter);

					if (EditorLayer.activeEditor != null) {
						pWriter.println();
						pWriter.println("Active editor detected. Attempting to save data.");
						try {
							if (EditorLayer.activeEditor.saveAll()) {
								pWriter.println("Successfully saved data.");
							}
						}
						catch (Exception ignored) { }
						EditorLayer.activeEditor = null;
					}
				}
				catch (Exception ex) {
					logger.error("Failed to write error file.");
					Thread.sleep(3000);
				}
				finally {
					StreamUtils.closeQuietly(pWriter);
				}
			}
			catch (Exception ignored) {

			}
		}
	}

	private static Lwjgl3ApplicationConfiguration initialize()
	{
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();

		config.setAudioConfig(16, 2048, 6);
		config.useVsync(false);
		config.setHdpiMode(HdpiMode.Pixels);

		config.setWindowIcon(Files.FileType.Internal, "taikoedit/images/icon_48.png", "taikoedit/images/icon_32.png", "taikoedit/images/icon_16.png");

		config.setWindowListener(new EventWindowListener() {
			@Override
			public void filesDropped(String[] files) {
				FileDropHandler.receive(files);
			}
		});

		try {
			boolean success = true;

			FileHandle configFile = getLocalFile(settingsPrefix + "config.cfg");
			Graphics.DisplayMode primaryDesktopMode = Lwjgl3ApplicationConfiguration.getDisplayMode();

			if (configFile.exists()) {
				try
				{
					ProgramConfig programConfig = new ProgramConfig(configFile.readString());

					if (!programConfig.complete) {
						success = graphicsConfig(programConfig, config, primaryDesktopMode, configFile);
					}

					//load/save/setup config

					if (programConfig.fullscreen) {
						config.setFullscreenMode(primaryDesktopMode);
						config.setAutoIconify(true);
						width = -1;
						height = -1;
						borderless = false;
					}
					else {
						width = Math.min(programConfig.width, primaryDesktopMode.width);
						height = Math.min(programConfig.height, primaryDesktopMode.height);

						borderless = (width >= primaryDesktopMode.width) && (height >= primaryDesktopMode.height);

						config.setWindowedMode(width, borderless ? height + 1 : height);
						config.setResizable(false);
						config.setAutoIconify(false);
					}

					config.useVsync(programConfig.fpsMode == 0);
					fps = programConfig.fpsMode == 0 ? 0 : (programConfig.fpsMode == 2 ? -1 : programConfig.fps);
					if (fps > 0)
						config.setForegroundFPS(fps);

					SettingsMaster.osuFolder = FileHelper.withSeparator(programConfig.osuFolder);

					if (programConfig.useFastMenu) {
						fast = true; //will be enabled by command line or this
					}
				}
				catch (Exception e)
				{
					logger.error("Failed while attempting to load graphical config file.");
					e.printStackTrace();

					success = graphicsConfig(config, primaryDesktopMode, configFile);
				}
			}
			else {
				success = graphicsConfig(config, primaryDesktopMode, configFile);
			}

			if (success)
				return config;
			else
				logger.error("Initialization failed; launch cancelled.");
		}
		catch (Exception e)
		{
			logger.error(e);
			e.printStackTrace();

			try {
				File f = new File("error.txt");
				PrintWriter pWriter = null;

				try {
					pWriter = new PrintWriter(f);
					pWriter.println("Version: " + TaikoEditor.VERSION);
					pWriter.println("Error occurred on main thread:");
					e.printStackTrace(pWriter);

					if (EditorLayer.activeEditor != null) {
						pWriter.println();
						pWriter.println("Active editor detected. Attempting to save data.");
						try {
							if (EditorLayer.activeEditor.saveAll()) {
								pWriter.println("Successfully saved data.");
							}
						}
						catch (Exception ignored) { }
						EditorLayer.activeEditor = null;
					}
				}
				catch (Exception ex) {
					logger.error("Failed to write error file.");
					Thread.sleep(3000);
				}
				finally {
					StreamUtils.closeQuietly(pWriter);
				}
			}
			catch (Exception ignored) {

			}
		}
		return null;
	}

	private static boolean graphicsConfig(Lwjgl3ApplicationConfiguration config, Graphics.DisplayMode displayMode, FileHandle configFile) {
		return graphicsConfig(new ProgramConfig(displayMode), config, displayMode, configFile);
	}
	private static boolean graphicsConfig(ProgramConfig programConfig, Lwjgl3ApplicationConfiguration config, Graphics.DisplayMode displayMode, FileHandle configFile)
	{
		Object lock = new Object();

		ConfigMenu configMenu = new ConfigMenu(programConfig, configFile, displayMode, lock);

		//noinspection SynchronizationOnLocalVariableOrMethodParameter
		synchronized(lock) {
			while (configMenu.state == 0)
				try {
					lock.wait();
				} catch (InterruptedException e) {
					logger.error("Error waiting for configuration; ", e);
				}
			logger.trace("Configuration complete. Completion state: " + configMenu.state);
		}

		if (configMenu.state == 2) {
			if (programConfig.fullscreen) {
				config.setFullscreenMode(displayMode);
				config.setAutoIconify(true);
				width = -1;
				height = -1;
				borderless = false;
			}
			else {
				width = Math.min(programConfig.width, displayMode.width);
				height = Math.min(programConfig.height, displayMode.height);

				borderless = (width >= displayMode.width) && (height >= displayMode.height);

				config.setWindowedMode(width, borderless ? height + 1 : height);
				config.setResizable(false);
				config.setAutoIconify(false);
			}

			config.useVsync(programConfig.fpsMode == 0);
			fps = programConfig.fpsMode == 0 ? 0 : (programConfig.fpsMode == 2 ? -1 : programConfig.fps);
			if (fps > 0)
				config.setForegroundFPS(fps);

			SettingsMaster.osuFolder = FileHelper.withSeparator(programConfig.osuFolder);

			if (programConfig.useFastMenu) {
				fast = true; //will be enabled by command line or this
			}
			return true;
		}
		else
		{
			logger.trace("Configuration ended in failure or closing.");

			return false;
		}
	}

	//can't use these methods in Gdx.files until after instantiated.
	public static FileHandle getLocalFile(String path)
	{
		return new Lwjgl3FileHandle(path, Files.FileType.Local);
	}
}
