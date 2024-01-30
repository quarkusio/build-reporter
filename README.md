# Build Reporter

[![Version](https://img.shields.io/maven-central/v/io.quarkus.bot/build-reporter-parent?logo=apache-maven&style=for-the-badge)](https://central.sonatype.com/artifact/io.quarkus.bot/build-reporter-parent)

## About

This repository contains the build reporter tools we use to collect and generate build reports from GitHub Actions run.
It is used by the Quarkus project to generate build reports for pull requests.

It is composed of:

- A Maven extension that collect the build status of each module (optional)
- The build reporter itself, which can be used in a Quarkus GitHub App-based project

This project is for instance consumed by the [Quarkus GitHub bot](https://github.com/quarkusio/quarkus-github-bot) and the [action-build-reporter](https://github.com/quarkusio/action-build-reporter).

## Installing the Maven extension

The Maven extension is optional.
If not present in the build, the build reports will solely be based on the Maven Surefire reports.
Thus, for instance, if the extension is not present, modules with compilation errors won't be included in the report.

It is recommended to use this extension when using the build reporter.

Adding the extension is easy:

```xml
    <!-- ... -->

    <build>
        <!-- ... -->
        <extensions>
            <extension>
                <groupId>io.quarkus.bot</groupId>
                <artifactId>build-reporter-maven-extension</artifactId>
                <version>${build-reporter.version}</version>
            </extension>
        </extensions>
    </build>

    <!-- ... -->
```

## Including the build reporter in a GitHub App

First, add the dependency below to your `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkus.bot</groupId>
    <artifactId>build-reporter-github-actions</artifactId>
    <version>${build-reporter.version}</version>
</dependency>
```

Then you need to listen on the appropriate events and call the build reporter:

```java
public class AnalyzeWorkflowRunResults {

    @Inject
    BuildReporterEventHandler buildReporterEventHandler;

    @Inject
    QuarkusGitHubBotConfig quarkusBotConfig;

    void analyzeWorkflowResults(@WorkflowRun.Completed @WorkflowRun.Requested GHEventPayload.WorkflowRun workflowRunPayload,
            @ConfigFile("quarkus-github-bot.yml") QuarkusGitHubBotConfigFile quarkusBotConfigFile,
            GitHub gitHub, DynamicGraphQLClient gitHubGraphQLClient) throws IOException {
        BuildReporterConfig buildReporterConfig = BuildReporterConfig.builder()
                .dryRun(quarkusBotConfig.isDryRun())
                .monitoredWorkflows(quarkusBotConfigFile.workflowRunAnalysis.workflows)
                .build();

        buildReporterEventHandler.handle(workflowRunPayload, buildReporterConfig, gitHub, gitHubGraphQLClient);
    }
}
```

Note that, in this example, we get the dry run configuration from the GitHub App configuration
and the monitored workflows from the configuration file hosted in the repository.

It is important to listen to both the `@WorkflowRun.Completed` and the `@WorkflowRun.Requested` events.

## Releasing

```
mvn release:prepare
mvn release:perform -Darguments=-DperformRelease -Prelease
```
