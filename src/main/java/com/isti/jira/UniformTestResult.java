package com.isti.jira;

import com.google.common.base.Function;
import hudson.model.AbstractBuild;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.test.MetaTabulatedResult;
import hudson.tasks.test.TestResult;
import org.apache.commons.codec.binary.Hex;
import org.tap4j.plugin.model.TapStreamResult;
import org.tap4j.plugin.model.TapTestResultResult;

import javax.annotation.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.google.common.collect.Iterables.transform;
import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.remove;


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
public final class UniformTestResult {

    /** A summary (used for JIRA issue title). */
    private String summary;

    /** A description (used for JIRA body). */
    private String description;

    /** The error message (hashed with the repo details). */
    private String error;

    /** Is this test new?  True if we don't know, since that forces creation
     * of a JIRA issue
     */
    private boolean isNew = true;


    /**
     * Assumes test is new and copies summary to error.
     * @param summary Summary of the failing test.
     * @param description Description of the failing test.
     */
    public UniformTestResult(final String summary, final String description) {
        this(summary, description, summary);
    }

    /**
     * Assumes test is new.
     * @param summary Summary of the failing test.
     * @param description Description of the failing test.
     * @param error The error message (independent of details that might change).
     */
    public UniformTestResult(final String summary,
                             final String description,
                             final String error) {
        this(summary, description, error, true);
    }

    /**
     * @param summary Summary of the failing test.
     * @param description Description of the failing test.
     * @param error The error message (independent of details that might change).
     * @param isNew Is the test a new failure?
     */
    public UniformTestResult(final String summary,
                             final String description,
                             final String error,
                             final boolean isNew) {
        this.summary = summary;
        this.description = description;
        this.error = error;
        this.isNew = isNew;
    }

    /**
     * @param result The TAP result to extract data from.
     */
    private UniformTestResult(final TapTestResultResult result, final Logger logger) {
        this(format("Test '%s' failed", result.getName()),
             format("%s: %s", result.getTitle(), result.getErrorDetails()),
             result.getErrorDetails());
        logger.debug("TAP: %s", this);
    }

    /**
     * @param result The CaseResult to extract data from.
     * @param workspace Workspace path (used to fix stack traces).
     */
    private UniformTestResult(final CaseResult result, final String workspace, final Logger logger) {
        this(format("Test '%s' failed in %s", result.getName(), result.getClassName()),
             format("%s\nClass: %s\nTrace: %s",
                    result.getErrorDetails(),
                    result.getClassName(),
                    result.getErrorStackTrace().replace(workspace, "")),
             result.getErrorDetails(),
             1 == result.getAge());
        logger.debug("Case: %s", this);
    }

    /**
     * @param result The generic result to extract data from.
     */
    private UniformTestResult(final TestResult result, final Logger logger) {
        this(format("Test '%s' failed", result.getName()),
             format("%s: %s", result.getTitle(), result.getErrorDetails()),
             result.getErrorDetails());
        logger.debug("Generic: %s", this);
    }

    /**
     * @param repo The git repo details.
     * @return A hash based on the error details and repo.
     */
    public String getHash(final RepoDetails repo) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(error.getBytes());
            md.update(new byte[]{0});
            if (!isBlank(repo.getURL())) {
                md.update(repo.getURL().getBytes());
            }
            md.update(new byte[]{0});
            if (!isBlank(repo.getBranch())) {
                md.update(repo.getBranch().getBytes());
            }
            return Hex.encodeHexString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return A summary of the failing test.
     */
    public String getSummary() {
        return summary;
    }

    /**
     * @return A description of the failing test.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return Whether the test is a new failure.
     */
    public boolean isNew() {
        return isNew;
    }

    @Override
    public String toString() {
        return summary;
    }

    /**
     * @param build The current build.
     * @return An iterable over the failed tests found.
     */
    public static Iterable<UniformTestResult> unpack(
            final AbstractBuild build,
            final Logger logger) {
        Object results = build.getTestResultAction().getResult();
        logger.debug("Unpacking %s", results.getClass().getSimpleName());
        if (results instanceof TapStreamResult) {
            logger.debug("TAP: %d", ((TapStreamResult) results).getFailedTests2().size());
            return transform(((TapStreamResult) results).getFailedTests2(),
                    new Factory(build, logger));
        } else if (results instanceof MetaTabulatedResult) {
            logger.debug("Meta: %d", ((MetaTabulatedResult) results).getFailedTests().size());
            return transform(((MetaTabulatedResult) results).getFailedTests(),
                    new Factory(build, logger));
        } else {
            throw new RuntimeException(format("Cannot handle results of type %s",
                    results.getClass().getSimpleName()));
        }
    }


    /**
     * A function to transform test results into uniform instances.
     */
    static class Factory implements Function<TestResult, UniformTestResult> {

        /** The workspace path. */
        private String workspace;

        private Logger logger;

        /**
         * @param build The current build.
         */
        public Factory(final AbstractBuild build, final Logger logger) {
            workspace = build.getWorkspace().toString();
            this.logger = logger;
        }

        /**
         * @param result The tets result to convert.
         * @return A uniform representation of the result.
         */
        public UniformTestResult apply(@Nullable final TestResult result) {
            logger.debug(result.getClass().getSimpleName());
            if (result instanceof CaseResult) {
                return new UniformTestResult((CaseResult) result, workspace, logger);
            } else if (result instanceof TapTestResultResult) {
                return new UniformTestResult((TapTestResultResult) result, logger);
            } else {
                return new UniformTestResult(result, logger);
            }
        }
    }

}
