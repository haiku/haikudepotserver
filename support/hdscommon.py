
# =====================================
# Copyright 2014, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# This script contains material that can be shared between other python scripts related to the release-management
# of the HDS software.

# =====================================

import os
import os.path
import sys
import xml.etree.ElementTree as etree
import subprocess

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
# GIT

def gitaddpomformodule(modulename):
    if modulename is None:
        if 0 == subprocess.call(["git", "add", "pom.xml"]):
            print "pom.xml: (added)"
        else:
            print "failed to git-add; pom.xml"
            sys.exit(1)
    else:
        if 0 == subprocess.call(["git", "add", modulename + "/pom.xml"]):
            print modulename + "/pom.xml: (added)"
        else:
            print("failed to git-add; "+modulename+"/pom.xml")
            sys.exit(1)