# docker-client

A Docker HTTP client written in Groovy

[![Remote API Coverage Status (46/48 endpoints)](http://progressed.io/bar/96?title=api%20coverage)](https://github.com/gesellix/docker-client/blob/master/roadmap.md)
[![Build Status](https://travis-ci.org/gesellix/docker-client.svg)](https://travis-ci.org/gesellix/docker-client)
[![Latest version](https://api.bintray.com/packages/gesellix/docker-utils/docker-client/images/download.svg) ](https://bintray.com/gesellix/docker-utils/docker-client/_latestVersion)

## Plain Usage

For use in Gradle, add the bintray repository first:

```
repositories {
  maven { url 'http://dl.bintray.com/gesellix/docker-utils' }
}
```

Then, you need to add the dependency, but please ensure to use the [latest version](https://bintray.com/gesellix/docker-utils/docker-client/_latestVersion):

```
dependencies {
  compile 'de.gesellix:docker-client:2015-10-07T21-43-15'
}
```

The tests in `DockerClientImplSpec` and `DockerClientImplIntegrationSpec` should give you an idea how to use the docker-client.

## Usage with Gradle-Docker-Plugin

My personal focus implementing this Docker client was to leverage the Docker remote API in our Gradle scripts.
A convenient integration in Gradle is possible by using the [Gradle-Docker-Plugin](https://github.com/gesellix/gradle-docker-plugin),
which will be developed along with the Docker client library.
