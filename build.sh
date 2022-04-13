#!/bin/bash

# This can be used to build the HaikuDepotServer.  It should be
# run from the top level of the HDS project.

git diff-index --quiet HEAD

if [[ $? != 0 ]]; then
  echo "! it seems that there are uncommitted changes - commit the changes before proceeding"
  exit 1
fi

HDS_TAG="$(git describe --tags)"
HDS_TAG_REGEX='^haikudepotserver-([0-9]+\.[0-9]+\.[0-9]+)$'

if [[ $HDS_TAG =~ $HDS_TAG_REGEX ]]; then
  HDS_VERSION="${BASH_REMATCH[1]}"
else
  echo "! tag \"${HDS_TAG}\" would indicate the repo is not on a tag"
  exit 1
fi

read -r -p "Press 'y' to docker build HaikuDepotServer ${HDS_VERSION}: " choice
if ! [[ "$choice" = 'y' ]]; then
  exit 1
fi

docker build --tag "docker.io/haiku/haikudepotserver:${HDS_VERSION}" .

if [[ $? != 0 ]]; then
  echo "failed to create docker image for HaikuDepotServer"
  exit 1
fi

read -r -p "Press 'y' to push the docker image HaikuDepotServer ${HDS_VERSION}: " choice
if ! [[ "$choice" = 'y' ]]; then
  exit 1
fi

docker push "docker.io/haiku/haikudepotserver:${HDS_VERSION}"

if [[ $? != 0 ]]; then
  echo "failed to push the docker image for HaikuDepotServer"
  exit 1
fi
