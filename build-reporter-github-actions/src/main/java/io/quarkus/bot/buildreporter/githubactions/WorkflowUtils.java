package io.quarkus.bot.buildreporter.githubactions;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.github.GHArtifact;

public final class WorkflowUtils {

    private static final Pattern BUILD_REPORTS_ARTIFACT_NAME_PATTERN = Pattern
            .compile(Pattern.quote(WorkflowConstants.BUILD_REPORTS_ARTIFACT_PREFIX) + "(?:([0-9]+)-)?(.*)");

    public static String getFilePath(String moduleName, String fullClassName) {
        String classPath = fullClassName.replace(".", "/");
        int dollarIndex = classPath.indexOf('$');
        if (dollarIndex > 0) {
            classPath = classPath.substring(0, dollarIndex);
        }
        return moduleName + "/src/test/java/" + classPath + ".java";
    }

    public static String getActiveStatusCommentMarker(String workflowName) {
        return String.format(WorkflowConstants.MESSAGE_ID_ACTIVE_FOR_WORKFLOW, workflowName);
    }

    public static String getHiddenStatusCommentMarker(String workflowName) {
        return String.format(WorkflowConstants.MESSAGE_ID_HIDDEN_FOR_WORKFLOW, workflowName);
    }

    @Deprecated(forRemoval = true, since = "3.7.0")
    public static String getOldActiveStatusCommentMarker(String workflowName) {
        return String.format(WorkflowConstants.OLD_MESSAGE_ID_ACTIVE_FOR_WORKFLOW, workflowName);
    }

    public static Map<String, GHArtifact> getBuildReportsArtifacts(List<GHArtifact> artifacts, long runAttempt) {
        Map<String, GHArtifact> mappedArtifacts = new TreeMap<>();

        for (GHArtifact artifact : artifacts) {
            Matcher matcher = BUILD_REPORTS_ARTIFACT_NAME_PATTERN.matcher(artifact.getName());

            if (!matcher.matches()) {
                continue;
            }

            String artifactRunAttempt = matcher.group(1);
            if (artifactRunAttempt != null) {
                // the file is following the new pattern including the run attempt
                if (runAttempt == Long.parseLong(artifactRunAttempt)) {
                    mappedArtifacts.put(matcher.group(2), artifact);
                }
            } else {
                mappedArtifacts.put(matcher.group(2), artifact);
            }
        }

        return mappedArtifacts;
    }

    public static boolean matchesNewBuildReportsArtifactNamePattern(String artifactName) {
        Matcher matcher = BUILD_REPORTS_ARTIFACT_NAME_PATTERN.matcher(artifactName);

        if (matcher.matches() && matcher.group(1) != null) {
            return true;
        }

        return false;
    }

    private WorkflowUtils() {
    }
}
