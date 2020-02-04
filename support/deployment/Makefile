VERSION ?= `git describe --tags | cut -d- -f2`

default:
	docker build --no-cache --tag docker.io/haiku/haikudepotserver:$(VERSION) .
push:
	docker push docker.io/haiku/haikudepotserver:$(VERSION)
