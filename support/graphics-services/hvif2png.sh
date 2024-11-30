#!/bin/sh
HVIF2PNG_HOME="$(dirname $0)/.."
LD_LIBRARY_PATH="${HVIF2PNG_HOME}/lib:${LD_LIBRARY_PATH}"
export LD_LIBRARY_PATH
"${HVIF2PNG_HOME}/bin/hvif2png" "$@"