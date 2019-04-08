# Creating a new container

## Requirements

* Release must be tagged + compiled at https://github.com/haiku/haikudepotserver/releases
* Check out tagged release via "git checkout haikudepotserver-VERSION"

## Build and push container

* cd haikudepotserver/support/deployment
* VERSION="haikudepotserver-VERSION" make
* VERSION="haikudepotserver-VERSION" make push

## Deploy container

Follow the standard deployment steps. (docker-compose pull/stop/rm/(up -d) haikudepotserver
