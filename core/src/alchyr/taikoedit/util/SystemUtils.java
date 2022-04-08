package alchyr.taikoedit.util;

import org.apache.logging.log4j.Logger;

import java.util.Locale;

public class SystemUtils {
    private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);

    public static void log(Logger logger) {
        logger.info("OS Name: " + System.getProperty ("os.name"));
        logger.info("Version: " + System.getProperty ("os.version"));
        logger.info("Arch: " + System.getProperty ("os.arch"));
    }

    public static SystemType getSystemType() {
        if (OS.contains("win"))
            return SystemType.WINDOWS;
        else if (OS.contains("mac"))
            return SystemType.MAC;
        else if (OS.contains("nix") || OS.contains("nux") || OS.contains("aix"))
            return SystemType.LINUX;

        return SystemType.UNSUPPORTED;
    }

    public enum SystemType {
        WINDOWS,
        MAC,
        LINUX,
        UNSUPPORTED
    }
}
