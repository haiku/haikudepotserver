#!/usr/bin/python

# =====================================
# Copyright 2017, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# This simple tool is designed to upload screenshot files to the HaikuDepotServer
# application server for a particular package.  This is possible to achieve by
# using a web-browser, but some web-browsers are not able to handle the necessary
# JS logic for some reason and so this script can be used instead.

import urlparse
import urllib
import urllib2
import re
import os
import argparse
import base64
import getpass


HDS_BASE_URL = 'https://depot.haiku-os.org/'


class ServerCoordinates:

    _webappbaseurl = HDS_BASE_URL
    _username = ''
    _password = ''

    def __init__(self, webappbaseurl, username, password):
        self._webappbaseurl = webappbaseurl
        self._username = username
        self._password = password

    def webappbaseurl(self):
        return self._webappbaseurl

    def username(self):
        return self._username

    def password(self):
        return self._password


def uploadscreenshot(filename, pkgname, servercoords):

    if not os.path.isfile(filename):
        print('unable to find the file [' + filename + '] -- ignored')
        return

    if not filename.endswith('.png'):
        print('expecting the filename [' + filename + '] to end with ".png" -- ignored')
        return

    upencoded = base64.b64encode(servercoords.username() + ':' + servercoords.password())
    baseurl = servercoords.webappbaseurl()
    url = urlparse.urljoin(baseurl, '__pkgscreenshot/' + urllib.quote_plus(pkgname) + '/add?'
                           + urllib.urlencode({'format': 'png'}))

    with open(filename, 'r') as f:
        payload = f.read() # TODO - should stream!
        request = urllib2.Request(url, data=payload)
        request.add_header('Authorization', 'Basic ' + upencoded)
        request.add_header('Content-Type', 'image/png')
        res = urllib2.urlopen(request)

        if res.getcode() == 200:
            screenshotcode = res.info().getheader('X-HaikuDepotServer-ScreenshotCode')

            if not screenshotcode or len(screenshotcode) == 0:
                print('did upload [' + filename + '] for pkg [' + pkgname + '], but no screenshot code was returned')
            else:
                print('did upload [' + filename + '] for pkg [' + pkgname + '] --> [' + screenshotcode + ']')
        else:
            raise Exception('attempted to add [' + filename + '] to pkgname [' + pkgname + '], but returned with'
                            + str(res.getcode()))


def main():
    parser = argparse.ArgumentParser(description='Upload screenshots to HaikuDepotServer')
    parser.add_argument("pkgname", help="The name of the package to which the screenshot should be added")
    parser.add_argument("username", help="The username to authenticate as")
    parser.add_argument('filenames', metavar='filenames', nargs='+', help='A file or files to upload')
    parser.add_argument('-u', '--webappbaseurl', help='The base HaikuDepotServer URL to write the screenshot to')
    parser.add_argument('--password', help='The password to authenticate as - otherwise queried from the user')

    args = parser.parse_args()

    if not re.match('^[^\\s/=!<>-]+$', args.pkgname):
        print('malformed package name [' + args.pkgname + ']')
        return

    baseurl = args.webappbaseurl

    if not baseurl or len(baseurl) == 0:
        baseurl = HDS_BASE_URL

    password = args.password

    if not password or len(password) == 0:
        password = getpass.getpass('haiku-depot-server password:')

    servercoords = ServerCoordinates(baseurl, args.username, password)

    for filename in args.filenames:
        uploadscreenshot(filename, args.pkgname, servercoords)


if __name__ == "__main__":
    main()
