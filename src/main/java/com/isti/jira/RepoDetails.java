package com.isti.jira;

import hudson.model.AbstractBuild;

/**
 * Encapsulate what we know about the git repository.
 */
public class RepoDetails {

    private String URL;

    private String branch;

    public RepoDetails(AbstractBuild build) {

    }

    public String getURL() {
        return URL;
    }

    public String getBranch() {
        return branch;
    }

}
