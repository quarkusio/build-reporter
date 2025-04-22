package io.quarkus.bot.buildreporter.githubactions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

class BuildReports {

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

    @Override
    public String toString() {
        return "BuildReports[\n"
                + "    jobDirectory=" + jobDirectory + "\n"
                + "    buildReportPath=" + buildReportPath + "\n"
                + "    gradleBuildScanUrlPath=" + gradleBuildScanUrlPath + "\n"
                + "    testResultsPaths=" + testResultsPaths + "\n"
                + "]";
    }

    static class Builder {

        private static final String TARGET = "target";
        private static final String FAILSAFE_REPORTS = "failsafe-reports";
        private static final String SUREFIRE_REPORTS = "surefire-reports";
        private static final Path GRADLE_REPORTS_PATH = Path.of("build", "test-results", "test");

        private final Path jobDirectory;
        private Path buildReportPath;
        private Path gradleBuildScanUrlPath;
        private Set<TestResultsPath> testResultsPaths = new TreeSet<>();

        private Set<Path> alreadyTreatedPaths = new HashSet<>();

        Builder(Path jobDirectory) {
            this.jobDirectory = jobDirectory;
        }

        void addPath(Path path) {
            if (path.endsWith(WorkflowConstants.BUILD_REPORT_PATH)) {
                buildReportPath = path;
                return;
            }
            if (path.endsWith(WorkflowConstants.GRADLE_BUILD_SCAN_URL_PATH)) {
                gradleBuildScanUrlPath = path;
                return;
            }

            // when we upload the files directly with upload-artifact,
            // we get entries for the directories so we can directly
            // resolve them
            if (addTestPath(path)) {
                return;
            }

            // when we are building a zip with the zip command,
            // we don't get so lucky so we need to resolve the parent
            // directory
            // all the test files are stored in the same parent directory,
            // there is no nesting
            addTestPath(path.getParent());
        }

        private boolean addTestPath(Path path) {
            if (path == null || alreadyTreatedPaths.contains(path)) {
                return true;
            }

            if (!Files.isDirectory(path) || path.getNameCount() < 2) {
                return false;
            }

            if (path.getFileName().toString().startsWith(SUREFIRE_REPORTS)) {
                if (path.getName(path.getNameCount() - 2).toString().equals(TARGET)) {
                    testResultsPaths.add(new SurefireTestResultsPath(path));
                    return true;
                }
            }
            if (path.getFileName().toString().startsWith(FAILSAFE_REPORTS)) {
                if (path.getName(path.getNameCount() - 2).toString().equals(TARGET)) {
                    testResultsPaths.add(new FailsafeTestResultsPath(path));
                    return true;
                }
            }

            if (path.endsWith(GRADLE_REPORTS_PATH)) {
                testResultsPaths.add(new GradleTestResultsPath(path));
                return true;
            }

            return false;
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
            Path modulePath = jobDirectory.relativize(path).getParent().getParent();
            if (modulePath == null) {
                return null;
            }

            return modulePath.toString();
        }

        @Override
        public int compareTo(TestResultsPath o) {
            return path.compareTo(o.getPath());
        }

        @Override
        public String toString() {
            return getClass().getName() + "[" + path + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(path);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            SurefireTestResultsPath other = (SurefireTestResultsPath) obj;
            return Objects.equals(path, other.path);
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
            Path modulePath = jobDirectory.relativize(path).getParent().getParent();
            if (modulePath == null) {
                return null;
            }

            return modulePath.toString();
        }

        @Override
        public String toString() {
            return getClass().getName() + "[" + path + "]";
        }

        @Override
        public int compareTo(TestResultsPath o) {
            return path.compareTo(o.getPath());
        }

        @Override
        public int hashCode() {
            return Objects.hash(path);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            FailsafeTestResultsPath other = (FailsafeTestResultsPath) obj;
            return Objects.equals(path, other.path);
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
            Path modulePath = jobDirectory.relativize(path).getParent().getParent().getParent();
            if (modulePath == null) {
                return null;
            }

            return modulePath.toString();
        }

        @Override
        public String toString() {
            return getClass().getName() + "[" + path + "]";
        }

        @Override
        public int compareTo(TestResultsPath o) {
            return path.compareTo(o.getPath());
        }

        @Override
        public int hashCode() {
            return Objects.hash(path);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            GradleTestResultsPath other = (GradleTestResultsPath) obj;
            return Objects.equals(path, other.path);
        }
    }
}