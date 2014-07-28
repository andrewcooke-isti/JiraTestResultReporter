package com.isti.jira;

import hudson.model.AbstractBuild;

import java.io.IOException;
import java.util.Map;

import static java.lang.String.format;


/**
 * Encapsulate what we know about the git repository.
 */
public final class RepoDetails {

    /** The git repository URL. */
    private String URL;

    /** The git branch. */
    private String branch;

    /** The git commit. */
    private String commit;

    /**
     * @param URL The git repository URL.
     * @param branch The git branch.
     */
    public RepoDetails(final String URL, final String branch, final String commit) {
        this.URL = URL;
        this.branch = branch;
        this.commit = commit;
    }

    /**
     * @param build The current build.
     */
    public RepoDetails(final AbstractBuild build) {
        this(vars(build).get("GIT_URL"),
             vars(build).get("GIT_BRANCH"),
             vars(build).get("GIT_COMMIT"));
    }

    /**
     * @param build The current build.
     * @return A map of environment variables.
     */
    private static Map<String, String> vars(final AbstractBuild build) {
//        return build.getBuildVariables();
        try {
            return build.getEnvironment(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return The git repository URL.
     */
    public String getURL() {
        return URL;
    }

    /**
     * @return The git branch.
     */
    public String getBranch() {
        return branch;
    }

    /**
     * @return The git commit.
     */
    public String getCommit() {
        return commit;
    }

    @Override
    public String toString() {
        return format("%s [%s] %s", URL, branch, commit);
    }

}
