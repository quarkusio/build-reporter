package io.quarkus.bot.buildreporter.githubactions;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRun.AnnotationLevel;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHCheckRunBuilder.Annotation;
import org.kohsuke.github.GHCheckRunBuilder.Output;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GHWorkflowRun;

import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReport;
import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReportJob;
import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReportJobIncludeStrategy;
import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReportTestCase;

@Singleton
public class BuildReporter {

    private static final Logger LOG = Logger.getLogger(BuildReporter.class);

    private static final int GITHUB_FIELD_LENGTH_HARD_LIMIT = 65000;

    @Inject
    WorkflowRunAnalyzer workflowRunAnalyzer;

    @Inject
    WorkflowReportFormatter workflowReportFormatter;

    @Inject
    WorkflowReportJobIncludeStrategy workflowReportJobIncludeStrategy;

    @Inject
    StackTraceShortener stackTraceShortener;

    public Optional<String> generateReportComment(GHWorkflowRun workflowRun,
            BuildReporterConfig buildReporterConfig,
            WorkflowContext workflowContext,
            Map<String, Optional<BuildReports>> buildReportsMap,
            boolean artifactsAvailable) throws IOException {
        List<GHWorkflowJob> jobs = workflowRun.listJobs().toList()
                .stream()
                .sorted(buildReporterConfig.getJobNameComparator())
                .collect(Collectors.toList());

        Optional<WorkflowReport> workflowReportOptional = workflowRunAnalyzer.getReport(workflowRun, workflowContext, jobs,
                buildReportsMap);
        if (workflowReportOptional.isEmpty()) {
            return Optional.empty();
        }

        WorkflowReport workflowReport = workflowReportOptional.get();

        Optional<GHCheckRun> checkRunOptional = createCheckRun(workflowRun, buildReporterConfig, workflowContext,
                artifactsAvailable, workflowReport);

        String workflowRunIdMarker = String.format(WorkflowConstants.WORKFLOW_RUN_ID_MARKER, workflowRun.getId());

        String reportComment = workflowReportFormatter.getReportComment(workflowReport,
                artifactsAvailable,
                checkRunOptional.orElse(null),
                WorkflowConstants.MESSAGE_ID_ACTIVE,
                workflowRunIdMarker,
                WorkflowConstants.BUILD_SCANS_CHECK_RUN_MARKER,
                true,
                true,
                workflowReportJobIncludeStrategy);
        if (reportComment.length() > GITHUB_FIELD_LENGTH_HARD_LIMIT) {
            reportComment = workflowReportFormatter.getReportComment(workflowReport,
                    artifactsAvailable,
                    checkRunOptional.orElse(null),
                    WorkflowConstants.MESSAGE_ID_ACTIVE,
                    workflowRunIdMarker,
                    WorkflowConstants.BUILD_SCANS_CHECK_RUN_MARKER,
                    false,
                    true,
                    workflowReportJobIncludeStrategy);
        }
        if (reportComment.length() > GITHUB_FIELD_LENGTH_HARD_LIMIT) {
            reportComment = workflowReportFormatter.getReportComment(workflowReport,
                    artifactsAvailable,
                    checkRunOptional.orElse(null),
                    WorkflowConstants.MESSAGE_ID_ACTIVE,
                    workflowRunIdMarker,
                    WorkflowConstants.BUILD_SCANS_CHECK_RUN_MARKER,
                    false,
                    false,
                    workflowReportJobIncludeStrategy);
        }
        return Optional.of(reportComment);
    }

    public Optional<GHCheckRun> createCheckRun(GHWorkflowRun workflowRun,
            BuildReporterConfig buildReporterConfig,
            WorkflowContext workflowContext,
            boolean artifactsAvailable, WorkflowReport workflowReport) {
        if (!workflowReport.hasTestFailures() || buildReporterConfig.isDryRun() || !buildReporterConfig.isCreateCheckRun()) {
            return Optional.empty();
        }

        try {
            String name = WorkflowConstants.BUILD_SUMMARY_CHECK_RUN_PREFIX + workflowRun.getHeadSha();
            String summary = workflowReportFormatter.getCheckRunReportSummary(workflowReport, workflowContext,
                    artifactsAvailable, workflowReportJobIncludeStrategy);
            String checkRunReport = workflowReportFormatter.getCheckRunReport(workflowReport, true, true);
            if (checkRunReport.length() > GITHUB_FIELD_LENGTH_HARD_LIMIT) {
                checkRunReport = workflowReportFormatter.getCheckRunReport(workflowReport, false, true);
            }
            if (checkRunReport.length() > GITHUB_FIELD_LENGTH_HARD_LIMIT) {
                checkRunReport = workflowReportFormatter.getCheckRunReport(workflowReport, false, false);
            }

            Output checkRunOutput = new Output(name, summary).withText(checkRunReport);

            for (WorkflowReportJob workflowReportJob : workflowReport.getJobs()) {
                if (!workflowReportJob.hasTestFailures()) {
                    continue;
                }

                List<WorkflowReportTestCase> annotatedWorkflowReportTestCases = workflowReportJob.getModules().stream()
                        .filter(m -> m.hasTestFailures())
                        .flatMap(m -> m.getTestFailures().stream())
                        .collect(Collectors.toList());

                for (WorkflowReportTestCase workflowReportTestCase : annotatedWorkflowReportTestCases) {
                    checkRunOutput.add(new Annotation(workflowReportTestCase.getClassPath(),
                            StringUtils.isNumeric(workflowReportTestCase.getFailureErrorLine())
                                    ? Integer.valueOf(workflowReportTestCase.getFailureErrorLine())
                                    : 1,
                            AnnotationLevel.FAILURE,
                            StringUtils.isNotBlank(workflowReportTestCase.getFailureDetail())
                                    ? stackTraceShortener.shorten(workflowReportTestCase.getFailureDetail(),
                                            GITHUB_FIELD_LENGTH_HARD_LIMIT, 3)
                                    : "The test failed.")
                            .withTitle(StringUtils.abbreviate(workflowReportJob.getName(), 255))
                            .withRawDetails(
                                    stackTraceShortener.shorten(workflowReportTestCase.getFailureDetail(),
                                            GITHUB_FIELD_LENGTH_HARD_LIMIT)));
                }
            }

            GHCheckRunBuilder checkRunBuilder = workflowRun.getRepository().createCheckRun(name, workflowRun.getHeadSha())
                    .add(checkRunOutput)
                    .withConclusion(GHCheckRun.Conclusion.NEUTRAL)
                    .withCompletedAt(new Date());

            return Optional.of(checkRunBuilder.create());
        } catch (Exception e) {
            LOG.error(workflowContext.getLogContext() + " - Unable to create check run for test failures", e);
            return Optional.empty();
        }
    }
}
