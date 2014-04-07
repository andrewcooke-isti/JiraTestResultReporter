package com.isti.jira;

import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.api.GetCreateIssueMetadataOptions;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicProject;
import com.atlassian.jira.rest.client.api.domain.CimIssueType;
import com.atlassian.jira.rest.client.api.domain.CimProject;
import com.atlassian.jira.rest.client.api.domain.IssueType;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.auth.AnonymousAuthenticationHandler;
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

import static java.lang.String.format;
import static java.util.Collections.singletonList;

/**
 * High level wrapper for Jira's own client.  This blocks on actions and throws RumtimeException on error.
 *
 * Default values for null parameters are taken from properties in the home directory (DEFAULTS).
 *
 * IMPORTANT: The client must be closed on exit, or the program will hang.  This is a feature of the
 * underlying Jira library.  Use try / finally at the top level.
 */
public class JiraClient {

    private static final Defaults DEFAULTS = new Defaults();
    private static final String DEFAULT_USER = "CATS";
    private static final String DEFAULT_URL = "http://localhost:8081";
    private static final String DEFAULT_PROJECT = "CATS";

    private JiraRestClient client;

    public JiraClient(final String url, final String user, final String password) {
        client = getClient(url, user, password);
    }

    /**
     * @param url The Jira URL
     * @param user The Jira user.
     * @param password A null password triggers an anon handler.
     * @return A Jira client that is used to call the REST API.
     */
    private static JiraRestClient getClient(final String url, final String user, final String password) {
        try {
            AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
            URI jiraServerUri = new URI(DEFAULTS.withDefault("url", url, DEFAULT_URL));
            return factory.create(jiraServerUri, getAuthHandler(
                    jiraServerUri,
                    DEFAULTS.withDefault("user", user, DEFAULT_USER),
                    DEFAULTS.withDefault("password", password, null, true)));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static AuthenticationHandler getAuthHandler(final URI uri, final String user, final String password) {
        if (password == null) {
            System.err.println(format("Connecting anonymously to %s", uri));
            return new AnonymousAuthenticationHandler();
        } else {
            System.err.println(format("Connecting to %s as %s", uri, user));
            return new BasicHttpAuthenticationHandler(user, password);
        }
    }

    public void close() {
        try {
            client.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Iterable<BasicProject> listProjects() {
        return client.getProjectClient().getAllProjects().claim();
    }

    public Iterable<CimIssueType> listIssueTypes(final String project) {
        String p = DEFAULTS.withDefault("project", project, DEFAULT_PROJECT);
        Iterator<CimProject> info = client.getIssueClient().getCreateIssueMetadata(
                new GetCreateIssueMetadataOptions(null, null, null, singletonList(p), null)).claim().iterator();
        if (info.hasNext()) {
            return info.next().getIssueTypes();
        } else {
            throw new MessageException(format("Could not find project %s", project));
        }
    }

    private CimIssueType matchIssue(String issueType, Iterable<CimIssueType> types) {
        String type = DEFAULTS.withDefault("issueType", issueType, null, false);
        for (CimIssueType issue : types) {
            if (issue.getName().equalsIgnoreCase(type)) {
                return issue;
            }
        }
        throw new MessageException(format("No issue matching %s", issueType));
    }

    public void createIssue(final String project, final String issueType,
                            final String summary, final String description) {
        IssueType type = matchIssue(issueType, listIssueTypes(project));
        IssueInputBuilder issueBuilder =
                new IssueInputBuilder(DEFAULTS.withDefault("project", project, DEFAULT_PROJECT), type.getId());
        issueBuilder.setSummary(DEFAULTS.withDefault("summary", summary, null));
        issueBuilder.setDescription(DEFAULTS.withDefault("description", description, null));
        client.getIssueClient().createIssue(issueBuilder.build()).claim();
    }

}
