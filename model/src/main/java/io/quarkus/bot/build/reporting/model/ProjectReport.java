package io.quarkus.bot.build.reporting.model;

import java.nio.file.Path;

public class ProjectReport {

    private String name;
    private BuildStatus status;
    private String basedir;
    private String error;
    private String groupId;
    private String artifactId;

    public static ProjectReport success(String name, Path basedir, String groupId, String artifactId) {
        return new ProjectReport(name, BuildStatus.SUCCESS, basedir, null, groupId, artifactId);
    }

    public static ProjectReport failure(String name, Path basedir, String error, String groupId,
            String artifactId) {
        return new ProjectReport(name, BuildStatus.FAILURE, basedir, error, groupId, artifactId);
    }

    public static ProjectReport skipped(String name, Path basedir, String groupId, String artifactId) {
        return new ProjectReport(name, BuildStatus.SKIPPED, basedir, null, groupId, artifactId);
    }

    /**
     * For Jackson deserialization.
     */
    public ProjectReport() {
    }

    private ProjectReport(String name, BuildStatus status, Path basedir, String error,
            String groupId, String artifactId) {
        this.name = name;
        this.status = status;
        this.basedir = basedir.toString();
        this.error = error;
        this.artifactId = artifactId;
        this.groupId = groupId;
    }

    public String getName() {
        return this.name;
    }

    public BuildStatus getStatus() {
        return this.status;
    }

    public String getBasedir() {
        return this.basedir;
    }

    public String getError() {
        return this.error;
    }

    public String getArtifactId() {
        return this.artifactId;
    }

    public String getGroupId() {
        return this.groupId;
    }
}
