package com.isti.jira;

import com.atlassian.jira.rest.client.AuthenticationHandler;
import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.ProgressMonitor;
import com.atlassian.jira.rest.client.auth.AnonymousAuthenticationHandler;
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler;
import com.atlassian.jira.rest.client.domain.BasicProject;
import com.atlassian.jira.rest.client.internal.jersey.JerseyJiraRestClientFactory;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import io.airlift.command.*;
import org.apache.tools.ant.Project;

import javax.management.ObjectName;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import static java.lang.String.format;

public class CmdLine {

    private static final String DOT_FILE = ".catsjira";

    private static final String DEFAULT_USER = "CATS";
    private static final String DEFAULT_URL = "http://localhost:8081";

    public static void main(String[] args) {
        Cli.CliBuilder<Runnable> builder = Cli.<Runnable>builder("CmdLine")
                .withDescription("Command line tool for Jira")
                .withDefaultCommand(Help.class)
                .withCommands(Help.class, CreateUser.class, ListProjects.class);
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

        @Option(type = OptionType.GLOBAL, name = "-u", description = "User to connect as")
        public String user;

        @Option(type = OptionType.GLOBAL, name = "-p", description = "Password to connect with")
        public String password;

        @Option(type = OptionType.GLOBAL, name = "-h", description = "Jira URL")
        public String url;

        private Properties propertiesCache = null;

        public JiraRestClient getClient() {
            try {
                JerseyJiraRestClientFactory factory = new JerseyJiraRestClientFactory();
                URI jiraServerUri = new URI(withDefault("url", this.url, DEFAULT_URL));
                return factory.create(jiraServerUri, getAuthHandler(jiraServerUri));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        private AuthenticationHandler getAuthHandler(URI uri) {
            String password = withDefault("password", this.password, null);
            if (password == null) {
                System.err.println(format("Connecting anonymously to %s", uri));
                return new AnonymousAuthenticationHandler();
            } else {
                String user = withDefault("user", this.user, DEFAULT_USER);
                System.err.println(format("Connecting to %s as %s", uri, user));
                return new BasicHttpAuthenticationHandler(user, password);
            }
        }

        private String withDefault(final String key, final String value, final String deflt) {
            String result = value;
            if (result == null) {
                Properties properties = getProperties();
                result = properties.getProperty(key);
            }
            if (result == null) {
                result = deflt;
            }
            return result;
        }

        private synchronized Properties getProperties() {
            if (propertiesCache == null) {
                propertiesCache = new Properties();
                loadPropertiesFrom("/var/lib/jenkins");
                loadPropertiesFrom(System.getProperty("home.user"));
            }
            return propertiesCache;
        }

        private void loadPropertiesFrom(final String dir) {
            if (dir == null) return;
            String path = dir + "/" + DOT_FILE;
            File file = new File(path);
            if (file.exists() && file.isFile() && file.canRead()) {
                try {
                    propertiesCache.load(new FileReader(file));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }

    @Command(name="add-user", description="Add a Jenkins user to Jira")
    public static class CreateUser extends Connection implements Runnable {

        public void run() {
            System.out.println("create user");
        }

    }

    @Command(name="list-projects", description="List Jira projects")
    public static class ListProjects extends Connection implements Runnable {

        public void run() {
            JiraRestClient client = getClient();
            for (BasicProject project : client.getProjectClient().getAllProjects(new NullProgressMonitor())) {
                System.out.println(project.getName());
            }
        }

    }

    private static class NullProgressMonitor implements ProgressMonitor {}

    private static boolean handleException(Throwable e) {
        // overloading is static resolution
        if (e instanceof UniformInterfaceException && handleException((UniformInterfaceException)e)) return true;
        return e.getCause() != null && handleException(e.getCause());
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
