package JiraTestResultReporter;

import com.isti.jira.JiraClient;
import hudson.Extension;
import hudson.FilePath;
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
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import static java.lang.String.format;


public class JiraReporter extends Notifier {

    public String projectKey;
    public String serverUrl;
    public String username;
    public String password;

    public boolean debugFlag;
    public boolean verboseDebugFlag;
    public boolean createAllFlag;

    private FilePath workspace;

    private static final String PluginName = "[JiraTestResultReporter]";
    private final String pInfo = format("%s [INFO]", PluginName);
    private final String pDebug = format("%s [DEBUG]", PluginName);
    private final String pVerbose = format("%s [DEBUGVERBOSE]", PluginName);
    private final String prefixError = format("%s [ERROR]", PluginName);

    @DataBoundConstructor
    public JiraReporter(String projectKey,
                        String serverUrl,
                        String username,
                        String password,
                        boolean createAllFlag,
                        boolean debugFlag,
                        boolean verboseDebugFlag) {

        this.projectKey = projectKey;
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = password;

        this.verboseDebugFlag = verboseDebugFlag;
        this.debugFlag = verboseDebugFlag || debugFlag;

        this.createAllFlag = createAllFlag;
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
                format("Build result is %s%n",
                        build.getResult().toString())
        );

        this.workspace = build.getWorkspace();
        debugLog(listener,
                format("%s Workspace is %s%n", pInfo, this.workspace.toString())
        );

        AbstractTestResultAction<?> testResultAction = build.getTestResultAction();
        List<CaseResult> failedTests = testResultAction.getFailedTests();
        printResultItems(failedTests, listener);
        createJiraIssue(failedTests, listener);

        logger.printf("%s Done.%n", pInfo);
        return true;
    }

    private void printResultItems(final List<CaseResult> failedTests,
                                  final BuildListener listener) {
        if (!this.debugFlag) {
            return;
        }
        PrintStream out = listener.getLogger();
        for (CaseResult result : failedTests) {
            out.printf("%s projectKey: %s%n", pDebug, this.projectKey);
            out.printf("%s errorDetails: %s%n", pDebug, result.getErrorDetails());
            out.printf("%s fullName: %s%n", pDebug, result.getFullName());
            out.printf("%s simpleName: %s%n", pDebug, result.getSimpleName());
            out.printf("%s title: %s%n", pDebug, result.getTitle());
            out.printf("%s packageName: %s%n", pDebug, result.getPackageName());
            out.printf("%s name: %s%n", pDebug, result.getName());
            out.printf("%s className: %s%n", pDebug, result.getClassName());
            out.printf("%s failedSince: %d%n", pDebug, result.getFailedSince());
            out.printf("%s status: %s%n", pDebug, result.getStatus().toString());
            out.printf("%s age: %s%n", pDebug, result.getAge());
            out.printf("%s ErrorStackTrace: %s%n", pDebug, result.getErrorStackTrace());

            String affectedFile = result.getErrorStackTrace().replace(this.workspace.toString(), "");
            out.printf("%s affectedFile: %s%n", pDebug, affectedFile);
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

    void createJiraIssue(final List<CaseResult> failedTests,
                         final BuildListener listener) {
        PrintStream logger = listener.getLogger();

        for (CaseResult result : failedTests) {
            if ((result.getAge() == 1) || (this.createAllFlag)) {
                debugLog(listener,
                        format("Creating issue in project %s at URL %s%n",
                                this.projectKey, this.serverUrl)
                );

                // create each time since it's not clear how to close on Jenkins shutdown
                // (and the overhead once per test isn't an issue anyway).
                JiraClient client = new JiraClient(this.serverUrl, this.username, this.password);
                try {
                    String summary = format("The test %s failed %s: %s",
                            result.getName(), result.getClassName(), result.getErrorDetails());
                    String description = format("Test class: %s -- %s",
                            result.getClassName(),
                            result.getErrorStackTrace().replace(this.workspace.toString(), ""));
                    client.createIssue(this.projectKey, null, summary, description);
                } finally {
                    client.close();
                }

            } else {
                logger.printf("%s This issue is old; not reporting.%n", pInfo);
            }
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Jira Test Result Reporter";
        }

        public FormValidation doCheckProjectKey(@QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.error("You must provide a project key.");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckServerAddress(@QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.error("You must provide an URL.");
            }

            try {
                new URL(value);
            } catch (final MalformedURLException e) {
                return FormValidation.error("This is not a valid URL.");
            }

            return FormValidation.ok();
        }
    }

}

