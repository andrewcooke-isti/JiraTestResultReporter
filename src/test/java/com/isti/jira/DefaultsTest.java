package com.isti.jira;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * Test the handling of default values.
 */
public class DefaultsTest {

    @Test
    public void areSystemDefaultsPresent() {
        Defaults defaults = new Defaults(new Properties());
        // this doesn't have a system default
        assertEquals(defaults.withDefault(Defaults.Key.branch, null, true), null);
        // but this does
        assertEquals(defaults.withDefault(Defaults.Key.user, null, true), "cats");
    }

    @Test
    public void raiseAnExceptionForEmptyValues() {
        final Defaults defaults = new Defaults(new Properties());
        assertError(new Runnable() {
            public void run() {
                defaults.withDefault(Defaults.Key.branch);
            }
        }, RuntimeException.class, "No value for parameter branch");
        assertError(new Runnable() {
            public void run() {
                defaults.withDefault(Defaults.Key.branch, null);
            }
        }, RuntimeException.class, "No value for parameter branch");
        assertError(new Runnable() {
            public void run() {
                defaults.withDefault(Defaults.Key.branch, "");
            }
        }, RuntimeException.class, "No value for parameter branch");
        assertError(new Runnable() {
            public void run() {
                defaults.withDefault(Defaults.Key.branch, "  ");
            }
        }, RuntimeException.class, "No value for parameter branch");
        assertError(new Runnable() {
            public void run() {
                defaults.withDefault(Defaults.Key.branch, " ", false);
            }
        }, RuntimeException.class, "No value for parameter branch");
    }

    @Test
    public void arePropertiesPresent() {
        Properties props = new Properties();
        props.setProperty(Defaults.Key.issue_type.name(), "foo");
        Defaults defaults = new Defaults(props);
        assertEquals(defaults.withDefault(Defaults.Key.issue_type), "foo");
    }

    /**
     * Check that executing the target raises the given exception.
     * @param target The code to execute.
     * @param clazz The exception type to expect.
     * @param message Must be present in the exception message.
     * @param <T> The exception type to expect.
     */
    private static <T extends Throwable> void assertError(final Runnable target,
                                                          final Class<T> clazz,
                                                          final String message) {
        try {
            target.run();
        } catch (Throwable t) {
            assertTrue(clazz.isInstance(t));
            assertTrue(t.getMessage(), t.getMessage().contains(message));
        }
    }

}
