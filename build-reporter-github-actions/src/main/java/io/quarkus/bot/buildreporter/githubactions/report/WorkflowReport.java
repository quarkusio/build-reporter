package io.quarkus.bot.buildreporter.githubactions.report;

import java.util.List;
import java.util.stream.Collectors;

import org.kohsuke.github.GHWorkflowRun.Conclusion;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class WorkflowReport {

    private final String workflowName;
    private final String sha;
    private final List<WorkflowReportJob> jobs;
    private final boolean sameRepository;
    private final Conclusion conclusion;
    private final String workflowRunUrl;

    public WorkflowReport(String workflowName, String sha, List<WorkflowReportJob> jobs, boolean sameRepository,
            Conclusion conclusion, String workflowRunUrl) {
        this.workflowName = workflowName;
        this.sha = sha;
        this.jobs = jobs;
        this.sameRepository = sameRepository;
        this.conclusion = conclusion;
        this.workflowRunUrl = workflowRunUrl;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getSha() {
        return sha;
    }

    public void addJob(WorkflowReportJob job) {
        this.jobs.add(job);
    }

    public List<WorkflowReportJob> getJobs() {
        return jobs;
    }

    public boolean hasJobsFailing() {
        for (WorkflowReportJob job : jobs) {
            if (job.isFailing()) {
                return true;
            }
        }
        return false;
    }

    public List<WorkflowReportJob> getJobsWithReportedFailures() {
        return jobs.stream().filter(j -> j.hasReportedFailures()).collect(Collectors.toList());
    }

    public List<WorkflowReportJob> getJobsWithFlakyTests() {
        return jobs.stream().filter(j -> j.hasFlakyTests()).collect(Collectors.toList());
    }

    public boolean hasReportedFailures() {
        return hasBuildReportFailures() || hasTestFailures();
    }

    public boolean hasBuildReportFailures() {
        for (WorkflowReportJob job : jobs) {
            if (job.hasBuildReportFailures()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasTestFailures() {
        for (WorkflowReportJob job : jobs) {
            if (job.hasTestFailures()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasFlakyTests() {
        for (WorkflowReportJob job : jobs) {
            if (job.hasFlakyTests()) {
                return true;
            }
        }
        return false;
    }

    public boolean isSameRepository() {
        return sameRepository;
    }

    public boolean isCancelled() {
        if (Conclusion.CANCELLED.equals(conclusion)) {
            return true;
        }

        return jobs.stream()
                .noneMatch(j -> Conclusion.CANCELLED != j.getConclusion()
                        && Conclusion.SKIPPED != j.getConclusion()
                        && Conclusion.NEUTRAL != j.getConclusion());
    }

    public boolean isFailure() {
        return Conclusion.FAILURE.equals(conclusion) || hasJobsFailing();
    }

    public String getWorkflowRunUrl() {
        return workflowRunUrl;
    }

    public boolean hasErrorDownloadingBuildReports() {
        for (WorkflowReportJob job : jobs) {
            if (job.hasErrorDownloadingBuildReports()) {
                return true;
            }
        }
        return false;
    }
}