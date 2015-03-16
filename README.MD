# Haiku Depot Server

## Purpose

This web application is an online tool for working with the software packages of the [Haiku Operating System](http://www.haiku-os.org/).  The web application aims to provide the following functionality;

* An internet-accessible catalog of the packages.
* A repository of _additional data_ related to packages that is not included in the standard package format - such as;
  * Screenshots
  * Iconography
  * Localizations
  * User-feedback
* A user interface to manipulate the _additional data_.
* A mechanism to vend this _additional data_ to HaikuDepot, a desktop application for managing packages.

## Deployments

| Environment | URL |
| --- | --- |
| Production | https://depot.haiku-os.org/ |
| Test | https://depot-test.haiku-os.org/ |

## Mailing List

A [mailing list](http://www.freelists.org/list/haiku-depot-web) exists to discuss and coordinate the project.

## Documentation and Development

The core part of the project is a java servlet application and is built using maven.  A module of the project called "haikudepotserver-docs" contains a LaTeX document that outlines how to setup a development environment for the application.  To build the documentation, you should;

1. Install a modern LaTeX distribution
2. Install Java (1.7 or better)
3. Install Maven
4. Clone the source tree
5. Set the present working directory to "haikudepotserver-docs" in the source tree
6. Execute "mvn latex:latex"

A PDF document should be generated at "target/docs.pdf".

## Other Helpful Resources

* [Haiku OS home page](http://www.haiku-os.org/)
* Home page for [HaikuPorts](http://bb.haikuports.org/haikuports/wiki/Home) - for external packages
* HaikuOS [package management](http://dev.haiku-os.org/wiki/PackageManagement) documentation