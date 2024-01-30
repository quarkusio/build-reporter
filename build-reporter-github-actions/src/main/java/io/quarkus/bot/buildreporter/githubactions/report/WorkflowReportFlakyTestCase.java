package io.quarkus.bot.buildreporter.githubactions.report;

import java.util.Collections;
import java.util.List;

import org.apache.maven.plugins.surefire.report.ReportTestCase;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class WorkflowReportFlakyTestCase implements Comparable<WorkflowReportFlakyTestCase> {

    private final String classPath;
    private final String fullName;
    private final String fullClassName;
    private final List<Flake> flakes;

    public WorkflowReportFlakyTestCase(String classPath, ReportTestCase reportTestCase, List<Flake> flakes) {
        this.classPath = classPath;
        this.fullName = reportTestCase.getFullName();
        this.fullClassName = reportTestCase.getFullClassName();
        this.flakes = Collections.unmodifiableList(flakes);
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

    public List<Flake> getFlakes() {
        return flakes;
    }

    @Override
    public int compareTo(WorkflowReportFlakyTestCase o) {
        return this.fullName.compareTo(o.fullName);
    }

    public static class Flake {

        private final String message;
        private final String type;
        private final String stackTrace;
        private final String abbreviatedStackTrace;

        public Flake(String message, String type, String stackTrace, String abbreviatedStackTrace) {
            this.message = message;
            this.type = type;
            this.stackTrace = stackTrace;
            this.abbreviatedStackTrace = abbreviatedStackTrace;
        }

        public String getMessage() {
            return message;
        }

        public String getType() {
            return type;
        }

        public String getStackTrace() {
            return stackTrace;
        }

        public String getAbbreviatedStackTrace() {
            return abbreviatedStackTrace;
        }
    }
}