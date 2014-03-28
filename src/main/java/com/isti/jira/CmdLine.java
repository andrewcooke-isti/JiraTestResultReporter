package com.isti.jira;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.internal.jersey.JerseyJiraRestClientFactory;

import java.net.URI;

public class CmdLine {

    public static void main(String[] args) throws Exception {
        JerseyJiraRestClientFactory factory = new JerseyJiraRestClientFactory();
        URI jiraServerUri = new URI("http://localhost:8081/jira");
        JiraRestClient restClient = factory.create(jiraServerUri, null);
    }

}
