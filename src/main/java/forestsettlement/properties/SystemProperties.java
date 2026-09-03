package forestsettlement.properties;

import org.tinylog.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class SystemProperties {

    public static final boolean DEBUG_MODE;

    static {
        DEBUG_MODE = loadDebugMode();
    }

    private static boolean loadDebugMode() {
        Properties properties = new Properties();

        try (InputStream in = SystemProperties.class.getResourceAsStream("/system.properties")) {
            if (in == null) {
                Logger.warn("system.properties not found on classpath — defaulting debug mode to false");
                return false;
            }

            properties.load(in);
        } catch (IOException e) {
            Logger.error(e, "Failed to read system.properties — defaulting debug mode to false");
            return false;
        }

        return Boolean.parseBoolean(properties.getProperty("debug", "false"));
    }
}
