package io.quarkus.bot.buildreporter.githubactions.report;

import org.apache.maven.plugins.surefire.report.ReportTestCase;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class WorkflowReportTestCase implements Comparable<WorkflowReportTestCase> {

    private final String classPath;
    private final String fullName;
    private final String fullClassName;
    private final String name;
    private final String failureType;
    private final String failureErrorLine;
    private final String abbreviatedFailureDetail;
    private final String failureDetail;
    private final String failureUrl;
    private final String shortenedFailureUrl;

    public WorkflowReportTestCase(String classPath, ReportTestCase reportTestCase, String abbreviatedFailureDetail,
            String failureUrl,
            String shortenedFailureUrl) {
        this.classPath = classPath;
        this.fullName = reportTestCase.getFullName();
        this.fullClassName = reportTestCase.getFullClassName();
        this.name = reportTestCase.getName();
        this.failureType = reportTestCase.getFailureType();
        this.failureErrorLine = reportTestCase.getFailureErrorLine();
        this.abbreviatedFailureDetail = abbreviatedFailureDetail;
        this.failureDetail = reportTestCase.getFailureDetail();
        this.failureUrl = failureUrl;
        this.shortenedFailureUrl = shortenedFailureUrl;
    }

    public String getClassPath() {
        return classPath;
    }

    public String getFullName() {
        return fullName;
    }

    public String getFullClassName() {
        return fullClassName;
    }

    public String getName() {
        return name;
    }

    public String getFailureType() {
        return failureType;
    }

    public String getFailureErrorLine() {
        return failureErrorLine;
    }

    public String getAbbreviatedFailureDetail() {
        return abbreviatedFailureDetail;
    }

    public String getFailureDetail() {
        return failureDetail;
    }

    public String getFailureUrl() {
        return failureUrl;
    }

    public String getShortenedFailureUrl() {
        return shortenedFailureUrl;
    }

    @Override
    public int compareTo(WorkflowReportTestCase o) {
        int compare = this.fullName.compareTo(o.fullName);

        if (compare != 0 ||
                this.failureErrorLine == null || this.failureErrorLine.isBlank() ||
                o.failureErrorLine == null || o.failureErrorLine.isBlank()) {
            return compare;
        }

        try {
            Integer thisLine = Integer.valueOf(this.failureErrorLine);
            Integer otherLine = Integer.valueOf(o.failureErrorLine);

            return thisLine.compareTo(otherLine);
        } catch (NumberFormatException e) {
            return compare;
        }
    }
}