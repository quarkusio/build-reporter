package io.quarkus.bot.buildreporter.githubactions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRun.Conclusion;

import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReport;

@Singleton
public class BuildReporterActionHandler {

    private static final Logger LOG = Logger.getLogger(BuildReporterActionHandler.class);

    @Inject
    WorkflowRunAnalyzer workflowRunAnalyzer;

    @Inject
    BuildReporter buildReporter;

    public Optional<String> generateReport(String workflowName, GHWorkflowRun workflowRun, Path buildReportsArtifactsPath,
            BuildReporterConfig buildReporterConfig) throws IOException {
        Map<String, Optional<BuildReports>> buildReportsMap = prepareBuildReportMap(buildReportsArtifactsPath);

        WorkflowContext workflowContext = new WorkflowContext(workflowRun);
        List<GHWorkflowJob> jobs = workflowRun.listJobs().toList()
                .stream()
                .filter(j -> j.getConclusion() != Conclusion.UNKNOWN && j.getConclusion() != Conclusion.ACTION_REQUIRED)
                .sorted(buildReporterConfig.getJobNameComparator())
                .collect(Collectors.toList());

        Optional<WorkflowReport> workflowReportOptional = workflowRunAnalyzer.getReport(workflowName, workflowRun,
                workflowContext,
                jobs,
                buildReportsMap);
        if (workflowReportOptional.isEmpty()) {
            return Optional.empty();
        }

        return buildReporter.generateReportComment(workflowName, workflowRun, buildReporterConfig,
                workflowContext,
                workflowReportOptional.get(), true, false, false);
    }

    private Map<String, Optional<BuildReports>> prepareBuildReportMap(Path buildReportsArtifactsPath) {
        if (!Files.exists(buildReportsArtifactsPath) || !Files.isDirectory(buildReportsArtifactsPath)) {
            return Map.of();
        }

        Map<String, Optional<BuildReports>> buildReportsMap = new HashMap<>();

        try (Stream<Path> jobBuildReportsDirectoriesStream = Files.list(buildReportsArtifactsPath).filter(Files::isDirectory)
                .filter(d -> d.getFileName().toString().startsWith(WorkflowConstants.BUILD_REPORTS_ARTIFACT_PREFIX))) {

            jobBuildReportsDirectoriesStream.forEach(jobBuildReportsDirectory -> {
                String jobName = jobBuildReportsDirectory.getFileName().toString()
                        .replace(WorkflowConstants.BUILD_REPORTS_ARTIFACT_PREFIX, "");

                BuildReports.Builder buildReportsBuilder = new BuildReports.Builder(jobBuildReportsDirectory);

                try (Stream<Path> jobBuildReportsFilesStream = Files.walk(jobBuildReportsDirectory)) {
                    jobBuildReportsFilesStream.forEach(p -> buildReportsBuilder.addPath(p));

                    buildReportsMap.put(jobName, Optional.of(buildReportsBuilder.build()));
                } catch (IOException e) {
                    LOG.error("Unable to extract build reports from subdirectory " + jobBuildReportsDirectory, e);

                    buildReportsMap.put(jobName, Optional.empty());
                }
            });

            return buildReportsMap;
        } catch (IOException e) {
            LOG.error("Unable to extract build reports from directory " + buildReportsArtifactsPath, e);

            return Map.of();
        }
    }
}
