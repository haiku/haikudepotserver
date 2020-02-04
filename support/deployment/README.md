# Creating a new container

## Requirements

* Release must be tagged + compiled at https://github.com/haiku/haikudepotserver/releases

## Build and push container

* Check out tagged release via "git checkout haikudepotserver-VERSION"
* cd haikudepotserver/support/deployment
* make
* make push

## Deploy container

Follow the standard deployment steps.
