package io.quarkus.bot.buildreporter.githubactions.report;

import java.util.List;

import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;

import io.quarkus.bot.build.reporting.model.BuildStatus;
import io.quarkus.bot.build.reporting.model.ProjectReport;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class WorkflowReportModule {

    public static final String ROOT_MODULE = "Root module";

    private final String name;
    private final ProjectReport projectReport;
    private final String projectReportFailure;
    private final List<ReportTestSuite> reportTestSuites;
    private final List<WorkflowReportTestCase> failures;
    private final List<WorkflowReportFlakyTestCase> flakyTests;

    public WorkflowReportModule(String name, ProjectReport projectReport, String projectReportFailure,
            List<ReportTestSuite> reportTestSuites, List<WorkflowReportTestCase> failures,
            List<WorkflowReportFlakyTestCase> flakyTests) {
        this.name = name;
        this.projectReport = projectReport;
        this.projectReportFailure = projectReportFailure;
        this.reportTestSuites = reportTestSuites;
        this.failures = failures;
        this.flakyTests = flakyTests;
    }

    public String getName() {
        return name;
    }

    public boolean hasReportedFailures() {
        return hasTestFailures() || hasBuildReportFailures();
    }

    public boolean hasTestFailures() {
        for (ReportTestSuite reportTestSuite : reportTestSuites) {
            if (reportTestSuite.getNumberOfErrors() > 0 || reportTestSuite.getNumberOfFailures() > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean hasBuildReportFailures() {
        return projectReport != null && projectReport.getStatus() == BuildStatus.FAILURE;
    }

    public boolean hasFlakyTests() {
        for (ReportTestSuite reportTestSuite : reportTestSuites) {
            if (reportTestSuite.getNumberOfFlakes() > 0) {
                return true;
            }
        }
        return false;
    }

    public List<WorkflowReportTestCase> getTestFailures() {
        return failures;
    }

    public List<WorkflowReportFlakyTestCase> getFlakyTests() {
        return flakyTests;
    }

    public int getTestCount() {
        int testCount = 0;
        for (ReportTestSuite reportTestSuite : reportTestSuites) {
            testCount += reportTestSuite.getNumberOfTests();
        }
        return testCount;
    }

    public int getSuccessCount() {
        int successCount = 0;
        for (ReportTestSuite reportTestSuite : reportTestSuites) {
            for (ReportTestCase reportTestCase : reportTestSuite.getTestCases()) {
                if (reportTestCase.isSuccessful()) {
                    successCount++;
                }
            }
        }
        return successCount;
    }

    public int getErrorCount() {
        int testCount = 0;
        for (ReportTestSuite reportTestSuite : reportTestSuites) {
            testCount += reportTestSuite.getNumberOfErrors();
        }
        return testCount;
    }

    public int getFailureCount() {
        int testCount = 0;
        for (ReportTestSuite reportTestSuite : reportTestSuites) {
            testCount += reportTestSuite.getNumberOfFailures();
        }
        return testCount;
    }

    public int getSkippedCount() {
        int testCount = 0;
        for (ReportTestSuite reportTestSuite : reportTestSuites) {
            testCount += reportTestSuite.getNumberOfSkipped();
        }
        return testCount;
    }

    public String getProjectReportFailure() {
        return projectReportFailure;
    }
}