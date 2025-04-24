package io.quarkus.bot.buildreporter.githubactions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHArtifact;

import io.quarkus.bot.buildreporter.githubactions.urlshortener.UrlShortener;

@ApplicationScoped
class BuildReportsUnarchiver {

    private static final Logger LOG = Logger.getLogger(BuildReportsUnarchiver.class);

    static final String NESTED_ZIP_FILE_NAME = "build-reports.zip";

    @Inject
    UrlShortener urlShortener;

    public Optional<BuildReports> getBuildReports(WorkflowContext workflowContext,
            GHArtifact buildReportsArtifact,
            Path jobDirectory) throws IOException {
        ArtifactIsDownloaded artifactIsDownloaded = new ArtifactIsDownloaded(workflowContext, buildReportsArtifact,
                jobDirectory);

        try {
            Awaitility.await()
                    .atMost(Duration.ofMinutes(5))
                    .pollDelay(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofSeconds(60))
                    .until(artifactIsDownloaded);
        } catch (ConditionTimeoutException e) {
            LOG.warn(workflowContext.getLogContext()
                    + " - Unable to download the artifacts in a timely manner, ignoring them");
            return Optional.empty();
        }

        return artifactIsDownloaded.getBuildReports();
    }

    private static class ArtifactIsDownloaded implements Callable<Boolean> {

        private static final Logger LOG = Logger.getLogger(ArtifactIsDownloaded.class);

        private final WorkflowContext workflowContext;
        private final GHArtifact buildReportsArtifact;
        private final Path jobDirectory;
        private BuildReports buildReports = null;
        private int retry = 0;

        private ArtifactIsDownloaded(WorkflowContext workflowContext,
                GHArtifact buildReportsArtifact,
                Path jobDirectory) {
            this.workflowContext = workflowContext;
            this.buildReportsArtifact = buildReportsArtifact;
            this.jobDirectory = jobDirectory;
        }

        @Override
        public Boolean call() {
            try {
                retry++;
                buildReports = buildReportsArtifact
                        .download((is) -> unzip(is, jobDirectory.resolve("retry-" + retry)));
                return true;
            } catch (Exception e) {
                LOG.error(workflowContext.getLogContext() + " - Unable to download artifact "
                        + buildReportsArtifact.getName() + "- retry #" + retry, e);
                return false;
            }
        }

        public Optional<BuildReports> getBuildReports() {
            return Optional.ofNullable(buildReports);
        }

        private BuildReports unzip(InputStream inputStream, Path jobDirectory) throws IOException {
            BuildReports.Builder buildReportsBuilder = new BuildReports.Builder(jobDirectory);

            try (final ZipInputStream zis = new ZipInputStream(inputStream)) {
                final byte[] buffer = new byte[1024];
                ZipEntry zipEntry = zis.getNextEntry();
                while (zipEntry != null) {
                    final Path newPath = getZipEntryPath(jobDirectory, zipEntry);
                    final File newFile = newPath.toFile();

                    if (zipEntry.isDirectory()) {
                        if (!newFile.isDirectory() && !newFile.mkdirs()) {
                            throw new IOException("Failed to create directory " + newFile);
                        }
                    } else if (NESTED_ZIP_FILE_NAME.equals(zipEntry.getName())) {
                        return unzip(zis, jobDirectory);
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

                    buildReportsBuilder.addPath(newPath);

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

}
