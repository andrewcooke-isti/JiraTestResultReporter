package com.isti.jira;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;


/**
 * Provide default values for named parameters via properties in DOT_FILE.
 */
public class Defaults {

    private static final String DOT_FILE = ".catsjira";

    private Properties propertiesCache = null;

    public final String withDefault(final String key, final String value, final String deflt, boolean nullOk) {
        String result = value;
        if (result == null) {
            Properties properties = getProperties();
            result = properties.getProperty(key);
        }
        if (result == null) {
            result = deflt;
        }
        if (result == null && !nullOk) {
            throw new MissingArgumentException(key);
        }
        return result;
    }

    public final String withDefault(final String key, final String value, final String deflt) {
        return withDefault(key, value, deflt, false);
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
