package com.isti.jira;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

import java.io.PrintStream;

import static java.lang.String.format;


/**
 * Encapsulate logging.
 */
public class Logger {

    private String project;

    private boolean isDebug;

    private PrintStream out;

    public Logger(final AbstractBuild build,
                  final BuildListener listener,
                  boolean debugFlag) {
        project = build.getProject().getName();
        out = listener.getLogger();
        isDebug = debugFlag;
    }

    public void info(final String template, final Object... args) {
        println("INFO", template, args);
    }

    public void debug(final String template, final Object... args) {
        if (isDebug) {
            println("DEBUG", template, args);
        }
    }

    private void println(final String level,
                         final String template,
                         final Object... args) {
        String line = format(template, args);
        out.printf("%s %s: %s%n", level, project, line);
    }

}
