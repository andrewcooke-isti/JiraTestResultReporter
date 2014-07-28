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

    /**
     * @param URL The git repository URL.
     * @param branch The git branch.
     */
    public RepoDetails(final String URL, final String branch) {
        this.URL = URL;
        this.branch = branch;
    }

    /**
     * @param build The current build.
     */
    public RepoDetails(final AbstractBuild build, final Logger log) {
        this(vars(build, log).get("GIT_URL"),
             vars(build, null).get("GIT_BRANCH"));
    }

    /**
     * @param build The current build.
     * @return A map of environment variables.
     */
    private static Map<String, String> vars(final AbstractBuild build, final Logger log) {
//        return build.getBuildVariables();
        try {
            Map<String, String> vars = build.getEnvironment(null);
            if (null != log) {
                log.debug("Vars %d", vars.size());
                for (String name: vars.keySet()) {
                    log.debug("%s: %s", name, vars.get(name));
                }
                log.debug("-----------");
            }
            return vars;
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

    @Override
    public String toString() {
        return format("%s [%s]", URL, branch);
    }

}
