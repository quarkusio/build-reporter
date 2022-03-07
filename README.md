# build-reporting-maven-extension

[![Version](https://img.shields.io/maven-central/v/io.quarkus.bot/build-reporting-maven-extension?logo=apache-maven&style=for-the-badge)](https://search.maven.org/artifact/io.quarkus.bot/build-reporting-maven-extension)

## About

A Maven extension collecting information about the build for consumption by the [Quarkus GitHub bot](https://github.com/quarkusio/quarkus-github-bot).

## Installing

To install the extension in a project, register the extension in the root `pom.xml` of your project:

```xml
<!-- ... -->
    <build>
        <!-- ... -->
        <extensions>
            <extension>
                <groupId>io.quarkus.bot</groupId>
                <artifactId>build-reporting-maven-extension</artifactId>
                <version>VERSION</version>
            </extension>
        </extensions>
    </build>
<!-- ... -->

```

Adjust the `VERSION` to use the latest version.

## Releasing

```
mvn release:prepare
mvn release:perform -Darguments=-DperformRelease -Prelease
```
