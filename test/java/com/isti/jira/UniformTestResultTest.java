package com.isti.jira;


import hudson.model.AbstractBuild;
import hudson.tasks.test.TestObject;
import hudson.tasks.test.TestResult;
import org.junit.Test;
import org.tap4j.plugin.model.TapTestResultResult;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test the extraction of results from different test results classes.
 */
public class UniformTestResultTest {

    @Test
    public void literal() {
        assertTestResult(new UniformTestResult("summary", "description", "summary", true),
                "summary", "description", true,
                "a3fb05806dc05cdb91804d4b15319336185ab367");
    }

    @Test
    public void tapResult() {
        TapTestResultResult result = mock(TapTestResultResult.class);
        when(result.getName()).thenReturn("name");
        when(result.getTitle()).thenReturn("title");
        when(result.getErrorDetails()).thenReturn("details");
        Logger logger = new Logger("project", new PrintStream(new ByteArrayOutputStream()), false);
        assertTestResult(new UniformTestResult(result, logger),
                "Test 'name' failed", "title: details", true,
                "03005379bdeeed81f0a47ed8add8654f73a1cf9b");
    }

    // this is untestable - CaseResult is a final, closed class with no interface and
    // no public constructor. it can't be mocked and it can't be crated.
//    @Test
//    public void caseResult() {
//        CaseResult result = mock(CaseResult.class);
//        when(result.getName()).thenReturn("name");
//        when(result.getClassName()).thenReturn("class");
//        when(result.getErrorDetails()).thenReturn("details");
//        when(result.getErrorStackTrace()).thenReturn("[workspace]");
//        Logger logger = new Logger("project", new PrintStream(new ByteArrayOutputStream()), false);
//        assertTestResult(new UniformTestResult(result, "workspace", logger),
//                "", "", true,
//                "");
//    }

    @Test
    public void testResult() {
        TestResult result = new TestResult() {
            @Override
            public AbstractBuild<?, ?> getOwner() {return null;}
            @Override
            public TestObject getParent() {return null;}
            @Override
            public TestResult findCorrespondingResult(String id) {return null;}
            public String getDisplayName() {return null;}
            @Override
            public String getName() {return "name";}
            @Override
            public String getTitle() {return "title";}
            @Override
            public String getErrorDetails() {return "details";}
        };
        Logger logger = new Logger("project", new PrintStream(new ByteArrayOutputStream()), false);
        assertTestResult(new UniformTestResult(result, logger),
                "Test 'name' failed", "title: details", true,
                "03005379bdeeed81f0a47ed8add8654f73a1cf9b");
    }

    private void assertTestResult(final UniformTestResult result,
                                  final String summary,
                                  final String description,
                                  final boolean isNew,
                                  final String hash) {
        assertEquals(result.getDescription(), description);
        assertEquals(result.getSummary(), summary);
        assertEquals(result.isNew(), isNew);
        RepoDetails details = new RepoDetails("url", "branch", "commit");
        assertEquals(result.getHash(details), hash);
    }

}
