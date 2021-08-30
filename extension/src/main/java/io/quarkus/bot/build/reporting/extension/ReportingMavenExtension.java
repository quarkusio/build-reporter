package io.quarkus.bot.build.reporting.extension;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.BuildFailure;
import org.apache.maven.execution.BuildSuccess;
import org.apache.maven.execution.BuildSummary;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.quarkus.bot.build.reporting.model.BuildReport;
import io.quarkus.bot.build.reporting.model.ProjectReport;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "quarkus-build-report")
public class ReportingMavenExtension extends AbstractMavenLifecycleParticipant {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final String BUILD_REPORT_JSON_FILENAME = "build-report.json";
    private static final Pattern REMOVE_COLORS = Pattern.compile("\u001B\\[[;\\d]*m");

    @Requirement
    private Logger logger;

    @Override
    public void afterSessionEnd(MavenSession session)
            throws MavenExecutionException {

        MavenExecutionResult result = session.getResult();
        List<MavenProject> projects = result.getTopologicallySortedProjects();

        BuildReport buildReport = new BuildReport();

        Path rootPath = Path.of("").toAbsolutePath();

        for (MavenProject project : projects) {
            BuildSummary buildSummary = result.getBuildSummary(project);
            Path projectPath = rootPath.relativize(project.getBasedir().toPath());

            if (buildSummary == null) {
                buildReport.addProjectReport(
                        ProjectReport.skipped(project.getName(), projectPath, project.getGroupId(),
                                project.getArtifactId()));
            } else if (buildSummary instanceof BuildFailure) {
                buildReport.addProjectReport(
                        ProjectReport.failure(project.getName(), projectPath,
                                REMOVE_COLORS.matcher(((BuildFailure) buildSummary).getCause().getMessage()).replaceAll(""),
                                project.getGroupId(), project.getArtifactId()));
            } else if (buildSummary instanceof BuildSuccess) {
                buildReport.addProjectReport(
                        ProjectReport.success(project.getName(), projectPath, project.getGroupId(),
                                project.getArtifactId()));
            }
        }

        Path targetDirectory = Path.of("target");
        try {
            Files.createDirectories(targetDirectory);
        } catch (Exception e) {
            logger.error("Unable to create the target directory", e);
            return;
        }

        try (FileOutputStream file = new FileOutputStream(targetDirectory.resolve(BUILD_REPORT_JSON_FILENAME).toFile())) {
            OBJECT_MAPPER.writeValue(file, buildReport);
        } catch (Exception e) {
            logger.error("Unable to create the " + BUILD_REPORT_JSON_FILENAME + " file", e);
        }
    }
}
