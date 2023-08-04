package io.quarkus.bot.buildreporter.githubactions;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

class BuildReports {

    private static final Path MAVEN_SUREFIRE_REPORTS_PATH = Path.of("target", "surefire-reports");
    private static final Path MAVEN_FAILSAFE_REPORTS_PATH = Path.of("target", "failsafe-reports");
    private static final Path GRADLE_REPORTS_PATH = Path.of("build", "test-results", "test");

    private final Path jobDirectory;
    private final Path buildReportPath;
    private final Path gradleBuildScanUrlPath;
    private final Set<TestResultsPath> testResultsPaths;

    private BuildReports(Path jobDirectory, Path buildReportPath, Path gradleBuildScanUrlPath,
            Set<TestResultsPath> testResultsPaths) {
        this.jobDirectory = jobDirectory;
        this.buildReportPath = buildReportPath;
        this.gradleBuildScanUrlPath = gradleBuildScanUrlPath;
        this.testResultsPaths = Collections.unmodifiableSet(testResultsPaths);
    }

    public Path getJobDirectory() {
        return jobDirectory;
    }

    public Path getBuildReportPath() {
        return buildReportPath;
    }

    public Path getGradleBuildScanUrlPath() {
        return gradleBuildScanUrlPath;
    }

    public Set<TestResultsPath> getTestResultsPaths() {
        return testResultsPaths;
    }

    interface TestResultsPath extends Comparable<TestResultsPath> {

        Path getPath();

        String getModuleName(Path jobDirectory);
    }

    static class Builder {

        private final Path jobDirectory;
        private Path buildReportPath;
        private Path gradleBuildScanUrlPath;
        private Set<TestResultsPath> testResultsPaths = new TreeSet<>();

        Builder(Path jobDirectory) {
            this.jobDirectory = jobDirectory;
        }

        void addPath(Path path) {
            if (path.endsWith(WorkflowConstants.BUILD_REPORT_PATH)) {
                buildReportPath = path;
            } else if (path.endsWith(WorkflowConstants.GRADLE_BUILD_SCAN_URL_PATH)) {
                gradleBuildScanUrlPath = path;
            } else if (path.endsWith(MAVEN_SUREFIRE_REPORTS_PATH)) {
                testResultsPaths.add(new SurefireTestResultsPath(path));
            } else if (path.endsWith(MAVEN_FAILSAFE_REPORTS_PATH)) {
                testResultsPaths.add(new FailsafeTestResultsPath(path));
            } else if (path.endsWith(GRADLE_REPORTS_PATH)) {
                testResultsPaths.add(new GradleTestResultsPath(path));
            }
        }

        BuildReports build() {
            return new BuildReports(jobDirectory, buildReportPath, gradleBuildScanUrlPath, testResultsPaths);
        }
    }

    private static class SurefireTestResultsPath implements TestResultsPath {

        private final Path path;

        SurefireTestResultsPath(Path path) {
            this.path = path;
        }

        @Override
        public Path getPath() {
            return path;
        }

        @Override
        public String getModuleName(Path jobDirectory) {
            return jobDirectory.relativize(path).getParent().getParent().toString();
        }

        @Override
        public int compareTo(TestResultsPath o) {
            return path.compareTo(o.getPath());
        }
    }

    static class FailsafeTestResultsPath implements TestResultsPath {

        private final Path path;

        FailsafeTestResultsPath(Path path) {
            this.path = path;
        }

        @Override
        public Path getPath() {
            return path;
        }

        @Override
        public String getModuleName(Path jobDirectory) {
            return jobDirectory.relativize(path).getParent().getParent().toString();
        }

        @Override
        public int compareTo(TestResultsPath o) {
            return path.compareTo(o.getPath());
        }
    }

    static class GradleTestResultsPath implements TestResultsPath {

        private final Path path;

        GradleTestResultsPath(Path path) {
            this.path = path;
        }

        @Override
        public Path getPath() {
            return path;
        }

        @Override
        public String getModuleName(Path jobDirectory) {
            return jobDirectory.relativize(path).getParent().getParent().getParent().toString();
        }

        @Override
        public int compareTo(TestResultsPath o) {
            return path.compareTo(o.getPath());
        }
    }
}