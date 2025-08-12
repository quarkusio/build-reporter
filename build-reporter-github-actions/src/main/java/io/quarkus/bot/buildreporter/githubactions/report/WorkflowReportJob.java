package io.quarkus.bot.buildreporter.githubactions.report;

import java.util.List;
import java.util.stream.Collectors;

import org.kohsuke.github.GHWorkflowRun.Conclusion;

import io.quarkus.bot.build.reporting.model.BuildReport;
import io.quarkus.bot.build.reporting.model.BuildStatus;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class WorkflowReportJob {

    private static final int MODULES_LIMIT = 3;

    private final String name;
    private final String label;
    private final String failuresAnchor;
    private final Conclusion conclusion;
    private final String failingStep;
    private final String url;
    private final String rawLogsUrl;
    private final String gradleBuildScanUrl;
    private final List<String> failingModules;
    private final List<String> skippedModules;
    private final List<WorkflowReportModule> modules;
    private final boolean errorDownloadingSurefireReports;

    public WorkflowReportJob(String name, String label, String failuresAnchor, Conclusion conclusion, String failingStep,
            String url, String rawLogsUrl, String gradleBuildScanUrl, BuildReport buildReport,
            List<WorkflowReportModule> modules,
            boolean errorDownloadingSurefireReports) {
        this.name = name;
        this.label = label;
        this.failuresAnchor = failuresAnchor;
        this.conclusion = conclusion;
        this.failingStep = failingStep;
        this.url = url;
        this.rawLogsUrl = rawLogsUrl;
        this.gradleBuildScanUrl = gradleBuildScanUrl;
        this.failingModules = buildReport.getProjectReports().stream()
                .filter(pr -> pr.getStatus() == BuildStatus.FAILURE)
                .map(pr -> pr.getBasedir())
                .map(n -> (n == null || n.isBlank()) ? WorkflowReportModule.ROOT_MODULE : n)
                .sorted()
                .map(n -> (n == null || n.isBlank()) ? WorkflowReportModule.ROOT_MODULE : n)
                .collect(Collectors.toList());
        this.skippedModules = buildReport.getProjectReports().stream()
                .filter(pr -> pr.getStatus() == BuildStatus.SKIPPED)
                .map(pr -> pr.getBasedir())
                .sorted()
                .collect(Collectors.toList());
        this.modules = modules;
        this.errorDownloadingSurefireReports = errorDownloadingSurefireReports;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public String getFailuresAnchor() {
        return failuresAnchor;
    }

    public Conclusion getConclusion() {
        return conclusion;
    }

    public String getConclusionEmoji() {
        // apparently, conclusion can sometimes be null...
        if (conclusion == null) {
            return ":question:";
        }

        switch (conclusion) {
            case SUCCESS:
                return ":heavy_check_mark:";
            case FAILURE:
                return ":x:";
            case CANCELLED:
                return ":hourglass:";
            case SKIPPED:
                return ":no_entry_sign:";
            default:
                return ":question:";
        }
    }

    public boolean isFailing() {
        return (!Conclusion.SUCCESS.equals(conclusion) && !Conclusion.SKIPPED.equals(conclusion)
                && !Conclusion.STALE.equals(conclusion)
                && !Conclusion.NEUTRAL.equals(conclusion))
                || hasReportedFailures();
    }

    public boolean isSkipped() {
        return Conclusion.SKIPPED.equals(conclusion);
    }

    public String getFailingStep() {
        return failingStep;
    }

    public String getUrl() {
        return url;
    }

    public String getRawLogsUrl() {
        return rawLogsUrl;
    }

    public String getGradleBuildScanUrl() {
        return gradleBuildScanUrl;
    }

    public List<WorkflowReportModule> getModules() {
        return modules;
    }

    public List<WorkflowReportModule> getModulesWithReportedFailures() {
        return modules.stream().filter(m -> m.hasReportedFailures()).collect(Collectors.toList());
    }

    public List<WorkflowReportModule> getModulesWithFlakyTests() {
        return modules.stream().filter(m -> m.hasFlakyTests()).collect(Collectors.toList());
    }

    public boolean hasReportedFailures() {
        return hasBuildReportFailures() || hasTestFailures();
    }

    public boolean hasBuildReportFailures() {
        for (WorkflowReportModule module : modules) {
            if (module.hasBuildReportFailures()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasTestFailures() {
        for (WorkflowReportModule module : modules) {
            if (module.hasTestFailures()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasFlakyTests() {
        for (WorkflowReportModule module : modules) {
            if (module.hasFlakyTests()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasErrorDownloadingBuildReports() {
        return errorDownloadingSurefireReports;
    }

    public List<String> getFailingModules() {
        return failingModules;
    }

    public List<String> getFirstFailingModules() {
        if (failingModules.size() <= MODULES_LIMIT) {
            return failingModules;
        }

        return failingModules.subList(0, MODULES_LIMIT);
    }

    public int getMoreFailingModulesCount() {
        if (failingModules.size() <= MODULES_LIMIT) {
            return 0;
        }

        return failingModules.size() - MODULES_LIMIT;
    }

    public List<String> getSkippedModules() {
        return skippedModules;
    }

    public List<String> getFirstSkippedModules() {
        if (skippedModules.size() <= MODULES_LIMIT) {
            return skippedModules;
        }

        return skippedModules.subList(0, MODULES_LIMIT);
    }

    public int getMoreSkippedModulesCount() {
        if (skippedModules.size() <= MODULES_LIMIT) {
            return 0;
        }

        return skippedModules.size() - MODULES_LIMIT;
    }
}