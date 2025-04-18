package io.quarkus.bot.buildreporter.githubactions;

import java.util.Comparator;
import java.util.Set;

import org.kohsuke.github.GHWorkflowJob;

import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReportJobIncludeStrategy;

public class BuildReporterConfig {

    private final boolean dryRun;
    private final WorkflowReportJobIncludeStrategy workflowReportJobIncludeStrategy;
    private final Comparator<GHWorkflowJob> workflowJobComparator;
    private final Set<String> monitoredWorkflows;
    private final boolean createCheckRun;
    private final boolean develocityEnabled;
    private final String develocityUrl;
    private final Set<String> ignoredFlakyTests;

    private BuildReporterConfig(boolean dryRun, WorkflowReportJobIncludeStrategy workflowReportJobIncludeStrategy,
            Comparator<GHWorkflowJob> workflowJobComparator,
            Set<String> monitoredWorkflows, boolean createCheckRun, boolean develocityEnabled,
            String develocityUrl, Set<String> ignoredFlakyTests) {
        this.dryRun = dryRun;
        this.workflowReportJobIncludeStrategy = workflowReportJobIncludeStrategy;
        this.workflowJobComparator = workflowJobComparator;
        this.monitoredWorkflows = monitoredWorkflows;
        this.createCheckRun = createCheckRun;
        this.develocityEnabled = develocityEnabled;
        this.develocityUrl = develocityUrl;
        this.ignoredFlakyTests = ignoredFlakyTests;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public WorkflowReportJobIncludeStrategy getWorkflowReportJobIncludeStrategy() {
        return workflowReportJobIncludeStrategy;
    }

    public Comparator<GHWorkflowJob> getJobNameComparator() {
        return workflowJobComparator;
    }

    public Set<String> getMonitoredWorkflows() {
        return monitoredWorkflows;
    }

    public boolean isCreateCheckRun() {
        return createCheckRun;
    }

    public boolean isDevelocityEnabled() {
        return develocityEnabled;
    }

    public String getDevelocityUrl() {
        return develocityUrl;
    }

    public Set<String> getIgnoredFlakyTests() {
        return ignoredFlakyTests;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean dryRun = false;
        private boolean createCheckRun = true;
        private WorkflowReportJobIncludeStrategy workflowReportJobIncludeStrategy;
        private Comparator<GHWorkflowJob> workflowJobComparator;
        private Set<String> monitoredWorkflows = Set.of();
        private boolean develocityEnabled;
        private String develocityUrl;
        private Set<String> ignoredFlakyTests = Set.of();

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public Builder workflowReportJobIncludeStrategy(WorkflowReportJobIncludeStrategy workflowReportJobIncludeStrategy) {
            this.workflowReportJobIncludeStrategy = workflowReportJobIncludeStrategy;
            return this;
        }

        public Builder workflowJobComparator(Comparator<GHWorkflowJob> workflowJobComparator) {
            this.workflowJobComparator = workflowJobComparator;
            return this;
        }

        public Builder monitoredWorkflows(Set<String> monitoredWorkflows) {
            this.monitoredWorkflows = monitoredWorkflows;
            return this;
        }

        public Builder createCheckRun(boolean createCheckRun) {
            this.createCheckRun = createCheckRun;
            return this;
        }

        public Builder enableDevelocity(boolean develocityEnabled) {
            this.develocityEnabled = develocityEnabled;
            return this;
        }

        public Builder develocityUrl(String develocityUrl) {
            this.develocityUrl = develocityUrl;
            return this;
        }

        public Builder ignoredFlakyTests(Set<String> ignoredFlakyTests) {
            this.ignoredFlakyTests = ignoredFlakyTests;
            return this;
        }

        public BuildReporterConfig build() {
            return new BuildReporterConfig(dryRun, workflowReportJobIncludeStrategy,
                    workflowJobComparator != null ? workflowJobComparator : DefaultJobNameComparator.INSTANCE,
                    monitoredWorkflows, createCheckRun, develocityEnabled, develocityUrl, ignoredFlakyTests);
        }
    }

    private static class DefaultJobNameComparator implements Comparator<GHWorkflowJob> {

        private static final DefaultJobNameComparator INSTANCE = new DefaultJobNameComparator();

        @Override
        public int compare(GHWorkflowJob o1, GHWorkflowJob o2) {
            return o1.getName().compareToIgnoreCase(o2.getName());
        }
    }
}
