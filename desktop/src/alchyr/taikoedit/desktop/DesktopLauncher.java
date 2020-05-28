package alchyr.taikoedit.desktop;

import alchyr.taikoedit.TaikoEditor;
import alchyr.taikoedit.desktop.config.ConfigMenu;
import alchyr.taikoedit.desktop.config.ProgramConfig;
import alchyr.taikoedit.management.SettingsMaster;
import alchyr.taikoedit.util.SystemUtils;
import alchyr.taikoedit.util.assets.FileHelper;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3FileHandle;
import com.badlogic.gdx.files.FileHandle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class DesktopLauncher {
	public static final Logger logger = LogManager.getLogger("DesktopLauncher");

	private static final String settingsPrefix = "settings" + File.separator;

	private static int fpsMode = 0; //0 = vsync, 1 = sync, 2 = unlimited
	private static int fps;

	public static void main (String[] arg) {
		/*logging test
		logger.trace("trace");
		logger.info("info");
		logger.error("error");*/

		SystemUtils.log(logger);

		initialize();
	}

	public static void launch(Lwjgl3ApplicationConfiguration config)
	{
		new Lwjgl3Application(new TaikoEditor(fpsMode == 0, fpsMode == 2, fps), config);
	}

	private static void initialize()
	{
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();

		config.setAudioConfig(16, 2048, 6);

		try {
			boolean success = true;

			FileHandle configFile = getLocalFile(settingsPrefix + "config.cfg");
			Graphics.DisplayMode primaryDesktopMode = Lwjgl3ApplicationConfiguration.getDisplayMode();

			if (configFile.exists()) {
				try
				{
					ProgramConfig programConfig = new ProgramConfig(configFile.readString());

					//load/save/setup config

					if (programConfig.fullscreen) {
						config.setFullscreenMode(primaryDesktopMode);
						SettingsMaster.updateDimensions(primaryDesktopMode.width, primaryDesktopMode.height);
						config.setAutoIconify(true);
					}
					else {
						SettingsMaster.updateDimensions(programConfig.width, programConfig.height);
						config.setWindowedMode(programConfig.width, programConfig.height);
						config.setResizable(false);
						config.setAutoIconify(false);
					}

					SettingsMaster.useFullscreenOffset(programConfig.fullscreen || (programConfig.width != primaryDesktopMode.width && programConfig.height != primaryDesktopMode.height));

					SettingsMaster.osuFolder = FileHelper.withSeparator(programConfig.osuFolder);
					config.useVsync((fpsMode = programConfig.fpsMode) == 0);
					fps = programConfig.fps;
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
				launch(config);
			else
				logger.error("Initialization failed; launch cancelled.");
		}
		catch (Exception e)
		{
			logger.error(e);
			e.printStackTrace();
		}
	}

	private static boolean graphicsConfig(Lwjgl3ApplicationConfiguration config, Graphics.DisplayMode displayMode, FileHandle configFile)
	{
		ProgramConfig programConfig = new ProgramConfig(displayMode);
		programConfig.fullscreen = false;

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
				SettingsMaster.updateDimensions(displayMode.width, displayMode.height);
				config.setAutoIconify(true);
			}
			else {
				SettingsMaster.updateDimensions(programConfig.width, programConfig.height);
				config.setWindowedMode(programConfig.width, programConfig.height);
				config.setResizable(false);
				config.setAutoIconify(false);
			}


			SettingsMaster.useFullscreenOffset(programConfig.fullscreen || (programConfig.width != displayMode.width && programConfig.height != displayMode.height));

			SettingsMaster.osuFolder = FileHelper.withSeparator(programConfig.osuFolder);
			config.useVsync((fpsMode = programConfig.fpsMode) == 0);
			fps = programConfig.fps;

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
