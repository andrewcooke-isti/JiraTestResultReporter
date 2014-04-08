package com.isti.jira;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Properties;

import static org.apache.commons.lang.StringUtils.isEmpty;


/**
 * Provide default values for named parameters via properties in DOT_FILE.
 */
public class Defaults {

    private static final String DOT_FILE = ".catsjira";

    /**
     * These are named exactly how the should appear in the properties file.
     */
    public static enum Key {

        // lower case because name used in properties
        user("cats"),
        password,
        url("http://localhost:80801"),
        project,
        issue_type("bug"),
        summary,
        description;

        private final String deflt;

        Key(final String deflt) {
            this.deflt = deflt;
        }

        Key() {
            this(null);
        }
        
    }

    private Properties propertiesCache = null;

    public final String withDefault(final Key key, final String value, boolean nullOk) {
        String result = value;
        if (isEmpty(result)) {
            Properties properties = getProperties();
            result = properties.getProperty(key.name());
        }
        if (isEmpty(result)) {
            result = key.deflt;
        }
        if (isEmpty(result) && !nullOk) {
            throw new MissingArgumentException(key.name());
        }
        return result;
    }

    public final String withDefault(final Key key, final String value) {
        return withDefault(key, value, false);
    }

    public final void listTo(PrintStream out) {
        for (String name: getProperties().stringPropertyNames()) {
            out.printf("%s: %s%n", name, getProperties().getProperty(name));
        }
    }

    private synchronized Properties getProperties() {
        if (propertiesCache == null) {
            propertiesCache = new Properties();
            loadPropertiesFrom("/var/lib/jenkins");
            loadPropertiesFrom(System.getProperty("home.user"));
        }
        return propertiesCache;
    }

    private void loadPropertiesFrom(final String dir) {
        if (dir == null) return;
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
