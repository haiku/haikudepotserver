# =====================================
# Copyright 2014-2022, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# This script is used to create a new tagged version of the software and to bump the 'current' version up one.
# This is essentially the 'release process'.

import os.path
import re
import subprocess
import xml.etree.ElementTree as etree

import hdscommon


def gitaddpomfiles():
    print("will git-add pom files")
    hdscommon.gitaddpomformodule(None)
    for name in rootPomModuleNames:
        hdscommon.gitaddpomformodule(name)


# ----------------
# PARSE TOP-LEVEL POM AND GET MODULE NAMES

if not os.path.isfile("pom.xml"):
    raise RuntimeError("the 'pom.xml' file should be accessible in the present working directory")

rootPomTree = etree.parse("pom.xml")

if not rootPomTree:
    raise RuntimeError("the 'pom.xml' should be accessible in the present working directory")

if hdscommon.pomextractartifactid(rootPomTree.getroot()) != "haikudepotserver":
    raise RuntimeError("the top level pom should have the 'haikudepotserver' artifactId")

rootPomModuleNames = hdscommon.scanmodules()

# ----------------
# GET VERSION

rootPomCurrentVersion = hdscommon.pomextractversion(rootPomTree.getroot())
rootPomCurrentVersionMatch = re.match("^([1-9][0-9]*\\.[0-9]+\\.)([1-9][0-9]*)-SNAPSHOT$", rootPomCurrentVersion)

if not rootPomCurrentVersionMatch:
    raise RuntimeError("the current root pom version is not a valid snapshot version; " + rootPomCurrentVersion)

rootPomCurrentVersionPrefix = rootPomCurrentVersionMatch.group(1)
rootPomCurrentVersionSuffix = rootPomCurrentVersionMatch.group(2)

print("top-level version; " + rootPomCurrentVersion)

releaseVersion = rootPomCurrentVersionPrefix + rootPomCurrentVersionSuffix
futureVersion = rootPomCurrentVersionPrefix + str(int(rootPomCurrentVersionSuffix) + 1) + "-SNAPSHOT"

# ----------------
# CHECK CURRENT CONSISTENCY

# This will make sure that all the modules have the same version.

print("will check version consistency")

for m in rootPomModuleNames:
    hdscommon.ensurecurrentversionconsistencyformodule(m, rootPomCurrentVersion)

# ----------------
# RESET THE VERSIONS SANS THE SNAPSHOT

hdscommon.mvnversionsset(releaseVersion)

# ----------------
# ADD POMS TO GIT, COMMIT AND TAG

gitaddpomfiles()
hdscommon.gitcommitversion(releaseVersion)

if 0 == subprocess.call(["git", "tag", "-a", "haikudepotserver-" + releaseVersion, "-m", "haikudepotserver-" + releaseVersion]):
    print("git tagged 'haikudepotserver-" + releaseVersion + "'")
else:
    raise RuntimeError("failed to git tag")

# ----------------
# UPDATE ALL POMS TO NEW SNAPSHOT

hdscommon.mvnversionsset(futureVersion)

# ----------------
# ADD POMS TO GIT, COMMIT

gitaddpomfiles()
hdscommon.gitcommitversion(futureVersion)

# ----------------
# REMINDER AT THE END TO PUSH

print("---------------")
print("to complete the release; git push && git push --tags")
