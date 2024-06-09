# Haiku Depot Server

## Purpose

This web application is an online tool for working with the software packages of the [Haiku operating system](https://www.haiku-os.org).  The web application aims to provide the following functionality:

* An internet-accessible catalog of the packages
* A repository of _additional data_ related to packages that is not included in the standard package format - such as;
  * Screenshots
  * Iconography
  * Localizations
  * User-feedback
* A user interface to manipulate the _additional data_.
* A mechanism to vend this _additional data_ to HaikuDepot, a desktop application for managing packages.

## Useful Links and resources

- [Haiku Depot Server web interface](https://depot.haiku-os.org)
- [Haiku Depot Server simplified web interface](https://depot.haiku-os.org/__multipage)
- [Haiku Depot Server Source code + issue tracking](https://github.com/haiku/haikudepotserver)
- [Haiku Depot Server mailing list](http://www.freelists.org/list/haiku-depot-web) to discuss and coordinate the project.
- [Haiku Depot Server license](LICENSE.TXT)
- [Haiku operating system project homepage](http://www.haiku-os.org)
- [HaikuPorts project homepage](https://github.com/haikuports/haikuports/wiki) (for external packages)
- [Haiku package management documentation](https://github.com/haiku/haiku/blob/master/docs/develop/packages)

## Documentation

### General System and Architecture

- [Architecture](docs/architecture.md)
- [API](docs/api.md)
- [Security](docs/security.md)
- [Data Model](docs/data-model.md)
- [Integration](docs/integration.md)
- [Repositories](docs/repositories.md)
- [User Ratings](docs/user-rating.md)
- [Data Localization](docs/data-localization.md)
- [HDS Application Localization](docs/application-localization.md)

### Development and Deployment

- [hvif2png](docs/hvif2png.md)
- [Local Development](docs/local-development.md)
- [Kubernetes Development](docs/k8s-development.md)
- [Deployment](docs/deployment.md)
