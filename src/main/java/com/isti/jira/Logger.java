package com.isti.jira;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

import java.io.PrintStream;

import static java.lang.String.format;


/**
 * Encapsulate logging.
 */
public final class Logger {

    /** The project name. */
    private String project;

    /** Whether debug messages should be displayed. */
    private boolean isDebug;

    /** Destination for messages. */
    private PrintStream out;

    /**
     * @param build The Jenkins build.
     * @param listener Destination for logging.
     * @param debugFlag Whether debug messages should be displayed.
     */
    public Logger(final AbstractBuild build,
                  final BuildListener listener,
                  final boolean debugFlag) {
        this(build.getProject().getName(), listener.getLogger(), debugFlag);
    }

    /**
     * @param project The project name.
     * @param out Destination for messages.
     * @param isDebug Whether debug messages should be displayed.
     */
    public Logger(final String project,
                  final PrintStream out,
                  final boolean isDebug) {
        this.project = project;
        this.out = out;
        this.isDebug = isDebug;
    }

    /**
     * Log an info level message.
     * @param template The format.
     * @param args Arguments to substitute in the format.
     */
    public void info(final String template, final Object... args) {
        println("INFO", template, args);
    }

    /**
     * Log a debug level message.
     * @param template The format.
     * @param args Arguments to substitute in the format.
     */
    public void debug(final String template, final Object... args) {
        if (isDebug) {
            println("DEBUG", template, args);
        }
    }

    /**
     * @param level The level to print.
     * @param template The format.
     * @param args Arguments to substitute in the format.
     */
    private void println(final String level,
                         final String template,
                         final Object... args) {
        String line = format(template, args);
        out.printf("%s %s: %s%n", level, project, line);
    }

}
