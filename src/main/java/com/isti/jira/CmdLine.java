package com.isti.jira;

import com.atlassian.jira.rest.client.api.domain.BasicProject;
import com.atlassian.jira.rest.client.api.domain.CimIssueType;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import io.airlift.command.*;

import static java.lang.String.format;


/**
 * A command line interface to the functionality exposed in JiraClient.  This allows testing and exploration at
 * the command line (debugging a plugin from within Jenkins is not much fun).
 *
 * The general interface is something like git, with various commands as parameters.  Use the -h option
 * to display the help text.
 */
public class CmdLine {

    public static void main(String[] args) {
        Cli.CliBuilder<Runnable> builder = Cli.<Runnable>builder("CmdLine")
                .withDescription("Command line tool for Jira")
                .withDefaultCommand(Help.class)
                .withCommands(Help.class, ListDefaults.class, ListProjects.class, ListIssueTypes.class,
                        CreateIssue.class);
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

    private static class Connection {

        @Option(type = OptionType.GLOBAL, name = "-U", description = "User to connect as")
        public String user;

        @Option(type = OptionType.GLOBAL, name = "-P", description = "Password to connect with")
        public String password;

        @Option(type = OptionType.GLOBAL, name = "-H", description = "Jira URL")
        public String url;

        public JiraClient getClient() {
            return new JiraClient(url, user, password);
        }

    }

    @Command(name="list-defaults", description="List default settings")
    public static class ListDefaults implements Runnable {

        public void run() {
            new Defaults().listTo(System.err);
        }

    }

    @Command(name="list-projects", description="List Jira projects")
    public static class ListProjects extends Connection implements Runnable {

        public void run() {
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

    @Command(name="list-issue-types", description="List Jira projects")
    public static class ListIssueTypes extends Connection implements Runnable {

        @Option(name = "-p", description = "Project ID")
        public String project;

        public void run() {
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

    @Command(name="create-issue", description="List Jira projects")
    public static class CreateIssue extends Connection implements Runnable {

        @Option(name = "-p", description = "Project ID")
        public String project;

        @Option(name = "-t", description = "Issue type")
        public String type;

        @Option(name = "-s", description = "Summary")
        public String summary;

        @Option(name = "-d", description = "Description")
        public String description;

        public void run() {
            JiraClient client = getClient();
            try {
                client.createIssue(project, type, summary, description);
            } finally {
                client.close();
            }
        }

    }

    private static boolean handleException(Throwable e) {
        // overloading is static resolution
        if (e instanceof UniformInterfaceException && handleException((UniformInterfaceException)e)) return true;
        if (e instanceof MissingArgumentException && handleException((MissingArgumentException)e)) return true;
        if (e instanceof MessageException && handleException((MessageException)e)) return true;
        return e.getCause() != null && handleException(e.getCause());
    }

    private static boolean handleException(MissingArgumentException e) {
        System.err.println(format("%s is required", e.getMessage()));
        return true;
    }

    private static boolean handleException(MessageException e) {
        System.err.println(e.getMessage());
        return true;
    }

    private static boolean handleException(UniformInterfaceException e) {
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
