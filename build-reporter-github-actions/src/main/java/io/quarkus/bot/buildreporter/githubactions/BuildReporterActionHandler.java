package io.quarkus.bot.buildreporter.githubactions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        Map<String, Optional<BuildReports>> buildReportsMap = prepareBuildReportMap(buildReportsArtifactsPath,
                workflowRun.getRunAttempt());

        WorkflowContext workflowContext = new WorkflowContext(workflowRun);
        List<GHWorkflowJob> jobs = workflowRun.listJobs().toList()
                .stream()
                .filter(j -> j.getConclusion() != Conclusion.UNKNOWN &&
                        j.getConclusion() != Conclusion.ACTION_REQUIRED &&
                        j.getConclusion() != Conclusion.NEUTRAL &&
                        j.getConclusion() != Conclusion.STALE)
                .sorted(buildReporterConfig.getJobNameComparator())
                .collect(Collectors.toList());

        Optional<WorkflowReport> workflowReportOptional = workflowRunAnalyzer.getReport(workflowName, workflowRun,
                workflowContext,
                buildReporterConfig.getIgnoredFlakyTests(),
                jobs,
                buildReportsMap);
        if (workflowReportOptional.isEmpty()) {
            return Optional.empty();
        }

        return buildReporter.generateReportComment(workflowName, workflowRun, buildReporterConfig,
                workflowContext,
                workflowReportOptional.get(), true, false, false);
    }

    private Map<String, Optional<BuildReports>> prepareBuildReportMap(Path buildReportsArtifactsPath, long runAttempt) {
        if (!Files.exists(buildReportsArtifactsPath) || !Files.isDirectory(buildReportsArtifactsPath)) {
            return Map.of();
        }

        boolean useNewBuildReportsArtifactNamePattern = false;
        try (Stream<Path> jobBuildReportsDirectoriesStream = Files.list(buildReportsArtifactsPath).filter(Files::isDirectory)) {
            useNewBuildReportsArtifactNamePattern = jobBuildReportsDirectoriesStream
                    .anyMatch(d -> WorkflowUtils.matchesNewBuildReportsArtifactNamePattern(d.getFileName().toString()));
        } catch (IOException e) {
            LOG.error("Unable to extract build reports from directory " + buildReportsArtifactsPath, e);
            return Map.of();
        }

        Map<String, Optional<BuildReports>> buildReportsMap = new HashMap<>();
        String buildReportsArtifactPrefix = useNewBuildReportsArtifactNamePattern
                ? WorkflowConstants.BUILD_REPORTS_ARTIFACT_PREFIX + runAttempt + "-"
                : WorkflowConstants.BUILD_REPORTS_ARTIFACT_PREFIX;

        try (Stream<Path> jobBuildReportsDirectoriesStream = Files.list(buildReportsArtifactsPath).filter(Files::isDirectory)
                .filter(d -> d.getFileName().toString().startsWith(buildReportsArtifactPrefix))) {

            jobBuildReportsDirectoriesStream.forEach(jobBuildReportsDirectory -> {
                String jobName = jobBuildReportsDirectory.getFileName().toString()
                        .replace(buildReportsArtifactPrefix, "");

                BuildReports.Builder buildReportsBuilder = new BuildReports.Builder(jobBuildReportsDirectory);

                Path buildReportsArchive = jobBuildReportsDirectory.resolve(BuildReportsUnarchiver.NESTED_ZIP_FILE_NAME);
                if (Files.isReadable(buildReportsArchive)) {
                    try {
                        try (InputStream buildReportsZipIs = Files.newInputStream(buildReportsArchive)) {
                            buildReportsMap.put(jobName, Optional.of(unzip(buildReportsZipIs,
                                    Files.createTempDirectory("build-reports-analyzer-action-" + jobName))));
                        }
                    } catch (IOException e) {
                        LOG.error("Unable to extract build reports from archive " + buildReportsArchive, e);
                    }
                } else {
                    try (Stream<Path> jobBuildReportsFilesStream = Files.walk(jobBuildReportsDirectory)) {
                        jobBuildReportsFilesStream.forEach(p -> buildReportsBuilder.addPath(p));

                        buildReportsMap.put(jobName, Optional.of(buildReportsBuilder.build()));
                    } catch (IOException e) {
                        LOG.error("Unable to extract build reports from subdirectory " + jobBuildReportsDirectory, e);

                        buildReportsMap.put(jobName, Optional.empty());
                    }
                }
            });

            return buildReportsMap;
        } catch (IOException e) {
            LOG.error("Unable to extract build reports from directory " + buildReportsArtifactsPath, e);

            return Map.of();
        }
    }

    private BuildReports unzip(InputStream inputStream, Path jobDirectory) throws IOException {
        BuildReports.Builder buildReportsBuilder = new BuildReports.Builder(jobDirectory);

        try (final ZipInputStream zis = new ZipInputStream(inputStream)) {
            final byte[] buffer = new byte[1024];
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                final Path newPath = getZipEntryPath(jobDirectory, zipEntry);
                final File newFile = newPath.toFile();

                buildReportsBuilder.addPath(newPath);

                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    final FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }

                    fos.close();
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }

        return buildReportsBuilder.build();
    }

    private static Path getZipEntryPath(Path destinationDirectory, ZipEntry zipEntry) throws IOException {
        Path destinationFile = destinationDirectory.resolve(zipEntry.getName());

        if (!destinationFile.toAbsolutePath().startsWith(destinationDirectory.toAbsolutePath())) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destinationFile;
    }
}
