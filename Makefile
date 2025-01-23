# =====================================
# Copyright 2024, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# This Makefile will produce the Dockerfiles required to build the HDS
# application container images.

INPUTS_SERVER_GRAPHICS := \
support/dockerfile/Dockerfile_part-base \
support/dockerfile/Dockerfile_part-hvif2png-build \
support/dockerfile/Dockerfile_part-oxipng-build \
support/dockerfile/Dockerfile_part-java-build \
support/dockerfile/Dockerfile_part-server-graphics

INPUTS_WEBAPP := \
support/dockerfile/Dockerfile_part-base \
support/dockerfile/Dockerfile_part-java-build \
support/dockerfile/Dockerfile_part-webapp

all: dockerfiles

Dockerfile_webapp: $(INPUTS_WEBAPP)
	rm -f $@
	touch $@
	$(foreach f,$(INPUTS_WEBAPP),sed '/^#/d' $(f) >> $@$(NL);)

Dockerfile_server_graphics: $(INPUTS_SERVER_GRAPHICS)
	rm -f $@
	touch $@
	$(foreach f,$(INPUTS_SERVER_GRAPHICS),sed '/^#/d' $(f) >> $@$(NL);)

dockerfiles: Dockerfile_webapp Dockerfile_server_graphics

clean:
	rm -f Dockerfile_server_graphics
	rm -f Dockerfile_webapp
