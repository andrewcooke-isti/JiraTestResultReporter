package com.isti.jira;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.internal.jersey.JerseyJiraRestClientFactory;
import io.airlift.command.Cli;
import io.airlift.command.Command;
import io.airlift.command.Help;

import java.net.URI;
import java.net.URISyntaxException;

public class CmdLine {

    public static void main(String[] args) {
        Cli.CliBuilder<Runnable> builder = Cli.<Runnable>builder("CmdLine")
                .withDescription("Command line tool for Jira")
                .withDefaultCommand(Help.class)
                .withCommands(Help.class, CreateUser.class);
        Cli<Runnable> parser = builder.build();
        parser.parse(args).run();
    }

    private static class Connection {

        public JiraRestClient getClient() throws URISyntaxException {
            JerseyJiraRestClientFactory factory = new JerseyJiraRestClientFactory();
            URI jiraServerUri = new URI("http://localhost:8081/jira");
            return factory.create(jiraServerUri, null);
        }

    }

    @Command(name="add-user", description="Add a Jenkins user to Jira")
    public static class CreateUser extends Connection implements Runnable {

        public void run() {
            System.out.println("create user");
        }
    }

}
