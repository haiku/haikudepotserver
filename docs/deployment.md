# Deployment

This section outlines the approximate steps to deploy the web application-server.  The application-server Maven build process produces a stand-alone self-executing `.jar` file using [SpringBoot](https://spring.io/projects/spring-boot).

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

The tags have the format;

- `haikudepotserver-1.0.10`
- `haikudepotserver-1.0.113`

Upon push, the GitHub Actions defined in `.github/workflows/` will trigger a build, package and release process for HDS and HDS-GS containers. The built containers...

- `haiku/haikudepotserver-server-graphics`
- `haiku/haikudepotserver`

...are pushed to the Haiku container registry `ghcr.io` and are tagged with the version such as `1.0.168`.

## Orchestrating a Release Deployment

The release deployment should be arranged with the Haiku sys-admin team. This can be achieved by opening a ticket on the [Haiku Infrastructure](https://github.com/haiku/infrastructure/issues) project. There is a template for this.