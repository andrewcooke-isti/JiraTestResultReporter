package JiraTestResultReporter;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.isti.jira.Defaults;
import com.isti.jira.JiraClient;
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
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.MetaTabulatedResult;
import hudson.tasks.test.TestResult;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.tap4j.plugin.model.TapStreamResult;

import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.isti.jira.Defaults.Key;
import static com.isti.jira.JiraClient.ALLOW_ANON;
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

        PrintStream logger = listener.getLogger();
        logger.printf("%s Examining test results...%n", pInfo);

        debugLog(listener,
                format("Build result is %s",
                        build.getResult().toString())
        );

        String workspace = build.getWorkspace().toString();
        String tag = jenkinsProjectTag(build.getProject().getName());
        AbstractTestResultAction<?> testResultAction = build.getTestResultAction();
        Object result = testResultAction.getResult();
        Collection<? extends TestResult> failedTests = null;
        if (result instanceof TapStreamResult) {
            failedTests = ((TapStreamResult) result).getFailedTests2();
        } else if (result instanceof MetaTabulatedResult) {
            failedTests = ((MetaTabulatedResult) result).getFailedTests();
        } else {
            throw new RuntimeException(format("Cannot handle type %s", result.getClass().getSimpleName()));
        }

        printResultItems(failedTests, workspace, listener);
        // create each time since it's not clear how to close on Jenkins shutdown
        // (and the overhead once per test isn't an issue anyway).
        JiraClient client = new JiraClient(serverUrl, username, password);
        try {
            Iterable<Issue> existingIssues = client.listUnresolvedIssues(projectKey, issueType);
            createJiraIssues(failedTests, existingIssues, tag, workspace, listener, client);
            closeJiraIssues(failedTests, existingIssues, tag, listener, client);
        } finally {
            client.close();
        }

        logger.printf("%s Done.%n", pInfo);
        return true;
    }

    private void printResultItems(final Collection<? extends TestResult> failedTests,
                                  final String workspace,
                                  final BuildListener listener) {
        if (!this.debugFlag) {
            return;
        }
        PrintStream out = listener.getLogger();
        for (TestResult result : failedTests) {
            out.printf("%s projectKey: %s%n", pDebug, this.projectKey);
            out.printf("%s errorDetails: %s%n", pDebug, result.getErrorDetails());
            out.printf("%s title: %s%n", pDebug, result.getTitle());
            out.printf("%s name: %s%n", pDebug, result.getName());
            out.printf("%s failedSince: %d%n", pDebug, result.getFailedSince());
            out.printf("%s ErrorStackTrace: %s%n", pDebug, result.getErrorStackTrace());
            String affectedFile = result.getErrorStackTrace().replace(workspace, "");
            out.printf("%s affectedFile: %s%n", pDebug, affectedFile);
            if (result instanceof CaseResult) {
                CaseResult caseResult = (CaseResult) result;
                out.printf("%s fullName: %s%n", pDebug, caseResult.getFullName());
                out.printf("%s simpleName: %s%n", pDebug, caseResult.getSimpleName());
                out.printf("%s packageName: %s%n", pDebug, caseResult.getPackageName());
                out.printf("%s className: %s%n", pDebug, caseResult.getClassName());
                out.printf("%s status: %s%n", pDebug, caseResult.getStatus().toString());
                out.printf("%s age: %s%n", pDebug, caseResult.getAge());
            } else {
                out.printf("WARNING: Not CaseResult instance%n");
            }
            out.printf("%s ----------------------------%n", pDebug);
        }
    }

    void debugLog(final BuildListener listener, final String message) {
        if (!this.debugFlag) {
            return;
        }
        PrintStream logger = listener.getLogger();
        logger.printf("%s %s%n", pDebug, message);
    }

    /**
     * @param jenkinsProjectName The (Jenkins) project name.
     * @return A prefix used to identify issues belonging to this project.
     */
    private String jenkinsProjectTag(final String jenkinsProjectName) {
        return format("[Jenkins: %s]", jenkinsProjectName);
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

    void createJiraIssues(final Collection<? extends TestResult> failedTests,
                          final Iterable<Issue> existingIssues,
                          final String tag,
                          final String workspace,
                          final BuildListener listener,
                          final JiraClient client) {
        PrintStream logger = listener.getLogger();

        Set<String> known = new HashSet<String>();
        for (Issue issue: existingIssues) {
            known.add(issue.getSummary());
        }

        for (TestResult result : failedTests) {
            CaseResult caseResult = castOrFail(result);
            String summary = summarize(tag, caseResult);

            if (known.contains(summary)) {
                logger.printf("%s Jira already contains \"%s\".%n", pInfo, summary);

            } else if ((caseResult.getAge() == 1) || (this.createAllFlag)) {
                debugLog(listener,
                        format("Creating issue in project %s at URL %s%n",
                               DEFAULTS.withDefault(Key.project, projectKey),
                               DEFAULTS.withDefault(Key.url, serverUrl)));
                String description = format("%s\nClass: %s\nTrace: %s",
                        caseResult.getErrorDetails(),
                        caseResult.getClassName(),
                        caseResult.getErrorStackTrace().replace(workspace, ""));
                client.createIssue(projectKey, issueType, summary, description);

            } else {
                logger.printf("%s This issue is old; not reporting (select the 'create all' checkbox to force).%n", pInfo);
            }
        }
    }

    void closeJiraIssues(final Collection<? extends TestResult> failedTests,
                         final Iterable<Issue> existingIssues,
                         final String tag,
                         final BuildListener listener,
                         final JiraClient client) {

        PrintStream logger = listener.getLogger();

        Set<String> known = new HashSet<String>();
        for (TestResult result: failedTests) {
            known.add(summarize(tag, castOrFail(result)));
        }

        // run through the open issues and see which are no longer present
        for (Issue issue: existingIssues) {
            String summary = issue.getSummary();
            if (summary.contains(tag)) {
                if (known.contains(summary)) {
                    logger.printf("%s Still open: %s%n", pInfo, summary);
                } else {
                    client.closeIssue(issue, transition);
                    logger.printf("%s Closed: %s%n", pInfo, summary);
                }
            }
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    // for now, just case - perhaps later we need to do something else?
    static CaseResult castOrFail(TestResult result) {
        return (CaseResult) result;
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

