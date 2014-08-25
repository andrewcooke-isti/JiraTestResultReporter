package com.isti.jira;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * Test the logger.
 */
public class LoggerTest {

    @Test
    public void output() {
        AbstractProject project = mock(AbstractProject.class);
        when(project.getName()).thenReturn("name");
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream stream = new PrintStream(buffer);
        BuildListener listener = mock(BuildListener.class);
        when(listener.getLogger()).thenReturn(stream);

        // without debug
        Logger logger = new Logger(build, listener, false);
        logger.debug("a %s message", "debug");
        logger.info("info message %d", 1);
        assertEquals(buffer.toString(), buffer.toString(), "INFO name: info message 1\n");

        // with debug
        buffer.reset();
        logger = new Logger(build, listener, true);
        logger.debug("a %s message", "debug");
        assertEquals(buffer.toString(), buffer.toString(), "DEBUG name: a debug message\n");
    }

}
