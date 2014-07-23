package com.isti.jira;

import com.google.common.base.Function;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.test.MetaTabulatedResult;
import hudson.tasks.test.TestResult;
import org.apache.commons.codec.binary.Hex;
import org.tap4j.plugin.model.TapStreamResult;
import org.tap4j.plugin.model.TapTestResultResult;

import javax.annotation.Nullable;

import java.io.PrintStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.google.common.collect.Iterables.transform;
import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * Different test plugins create different classes to represent test results.
 * This gives a uniform interface to them "all" (currently three).
 *
 * To identify a test we use the following information:
 * - Repo URL
 * - Branch name
 * - The error from the test
 * where the last of these must come from this class.  In addition, we need
 * a summary and description for the report.  The hash must not include test
 * number or line number, since those may change, but that information may
 * be in the summary or description.
 *
 * In addition, we need the know whether the failure is new (if available).
 */
public class UniformTestResult {

    /** A summary (used for JIRA issue title). */
    private String summary;

    /** A description (used ofr JIRA body). */
    private String description;

    /** The error message (hashed with the repo details). */
    private String error;

    /** Is this test new?  True if we don't know, since that forces creation
     * of a JIRA issue
     */
    private boolean isNew = true;


    public UniformTestResult(final String summary, final String description) {
        this(summary, description, summary, true);
    }

    public UniformTestResult(final String summary,
                             final String description,
                             final String error) {
        this(summary, description, error, true);
    }

    public UniformTestResult(final String summary,
                             final String description,
                             final String error,
                             final boolean isNew) {
        this.summary = summary;
        this.description = description;
        this.error = error;
        this.isNew = isNew;
    }

    private UniformTestResult(TapTestResultResult result) {
        throw new RuntimeException("TAP not supported yet");
    }

    private UniformTestResult(CaseResult result, String workspace) {
        this(format("Test %s failed in %s", result.getName(), result.getClassName()),
             format("%s\nClass: %s\nTrace: %s",
                    result.getErrorDetails(),
                    result.getClassName(),
                    result.getErrorStackTrace().replace(workspace, "")),
             result.getErrorDetails(),
             1 == result.getAge());
    }

    private UniformTestResult(TestResult result) {
        throw new RuntimeException("Base case not supported yet");
    }

    public String getHash(RepoDetails repo) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(error.getBytes());
            md.update(new byte[]{0});
            if (!isBlank(repo.getURL())) md.update(repo.getURL().getBytes());
            md.update(new byte[]{0});
            if (!isBlank(repo.getBranch())) md.update(repo.getBranch().getBytes());
            return Hex.encodeHexString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public boolean isNew() {
        return isNew;
    }

    @Override
    public String toString() {
        return summary;
    }

    public static Iterable<UniformTestResult> unpack(AbstractBuild build) {
        Object results = build.getTestResultAction().getResult();
        if (results instanceof TapStreamResult) {
            return transform(((TapStreamResult) results).getFailedTests2(),
                    new Factory(build));
        } else if (results instanceof MetaTabulatedResult) {
            return transform(((MetaTabulatedResult) results).getFailedTests(),
                    new Factory(build));
        } else {
            throw new RuntimeException(format("Cannot handle results of type %s",
                    results.getClass().getSimpleName()));
        }
    }

    static class Factory implements Function<TestResult, UniformTestResult> {

        private String workspace;

        public Factory(final AbstractBuild build) {
            workspace = build.getWorkspace().toString();
        }

        public UniformTestResult apply (@Nullable TestResult result){
            if (result instanceof CaseResult) {
                return new UniformTestResult((CaseResult) result, workspace);
            } else if (result instanceof TapTestResultResult) {
                return new UniformTestResult((TapTestResultResult) result);
            } else {
                return new UniformTestResult(result);
            }
        }
    }

}
