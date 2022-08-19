package io.quarkus.bot.build.reporting.model;

import java.util.ArrayList;
import java.util.List;

public class BuildReport {

    private List<ProjectReport> projectReports = new ArrayList<>();

    public void addProjectReport(ProjectReport projectReport) {
        this.projectReports.add(projectReport);
    }

    public List<ProjectReport> getProjectReports() {
        return this.projectReports;
    }
}