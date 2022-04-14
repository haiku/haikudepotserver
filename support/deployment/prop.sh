#!/bin/bash
grep $1 /opt/haikudepotserver/build.properties | sed -e "s/^$1=\(..*\)$/\1/"
