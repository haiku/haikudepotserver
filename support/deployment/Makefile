VERSION ?= latest

default:
	docker build --no-cache --tag docker.io/haiku/haikudepotserver:$(VERSION) .
push:
	docker push docker.io/haiku/haikudepotserver:$(VERSION)
