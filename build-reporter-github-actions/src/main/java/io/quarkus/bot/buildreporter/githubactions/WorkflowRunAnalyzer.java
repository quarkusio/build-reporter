package io.quarkus.bot.buildreporter.githubactions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.surefire.log.api.NullConsoleLogger;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.SurefireReportParser;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowJob;
import org.kohsuke.github.GHWorkflowJob.Step;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GHWorkflowRun.Conclusion;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.bot.build.reporting.model.BuildReport;
import io.quarkus.bot.build.reporting.model.ProjectReport;
import io.quarkus.bot.buildreporter.githubactions.BuildReports.TestResultsPath;
import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReport;
import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReportFlakyTestCase;
import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReportJob;
import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReportModule;
import io.quarkus.bot.buildreporter.githubactions.report.WorkflowReportTestCase;
import io.quarkus.bot.buildreporter.githubactions.urlshortener.UrlShortener;
import io.quarkus.runtime.annotations.RegisterForReflection;

@ApplicationScoped
@RegisterForReflection(targets = { BuildReport.class, ProjectReport.class })
public class WorkflowRunAnalyzer {

    private static final Logger LOG = Logger.getLogger(WorkflowRunAnalyzer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final BuildReport EMPTY_BUILD_REPORT = new BuildReport();

    @Inject
    BuildReportsUnarchiver buildReportsUnarchiver;

    @Inject
    WorkflowJobLabeller workflowJobLabeller;

    @Inject
    StackTraceShortener stackTraceShortener;

    @Inject
    UrlShortener urlShortener;

    public Optional<WorkflowReport> getReport(String workflowName,
            GHWorkflowRun workflowRun,
            WorkflowContext workflowContext,
            Set<String> ignoredFlakyTests,
            List<GHWorkflowJob> jobs,
            Map<String, Optional<BuildReports>> buildReportsMap) throws IOException {
        if (jobs.isEmpty()) {
            LOG.error(workflowContext.getLogContext() + " - No jobs found");
            return Optional.empty();
        }

        GHRepository workflowRunRepository = workflowRun.getRepository();
        String sha = workflowRun.getHeadSha();

        List<WorkflowReportJob> workflowReportJobs = new ArrayList<>();

        for (GHWorkflowJob job : jobs) {
            if (job.getConclusion() != Conclusion.FAILURE && job.getConclusion() != Conclusion.CANCELLED
                    && job.getConclusion() != Conclusion.SUCCESS) {
                workflowReportJobs.add(new WorkflowReportJob(job.getName(), workflowJobLabeller.label(job.getName()),
                        null, job.getConclusion(), null, null, null, null,
                        EMPTY_BUILD_REPORT, Collections.emptyList(), false));
                continue;
            }

            Optional<BuildReports> buildReportsOptional = buildReportsMap.get(job.getName());

            BuildReport buildReport = EMPTY_BUILD_REPORT;
            String gradleBuildScanUrl = null;
            List<WorkflowReportModule> modules = Collections.emptyList();
            boolean errorDownloadingBuildReports = false;
            if (buildReportsOptional != null) {
                if (buildReportsOptional.isPresent()) {
                    BuildReports buildReports = buildReportsOptional.get();
                    if (buildReports.getBuildReportPath() != null) {
                        buildReport = getBuildReport(workflowContext, buildReports.getBuildReportPath());
                    }
                    if (buildReports.getGradleBuildScanUrlPath() != null) {
                        try {
                            gradleBuildScanUrl = Files.readString(buildReports.getGradleBuildScanUrlPath()).trim();
                        } catch (Exception e) {
                            LOG.warn("Unable to read file containing Gradle Build Scan URL", e);
                        }
                    }

                    modules = getModules(workflowContext, buildReport, buildReports.getJobDirectory(),
                            buildReports.getTestResultsPaths(), ignoredFlakyTests,
                            sha);
                } else {
                    errorDownloadingBuildReports = true;
                    LOG.error(workflowContext.getLogContext() + " - Unable to analyze build report for job "
                            + job.getName() + " - see exceptions above");
                }
            }

            workflowReportJobs.add(new WorkflowReportJob(job.getName(),
                    workflowJobLabeller.label(job.getName()),
                    getFailuresAnchor(job.getId()),
                    job.getConclusion(),
                    getFailingStep(job.getSteps()),
                    getJobUrl(job),
                    getRawLogsUrl(job, workflowRun.getHeadSha()),
                    gradleBuildScanUrl,
                    buildReport,
                    modules,
                    errorDownloadingBuildReports));
        }

        if (workflowReportJobs.isEmpty()) {
            LOG.warn(workflowContext.getLogContext() + " - Report jobs empty");
            return Optional.empty();
        }

        WorkflowReport report = new WorkflowReport(workflowName, sha, workflowReportJobs,
                workflowRunRepository.getFullName().equals(workflowContext.getRepository()),
                workflowRun.getConclusion(), workflowRun.getHtmlUrl().toString());

        return Optional.of(report);
    }

    private static BuildReport getBuildReport(WorkflowContext workflowContext, Path buildReportPath) {
        if (buildReportPath == null) {
            return new BuildReport();
        }

        try {
            return OBJECT_MAPPER.readValue(buildReportPath.toFile(), BuildReport.class);
        } catch (Exception e) {
            LOG.error(workflowContext.getLogContext() + " - Unable to deserialize "
                    + WorkflowConstants.BUILD_REPORT_PATH, e);
            return new BuildReport();
        }
    }

    private List<WorkflowReportModule> getModules(
            WorkflowContext workflowContext,
            BuildReport buildReport,
            Path jobDirectory,
            Set<TestResultsPath> testResultsPaths,
            Set<String> ignoredFlakyTests,
            String sha) {
        List<WorkflowReportModule> modules = new ArrayList<>();

        Map<String, ModuleReports> moduleReportsMap = mapModuleReports(buildReport, testResultsPaths, jobDirectory);

        for (Entry<String, ModuleReports> moduleReportsEntry : moduleReportsMap.entrySet()) {
            String moduleName = moduleReportsEntry.getKey();
            ModuleReports moduleReports = moduleReportsEntry.getValue();

            List<ReportTestSuite> reportTestSuites = new ArrayList<>();
            List<WorkflowReportTestCase> workflowReportTestCases = new ArrayList<>();
            List<WorkflowReportFlakyTestCase> workflowReportFlakyTestCases = new ArrayList<>();
            for (TestResultsPath testResultPath : moduleReports.getTestResultsPaths()) {
                try {
                    SurefireReportParser surefireReportsParser = new SurefireReportParser(
                            Collections.singletonList(testResultPath.getPath().toFile()),
                            new NullConsoleLogger());
                    reportTestSuites.addAll(surefireReportsParser.parseXMLReportFiles());
                    workflowReportTestCases.addAll(surefireReportsParser.getFailureDetails(reportTestSuites).stream()
                            .filter(rtc -> !rtc.hasSkipped())
                            .map(rtc -> new WorkflowReportTestCase(
                                    WorkflowUtils.getFilePath(moduleName, rtc.getFullClassName()),
                                    rtc,
                                    stackTraceShortener.shorten(rtc.getFailureDetail(), 1000, 8),
                                    getFailureUrl(workflowContext.getRepository(), sha, moduleName, rtc),
                                    urlShortener.shorten(getFailureUrl(workflowContext.getRepository(), sha, moduleName, rtc))))
                            .collect(Collectors.toList()));

                    workflowReportFlakyTestCases.addAll(getFlakeDetails(reportTestSuites).stream()
                            .filter(rtc -> !rtc.hasSkipped())
                            .filter(rtc -> !ignoredFlakyTests.contains(rtc.getFullName())
                                    && !ignoredFlakyTests.contains(rtc.getFullClassName()))
                            .map(rtc -> new WorkflowReportFlakyTestCase(
                                    WorkflowUtils.getFilePath(moduleName, rtc.getFullClassName()),
                                    rtc,
                                    Stream.concat(
                                            rtc.getFlakyErrors().stream()
                                                    .map(fe -> new WorkflowReportFlakyTestCase.Flake(
                                                            stackTraceShortener.shorten(fe.getMessage(), 1000, 5),
                                                            fe.getType(), fe.getStackTrace(),
                                                            stackTraceShortener.shorten(fe.getStackTrace(), 1000, 8))),
                                            rtc.getFlakyFailures().stream()
                                                    .map(fe -> new WorkflowReportFlakyTestCase.Flake(
                                                            stackTraceShortener.shorten(fe.getMessage(), 1000, 5),
                                                            fe.getType(), fe.getStackTrace(),
                                                            stackTraceShortener.shorten(fe.getStackTrace(), 1000, 8))))
                                            .collect(Collectors.toList())))
                            .collect(Collectors.toList()));
                } catch (Exception e) {
                    LOG.error(workflowContext.getLogContext() + " - Unable to parse test results for file "
                            + testResultPath.getPath(), e);
                }
            }

            Collections.sort(workflowReportTestCases);
            Collections.sort(workflowReportFlakyTestCases);

            WorkflowReportModule module = new WorkflowReportModule(
                    moduleName,
                    moduleReports.getProjectReport(),
                    moduleReports.getProjectReport() != null ? firstLines(moduleReports.getProjectReport().getError(), 5)
                            : null,
                    reportTestSuites,
                    workflowReportTestCases,
                    workflowReportFlakyTestCases);

            if (module.hasReportedFailures() || module.hasFlakyTests()) {
                modules.add(module);
            }
        }

        return modules;
    }

    private static Map<String, ModuleReports> mapModuleReports(BuildReport buildReport, Set<TestResultsPath> testResultsPaths,
            Path jobDirectory) {
        Set<String> modules = new TreeSet<>();
        modules.addAll(buildReport.getProjectReports().stream().map(pr -> normalizeModuleName(pr.getBasedir()))
                .collect(Collectors.toList()));
        modules.addAll(testResultsPaths.stream().map(trp -> normalizeModuleName(trp.getModuleName(jobDirectory)))
                .collect(Collectors.toList()));

        Map<String, ModuleReports> moduleReports = new TreeMap<>();
        for (String module : modules) {
            moduleReports.put(module, new ModuleReports(
                    buildReport.getProjectReports().stream().filter(pr -> normalizeModuleName(pr.getBasedir()).equals(module))
                            .findFirst().orElse(null),
                    testResultsPaths.stream().filter(trp -> normalizeModuleName(trp.getModuleName(jobDirectory)).equals(module))
                            .collect(Collectors.toCollection(TreeSet::new))));
        }

        return moduleReports;
    }

    private static String normalizeModuleName(String moduleName) {
        if (moduleName == null || moduleName.isBlank()) {
            return WorkflowReportModule.ROOT_MODULE;
        }

        return moduleName.replace('\\', '/');
    }

    private static String getFailuresAnchor(Long jobId) {
        return "test-failures-job-" + jobId;
    }

    private static String getFailingStep(List<Step> steps) {
        for (Step step : steps) {
            if (step.getConclusion() != Conclusion.SUCCESS && step.getConclusion() != Conclusion.SKIPPED
                    && step.getConclusion() != Conclusion.NEUTRAL) {
                return step.getName();
            }
        }
        return null;
    }

    private String getJobUrl(GHWorkflowJob job) {
        return urlShortener.shorten(job.getHtmlUrl().toString());
    }

    private String getRawLogsUrl(GHWorkflowJob job, String sha) {
        return urlShortener.shorten(job.getRepository().getHtmlUrl().toString() +
                "/commit/" + sha + "/checks/" + job.getId() + "/logs");
    }

    private static String getFailureUrl(String repository, String sha, String moduleName, ReportTestCase reportTestCase) {
        String classPath = reportTestCase.getFullClassName().replace(".", "/");
        int dollarIndex = reportTestCase.getFullClassName().indexOf('$');
        if (dollarIndex > 0) {
            classPath = classPath.substring(0, dollarIndex);
        }
        classPath = "src/test/java/" + classPath + ".java";

        StringBuilder sb = new StringBuilder();
        sb.append("https://github.com/").append(repository).append("/blob/").append(sha).append("/")
                .append(WorkflowUtils.getFilePath(moduleName, reportTestCase.getFullClassName()));
        if (StringUtils.isNotBlank(reportTestCase.getFailureErrorLine())) {
            sb.append("#L").append(reportTestCase.getFailureErrorLine());
        }
        return sb.toString();
    }

    private static List<ReportTestCase> getFlakeDetails(List<ReportTestSuite> testSuites) {
        List<ReportTestCase> flakeDetails = new ArrayList<>();

        for (ReportTestSuite suite : testSuites) {
            for (ReportTestCase tCase : suite.getTestCases()) {
                if (tCase.hasFlakes()) {
                    flakeDetails.add(tCase);
                }
            }
        }

        return flakeDetails;
    }

    private static String firstLines(String string, int numberOfLines) {
        if (string == null || string.isBlank()) {
            return null;
        }

        return string.lines().limit(numberOfLines).collect(Collectors.joining("\n"));
    }

    private static class ModuleReports {

        private final ProjectReport projectReport;
        private final Set<TestResultsPath> testResultsPaths;

        private ModuleReports(ProjectReport projectReport, Set<TestResultsPath> testResultsPaths) {
            this.projectReport = projectReport;
            this.testResultsPaths = testResultsPaths;
        }

        public ProjectReport getProjectReport() {
            return projectReport;
        }

        public Set<TestResultsPath> getTestResultsPaths() {
            return testResultsPaths;
        }
    }
}
