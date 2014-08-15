package com.isti.jira;

import com.atlassian.jira.rest.client.api.domain.BasicProject;
import com.atlassian.jira.rest.client.api.domain.CimIssueType;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Transition;
import io.airlift.command.Cli;
import io.airlift.command.Command;
import io.airlift.command.Help;
import io.airlift.command.Option;
import io.airlift.command.OptionType;

import java.net.URI;

import static com.isti.jira.JiraClient.CATS_HASH;
import static java.lang.String.format;
import static java.lang.System.exit;


/**
 * A command line interface to the functionality exposed in JiraClient.  This allows testing and exploration at
 * the command line (debugging a plugin from within Jenkins is not much fun).
 *
 * The general interface is something like git, with various commands as parameters.  Use the -h option
 * to display the help text.
 */
public final class CmdLine {

    /**
     * Source of default values (most provided at lower layers, but sometimes we need to check here).
     */
    private static final Defaults DEFAULTS = new Defaults();

    /**
     * Hide constructor for utility class.
     */
    private CmdLine() {
    }

    /**
     * Invoke the utility from the command line.
     *
     * @param args Command line arguments.
     */
    public static void main(final String[] args) {
        // this is installed as jira-remote
        Cli.CliBuilder<Runnable> builder = Cli.<Runnable>builder("jira-remote")
                .withDescription("Command line tool for Jira")
                .withDefaultCommand(Help.class)
                .withCommands(Help.class, ListDefaults.class, ListProjects.class, ListIssueTypes.class,
                        CreateIssue.class, ListUnresolvedIssues.class, ListTransitions.class, CloseIssue.class);
        // clunky because we need to handle *only* the parser errors.
        Runnable cmd = null;
        try {
            cmd = builder.build().parse(args);
        } catch (RuntimeException e) {
            System.err.println(format("ERROR: %s", e.getMessage()));
            exit(1);
        }
        cmd.run();
    }


    /**
     * Handle errors verbosely if required.
     */
    private abstract static class VerboseCommand implements Runnable {

        /**
         * Verbose flag from the command line.
         */
        @Option(type = OptionType.GLOBAL, name = "-v", description = "Show error details")
        private Boolean verbose = false;

        /**
         * Subclasses should override this to implement the command.
         */
        protected abstract void doCommand();

        /**
         * Run the command and display eny error.
         */
        public void run() {
            try {
                doCommand();
            } catch (RuntimeException e) {
                if (verbose) {
                    throw e;
                } else {
                    System.err.println(format("ERROR: %s", e.getMessage()));
                    exit(1);
                }
            }
        }

    }


    /**
     * Encapsulate the connection command line arguments; implementation handled by jiraClient.
     */
    private abstract static class Connection extends VerboseCommand {

        /**
         * Username from the command line.
         */
        @Option(type = OptionType.GLOBAL, name = "-U", description = "User to connect as")
        private String user;

        /**
         * Password from the command line.
         */
        @Option(type = OptionType.GLOBAL, name = "-P", description = "Password to connect with")
        private String password;

        /**
         * URL to connect to from the command line.
         */
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
     * Add further command line arguments for the repo.
     */
    private abstract static class GitConnection extends Connection {

        /**
         * Git repository.
         */
        @Option(name = "-r", description = "Git repository")
        private String repo;

        /**
         * Git branch.
         */
        @Option(name = "-b", description = "Git branch")
        private String branch;

        /**
         * Git commit from the command line..
         */
        @Option(name = "-c", description = "Git commit")
        private String commit;

        /**
         * @return Repository details taken from the command line.
         */
        public RepoDetails getRepo() {
            return new RepoDetails(repo, branch, commit);
        }

    }

    /**
     * The command to list default settings from the global properties file.
     */
    @Command(name = "list-defaults", description = "List default settings")
    public static class ListDefaults extends VerboseCommand {

        /**
         * Run the command.
         */
        public final void doCommand() {
            new Defaults().listTo(System.err);
        }

    }

    /**
     * The command to list projects.
     */
    @Command(name = "list-projects", description = "List Jira projects")
    public static class ListProjects extends Connection {

        /**
         * Run the command.
         */
        public final void doCommand() {
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
    public static class ListIssueTypes extends Connection {

        /**
         * Project from the command line.
         */
        @Option(name = "-p", description = "Project ID")
        private String project;

        /**
         * Run the command.
         */
        public final void doCommand() {
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
    public static class CreateIssue extends GitConnection {

        /**
         * Project from the command line.
         */
        @Option(name = "-p", description = "Project ID")
        private String project;

        /**
         * Issue type from the command line.
         */
        @Option(name = "-t", description = "Issue type")
        private String type;

        /**
         * Summary from the command line.
         */
        @Option(name = "-s", description = "Summary")
        private String summary;

        /**
         * Description from the command line.
         */
        @Option(name = "-d", description = "Description")
        private String description;

        /**
         * Run the command.
         */
        public final void doCommand() {
            String title = DEFAULTS.withDefault(Defaults.Key.summary, summary, false);
            RepoDetails repo = getRepo();
            UniformTestResult result = new UniformTestResult(title, description);
            String hash = result.getHash(repo);
            JiraClient client = getClient();
            try {
                for (Issue known : client.listUnresolvedIssues(project, type, repo)) {
                    if (hash.equals(known.getFieldByName(CATS_HASH).toString())) {
                        throw new RuntimeException("Issue already exists");
                    }
                }
                client.createIssue(project, type, repo, new UniformTestResult(title, description));
            } finally {
                client.close();
            }
        }

    }

    /**
     * The command to list unresolved issues for a given project.
     */
    @Command(name = "list-unresolved-issues", description = "List unresolved issues for a project")
    public static class ListUnresolvedIssues extends GitConnection {

        /**
         * Project from the command line.
         */
        @Option(name = "-p", description = "Project ID")
        private String project;

        /**
         * Issue type from the command line.
         */
        @Option(name = "-t", description = "Issue type")
        private String type;

        /**
         * Run the command.
         */
        public final void doCommand() {
            RepoDetails repo = getRepo();
            JiraClient client = getClient();
            try {
                for (Issue issue : client.listUnresolvedIssues(project, type, repo)) {
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
            return format("\nID: %d\nSummary: %s\nDescription: %s\nURI: %s\n",
                    issue.getId(), issue.getSummary(),
                    issue.getDescription().replace("\n", "\n "),
                    issue.getTransitionsUri());
        }

    }

    /**
     * The command to list transitions for a given URI (the URI is displayed when issues are listed).
     */
    @Command(name = "list-transitions", description = "List transitions for a given URI")
    public static class ListTransitions extends Connection {

        /**
         * Project from the command line.
         */
        @Option(name = "-u", description = "Transition URI", required = true)
        private String uri;

        /**
         * Run the command.
         */
        public final void doCommand() {
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
    public static class CloseIssue extends GitConnection {

        /**
         * Project from the command line.
         */
        @Option(name = "-p", description = "Project ID")
        private String project;

        /**
         * Issue type from the command line.
         */
        @Option(name = "-t", description = "Issue type")
        private String type;

        /**
         * Issue ID from the command line.
         */
        @Option(name = "-i", description = "Issue ID", required = true)
        private Long id;

        /**
         * Transition from the command line.
         */
        @Option(name = "-n", description = "Transition name")
        private String transition;

        /**
         * Run the command.
         */
        public final void doCommand() {
            JiraClient client = getClient();
            try {
                client.closeIssue(project, type, getRepo(), id, transition);
            } finally {
                client.close();
            }
        }

    }

}
