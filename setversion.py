#!/usr/bin/python

# =====================================
# Copyright 2014, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# This script is used to configure an updated version through the various project files in haikudepotserver.  It is
# also able to use the "git" command to schedule the project files (pom files) for the next commit, but it will not
# actually undertake the next commit.  It is a "python" script and so a python interpreter should be available to
# use this.  See the module "haikudepotserver-docs" for further detail on how to use this tool.

# =====================================

import os
import os.path
import subprocess
import sys
import re
import xml.etree.ElementTree as etree

gitAddPoms = False
updatedVersion = ""


# =====================================
# ARGUMENTS / SYNTAX


def syntax():
    print "syntax; setsversion.py [-v <version>] [-a]"
    sys.exit(1)


def parseargs():

    global updatedVersion
    global gitAddPoms

    if len(sys.argv) < 2:
        syntax()

    i = 1

    while i < len(sys.argv):
        if sys.argv[i] == "-v":
            updatedVersion = sys.argv[i+1]
            i = i + 2

            if not re.match("^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?$", updatedVersion):
                print "bad version; " + updatedVersion
                sys.exit(1)

        elif sys.argv[i] == '-a':
            gitAddPoms = True
            i = i + 1
        else:
            syntax()


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
        print "invalid tag missing namespace (open) ; " + tag
        sys.exit(1)

    closebraceindex = tag.find("}")

    if -1 == closebraceindex:
        print "invalid tag missing namespace (close) ; " + tag
        sys.exit(1)

    return tag[1:closebraceindex]


def pomtoplevelelement(tree, taglocalname):
    roote = tree.getroot()
    namespace = extractdefaultnamespace(roote.tag)

    el = roote.find("{"+namespace+"}"+taglocalname)

    if el is None:
        print "unable to find the "+taglocalname+" element"
        sys.exit(1)

    return el


# =====================================
# DOM / ELEMENT HANDLING FOR POM

def pomextractartifactid(tree):
    return pomtoplevelelement(tree, "artifactId").text


def pomextractversion(tree):
    return pomtoplevelelement(tree, "version").text


def writepom(tree, filename):
    tree.write(
        filename,
        xml_declaration=True,
        encoding='UTF-8',
        method='xml',
        default_namespace=extractdefaultnamespace(tree.getroot().tag))


# =====================================
# LOGIC CHUNKS


def ensurecurrentversionconsistencyformodule(modulename, expectedversion):
    modulepomtree = etree.parse(modulename + "/pom.xml")

    if not modulepomtree:
        print "the 'pom.xml' for module "+modulename+" should be accessible"
        sys.exit(1)

    if modulename == "haikudepotserver-parent":
        actualversion = pomtoplevelelement(modulepomtree, "version").text

        if actualversion != expectedversion:
            print("the version of the module "+modulename+" is inconsistent with the expected; " + actualversion)
            sys.exit(1)
        else:
            print(modulename + ": " + actualversion + " (ok)")

    else:
        parente = pomtoplevelelement(modulepomtree, "parent")
        namespace = extractdefaultnamespace(parente.tag)
        versione = parente.find("{"+namespace+"}version")

        if versione is None:
            print "the parent element of module " + modulename + " has no version specified"
            sys.exit(1)
        else:
            actualversion = versione.text

            if actualversion != expectedversion:
                print "the version of the module "+modulename+" is inconsistent with the expected; " + actualversion
                sys.exit(1)
            else:
                print modulename + ": " + actualversion + " (ok)"


def updateversionformodule(modulename, version):
    filename = modulename + "/pom.xml"
    modulepomtree = etree.parse(filename)

    if not modulepomtree:
        print "the 'pom.xml' for module "+modulename+" should be accessible"
        sys.exit(1)

    if modulename == "haikudepotserver-parent":
        versione = pomtoplevelelement(modulepomtree, "version")
        versione.text = version
    else:
        parente = pomtoplevelelement(modulepomtree, "parent")
        namespace = extractdefaultnamespace(parente.tag)
        versione = parente.find("{"+namespace+"}version")
        versione.text = version

    writepom(modulepomtree, filename)
    print modulename + ": " + version + " (updated)"


# =====================================
# MAIN FLOW

parseargs()

if not os.path.isfile("pom.xml"):
    print "the 'pom.xml' file should be accessible in the present working directory"
    sys.exit(1)

rootPomTree = etree.parse("pom.xml")

if not rootPomTree:
    print "the 'pom.xml' should be accessible in the present working directory"
    sys.exit(1)

if pomextractartifactid(rootPomTree) != "haikudepotserver":
    print "the top level pom should have the 'haikudepotserver' artifactId"
    sys.exit(1)

rootPomCurrentVersion = pomextractversion(rootPomTree)

if rootPomCurrentVersion == updatedVersion:
    print "the updated version and the current version are the same"
    sys.exit(1)

rootPomModuleNames = scanmodules()

if 0 == len(rootPomModuleNames):
    print("expecting the project to have some modules")
    sys.exit(1)

# One of the modules, will have "-parent" on the end and will be the parent.  The others will be
# subordinate to that in terms of versions.  So, the aim here is to update the actual version of
# the root and parent pom and then update the parent reference on the other modules.  To start
# with, it is expected that all of the versions will be consistent and so the process will
# ensure that this is the case as well.

print "will check version consistency"

print "top-level; " + rootPomCurrentVersion

for m in rootPomModuleNames:
    ensurecurrentversionconsistencyformodule(m, rootPomCurrentVersion)

print "did check version consistency"

if 0 != len(updatedVersion):
    print "will update version to; " + updatedVersion

    versionE = pomtoplevelelement(rootPomTree, "version")
    versionE.text = updatedVersion
    writepom(rootPomTree, "pom.xml")
    print "top-level: " + updatedVersion + " (updated)"

    for m in rootPomModuleNames:
        updateversionformodule(m, updatedVersion)

    print "did update version"

# If the user chosen to, run the git add command to add all of the poms ready for the next commit.

if gitAddPoms:

    print "will git-add pom files"

    if 0 == subprocess.call(["git", "add", "pom.xml"]):
        print "pom.xml: (added)"
    else:
        print "failed to git-add; pom.xml"
        sys.exit(1)

    for m in rootPomModuleNames:

        if 0 == subprocess.call(["git", "add", m + "/pom.xml"]):
            print m + "/pom.xml: (added)"
        else:
            print("failed to git-add; "+m+"/pom.xml")
            sys.exit(1)

    print "did git-add pom files"
