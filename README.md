# Build Reporter

[![Version](https://img.shields.io/maven-central/v/io.quarkus.bot/build-reporter-parent?logo=apache-maven&style=for-the-badge)](https://search.maven.org/artifact/io.quarkus.bot/build-reporter-parent)

## About

This repository contains the build reporter tools we use to collect and generate build reports from GitHub Actions run.
It is used by the Quarkus project to generate build reports for pull requests.

It is composed of:

- A Maven extension that collect the build status of each module (optional)
- The build reporter itself, which can be used in a Quarkus GitHub App-based project

This project is for instance consumed by the [Quarkus GitHub bot](https://github.com/quarkusio/quarkus-github-bot).

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
                <version>VERSION</version>
            </extension>
        </extensions>
    </build>

    <!-- ... -->
```

## Releasing

```
mvn release:prepare
mvn release:perform -Darguments=-DperformRelease -Prelease
```
