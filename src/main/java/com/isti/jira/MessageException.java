package com.isti.jira;

/**
 * We use RuntimeException throughout the code.  This is a subclass that is intended to be reported directly
 * to the user (without a stack trace).
 */
public class MessageException extends RuntimeException {

    public MessageException(String msg) {super(msg);}

}
