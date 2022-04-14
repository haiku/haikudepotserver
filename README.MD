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

## Deployments

| Environment | Modern Web | Simple Web |
| --- | --- | --- |
| Production | https://depot.haiku-os.org | https://depot.haiku-os.org/__multipage |

## Mailing List

A [mailing list](http://www.freelists.org/list/haiku-depot-web) exists to discuss and coordinate the project.

## Documentation and Development

Assuming the application server is deployed, you should be able to see relatively recent documentation
[here](http://depot.haiku-os.org/__docs/index.html).

## Other Helpful Resources

* [Haiku homepage](http://www.haiku-os.org)
* [HaikuPorts homepage](https://github.com/haikuports/haikuports/wiki) (for external packages)
* Haiku [package management documentation](https://github.com/haiku/haiku/blob/master/docs/develop/packages)
