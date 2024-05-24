#!/bin/bash

# =====================================
# Copyright 2024, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# See the `README.md` file for instructions on use.

set -eo pipefail

MVNW=./mvnw

CLIENT_TOOLS_HOME="$(dirname ${BASH_SOURCE[0]})"
PY_API_SRC_ROOT=haikudepotserver-api2/target/generated-sources/openapi/python-client

APIS=(
  authorization
  authorization-job
  captcha
  job
  metrics-job
  miscellaneous
  miscellaneous-job
  pkg
  pkg-job
  repository
  repository-job
  user
  user-rating
  user-rating-job
)

main() {

  local api_underscore

  if [ ! -f "${MVNW}" ]; then
    echo "! expected to find the file 'mvnw' in the current directory; check you are running this with the pwd set to the top directory of the project"
    exit 1
  fi

  "${MVNW}" -f haikudepotserver-api2 -Ppython clean generate-sources

  for api in "${APIS[@]}"; do
    api_underscore="$(echo ${api} | tr '-' '_')"
    echo "copying api [${api}]..."
    cp -r "${PY_API_SRC_ROOT}/${api}/hds_${api_underscore}_client" "${CLIENT_TOOLS_HOME}"
  done

}

main "$@"