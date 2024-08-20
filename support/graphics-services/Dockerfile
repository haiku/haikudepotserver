# =====================================
# Copyright 2022-2024, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

FROM debian:12.5-slim as base

ENV HAIKU_HASH=d75f6109c213b70895a2bf0eed412cdd7a68f990
ENV BUILDTOOLS_HASH=fef550b1fc8de8810ff74f2edc79a43bc8ec660b

RUN apt-get update && \
    apt-get -y install git nasm bc autoconf automake texinfo flex bison gawk build-essential unzip wget \
        zip less zlib1g-dev libzstd-dev xorriso libtool gcc-multilib python3 libpng16-16

ADD "https://github.com/haiku/buildtools/archive/${BUILDTOOLS_HASH}.tar.gz" /buildtools.tgz
ADD "https://github.com/haiku/haiku/archive/${HAIKU_HASH}.tar.gz" /haiku.tgz

RUN tar -xzf ./buildtools.tgz
RUN tar -xzf ./haiku.tgz

RUN mv buildtools-${BUILDTOOLS_HASH} buildtools
RUN mv haiku-${HAIKU_HASH} haiku

RUN mkdir generated.x86_64
WORKDIR "generated.x86_64"
RUN ../haiku/configure --cross-tools-source ../buildtools --build-cross-tools x86_64
RUN jam -q "<build>hvif2png"