package io.quarkus.bot.buildreporter.githubactions;

public final class WorkflowUtils {

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

    private WorkflowUtils() {
    }
}
