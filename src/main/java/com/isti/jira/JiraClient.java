package com.isti.jira;

import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.api.GetCreateIssueMetadataOptions;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicProject;
import com.atlassian.jira.rest.client.api.domain.CimIssueType;
import com.atlassian.jira.rest.client.api.domain.CimProject;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueType;
import com.atlassian.jira.rest.client.api.domain.Transition;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import com.atlassian.jira.rest.client.auth.AnonymousAuthenticationHandler;
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

import static com.isti.jira.Defaults.Key;
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
public final class JiraClient {

    /**
     * Allo anonymous connectinos (possible but useless)
     */
    public static final boolean ALLOW_ANON = false;

    /**
     * Source of default values (read from a "dot file").
     */
    private static final Defaults DEFAULTS = new Defaults();

    /**
     * The underlying client that does the work of connecting to Jira.
     */
    private JiraRestClient client;

    /**
     * @param url The URL to connect to.
     * @param user The Jira user.
     * @param password The password to use in the connection.
     */
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
            URI jiraServerUri = new URI(DEFAULTS.withDefault(Key.url, url));
            return factory.create(jiraServerUri, getAuthHandler(
                    jiraServerUri,
                    DEFAULTS.withDefault(Key.user, user),
                    DEFAULTS.withDefault(Key.password, password, ALLOW_ANON)));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param uri The URI to connect to.
     * @param user The Jira user.
     * @param password The password to use in the connection.
     * @return An auth handler that provides the username and password, if given, or tries an anon connection.
     */
    private static AuthenticationHandler getAuthHandler(final URI uri, final String user, final String password) {
        if (password == null) {
            System.err.println(format("Connecting anonymously to %s", uri));
            return new AnonymousAuthenticationHandler();
        } else {
            System.err.println(format("Connecting to %s as %s", uri, user));
            return new BasicHttpAuthenticationHandler(user, password);
        }
    }

    /**
     * Close and free resources.  MUST be called as the Jira client requires this.
     */
    public void close() {
        try {
            client.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return A list of all projects.
     */
    public Iterable<BasicProject> listProjects() {
        return client.getProjectClient().getAllProjects().claim();
    }

    /**
     * @param project The project whose issues we want.
     * @return A list of all issue types for the project.
     */
    public Iterable<CimIssueType> listIssueTypes(final String project) {
        String p = DEFAULTS.withDefault(Key.project, project);
        Iterator<CimProject> info = client.getIssueClient().getCreateIssueMetadata(
                new GetCreateIssueMetadataOptions(null, null, null, singletonList(p), null)).claim().iterator();
        if (info.hasNext()) {
            return info.next().getIssueTypes();
        } else {
            throw new MessageException(format("Could not find project %s", p));
        }
    }

    /**
     * Find an issue that matches the information given.
     *
     * @param issueType An issue type entered by the user.
     * @param types Types known to the system.
     * @return A type known to the system that matches the name given by the user.
     */
    private CimIssueType matchIssueType(final String issueType, final Iterable<CimIssueType> types) {
        String type = DEFAULTS.withDefault(Key.issue_type, issueType);
        for (CimIssueType issue : types) {
            if (issue.getName().equalsIgnoreCase(type)) {
                return issue;
            }
        }
        throw new MessageException(format("No issue type matching %s", type));
    }

    /**
     * Create an issue.  All the parameters below are provided with defaults and the issue type is expanded
     * against the project's known issue types.
     *
     * @param project The project name.
     * @param issueType The issue type.
     * @param summary The summary of the issue (used as a title in Jira).
     * @param description The description of the issue (used as the text in Jira).
     */
    public void createIssue(final String project, final String issueType,
                            final String summary, final String description) {
        IssueType type = matchIssueType(issueType, listIssueTypes(project));
        IssueInputBuilder issueBuilder =
                new IssueInputBuilder(DEFAULTS.withDefault(Key.project, project), type.getId());
        issueBuilder.setSummary(DEFAULTS.withDefault(Key.summary, summary));
        issueBuilder.setDescription(DEFAULTS.withDefault(Key.description, description));
        client.getIssueClient().createIssue(issueBuilder.build()).claim();
    }

    /**
     * List unresolved issues (of the given type) for a given project.
     *
     * @param project The project name.
     * @param issueType The issue type.
     * @return A list of unresolved issues that match thr project and type.
     */
    public Iterable<Issue> listUnresolvedIssues(final String project, final String issueType) {
        String p = DEFAULTS.withDefault(Key.project, project);
        IssueType type = matchIssueType(issueType, listIssueTypes(p));
        String role = DEFAULTS.withDefault(Key.role);
        String jsql =
                format("project=\"%s\" and %s=currentUser() and issuetype=\"%s\" and resolution=\"unresolved\"",
                        p, role, type.getName());
        return client.getSearchClient().searchJql(jsql).claim().getIssues();
    }

    /**
     * Find an issue that matches the information given.
     *
     * @param issueId The ID of the issue
     * @param issues The available issues.
     * @return An issue whose ID matches that given, or an exception is raised.
     */
    private Issue matchIssue(final long issueId, final Iterable<Issue> issues) {
        for (Issue issue : issues) {
            if (issue.getId() == issueId) {
                return issue;
            }
        }
        throw new MessageException(format("No issue matching ID %d", issueId));
    }

    /**
     * List transitions for a given issue.
     *
     * @param uri The transition URI.
     * @return A list of transitions for that URI.
     */
    public Iterable<Transition> listTransitions(final URI uri) {
        return client.getIssueClient().getTransitions(uri).claim();
    }

    /**
     * Find a transition that matches the information given.
     *
     * @param transitionName The name of the transition
     * @param transitions The available transitions.
     * @return A transition whose name matches that given, or an exception is raised.
     */
    private Transition matchTransitions(final String transitionName, final Iterable<Transition> transitions) {
        String name = DEFAULTS.withDefault(Key.transition, transitionName);
        for (Transition transition : transitions) {
            if (transition.getName().equalsIgnoreCase(name)) {
                return transition;
            }
        }
        throw new MessageException(format("No transition matching %s", name));
    }

    /**
     * Close an issue (more exactly, apply the given transition).
     *
     * @param issue The issue to close.
     * @param transitionName The name of the transition.
     */
    public void closeIssue(final Issue issue, final String transitionName) {
        Transition transition = matchTransitions(transitionName, listTransitions(issue.getTransitionsUri()));
        TransitionInput input = new TransitionInput(transition.getId());
        client.getIssueClient().transition(issue, input).claim();
    }

    /**
     * Close an issue (more exactly, apply the given transition).
     *
     * @param project The project name.
     * @param issueType The issue type.
     * @param issueId The issue ID.
     * @param transitionName The name of the transition.
     */
    public void closeIssue(final String project, final String issueType,
            final Long issueId, final String transitionName) {
        Issue issue = matchIssue(issueId, listUnresolvedIssues(project, issueType));
        closeIssue(issue, transitionName);
    }

}
