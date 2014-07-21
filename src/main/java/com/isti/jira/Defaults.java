package com.isti.jira;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import static org.apache.commons.lang.StringUtils.isEmpty;


/**
 * Provide default values for named parameters via properties in DOT_FILE.
 */
public class Defaults {

    /**
     * The name of the file that contains the defaults.
     */
    private static final String DOT_FILE = ".jira-remote";

    /**
     * These are named exactly how the should appear in the properties file.
     */
    public static enum Key {

        // lower case because name used in properties

        /** The user to use in a connection. */
        user("cats"),
        /** The password to use in a connection. */
        password,
        /** The URL to connect to. */
        url("http://localhost:8081"),
        /** The project to create issues for (usually not supplied). */
        project,
        /** The issue type to create. */
        issue_type("bug"),
        /** The role that "owns" an issue. */
        role("reporter"),
        /** A summary of the issue (usually not supplied). */
        summary,
        /** A description of the issue (usually not supplied). */
        description,
        /** The transition needed to resolve the issue. */
        transition("done");

        /**
         * The default value (may be null, eg in the case of password).
         */
        private final String deflt;

        /**
         * Create a key with a default.
         *
         * @param theDeflt The default value if non supplied.
         */
        Key(final String theDeflt) {
            deflt = theDeflt;
        }

        /**
         * Create a key without a default value.
         */
        Key() {
            this(null);
        }

    }

    /**
     * A cache for the properties, so we don't need to read it each time.
     */
    private Properties propertiesCache = null;

    /**
     * Process a value, replacing null with any defaults found, and then raising an exception if still null when
     * nullOk is false.
     *
     * @param key The name of the value.
     * @param value The initial value (may be null).
     * @param nullOk If true then a final null value does not raise an exception.
     * @return A value with defaults applied.
     */
    public final String withDefault(final Key key, final String value, final boolean nullOk) {
        String result = value == null ? value : value.trim();
        if (isEmpty(result) && getProperties().contains(key.name())) {
            result = getProperties().getProperty(key.name());
        }
        if (isEmpty(result)) {
            result = key.deflt;
        }
        if (isEmpty(result) && !nullOk) {
            throw new MissingArgumentException(key.name());
        }
        return result;
    }

    /**
     * Apply defaults; raise an exception if no value found.
     *
     * @param key The name of the The name of the value.
     * @param value The initial value (may be null).
     * @return A value with defaults applied.
     */
    public final String withDefault(final Key key, final String value) {
        return withDefault(key, value, false);
    }

    /**
     * Apply defaults; raise an exception if no value found.
     *
     * @param key The name of the The name of the value.
     * @return A value with defaults applied.
     */
    public final String withDefault(final Key key) {
        return withDefault(key, null, false);
    }

    /**
     * List all the key/value pairs that were defined in DOT_FILE.
     *
     * @param out The destination to list to.
     */
    public final void listTo(final PrintStream out) {
        Properties properties = getProperties();
        Set<String> known = new HashSet<String>();
        for (Key key: Key.values()) {
            String value = withDefault(key, null, true);
            out.printf("%s: %s%n", key.name(), value);
            known.add(key.name());
        }
        for (String name: properties.stringPropertyNames()) {
            if (! known.contains(name)) {
                String value = properties.getProperty(name);
                out.printf("%s (unknown): %s%n", name, value);
            }
        }
    }

    /**
     * @return The cached properties.
     */
    private synchronized Properties getProperties() {
        if (propertiesCache == null) {
            propertiesCache = new Properties();
            loadPropertiesFrom("/apps/data");
            loadPropertiesFrom("/var/lib/jenkins");
            loadPropertiesFrom(System.getProperty("home.user"));
        }
        return propertiesCache;
    }

    /**
     * @param dir The directory to load the properties from.
     */
    private void loadPropertiesFrom(final String dir) {
        if (dir == null) {
            return;
        }
        String path = dir + "/" + DOT_FILE;
        File file = new File(path);
        if (file.exists() && file.isFile() && file.canRead()) {
            try {
                propertiesCache.load(new FileReader(file));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
