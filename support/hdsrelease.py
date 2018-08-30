# =====================================
# Copyright 2014-2018, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# This script is used to create a new tagged version of the software and to bump the 'current' version up one.
# This is essentially the 'release process'.

import os.path
import sys
import re
import xml.etree.ElementTree as etree
import hdscommon
import subprocess


DOCKERFILE = 'support/deployment/Dockerfile'


def gitaddpomanddockerfiles():
    print("will git-add pom files")
    hdscommon.gitaddpomformodule(None)

    for m in rootPomModuleNames:
        hdscommon.gitaddpomformodule(m)

    print('will git-add Dockerfile')
    hdscommon.gitaddfile(DOCKERFILE)


# ----------------
# PARSE TOP-LEVEL POM AND GET MODULE NAMES

if not os.path.isfile("pom.xml"):
    print("the 'pom.xml' file should be accessible in the present working directory")
    sys.exit(1)

rootPomTree = etree.parse("pom.xml")

if not rootPomTree:
    print("the 'pom.xml' should be accessible in the present working directory")
    sys.exit(1)

if hdscommon.pomextractartifactid(rootPomTree) != "haikudepotserver":
    print("the top level pom should have the 'haikudepotserver' artifactId")
    sys.exit(1)

rootPomModuleNames = hdscommon.scanmodules()

# ----------------
# GET VERSION

rootPomCurrentVersion = hdscommon.pomextractversion(rootPomTree)
rootPomCurrentVersionMatch = re.match("^([1-9][0-9]*\.[0-9]+\.)([1-9][0-9]*)-SNAPSHOT$", rootPomCurrentVersion)

if not rootPomCurrentVersionMatch:
    print("the current root pom version is not a valid snapshot version; " + rootPomCurrentVersion)
    sys.exit(1)

rootPomCurrentVersionPrefix = rootPomCurrentVersionMatch.group(1)
rootPomCurrentVersionSuffix = rootPomCurrentVersionMatch.group(2)

print("top-level version; " + rootPomCurrentVersion)

releaseVersion = rootPomCurrentVersionPrefix + rootPomCurrentVersionSuffix
futureVersion = rootPomCurrentVersionPrefix + str(int(rootPomCurrentVersionSuffix) + 1) + "-SNAPSHOT"

# ----------------
# CHECK CURRENT CONSISTENCY

# This will make sure that all of the modules have the same version.

print("will check version consistency")

for m in rootPomModuleNames:
    hdscommon.ensurecurrentversionconsistencyformodule(m, rootPomCurrentVersion)

# ----------------
# RESET THE VERSIONS SANS THE SNAPSHOT

hdscommon.mvnversionsset(releaseVersion)

# ----------------
# SETUP THE DOCKERFILE WITH THE RIGHT VERSIONS


def replacedockerenvs():
    parentpome = etree.parse("haikudepotserver-parent/pom.xml")
    postgresversion = hdscommon.pomextractproperty(parentpome, 'postgresql.version')
    jettyversion = hdscommon.pomextractproperty(parentpome, 'jetty.version')
    envreplacements = {
        'HDS_VERSION': releaseVersion,
        'JETTY_VERSION': jettyversion,
        'PG_VERSION': postgresversion
    }
    hdscommon.dockerreplaceenvs(DOCKERFILE, envreplacements)


replacedockerenvs()


# ----------------
# ADD POMS TO GIT, COMMIT AND TAG

gitaddpomanddockerfiles()
hdscommon.gitcommitversion(releaseVersion)

if 0 == subprocess.call(["git", "tag", "-a", "haikudepotserver-" + releaseVersion, "-m", "haikudepotserver-" + releaseVersion]):
    print("git tagged 'haikudepotserver-" + releaseVersion + "'")
else:
    print("failed to git tag")
    sys.exit(1)

# ----------------
# UPDATE ALL POMS TO NEW SNAPSHOT

hdscommon.mvnversionsset(futureVersion)

# ----------------
# RESET THE DOCKERFILE TO PLACEBO VALUE

hdscommon.dockerreplaceenvs(DOCKERFILE, {
    'HDS_VERSION': '???',
    'JETTY_VERSION': '???',
    'PG_VERSION': '???'
})

# ----------------
# ADD POMS TO GIT, COMMIT

gitaddpomanddockerfiles()
hdscommon.gitcommitversion(futureVersion)

# ----------------
# REMINDER AT THE END TO PUSH

print("---------------")
print("to complete the release; git push && git push --tags")