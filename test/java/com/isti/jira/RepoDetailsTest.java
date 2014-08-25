package com.isti.jira;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * Test the repository details object.
 */
public class RepoDetailsTest {

    @Test
    public void constructorAndSetters() {
        assertDetails(new RepoDetails("url", "branch", "commit"));
    }

    @Test
    public void readFromEnvironment() {
        EnvVars vars = mock(EnvVars.class);
        when(vars.get("GIT_URL")).thenReturn("url");
        when(vars.get("GIT_BRANCH")).thenReturn("branch");
        when(vars.get("GIT_COMMIT")).thenReturn("commit");
        AbstractBuild build = mock(AbstractBuild.class);
        try {
            when(build.getEnvironment(null)).thenReturn(vars);
        } catch (Exception e) {
            fail(e.getMessage());
        }
        assertDetails(new RepoDetails(build));
    }

    /**
     * Validate details with known values.
     * @param details Details with "url", "branch", and "commit".
     */
    private void assertDetails(final RepoDetails details) {
        assertEquals(details.getBranch(), "branch");
        assertEquals(details.getCommit(), "commit");
        assertEquals(details.getURL(), "url");
        assertEquals(details.toString(), "url [branch] commit");
    }

}
