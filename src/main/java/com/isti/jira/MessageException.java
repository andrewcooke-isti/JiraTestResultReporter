package com.isti.jira;

/**
 * We use RuntimeException throughout the code.  This is a subclass that is intended to be reported directly
 * to the user (without a stack trace).
 */
public class MessageException extends RuntimeException {

    /**
     * @param msg The message to display to the user.
     */
    public MessageException(final String msg) {
        super(msg);
    }

}
