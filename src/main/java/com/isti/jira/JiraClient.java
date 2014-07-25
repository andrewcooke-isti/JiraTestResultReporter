package com.isti.jira;

import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.api.GetCreateIssueMetadataOptions;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicProject;
import com.atlassian.jira.rest.client.api.domain.CimIssueType;
import com.atlassian.jira.rest.client.api.domain.CimProject;
import com.atlassian.jira.rest.client.api.domain.Field;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.isti.jira.Defaults.Key;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang.StringUtils.isBlank;


/**
 * High level wrapper for Jira's own client.  This blocks on actions and throws RumtimeException on error.
 *
 * Default values for null parameters are taken from properties in the home directory (DEFAULTS).
 *
 * IMPORTANT: The client must be closed on exit, or the program will hang.  This is a feature of the
 * underlying Jira library.  Use try / finally at the top level.
 */
public final class JiraClient {

    /** This field must be added to JIRA to store the git repo. */
    public static final String CATS_REPOSITORY = "CATS Repository";

    /** This field must be added to JIRA to store the git branch. */
    public static final String CATS_BRANCH = "CATS Branch";

    /** This field must be added to JIRA to store the hash used to identify issues. */
    public static final String CATS_HASH = "CATS Hash";

    /**
     * Allow anonymous connections (possible but useless).
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
     * A cache of known fields.  Access indirectly via matchFieldName().
     */
    private Map<String, Field> cachedFields = null;

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
     * @param fieldName The field name
     * @return A field with the given name, if it is unique.
     */
    private synchronized Field matchFieldName(final String fieldName) {
        if (null == cachedFields) {
            cachedFields = new HashMap<String, Field>();
            for (Field field: client.getMetadataClient().getFields().claim()) {
                // use null to indicate duplicates
                cachedFields.put(field.getName(), cachedFields.containsKey(field.getName()) ? null : field);
            }
        }
        if (cachedFields.containsKey(fieldName)) {
            if (null == cachedFields.get(fieldName)) {
                throw new RuntimeException(format("Field name '%s' is not unique", fieldName));
            } else {
                return cachedFields.get(fieldName);
            }
        } else {
            throw new RuntimeException(format("Unknown field name '%s'", fieldName));
        }
    }

    /**
     * Create an issue.  All the parameters below are provided with defaults and the issue type is expanded
     * against the project's known issue types.
     *
     * @param project The project name.
     * @param issueType The issue type.
     * @param repo The git repository details.
     * @param result Details of the test failure.
     */
    public void createIssue(final String project,
                            final String issueType,
                            final RepoDetails repo,
                            final UniformTestResult result) {
        IssueType type = matchIssueType(issueType, listIssueTypes(project));
        IssueInputBuilder issueBuilder =
                new IssueInputBuilder(DEFAULTS.withDefault(Key.project, project), type.getId());
        issueBuilder.setSummary(DEFAULTS.withDefault(Key.summary, result.getSummary()));
        issueBuilder.setDescription(DEFAULTS.withDefault(Key.description, result.getDescription()));
        issueBuilder.setFieldValue(
                matchFieldName(CATS_REPOSITORY).getId(),
                DEFAULTS.withDefault(Key.repository, repo.getURL(), true));
        issueBuilder.setFieldValue(
                matchFieldName(CATS_BRANCH).getId(),
                DEFAULTS.withDefault(Key.branch, repo.getBranch(), true));
        issueBuilder.setFieldValue(matchFieldName(CATS_HASH).getId(), result.getHash(repo));
        client.getIssueClient().createIssue(issueBuilder.build()).claim();
    }

    /**
     * List unresolved issues (of the given type) for a given project.
     *
     * @param project The project name.
     * @param issueType The issue type.
     * @param repo Details of the git repository.
     * @return A list of unresolved issues that match thr project and type.
     */
    public Iterable<Issue> listUnresolvedIssues(final String project,
                                                final String issueType,
                                                final RepoDetails repo) {
        String p = DEFAULTS.withDefault(Key.project, project);
        IssueType type = matchIssueType(issueType, listIssueTypes(p));
        String role = DEFAULTS.withDefault(Key.role);
        StringBuilder jsql = new StringBuilder(
                format("project=\"%s\" and %s=currentUser() and issuetype=\"%s\" and resolution=\"unresolved\"",
                        p, role, type.getName()));
        String url = DEFAULTS.withDefault(Key.repository, repo.getURL(), true);
        if (!isBlank(url)) {
            // both searches are on text fields and require "contains".
            //for an exact match they also require quotes.
            // in jql that means "foo"~"\"bar\""
            jsql.append(format(" and \"%s\"~\"\\\"%s\\\"\"", CATS_REPOSITORY, url));
        }
        String branch = DEFAULTS.withDefault(Key.branch, repo.getBranch(), true);
        if (!isBlank(branch)) {
            jsql.append(format(" and \"%s\"~\"\\\"%s\\\"\"", CATS_BRANCH, branch));
        }
        return client.getSearchClient().searchJql(jsql.toString()).claim().getIssues();
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
     * @param repo The git repository details.
     * @param issueId The issue ID.
     * @param transitionName The name of the transition.
     */
    public void closeIssue(final String project,
                           final String issueType,
                           final RepoDetails repo,
                           final Long issueId,
                           final String transitionName) {
        Issue issue = matchIssue(issueId, listUnresolvedIssues(project, issueType, repo));
        closeIssue(issue, transitionName);
    }

}
