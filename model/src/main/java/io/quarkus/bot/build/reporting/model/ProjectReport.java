package io.quarkus.bot.build.reporting.model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class ProjectReport {

    private String name;
    private BuildStatus status;
    private String basedir;
    private List<String> errors;
    private String groupId;
    private String artifactId;

    public static ProjectReport success(String name, Path basedir, String groupId, String artifactId) {
        return new ProjectReport(name, BuildStatus.SUCCESS, basedir, Collections.emptyList(), groupId, artifactId);
    }

    public static ProjectReport failure(String name, Path basedir, List<String> errors, String groupId,
            String artifactId) {
        return new ProjectReport(name, BuildStatus.FAILURE, basedir, errors, groupId, artifactId);
    }

    public static ProjectReport skipped(String name, Path basedir, String groupId, String artifactId) {
        return new ProjectReport(name, BuildStatus.SKIPPED, basedir, Collections.emptyList(), groupId, artifactId);
    }

    private ProjectReport(String name, BuildStatus status, Path basedir, List<String> errors,
            String groupId, String artifactId) {
        this.name = name;
        this.status = status;
        this.basedir = basedir.toString();
        this.errors = errors;
        this.artifactId = artifactId;
        this.groupId = groupId;
    }

    // getters
    public String getName() {
        return this.name;
    }

    public BuildStatus getStatus() {
        return this.status;
    }

    public String getBasedir() {
        return this.basedir;
    }

    public List<String> getErrors() {
        return this.errors;
    }

    public String getArtifactId() {
        return this.artifactId;
    }

    public String getGroupId() {
        return this.groupId;
    }
}
