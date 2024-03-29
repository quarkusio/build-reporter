package io.quarkus.bot.buildreporter.githubactions;

import jakarta.enterprise.context.ApplicationScoped;

import org.kohsuke.github.GHCheckRun;

import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReport;
import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReportJobIncludeStrategy;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@ApplicationScoped
public class WorkflowReportFormatter {

    public String getCheckRunReportSummary(WorkflowReport report, WorkflowContext workflowContext, boolean artifactsAvailable,
            WorkflowReportJobIncludeStrategy workflowReportJobIncludeStrategy) {
        return Templates.checkRunReportSummary(report, workflowContext, artifactsAvailable, workflowReportJobIncludeStrategy)
                .render();
    }

    public String getCheckRunReport(WorkflowReport report, boolean develocityEnabled, String develocityUrl,
            boolean includeStackTraces, boolean includeFailureLinks) {
        return Templates.checkRunReport(report, develocityEnabled, develocityUrl, includeStackTraces, includeFailureLinks)
                .render();
    }

    public String getReportComment(WorkflowReport report, boolean artifactsAvailable, GHCheckRun checkRun,
            String messageIdActive, String workflowRunId, String buildScansCheckRunMarker,
            boolean develocityEnabled, String develocityUrl, boolean indicateSuccess, boolean hasOtherPendingCheckRuns,
            boolean includeStackTraces, boolean includeFailureLinks,
            WorkflowReportJobIncludeStrategy workflowReportJobIncludeStrategy) {
        return Templates
                .commentReport(report, artifactsAvailable, checkRun, messageIdActive, workflowRunId, buildScansCheckRunMarker,
                        develocityEnabled, develocityUrl, indicateSuccess, hasOtherPendingCheckRuns,
                        includeStackTraces, includeFailureLinks, workflowReportJobIncludeStrategy)
                .render();
    }

    @CheckedTemplate
    private static class Templates {

        public static native TemplateInstance checkRunReportSummary(WorkflowReport report, WorkflowContext workflowContext,
                boolean artifactsAvailable, WorkflowReportJobIncludeStrategy workflowReportJobIncludeStrategy);

        public static native TemplateInstance checkRunReport(WorkflowReport report,
                boolean develocityEnabled, String develocityUrl,
                boolean includeStackTraces, boolean includeFailureLinks);

        public static native TemplateInstance commentReport(WorkflowReport report, boolean artifactsAvailable,
                GHCheckRun checkRun, String messageIdActive, String workflowRunId, String buildScansCheckRunMarker,
                boolean develocityEnabled, String develocityUrl, boolean indicateSuccess, boolean hasOtherPendingCheckRuns,
                boolean includeStackTraces, boolean includeFailureLinks,
                WorkflowReportJobIncludeStrategy workflowReportJobIncludeStrategy);
    }
}
