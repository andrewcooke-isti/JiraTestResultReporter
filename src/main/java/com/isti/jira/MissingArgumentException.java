package com.isti.jira;

/**
 * Indicate to the user that a particular named value was not supplied.
 */
public class MissingArgumentException extends RuntimeException {

    /**
     * @param key The name of the missing argmuent.
     */
    public MissingArgumentException(final String key) {
        super(key);
    }

}
