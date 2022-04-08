# =====================================
# Copyright 2014-2022, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# This script contains material that can be shared between other python scripts related to the release-management
# of the HDS software.

# =====================================

import os
import os.path
import sys
import re
import xml.etree.ElementTree as etree
import subprocess


# =====================================
# TYPES

class GroupArtifactVersion:

    _groupId = None
    _artifactId = None
    _version = None

    def __init__(self, groupid, artifactid, version):
        if not groupid:
            raise RuntimeError('expected `group` to be supplied')
        if not artifactid:
            raise RuntimeError('expected `artifact` to be supplied')
        if not version:
            raise RuntimeError('expected `version` to be supplied')
        self._groupId = groupid
        self._artifactId = artifactid
        self._version = version

    def groupid(self):
        return self._groupId

    def artifactid(self):
        return self._artifactId

    def version(self):
        return self._version


# =====================================
# DICT HANDLING


def uniondicts(d1, d2):
    d = dict(d1)
    d.update(d2)
    return d


# This function will return the list of modules' names based on scanning the file
# system rather than looking at the top level POM.  It does this because different
# profiles may be employed to avoid, for example, building an RPM on a non-linux
# host.

def scanmodules():
    result = []

    for f in os.listdir('.'):
        if os.path.isdir(f) and not f.startswith(".") and os.path.isfile(f + "/pom.xml"):
            result.append(f)

    return result


# =====================================
# DOM / ELEMENT HANDLING

# The tag has the form {namespace}tag and this method will extract the namespace part
# of that.


def extractdefaultnamespace(tag):
    nsmatch = re.match("^{(.+)}.+$", tag)
    if not nsmatch:
        raise RuntimeError("invalid tag missing namespace (open) ; " + tag)
    return nsmatch.group(1)


def pomtoplevelelement(el, taglocalname):
    namespace = extractdefaultnamespace(el.tag)
    resulte = el.find(("{0}"+taglocalname).format('{'+namespace+'}'))
    if None is resulte:
        raise RuntimeError("unable to find the top level element [" + taglocalname + "]")
    return resulte


# =====================================
# DOM / ELEMENT HANDLING FOR POM

def pomextractartifactid(tree):
    return pomextractgav(tree).artifactid()


def pomextractversion(tree):
    return pomextractgav(tree).version()


def pomextractgav(el):
    return GroupArtifactVersion(
        pomtoplevelelement(el, "groupId").text,
        pomtoplevelelement(el, "artifactId").text,
        pomtoplevelelement(el, "version").text)


def pomextractproperty(tree, name):
    roote = tree.getroot()
    namespace = extractdefaultnamespace(roote.tag)
    propertye = roote.find(('./{0}properties/{0}' + name).format('{'+namespace+'}'))

    if propertye is None:
        raise Exception('unable to find the property [' + name + ']')

    property = propertye.text

    if not property or 0 == len(property):
        raise Exception('no stored value for property [' + name + ']')

    return property


# This function will walk through the POM's plugins and extract their
# GAV values.

def pomextractplugingavs(tree):
    roote = tree.getroot()
    namespace = extractdefaultnamespace(roote.tag)
    plugines = roote.findall('./{0}build/{0}plugins/{0}plugin'.format('{'+namespace+'}'))
    return map(lambda el: pomextractgav(el), plugines)


def pomextractdependencygavs(tree):
    roote = tree.getroot()
    namespace = extractdefaultnamespace(roote.tag)
    dependencyes = roote.findall('./{0}dependencies/{0}dependency'.format('{'+namespace+'}'))
    return map(lambda el: pomextractgav(el), dependencyes)


# =====================================
# LOGIC CHUNKS

def ensurecurrentversionconsistencyformodule(modulename, expectedversion):
    modulepomtree = etree.parse(modulename + "/pom.xml")

    if not modulepomtree:
        raise RuntimeError("the 'pom.xml' for module "+modulename+" should be accessible")

    parente = pomtoplevelelement(modulepomtree.getroot(), "parent")
    namespace = extractdefaultnamespace(parente.tag)
    versione = parente.find("{"+namespace+"}version")

    if versione is None:
        print("the parent element of module " + modulename + " has no version specified")
        sys.exit(1)
    else:
        actualversion = versione.text

        if actualversion != expectedversion:
            print("the version of the module "+modulename+" is inconsistent with the expected; " + actualversion)
            sys.exit(1)
        else:
            print(modulename + ": " + actualversion + " (ok)")


# =====================================
# GIT


def gitcommitversion(version):
    if 0 == subprocess.call(["git", "commit", "-m", "version " + version]):
        print("git committed 'version " + version + "'")
    else:
        raise RuntimeError("failed to git commit [" + version + "]")


def gitaddfile(file):
    if 0 == subprocess.call(["git", "add", file]):
        print(file + " : (added)")
    else:
        raise RuntimeError("failed to git-add [" + file + "]")


def gitaddpomformodule(modulename):
    if modulename is None:
        gitaddfile("pom.xml")
    else:
        gitaddfile(modulename + "/pom.xml")


# =====================================
# MAVEN

def mvnversionsset(version):
    if 0 == subprocess.call(["mvn", "-q", "versions:set", "-DnewVersion=" + version, "-DgenerateBackupPoms=false"]):
        print("versions:set to " + version)
    else:
        raise RuntimeError("failed to set maven versions to [" + version + "]")
