# build-reporting-maven-extension

[![Version](https://img.shields.io/maven-central/v/io.quarkus.bot/build-reporting-maven-extension?logo=apache-maven&style=for-the-badge)](https://search.maven.org/artifact/io.quarkus.bot/build-reporting-maven-extension)

## About

A Maven extension collecting information about the build for consumption by the [Quarkus GitHub bot](https://github.com/quarkusio/quarkus-github-bot).

## Installing

To install the extension in a project, just create a `.mvn/extensions.xml` file at the root of the Maven project with the following content:

```xml
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
  <extension>
    <groupId>io.quarkus.bot</groupId>
    <artifactId>build-reporting-maven-extension</artifactId>
    <version>VERSION</version>
  </extension>
</extensions>
```

Adjust the `VERSION` to use the latest version.

## Releasing

```
mvn release:prepare
mvn release:perform -Darguments=-DperformRelease -Prelease
```
