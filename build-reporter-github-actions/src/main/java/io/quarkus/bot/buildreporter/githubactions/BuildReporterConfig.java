package io.quarkus.bot.buildreporter.githubactions;

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

import org.kohsuke.github.GHWorkflowJob;

public class BuildReporterConfig {

    private final boolean dryRun;
    private final Comparator<GHWorkflowJob> workflowJobComparator;
    private final Set<String> monitoredWorkflows;
    private final boolean createCheckRun;

    private BuildReporterConfig(boolean dryRun, Comparator<GHWorkflowJob> workflowJobComparator,
            Set<String> monitoredWorkflows, boolean createCheckRun) {
        this.dryRun = dryRun;
        this.workflowJobComparator = workflowJobComparator;
        this.monitoredWorkflows = monitoredWorkflows;
        this.createCheckRun = createCheckRun;
    }

    public boolean isDryRun() {
        return dryRun;
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean dryRun = false;
        private boolean createCheckRun = true;
        private Comparator<GHWorkflowJob> workflowJobComparator = DefaultJobNameComparator.INSTANCE;
        private Set<String> monitoredWorkflows = Collections.emptySet();

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
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

        public BuildReporterConfig build() {
            return new BuildReporterConfig(dryRun, workflowJobComparator, monitoredWorkflows, createCheckRun);
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
