
# =====================================
# Copyright 2014-2018, Andrew Lindesay
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
    if "{" != tag[0]:
        print("invalid tag missing namespace (open) ; " + tag)
        sys.exit(1)

    closebraceindex = tag.find("}")

    if -1 == closebraceindex:
        print("invalid tag missing namespace (close) ; " + tag)
        sys.exit(1)

    return tag[1:closebraceindex]


def pomtoplevelelement(tree, taglocalname):
    roote = tree.getroot()
    namespace = extractdefaultnamespace(roote.tag)

    el = roote.find("{"+namespace+"}"+taglocalname)

    if el is None:
        print("unable to find the "+taglocalname+" element")
        sys.exit(1)

    return el


# =====================================
# DOM / ELEMENT HANDLING FOR POM

def pomextractartifactid(tree):
    return pomtoplevelelement(tree, "artifactId").text


def pomextractversion(tree):
    return pomtoplevelelement(tree, "version").text


def pomextractproperty(tree, name):
    propertiese = pomtoplevelelement(tree, "properties")
    namespace = extractdefaultnamespace(propertiese.tag)
    propertye = propertiese.find("{"+namespace+"}" + name)

    if propertye is None:
        raise Exception('unable to find the property [' + name + ']')

    print(propertye)

    property = propertye.text

    if not property or 0 == len(property):
        raise Exception('no stored value for property [' + name + ']')

    return property


# =====================================
# LOGIC CHUNKS

def ensurecurrentversionconsistencyformodule(modulename, expectedversion):
    modulepomtree = etree.parse(modulename + "/pom.xml")

    if not modulepomtree:
        print("the 'pom.xml' for module "+modulename+" should be accessible")
        sys.exit(1)

    parente = pomtoplevelelement(modulepomtree, "parent")
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


# =====================================
# DOCKERFILE


def dockerreplaceenvs(filename, replacements):
    with open(filename, "r") as dockerfile:
        dockerlines = dockerfile.readlines()

    def maybereplaceenv(l):
        envmatch = re.match("^ENV ([A-Z0-9_]+) \".+\"$", l)

        if envmatch:
            envname = envmatch.group(1)
            replacementvalue = replacements.get(envname)

            if replacementvalue:
                return 'ENV ' + envname + ' "' + replacementvalue + '"\n'

        return l

    dockerlines = list(map(maybereplaceenv, dockerlines))

    with open(filename, "w") as dockerfile:
        dockerfile.writelines(dockerlines)
