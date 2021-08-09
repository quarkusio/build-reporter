# build-report-maven-extension

## About

A Maven extension collecting information about the build for consumption by the [Quarkus GitHub bot](https://github.com/quarkusio/quarkus-github-bot).

## Releasing

```
mvn release:prepare
mvn release:perform -Darguments=-DperformRelease -Prelease
```
