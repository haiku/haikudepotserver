#!/usr/bin/python

# =====================================
# Copyright 2017-2024, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# This simple tool is designed to upload screenshot files to the HaikuDepotServer
# application server for a particular package.  This is possible to achieve by
# using a web-browser, but some web-browsers are not able to handle the necessary
# JS logic for some reason and so this script can be used instead.

import urllib3
import urllib.parse as urlparse
import re
import os
import argparse
import getpass
import dataclasses
import logging

from hds_user_client.api_client import ApiClient as UserApiApiClient
from hds_user_client.api.user_api import UserApi
from hds_user_client.configuration import Configuration as UserApiConfiguration
from hds_user_client.models.authenticate_user_request_envelope import AuthenticateUserRequestEnvelope


BASE_URL = 'https://depot.haiku-os.org/'


@dataclasses.dataclass
class Context:
    user_api_configuration: UserApiConfiguration = None
    logger: logging.Logger = logging.getLogger(__name__)
    base_url: str = BASE_URL
    auth_token: str = ""


logging.basicConfig(level=logging.INFO)
http = urllib3.PoolManager()


def authenticate_and_get_token(ctx: Context, username: str, password_clear: str) -> str:
    with UserApiApiClient(ctx.user_api_configuration) as user_api_client:
        user_api_instance = UserApi(user_api_client)
        response_envelope = user_api_instance.authenticate_user(
            authenticate_user_request_envelope=AuthenticateUserRequestEnvelope(
                nickname=username,
                password_clear=password_clear
            )
        )

        if response_envelope.error:
            raise Exception("authentication error %s" % (response_envelope.error.message))

        token = response_envelope.result.token

        if not token:
            raise Exception("failed to authenticate user")

        ctx.logger.info("did authenticate user [%s]" % (username))

        return token


def screenshot_upload_file(ctx: Context, pkg_name: str, filename: str):

    if not os.path.isfile(filename):
        raise Exception('unable to find the file [' + filename + '] -- ignored')

    if not filename.endswith('.png'):
        raise Exception('expecting the filename [' + filename + '] to end with ".png" -- ignored')

    url = urlparse.urljoin(
        ctx.base_url,
        '__pkgscreenshot/' + urlparse.quote(pkg_name) + '/add?' +
        urlparse.urlencode({'format': 'png'}))

    with open(filename, 'rb') as f:
        filedata = f.read()

    resp = http.request(
        "POST", url, body=filedata,
        headers={"Authorization": "Bearer %s" % ctx.auth_token})

    if 200 != resp.status:
        ctx.logger.info("failure to upload screenshot file [%s] for package [%s]" % (filename, pkg_name))

    screenshot_code = resp.headers.get('X-HaikuDepotServer-ScreenshotCode')

    if not screenshot_code:
        raise Exception("missing screenshot code when uploading file [%s]" % filename)

    ctx.logger.info("uploaded screenshot file [%s] for package [%s]" % (filename, pkg_name))

    return screenshot_code


def main():
    parser = argparse.ArgumentParser(description="Upload screenshots to HaikuDepotServer")
    parser.add_argument("--pkgname", dest="pkg_name", required=True,
                        help="The name of the package to which the screenshot should be added")
    parser.add_argument("--username", dest="username", required=True,
                        help="The username to authenticate as")
    parser.add_argument('--password', dest="password",
                        help="The password to authenticate as - otherwise queried from the user")
    parser.add_argument("-u", "--webappbaseurl",
                        help="The base HaikuDepotServer URL to write the screenshot to")
    parser.add_argument("filenames", nargs="+", help="A file or files to upload")

    args = parser.parse_args()

    if not re.match("^[a-z0-9_-]+$", args.pkg_name):
        print('malformed package name [' + args.pkg_name + ']')
        return

    ctx = Context()

    if args.webappbaseurl:
        ctx.base_url = args.webappbaseurl

    ctx.user_api_configuration = UserApiConfiguration(host=ctx.base_url)

    password = args.password

    if not password or len(password) == 0:
        password = getpass.getpass('haiku-depot-server password:')

    ctx.auth_token = authenticate_and_get_token(ctx, args.username, password)

    for filename in args.filenames:
        screenshot_upload_file(ctx, args.pkg_name, filename)


if __name__ == "__main__":
    main()
