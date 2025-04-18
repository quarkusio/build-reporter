package io.quarkus.bot.buildreporter.githubactions;

import static io.quarkus.bot.buildreporter.githubactions.WorkflowUtils.getActiveStatusCommentMarker;
import static io.quarkus.bot.buildreporter.githubactions.WorkflowUtils.getHiddenStatusCommentMarker;
import static io.quarkus.bot.buildreporter.githubactions.WorkflowUtils.getOldActiveStatusCommentMarker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRun.Conclusion;
import org.kohsuke.github.GHWorkflowRun.Status;
import org.kohsuke.github.GitHub;

import io.quarkiverse.githubapp.event.Actions;
import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReport;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

@Singleton
public class BuildReporterEventHandler {

    private static final Logger LOG = Logger.getLogger(BuildReporterEventHandler.class);

    private static final String LABEL_FLAKY_TEST = "triage/flaky-test";

    @Inject
    WorkflowRunAnalyzer workflowRunAnalyzer;

    @Inject
    BuildReporter buildReporter;

    @Inject
    BuildReportsUnarchiver buildReportsUnarchiver;

    public void handle(GHEventPayload.WorkflowRun workflowRunPayload,
            BuildReporterConfig buildReporterConfig,
            GitHub gitHub, DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        if (buildReporterConfig.getMonitoredWorkflows() == null ||
                buildReporterConfig.getMonitoredWorkflows().isEmpty()) {
            return;
        }

        GHWorkflowRun workflowRun = workflowRunPayload.getWorkflowRun();
        GHWorkflow workflow = workflowRunPayload.getWorkflow();

        if (!buildReporterConfig.getMonitoredWorkflows().contains(workflow.getName())) {
            return;
        }

        switch (workflowRunPayload.getAction()) {
            case Actions.COMPLETED:
                handleCompleted(workflow, workflowRun, buildReporterConfig, gitHub, gitHubGraphQLClient);
                break;
            case Actions.REQUESTED:
                handleRequested(workflow, workflowRun, buildReporterConfig, gitHub, gitHubGraphQLClient);
                break;
            default:
                // we don't do anything for other actions
                break;
        }
    }

    private void handleCompleted(GHWorkflow workflow,
            GHWorkflowRun workflowRun,
            BuildReporterConfig buildReporterConfig,
            GitHub gitHub,
            DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        List<GHArtifact> artifacts;
        boolean artifactsAvailable;
        try {
            ArtifactsAreReady artifactsAreReady = new ArtifactsAreReady(workflowRun);
            Awaitility.await()
                    .atMost(Duration.ofMinutes(5))
                    .pollDelay(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofSeconds(30))
                    .ignoreExceptions()
                    .until(artifactsAreReady);
            artifacts = artifactsAreReady.getArtifacts();
            artifactsAvailable = true;
        } catch (ConditionTimeoutException e) {
            if (workflowRun.getConclusion() != Conclusion.CANCELLED) {
                LOG.warn("Workflow run " + workflowRun.getRepository().getFullName() + "#"
                        + workflowRun.getName() + ":" + workflowRun.getId()
                        + " - Unable to get the artifacts in a timely manner, ignoring them");
            }
            artifacts = Collections.emptyList();
            artifactsAvailable = false;
        }

        Path allBuildReportsDirectory = artifactsAvailable ? Files.createTempDirectory("build-reports-analyzer-") : null;

        try {
            Conclusion conclusion = workflowRun.getConclusion();

            if (workflowRun.getEvent() == GHEvent.PULL_REQUEST) {
                Optional<GHPullRequest> pullRequestOptional = getAssociatedPullRequest(workflowRun, artifacts);

                if (pullRequestOptional.isEmpty()) {
                    LOG.error("Workflow run #" + workflowRun.getId() + " - Unable to find the associated pull request");
                    return;
                }

                GHPullRequest pullRequest = pullRequestOptional.get();
                WorkflowContext workflowContext = new WorkflowContext(pullRequest);

                hideOutdatedWorkflowRunResults(buildReporterConfig, workflowContext, pullRequest,
                        workflow.getName(), gitHubGraphQLClient);

                if ((conclusion == Conclusion.SUCCESS && pullRequest.isDraft())
                        || conclusion == Conclusion.CANCELLED) {
                    return;
                }

                Map<String, Optional<BuildReports>> buildReportsMap = downloadBuildReports(workflowContext,
                        allBuildReportsDirectory,
                        artifacts, artifactsAvailable, workflowRun.getRunAttempt());
                List<GHWorkflowJob> jobs = workflowRun.listJobs().toList()
                        .stream()
                        .sorted(buildReporterConfig.getJobNameComparator())
                        .collect(Collectors.toList());

                Optional<WorkflowReport> workflowReportOptional = workflowRunAnalyzer.getReport(workflow.getName(), workflowRun,
                        workflowContext,
                        buildReporterConfig.getIgnoredFlakyTests(),
                        jobs,
                        buildReportsMap);
                if (workflowReportOptional.isEmpty()) {
                    return;
                }

                WorkflowReport workflowReport = workflowReportOptional.get();

                Optional<String> reportCommentOptional = buildReporter.generateReportComment(workflow.getName(), workflowRun,
                        buildReporterConfig,
                        workflowContext,
                        workflowReport,
                        artifactsAvailable,
                        true,
                        hasOtherPendingWorkflowRuns(pullRequest, buildReporterConfig));

                if (reportCommentOptional.isEmpty()) {
                    return;
                }

                String reportComment = reportCommentOptional.get();

                if (pullRequest.isDraft()) {
                    // if pull request has been marked draft while analyzing the report, let's not add the comment
                    return;
                }

                if (!buildReporterConfig.isDryRun()) {
                    pullRequest.comment(reportComment);
                } else {
                    LOG.info("Pull request #" + pullRequest.getNumber() + " - Add test failures:\n" + reportComment);
                }

                if (workflowReport.hasFlakyTests()) {
                    if (!buildReporterConfig.isDryRun()) {
                        pullRequest.addLabels(LABEL_FLAKY_TEST);
                    } else {
                        LOG.info("Pull request #" + pullRequest.getNumber() + " - Add label " + LABEL_FLAKY_TEST);
                    }
                }
            } else {
                Optional<GHIssue> reportIssueOptional = getAssociatedReportIssue(gitHub, workflowRun, artifacts);

                if (reportIssueOptional.isEmpty()) {
                    return;
                }

                GHIssue reportIssue = reportIssueOptional.get();
                WorkflowContext workflowContext = new WorkflowContext(reportIssue);

                hideOutdatedWorkflowRunResults(buildReporterConfig, workflowContext, reportIssue,
                        workflow.getName(), gitHubGraphQLClient);

                if (conclusion == Conclusion.SUCCESS
                        && reportIssue.getState() == GHIssueState.OPEN) {
                    String fixedComment = ":heavy_check_mark: **Build fixed:**\n* Link to latest CI run: "
                            + workflowRun.getHtmlUrl().toString();

                    if (!buildReporterConfig.isDryRun()) {
                        reportIssue.comment(fixedComment);
                        reportIssue.close();
                    } else {
                        LOG.info("Issue #" + reportIssue.getNumber() + " - Add comment: " + fixedComment);
                        LOG.info("Issue #" + reportIssue.getNumber() + " - Closing report issue");
                    }
                    return;
                }

                if (conclusion != Conclusion.FAILURE) {
                    return;
                }

                if (!buildReporterConfig.isDryRun()) {
                    reportIssue.reopen();
                } else {
                    LOG.info("Issue #" + reportIssue.getNumber() + " - Reopening report issue");
                }

                Map<String, Optional<BuildReports>> buildReportsMap = downloadBuildReports(workflowContext,
                        allBuildReportsDirectory,
                        artifacts, artifactsAvailable, workflowRun.getRunAttempt());

                List<GHWorkflowJob> jobs = workflowRun.listJobs().toList()
                        .stream()
                        .sorted(buildReporterConfig.getJobNameComparator())
                        .collect(Collectors.toList());

                Optional<WorkflowReport> workflowReportOptional = workflowRunAnalyzer.getReport(workflow.getName(), workflowRun,
                        workflowContext,
                        buildReporterConfig.getIgnoredFlakyTests(),
                        jobs,
                        buildReportsMap);
                if (workflowReportOptional.isEmpty()) {
                    return;
                }

                WorkflowReport workflowReport = workflowReportOptional.get();

                Optional<String> reportCommentOptional = buildReporter.generateReportComment(workflow.getName(), workflowRun,
                        buildReporterConfig,
                        workflowContext,
                        workflowReport, artifactsAvailable, false, false);

                if (reportCommentOptional.isEmpty()) {
                    // not able to generate a proper report but let's post a default comment anyway
                    String defaultFailureComment = "The build is failing and we were not able to generate a report:\n* Link to latest CI run: "
                            + workflowRun.getHtmlUrl().toString();

                    if (!buildReporterConfig.isDryRun()) {
                        reportIssue.comment(defaultFailureComment);
                    } else {
                        LOG.info("Issue #" + reportIssue.getNumber() + " - Add comment: " + defaultFailureComment);
                    }
                    return;
                }

                String reportComment = reportCommentOptional.get();

                if (!buildReporterConfig.isDryRun()) {
                    reportIssue.comment(reportComment);
                } else {
                    LOG.info("Issue #" + reportIssue.getNumber() + " - Add test failures:\n" + reportComment);
                }
            }
        } finally {
            if (allBuildReportsDirectory != null) {
                try {
                    Files.walk(allBuildReportsDirectory)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (IOException e) {
                    LOG.error("Unable to delete temp directory " + allBuildReportsDirectory);
                }
            }
        }
    }

    /**
     * Unfortunately when the pull request is coming from a fork, the pull request is not in the payload
     * so we use a dirty trick to get it.
     * We use the sha as last resort as the workflow takes some time and the sha might not be associated to the pull request
     * anymore.
     */
    private Optional<GHPullRequest> getAssociatedPullRequest(GHWorkflowRun workflowRun, List<GHArtifact> artifacts)
            throws NumberFormatException, IOException {
        Optional<GHArtifact> pullRequestNumberArtifact = artifacts.stream()
                .filter(a -> a.getName().startsWith(WorkflowConstants.PULL_REQUEST_NUMBER_PREFIX)).findFirst();
        if (!pullRequestNumberArtifact.isEmpty()) {
            GHPullRequest pullRequest = workflowRun.getRepository().getPullRequest(
                    Integer.valueOf(
                            pullRequestNumberArtifact.get().getName().replace(WorkflowConstants.PULL_REQUEST_NUMBER_PREFIX,
                                    "")));
            return Optional.of(pullRequest);
        }

        LOG.warn("Workflow run #" + workflowRun.getId() + " - Unable to get the pull request artifact, trying with sha");

        List<GHPullRequest> pullRequests = workflowRun.getRepository().queryPullRequests()
                .state(GHIssueState.OPEN)
                .head(workflowRun.getHeadRepository().getOwnerName() + ":" + workflowRun.getHeadBranch())
                .list().toList();
        if (!pullRequests.isEmpty()) {
            return Optional.of(pullRequests.get(0));
        }

        return Optional.empty();
    }

    /**
     * It is possible to associate a build with an issue to report to.
     */
    private Optional<GHIssue> getAssociatedReportIssue(GitHub gitHub, GHWorkflowRun workflowRun, List<GHArtifact> artifacts)
            throws NumberFormatException, IOException {
        Optional<GHArtifact> reportIssueNumberArtifact = artifacts.stream()
                .filter(a -> a.getName().startsWith(WorkflowConstants.REPORT_ISSUE_NUMBER_PREFIX)).findFirst();
        if (!reportIssueNumberArtifact.isEmpty()) {
            String issueReference = reportIssueNumberArtifact.get().getName()
                    .replace(WorkflowConstants.REPORT_ISSUE_NUMBER_PREFIX, "");
            if (issueReference.contains("#")) {
                String[] issueReferenceParts = issueReference.split("#", 2);
                return Optional
                        .of(gitHub.getRepository(issueReferenceParts[0]).getIssue(Integer.valueOf(issueReferenceParts[1])));
            }

            return Optional.of(workflowRun.getRepository().getIssue(Integer.valueOf(issueReference)));
        }

        return Optional.empty();
    }

    private Map<String, Optional<BuildReports>> downloadBuildReports(WorkflowContext workflowContext,
            Path allBuildReportsDirectory,
            List<GHArtifact> artifacts, boolean artifactsAvailable, long runAttempt) throws IOException {
        if (!artifactsAvailable) {
            return Collections.emptyMap();
        }

        Map<String, GHArtifact> buildReportsArtifacts = WorkflowUtils.getBuildReportsArtifacts(artifacts, runAttempt);

        Map<String, Optional<BuildReports>> buildReportsMap = new HashMap<>();
        Set<String> alreadyHandledArtifacts = new HashSet<>();

        for (Entry<String, GHArtifact> artifactEntry : buildReportsArtifacts.entrySet()) {
            String jobName = artifactEntry.getKey();
            GHArtifact artifact = artifactEntry.getValue();

            if (alreadyHandledArtifacts.contains(artifact.getName())) {
                continue;
            }

            Path jobDirectory = allBuildReportsDirectory.resolve(artifact.getName());

            Optional<BuildReports> buildReportsOptional = buildReportsUnarchiver.getBuildReports(workflowContext,
                    artifact, jobDirectory);

            buildReportsMap.put(jobName, buildReportsOptional);

            alreadyHandledArtifacts.add(artifact.getName());
        }

        return buildReportsMap;
    }

    private void handleRequested(GHWorkflow workflow, GHWorkflowRun workflowRun,
            BuildReporterConfig buildReporterConfig,
            GitHub gitHub, DynamicGraphQLClient gitHubGraphQLClient) throws IOException {

        List<GHPullRequest> pullRequests = workflowRun.getRepository().queryPullRequests()
                .state(GHIssueState.OPEN)
                .head(workflowRun.getHeadRepository().getOwnerName() + ":" + workflowRun.getHeadBranch())
                .list().toList();
        if (pullRequests.isEmpty()) {
            return;
        }

        hideOutdatedWorkflowRunResults(buildReporterConfig, new WorkflowContext(pullRequests.get(0)), pullRequests.get(0),
                workflow.getName(), gitHubGraphQLClient);
    }

    private static void hideOutdatedWorkflowRunResults(BuildReporterConfig buildReporterConfig,
            WorkflowContext workflowContext, GHIssue issue,
            String workflowName,
            DynamicGraphQLClient gitHubGraphQLClient)
            throws IOException {
        List<GHIssueComment> comments = issue.getComments();

        for (GHIssueComment comment : comments) {
            boolean noOldMarkersFound = !comment.getBody().contains(WorkflowConstants.OLD_MESSAGE_ID_ACTIVE) &&
                    !comment.getBody().contains(getOldActiveStatusCommentMarker(workflowName));
            if ((!comment.getBody().contains(WorkflowConstants.MESSAGE_ID_ACTIVE) &&
                    !comment.getBody().contains(getActiveStatusCommentMarker(workflowName))) &&
                    noOldMarkersFound) {
                continue;
            }

            String updatedComment = getUpdatedComment(workflowName, comment);

            if (!buildReporterConfig.isDryRun()) {
                try {
                    comment.update(updatedComment);
                } catch (IOException e) {
                    LOG.error(workflowContext.getLogContext() +
                            " - Unable to hide outdated workflow run status for comment " + comment.getId());
                }
                try {
                    minimizeOutdatedComment(gitHubGraphQLClient, comment);
                } catch (ExecutionException | InterruptedException e) {
                    LOG.error(workflowContext.getLogContext() +
                            " - Unable to minimize outdated workflow run status for comment " + comment.getId());
                }
            } else {
                LOG.info(workflowContext.getLogContext() + " - Hide outdated workflow run status " + comment.getId());
            }
        }
    }

    private static String getUpdatedComment(String workflowName, GHIssueComment comment) {
        StringBuilder updatedComment = new StringBuilder();
        updatedComment.append(WorkflowConstants.HIDE_MESSAGE_PREFIX);
        // Replace old markers with new ones for compatibility reasons
        if (comment.getBody().contains(getOldActiveStatusCommentMarker(workflowName))) {
            updatedComment.append(comment.getBody().replace(getOldActiveStatusCommentMarker(workflowName),
                    getHiddenStatusCommentMarker(workflowName))
                    .replace(WorkflowConstants.OLD_MESSAGE_ID_ACTIVE, getHiddenStatusCommentMarker(workflowName)));
        } else {
            updatedComment.append(comment.getBody().replace(getActiveStatusCommentMarker(workflowName),
                    getHiddenStatusCommentMarker(workflowName))
                    .replace(WorkflowConstants.MESSAGE_ID_ACTIVE, getHiddenStatusCommentMarker(workflowName)));
        }
        return updatedComment.toString();
    }

    private static void minimizeOutdatedComment(DynamicGraphQLClient gitHubGraphQLClient, GHIssueComment comment)
            throws ExecutionException, InterruptedException {
        Map<String, Object> variables = new HashMap<>();
        variables.put("subjectId", comment.getNodeId());
        gitHubGraphQLClient.executeSync("""
                mutation MinimizeOutdatedContent($subjectId: ID!) {
                  minimizeComment(input: {
                    subjectId: $subjectId,
                    classifier: OUTDATED}) {
                      minimizedComment {
                        isMinimized
                      }
                    }
                }
                """, variables);
    }

    private final static class ArtifactsAreReady implements Callable<Boolean> {
        private final GHWorkflowRun workflowRun;
        private List<GHArtifact> artifacts;

        private ArtifactsAreReady(GHWorkflowRun workflowRun) {
            this.workflowRun = workflowRun;
        }

        @Override
        public Boolean call() throws Exception {
            artifacts = workflowRun.listArtifacts().toList();

            boolean useNewBuildReportsArtifactNamePattern = artifacts.stream()
                    .anyMatch(a -> WorkflowUtils.matchesNewBuildReportsArtifactNamePattern(a.getName()));

            String buildReportsArtifactNamePrefix = useNewBuildReportsArtifactNamePattern
                    ? WorkflowConstants.BUILD_REPORTS_ARTIFACT_PREFIX + workflowRun.getRunAttempt() + "-"
                    : WorkflowConstants.BUILD_REPORTS_ARTIFACT_PREFIX;

            return artifacts.stream().anyMatch(a -> a.getName().startsWith(buildReportsArtifactNamePrefix));
        }

        public List<GHArtifact> getArtifacts() {
            return artifacts;
        }
    }

    private static boolean hasOtherPendingWorkflowRuns(GHPullRequest pullRequest, BuildReporterConfig buildReporterConfig) {
        try {
            return pullRequest.getRepository().queryWorkflowRuns().headSha(pullRequest.getHead().getSha()).list().toList()
                    .stream()
                    .filter(w -> isWorkflowMonitored(w, buildReporterConfig))
                    .anyMatch(w -> w.getStatus() == Status.QUEUED || w.getStatus() == Status.IN_PROGRESS);
        } catch (Exception e) {
            LOG.warnf(e, "Error while getting workflow runs for %s#%s", pullRequest.getRepository().getFullName(),
                    pullRequest.getNumber());
            return false;
        }
    }

    private static boolean isWorkflowMonitored(GHWorkflowRun workflowRun, BuildReporterConfig buildReporterConfig) {
        for (String workflowName : buildReporterConfig.getMonitoredWorkflows()) {
            if (workflowName.equals(workflowRun.getName())) {
                return true;
            }
        }
        return false;
    }
}
