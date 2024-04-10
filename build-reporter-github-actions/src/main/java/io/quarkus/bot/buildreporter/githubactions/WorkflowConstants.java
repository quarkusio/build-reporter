package io.quarkus.bot.buildreporter.githubactions;

import java.nio.file.Path;

public class WorkflowConstants {

    public static final String BUILD_REPORTS_ARTIFACT_PREFIX = "build-reports-";
    public static final Path BUILD_REPORT_PATH = Path.of("target", "build-report.json");
    public static final Path GRADLE_BUILD_SCAN_URL_PATH = Path.of("target", "gradle-build-scan-url.txt");
    public static final String PULL_REQUEST_NUMBER_PREFIX = "pull-request-number-";
    public static final String REPORT_ISSUE_NUMBER_PREFIX = "report-issue-number-";

    public static final String BUILD_SUMMARY_CHECK_RUN_PREFIX = "Build summary for ";

    public static final String MESSAGE_ID_ACTIVE = "<!-- Build-Reporter/msg-id:workflow-run-status-active -->";
    public static final String MESSAGE_ID_ACTIVE_FOR_WORKFLOW = "<!-- Build-Reporter/msg-id:workflow-run-status-active:%1$s -->";
    public static final String MESSAGE_ID_HIDDEN_FOR_WORKFLOW = "<!-- Build-Reporter/msg-id:workflow-run-status-hidden:%1$s -->";
    public static final String WORKFLOW_RUN_ID_MARKER = "<!-- Build-Reporter/workflow-run-id:%1$s -->";
    public static final String BUILD_SCANS_CHECK_RUN_MARKER = "<!-- Build-Reporter/build-scans-check-run -->";
    public static final String HIDE_MESSAGE_PREFIX = """
            ---
            > :waning_crescent_moon: **_This workflow status is outdated as a new workflow run has been triggered._**
            ---

            """;

    @Deprecated(forRemoval = true, since = "3.7.0")
    public static final String OLD_MESSAGE_ID_ACTIVE = "<!-- Quarkus-GitHub-Bot/msg-id:workflow-run-status-active -->";
    @Deprecated(forRemoval = true, since = "3.7.0")
    public static final String OLD_MESSAGE_ID_ACTIVE_FOR_WORKFLOW = "<!-- Quarkus-GitHub-Bot/msg-id:workflow-run-status-active:%1$s -->";
}
