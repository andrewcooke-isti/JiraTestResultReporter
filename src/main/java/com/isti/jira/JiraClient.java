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


public class JiraClient {

    private JiraRestClient client;

    public JiraClient(final String url, final String user, final String password) {
        client = getClient(url, user, password);
    }

    private static JiraRestClient getClient(final String url, final String user, final String password) {
        try {
            AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
            URI jiraServerUri = new URI(url);
            return factory.create(jiraServerUri, getAuthHandler(jiraServerUri, user, password));
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
        Iterator<CimProject> info = client.getIssueClient().getCreateIssueMetadata(
                new GetCreateIssueMetadataOptions(null, null, null, singletonList(project), null)).claim().iterator();
        if (info.hasNext()) {
            return info.next().getIssueTypes();
        } else {
            throw new MessageException(format("Could not find project %s", project));
        }
    }

    private CimIssueType matchIssue(String issueType, Iterable<CimIssueType> types) {
        for (CimIssueType issue : types) {
            if (issue.getName().equalsIgnoreCase(issueType)) {
                return issue;
            }
        }
        throw new RuntimeException(format("No issue matching %s", issueType));
    }

    public void createIssue(final String project, final String issueType,
                            final String summary, final String description) {
        IssueType type = matchIssue(issueType, listIssueTypes(project));
        IssueInputBuilder issueBuilder = new IssueInputBuilder(project, type.getId());
        issueBuilder.setSummary(summary);
        issueBuilder.setDescription(description);
        client.getIssueClient().createIssue(issueBuilder.build()).claim();
    }

}
