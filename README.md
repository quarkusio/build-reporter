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

That is all for the GitHub App part.
You also need to upload the reports as artifacts of the workflow run in your GitHub Action workflow file.

For instance, to upload the build reports of a job named `JVM Tests - JDK ${{matrix.java.name}}`, you would use:

```yaml
      - name: Upload build reports
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: "build-reports-JVM Tests - JDK ${{matrix.java.name}}"
          path: |
            **/target/*-reports/TEST-*.xml
            target/build-report.json
            LICENSE
          retention-days: 7
```

Note that the name of the artifact is important:

- It must start with `build-reports-`
- Then include the exact job name - the job name must be used, not the job id (the job id is not available in the payloads so we cannot map reports to jobs with the job id)

The artifact may never be empty so include a file that will always be present (e.g. the `LICENSE` file).

In the unfortunate case when the paths of the files you want to upload contain characters not supported by `upload-artifact`,
you can create an archive and push the archive instead:

```yaml
      - name: Prepare build reports archive
        if: always()
        run: |
          7z a -tzip build-reports.zip -r \
              '**/target/*-reports/TEST-*.xml' \
              'target/build-report.json' \
              'target/gradle-build-scan-url.txt' \
              LICENSE.txt
      - name: Upload build reports
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: "build-reports-JVM Tests - JDK ${{matrix.java.name}}"
          path: |
            build-reports.zip
          retention-days: 7
```

The Build Reporter will automatically unarchive the reports before analyzing them.

## Using the GitHub Action

You might not have the ability to set up a GitHub App,
or you might want to also provide build reports on forks.

For these use cases, we also offer a GitHub Action that can be used directly in your workflows.

See [Action Build Reporter](https://github.com/quarkusio/action-build-reporter/) for more information.

## Release

To release a new version, follow these steps:

https://github.com/smallrye/smallrye/wiki/Release-Process#releasing

The staging repository is automatically closed. The sync with Maven Central should take ~30 minutes.
