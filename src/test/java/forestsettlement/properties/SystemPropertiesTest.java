package forestsettlement.properties;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemPropertiesTest {

    @Test
    void debugTrueIsParsedAsEnabled() {
        Properties properties = new Properties();
        properties.setProperty("debug", "true");

        assertTrue(SystemProperties.parseDebugMode(properties));
    }

    @Test
    void debugFalseIsParsedAsDisabled() {
        Properties properties = new Properties();
        properties.setProperty("debug", "false");

        assertFalse(SystemProperties.parseDebugMode(properties));
    }

    @Test
    void missingDebugKeyDefaultsToDisabled() {
        Properties properties = new Properties();

        assertFalse(SystemProperties.parseDebugMode(properties));
    }

    @Test
    void unrecognizedValueDefaultsToDisabled() {
        Properties properties = new Properties();
        properties.setProperty("debug", "yes");

        assertFalse(SystemProperties.parseDebugMode(properties));
    }
}
