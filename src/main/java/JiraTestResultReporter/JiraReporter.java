package JiraTestResultReporter;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.isti.jira.Defaults;
import com.isti.jira.JiraClient;
import com.isti.jira.Logger;
import com.isti.jira.RepoDetails;
import com.isti.jira.UniformTestResult;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.junit.CaseResult;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import static com.google.common.collect.Lists.newArrayList;
import static com.isti.jira.Defaults.Key;
import static com.isti.jira.JiraClient.ALLOW_ANON;
import static com.isti.jira.JiraClient.CATS_HASH;
import static com.isti.jira.UniformTestResult.unpack;
import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.isEmpty;


/**
 * The main plugin class.
 *
 * This is forked from previous work by mapleSteve, which was a big help (thanks!), but makes checkstyle unhappy.
 */
public final class JiraReporter extends Notifier {

    // THESE MUST BE PUBLIC OR THE PLUGIN DOESN'T WORK (field values read from here afaict)
    public String projectKey;
    public String issueType;
    public String serverUrl;
    public String username;
    public String password;
    public String transition;
    public boolean createAllFlag;
    public boolean debugFlag;

    private static final String PLUGIN_NAME = "[JiraTestResultReporter]";
    private final String pInfo = format("%s [INFO]", PLUGIN_NAME);
    private final String pDebug = format("%s [DEBUG]", PLUGIN_NAME);

    private static final Defaults DEFAULTS = new Defaults();

    // THESE ARGUMENTS MUST MATCH THE ATTRIBUTE NAMES OR THE PLUGIN DOESN'T WORK (field values set by name afaict)
    @DataBoundConstructor
    public JiraReporter(final String projectKey,
                        final String issueType,
                        final String serverUrl,
                        final String username,
                        final String password,
                        final String transition,
                        final boolean createAllFlag,
                        final boolean debugFlag) {

        this.projectKey = projectKey;
        this.issueType = issueType;
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = password;
        this.transition = transition;
        this.createAllFlag = createAllFlag;
        this.debugFlag = debugFlag;

    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(final AbstractBuild build,
                           final Launcher launcher,
                           final BuildListener listener) {

        Logger logger = new Logger(build, listener, debugFlag);
        logger.info("Examining test results...");
        logger.debug("Build result is %s", build.getResult().toString());

        // create a list here to avoid repeatedly running constructors on each use
        Iterable<UniformTestResult> failedTests = newArrayList(unpack(build, logger));
        printFailedTests(logger, failedTests);
        RepoDetails repo = new RepoDetails(build);
        logger.debug("Repo details: %s", repo);

        // create each time since it's not clear how to close on Jenkins shutdown
        // (and the overhead once per test isn't an issue anyway).
        JiraClient client = new JiraClient(serverUrl, username, password);
        try {
            Iterable<Issue> existingIssues = client.listUnresolvedIssues(projectKey, issueType, repo);
            createJiraIssues(failedTests, existingIssues, repo, client, logger);
            closeJiraIssues(failedTests, existingIssues, repo, client, logger);
        } finally {
            client.close();
        }

        logger.info("Done");
        return true;
    }

    private void printFailedTests(final Logger logger,
                                  final Iterable<UniformTestResult> failedTests) {
        for (UniformTestResult result : failedTests) {
            logger.debug(result.toString());
        }
    }

    /**
     * Generate a summary of the test (important because it's how we identify tests later).
     *
     * @param tag The tag used to identify this Jenkins project..
     * @param failedTest The failing test.
     * @return A summary used to identify the test.
     */
    private String summarize(final String tag, final CaseResult failedTest) {
        return format("Test %s failed in %s %s", failedTest.getName(), failedTest.getClassName(), tag);
    }

    void createJiraIssues(final Iterable<UniformTestResult> failedTests,
                          final Iterable<Issue> existingIssues,
                          final RepoDetails repo,
                          final JiraClient client,
                          final Logger logger) {

        Set<String> known = new HashSet<String>();
        for (Issue issue: existingIssues) {
            String hash = issue.getFieldByName(CATS_HASH).getValue().toString();
            known.add(hash);
            logger.debug("Known: %s", hash);
        }

        Set<String> duplicates = new HashSet<String>();
        for (UniformTestResult result : failedTests) {
            String hash = result.getHash(repo);
            
            if (known.contains(hash)) {
                logger.info("Jira already contains '%s'", result);
                logger.info("Hash %s", result.getHash(repo));

            } else if (duplicates.contains(hash)) {
                logger.info("Ignoring duplicate %s", result);

            } else if (result.isNew() || (this.createAllFlag)) {
                logger.info("Creating issue in project %s at URL %s",
                            DEFAULTS.withDefault(Key.project, projectKey),
                            DEFAULTS.withDefault(Key.url, serverUrl));
                client.createIssue(projectKey, issueType, repo, result);
                duplicates.add(hash);

            } else {
                logger.info("This issue is old; not reporting (select the 'create all' checkbox to force)");
            }
        }
    }

    void closeJiraIssues(final Iterable<? extends UniformTestResult> failedTests,
                         final Iterable<Issue> existingIssues,
                         final RepoDetails repo,
                         final JiraClient client,
                         final Logger logger) {

        Set<String> known = new HashSet<String>();
        for (UniformTestResult result: failedTests) {
            known.add(result.getHash(repo));
        }

        int count = 0;
        // run through the open issues and see which are no longer present
        for (Issue issue: existingIssues) {
            String hash = issue.getFieldByName(CATS_HASH).getValue().toString();
            if (known.contains(hash)) {
                logger.info("Keeping: '%s'", issue.getSummary());
                count++;
            } else {
                logger.info("Closing: '%s'", issue.getSummary());
                client.closeIssue(issue, transition);
            }
        }
        logger.debug("Pre-existing issues: %d", count);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        /**
         * Source of default values (typically from /var/lib/jenkins/.catsjira).
         */
        private static final Defaults DEFAULTS = new Defaults();

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Jira Test Result Reporter";
        }

        public FormValidation doCheckProjectKey(@QueryParameter String value) {
            value = DEFAULTS.withDefault(Key.project, value, true);
            if (isEmpty(value)) {
                return FormValidation.error("You must provide a project key (here or in the defaults file).");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckIssueType(@QueryParameter String value) {
            value = DEFAULTS.withDefault(Key.issue_type, value, true);
            if (isEmpty(value)) {
                return FormValidation.error("You must provide an issue type (here or in the defaults file).");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckPassword(@QueryParameter String value) {
            value = DEFAULTS.withDefault(Key.password, value, true);
            if (isEmpty(value) && !ALLOW_ANON) {
                return FormValidation.error("You must provide a password (here or in the defaults file).");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckServerAddress(@QueryParameter String value) {
            value = DEFAULTS.withDefault(Key.url, value, true);
            if (isEmpty(value)) {
                return FormValidation.error("You must provide a URL (here or in the defaults file).");
            }

            try {
                new URL(value);
            } catch (final MalformedURLException e) {
                return FormValidation.error("This is not a valid URL.");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckTransition(@QueryParameter String value) {
            value = DEFAULTS.withDefault(Key.transition, value, true);
            if (isEmpty(value)) {
                return FormValidation.error("You must provide a transition (here or in the defaults file).");
            } else {
                return FormValidation.ok();
            }
        }

    }

}

