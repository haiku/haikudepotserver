# Deployment

This section outlines the approximate steps to deploy the web application-server.  The application-server
Maven build process produces a stand-alone self-executing `.jar` file that runs with [SpringBoot](https://spring.io/projects/spring-boot) and [Tomcat](https://tomcat.apache.org/).

## Versions

A maven project has a version which is either a final version such as `2.3.1` or is a snapshot version such as `2.3.2-SNAPSHOT`. The snapshot version is the work-in-progress for the next release. Once the snapshot is ready, a release is made wherein that source-code is fixed to the version number without the trailing `-SNAPSHOT` and then the snapshot version is incremented. The release yields a tag in the source code management system (git) in order to be able to reproduce the source-code for that release against a release version. The tag will have a form such as `haikudepotserver-2.3.2`.

## Undertaking a Release

A Python script is provided to perform a release from the HEAD of the master branch. In the following example, the following assumed (fictitious) version numbers are used for demonstration purposes;

|Version|Purpose|
|---|---|
|`1.2.3-SNAPSHOT`|The version prior to release|
|`1.2.3`|The version of the release|
|`1.2.4-SNAPSHOT`|The version after the release|

The script performs the following steps;

- Check the current version is of the form `1.2.3-SNAPSHOT`
- Check all modules have the same version
- Update all modules to the version `1.2.3`
- Git commit
- Git tag
- Update all modules to the version `1.2.4-SNAPSHOT`
- Git commit

Prior to a release;

- All changes should be committed
- A `verify` goal is run to ensure that automated testing passes
- A `clean` goal is run to clean out any build files

The following series of commands would orchestrate the release process;

```
python3 support/hdsrelease.py
...
git push
git push --tags
```

## Obtaining Source for a Release and then Building Docker Image

In order to obtain source code state for a specific release, first `git fetch -all` any pending changes from the remote repository and then checkout the source at the particular tag; `git checkout tags/haikudepotserver-2.3.2`.

From there it will be possible to create a build product for that particular release by initiating the build process.

```
./build.sh
```

This will trigger a multi-stage Docker build described in `Dockerfile`.  The first stage will pull down all of the dependencies of the build and will then build the build products.  The second stage will assemble the immutable Docker container that will run the HDS system.

## Orchestrating a Release Deployment

The release deployment should be arranged with the Haiku sys-admin team. This can be achieved by opening a ticket on the [Haiku Infrastructure](https://github.com/haiku/infrastructure/issues) project. There is a template for this.

## hvif2png

See [here](hvif2png.md) for details on this tool. There is a build product tar-ball loaded into the HDS repository to be included in the build process in the `support` directory.