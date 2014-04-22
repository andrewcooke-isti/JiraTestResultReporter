package com.isti.jira;

import com.atlassian.jira.rest.client.api.domain.BasicProject;
import com.atlassian.jira.rest.client.api.domain.CimIssueType;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Transition;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import io.airlift.command.Cli;
import io.airlift.command.Command;
import io.airlift.command.Help;
import io.airlift.command.Option;
import io.airlift.command.OptionType;

import java.net.URI;

import static java.lang.String.format;


/**
 * A command line interface to the functionality exposed in JiraClient.  This allows testing and exploration at
 * the command line (debugging a plugin from within Jenkins is not much fun).
 *
 * The general interface is something like git, with various commands as parameters.  Use the -h option
 * to display the help text.
 */
public final class CmdLine {

    /**
     * Hide constructor for utility class.
     */
    private CmdLine() { }

    /**
     * Invoke the utility from the command line.
     *
     * @param args Command line arguments.
     */
    public static void main(final String[] args) {
        Cli.CliBuilder<Runnable> builder = Cli.<Runnable>builder("CmdLine")
                .withDescription("Command line tool for Jira")
                .withDefaultCommand(Help.class)
                .withCommands(Help.class, ListDefaults.class, ListProjects.class, ListIssueTypes.class,
                        CreateIssue.class, ListUnresolvedIssues.class, ListTransitions.class, CloseIssue.class);
        Cli<Runnable> parser = builder.build();
        try {
            parser.parse(args).run();
        } catch (RuntimeException e) {
            if (handleException(e)) {
                System.exit(1);
            } else {
                throw e;
            }
        }
    }

    /**
     * Encapsulate the connection command line arguments; implementation handled by jiraClient.
     */
    private static class Connection {

        /** Username from the command line. */
        @Option(type = OptionType.GLOBAL, name = "-U", description = "User to connect as")
        private String user;

        /** Password from the command line. */
        @Option(type = OptionType.GLOBAL, name = "-P", description = "Password to connect with")
        private String password;

        /** URL to connect to from the command line. */
        @Option(type = OptionType.GLOBAL, name = "-H", description = "Jira URL")
        private String url;

        /**
         * @return A client that connects with the parameters / attributes above.
         */
        public JiraClient getClient() {
            return new JiraClient(url, user, password);
        }

    }

    /**
     * The command to list default settings from the global properties file.
     */
    @Command(name = "list-defaults", description = "List default settings")
    public static class ListDefaults implements Runnable {

        /**
         * Run the command.
         */
        public final void run() {
            new Defaults().listTo(System.err);
        }

    }

    /**
     * The command to list projects.
     */
    @Command(name = "list-projects", description = "List Jira projects")
    public static class ListProjects extends Connection implements Runnable {

        /**
         * Run the command.
         */
        public final void run() {
            JiraClient client = getClient();
            try {
                for (BasicProject project : client.listProjects()) {
                    System.out.println(format("%s: %s", project.getKey(), project.getName()));
                }
            } finally {
                client.close();
            }
        }

    }

    /**
     * The command to list issue types for a given project.
     */
    @Command(name = "list-issue-types", description = "List issue types for a project")
    public static class ListIssueTypes extends Connection implements Runnable {

        /** Project from the command line. */
        @Option(name = "-p", description = "Project ID")
        private String project;

        /**
         * Run the command.
         */
        public final void run() {
            JiraClient client = getClient();
            try {
                for (CimIssueType type : client.listIssueTypes(project)) {
                    System.out.println(type.getName());
                }
            } finally {
                client.close();
            }
        }

    }

    /**
     * Command to create a new issue.
     */
    @Command(name = "create-issue", description = "Create a new issue")
    public static class CreateIssue extends Connection implements Runnable {

        /** Project from the command line. */
        @Option(name = "-p", description = "Project ID")
        private String project;

        /** Issue type from the command line. */
        @Option(name = "-t", description = "Issue type")
        private String type;

        /** Summary from the command line. */
        @Option(name = "-s", description = "Summary")
        private String summary;

        /** Description from the command line. */
        @Option(name = "-d", description = "Description")
        private String description;

        /**
         * Run the command.
         */
        public final void run() {
            JiraClient client = getClient();
            try {
                client.createIssue(project, type, summary, description);
            } finally {
                client.close();
            }
        }

    }

    /**
     * The command to list unresolved issues for a given project.
     */
    @Command(name = "list-unresolved-issues", description = "List unresolved issues for a project")
    public static class ListUnresolvedIssues extends Connection implements Runnable {

        /** Project from the command line. */
        @Option(name = "-p", description = "Project ID")
        private String project;

        /** Issue type from the command line. */
        @Option(name = "-t", description = "Issue type")
        private String type;

        /**
         * Run the command.
         */
        public final void run() {
            JiraClient client = getClient();
            try {
                for (Issue issue : client.listUnresolvedIssues(project, type)) {
                    System.out.println(formatIssue(issue));
                }
            } finally {
                client.close();
            }
        }

        /**
         * @param issue The issue to format
         * @return Something we can print that's useful and not terribly ugly.
         */
        private static String formatIssue(final Issue issue) {
            return format("\n[%d] %s\n%s\n%s\n",
                    issue.getId(), issue.getSummary(), issue.getDescription(), issue.getTransitionsUri());
        }

    }

    /**
     * The command to list transitions for a given URI (the URI is displayed when issues are listed).
     */
    @Command(name = "list-transitions", description = "List transitions for a given URI")
    public static class ListTransitions extends Connection implements Runnable {

        /** Project from the command line. */
        @Option(name = "-u", description = "Transition URI")
        private String uri;

        /**
         * Run the command.
         */
        public final void run() {
            JiraClient client = getClient();
            try {
                for (Transition transition : client.listTransitions(URI.create(uri))) {
                    System.out.println(transition);
                }
            } finally {
                client.close();
            }
        }

    }

    /**
     * Command to create a new issue.
     */
    @Command(name = "close-issue", description = "Close an unresolved issue")
    public static class CloseIssue extends Connection implements Runnable {

        /** Project from the command line. */
        @Option(name = "-p", description = "Project ID")
        private String project;

        /** Issue type from the command line. */
        @Option(name = "-t", description = "Issue type")
        private String type;

        /** Issue ID from the command line. */
        @Option(name = "-i", description = "Issue ID")
        private Long id;

        /** Transition from the command line. */
        @Option(name = "-n", description = "Transition name")
        private String transition;

        /**
         * Run the command.
         */
        public final void run() {
            JiraClient client = getClient();
            try {
                client.closeIssue(project, type, id, transition);
            } finally {
                client.close();
            }
        }

    }

    /**
     * Handle exceptions that arrive at the top level.  We try a variety of types until some handler returns true.
     * If no handler at this level works, then we try the cause.  If that doesn't work we return false and the
     * exception is likely thrown to the user.
     *
     * @param e The exception to handle.
     * @return true if the expected action was taken (and the exception can be discarded).
     */
    private static boolean handleException(final Throwable e) {
        // overloading is static resolution
        if (e instanceof UniformInterfaceException && handleException((UniformInterfaceException) e)) {
            return true;
        }
        if (e instanceof MissingArgumentException && handleException((MissingArgumentException) e)) {
            return true;
        }
        if (e instanceof MessageException && handleException((MessageException) e)) {
            return true;
        }
        return e.getCause() != null && handleException(e.getCause());
    }

    /**
     * For missing arguments, display a message to the user.
     *
     * @param e The exception with details of the missing argument.
     * @return true always.
     */
    private static boolean handleException(final MissingArgumentException e) {
        System.err.println(format("%s is required", e.getMessage()));
        return true;
    }

    /**
     * For message exceptions, display the message.
     *
     * @param e The exception with a message to display.
     * @return true always.
     */
    private static boolean handleException(final MessageException e) {
        System.err.println(e.getMessage());
        return true;
    }

    /**
     * The UniformInterfaceException comes from the low level HTTP libraries and caontains an HTTP error code
     * which we can interpret for the user.
     *
     * @param e The exception with an HTTP code.
     * @return true if we understand the code or have an understandable cause.
     */
    private static boolean handleException(final UniformInterfaceException e) {
        if (e.getResponse() != null) {
            ClientResponse response = e.getResponse();
            ClientResponse.Status status = response.getClientResponseStatus();
            if (status != null) {
                if (status.getStatusCode() == 503) {
                    System.err.println("JIRA not available (503; not configured?)");
                    return true;
                } else if (status.getStatusCode() == 404) {
                    System.err.println("JIRA doesn't have the resource (404; not configured on error in plugin?)");
                    return true;
                }
            }
        }
        return e.getCause() != null && handleException(e.getCause());
    }

}
