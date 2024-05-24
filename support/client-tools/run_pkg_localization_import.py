#!/usr/bin/python

# =====================================
# Copyright 2024, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# See the `README.md` file for details.

import logging
import time
import argparse
import dataclasses
import getpass

import urllib3

from hds_user_client.api_client import ApiClient as UserApiApiClient
from hds_user_client.api.user_api import UserApi
from hds_user_client.configuration import Configuration as UserApiConfiguration
from hds_user_client.models.authenticate_user_request_envelope import AuthenticateUserRequestEnvelope

from hds_pkg_job_client.api_client import ApiClient as PkgJobApiClient
from hds_pkg_job_client.api.pkg_job_api import PkgJobApi
from hds_pkg_job_client.configuration import Configuration as PkgJobConfiguration
from hds_pkg_job_client.models.queue_pkg_dump_localization_import_job_request_envelope import QueuePkgDumpLocalizationImportJobRequestEnvelope

from hds_job_client.api_client import ApiClient as JobApiClient
from hds_job_client.api.job_api import JobApi
from hds_job_client.configuration import Configuration as JobConfiguration
from hds_job_client.models.get_job_request_envelope import GetJobRequestEnvelope
from hds_job_client.models.job_data import JobData

ORIGIN_SYSTEM_DESCRIPTION = "hds-support-script"

BASE_URL = "https://depot.haiku-os.org"

HEADER_DATAGUID = "X-HaikuDepotServer-DataGuid"


@dataclasses.dataclass
class Context:
    user_api_configuration: UserApiConfiguration = None
    pkg_job_api_configuration: PkgJobConfiguration = None
    job_api_configuration: JobConfiguration = None
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


def upload_job_data(ctx: Context, filename: str) -> str:
    """Provide raw data up to the server to then use in a job later.
    
    This is done via a raw HTTP request as there is no API specification for this API and so no
    code has been generated.
    """

    with open(filename, "rb") as f:
        filedata = f.read()

    resp = http.request(
        "POST",
        ctx.base_url + "/__secured/jobdata",
        body=filedata,
        headers={"Authorization": "Bearer %s" % ctx.auth_token})

    if 200 != resp.status:
        raise Exception("failure to upload data; expected 200 got %d" % (resp.status))

    data_guid = resp.headers.get(HEADER_DATAGUID)

    if not data_guid:
        raise Exception("data was uploaded ok, but there was no data guid returned")

    ctx.logger.info("uploaded file [%s] and received data guid [%s]" % (filename, data_guid))

    return data_guid


def initiate_pkg_localization_import_job(ctx: Context, data_guid: str) -> str:
    with PkgJobApiClient(
            configuration=ctx.user_api_configuration,
            header_name="Authorization",
            header_value="Bearer %s" % ctx.auth_token) as pkg_job_api_client:
        pkg_job_api_instance = PkgJobApi(pkg_job_api_client)
        response_envelope = pkg_job_api_instance.queue_pkg_dump_localization_import_job(
            queue_pkg_dump_localization_import_job_request_envelope=QueuePkgDumpLocalizationImportJobRequestEnvelope(
                origin_system_description=ORIGIN_SYSTEM_DESCRIPTION,
                input_data_guid=data_guid,
            )
        )

        if response_envelope.error:
            raise Exception("queue job error; %s" % response_envelope.error.message)

        guid = response_envelope.result.guid

        if not guid:
            raise Exception("failed to get a guid when queueing a job")

        ctx.logger.info("did start job with guid [%s]" % guid)

        return guid


def loop_until_job_completed(ctx: Context, job_guid: str) -> str|None:
    """Keep checking the job's status until it stops

    Returns:
        A string which represents the GUID that can be used to get data about the
        job. None if no data was present.
    """

    def get_job_data_guid(job_datas: list[JobData]) -> str:
        if 0 == len(job_datas):
            raise Exception("expected a job data on return but did not find one")
        return job_datas[0].guid

    with JobApiClient(
            configuration=ctx.job_api_configuration,
            header_name="Authorization",
            header_value="Bearer %s" % ctx.auth_token) as job_api_client:

        job_api_instance = JobApi(job_api_client)

        while True:

            response_envelope = job_api_instance.get_job(
                get_job_request_envelope=GetJobRequestEnvelope(guid=job_guid)
            )

            if response_envelope.error:
                raise Exception("queue job error; %s" % response_envelope.error.message)

            job_status = response_envelope.result.job_status.name
            ctx.logger.info("job status for [%s] is [%s]" % (job_guid, job_status))

            if job_status == "FAILED":
                return None
            elif job_status == "CANCELLED":
                return None
            elif job_status == "FINISHED":
                return get_job_data_guid(response_envelope.result.generated_datas)
            else:
                ctx.logger.info("sleeping")
                time.sleep(2)


def download_job_data(ctx: Context, job_data_guid: str, filename: str):
    resp = http.request(
        "GET",
        ctx.base_url + "/__secured/jobdata/%s/download" % job_data_guid,
        headers={"Authorization": "Bearer %s" % ctx.auth_token})

    if 200 != resp.status:
        raise Exception("failure to upload data; expected 200 got %d" % (resp.status))

    with open(filename, "wb") as f:
        f.write(resp.data)

    ctx.logger.info("did write job data [%s] to [%s]" % (job_data_guid, filename))


def main():

    parser = argparse.ArgumentParser(description='Upload pkg localizations to HaikuDepotServer')
    parser.add_argument("--username", dest="username", required=True,
                        help="The username to authenticate as")
    parser.add_argument("--password", dest="password",
                        help="The password to authenticate with - otherwise queried from the user")
    parser.add_argument("-i", dest="input", required=True,
                        help="A file to upload to feed the job with data")
    parser.add_argument("-o", dest="output", required=True,
                        help="A file to upload to feed the job with data")
    parser.add_argument('-u', '--webappbaseurl',
                        help='The base HaikuDepotServer URL to communicate with')

    args = parser.parse_args()

    ctx = Context()

    if args.webappbaseurl:
        ctx.base_url = args.webappbaseurl

    ctx.user_api_configuration = UserApiConfiguration(host=ctx.base_url)
    ctx.pkg_job_api_configuration = PkgJobConfiguration(host=ctx.base_url)
    ctx.job_api_configuration = JobConfiguration(host=ctx.base_url)

    password_clear = args.password

    if not password_clear:
        password_clear = getpass.getpass('haiku-depot-server password:')

    ctx.auth_token = authenticate_and_get_token(ctx, args.username, password_clear)
    data_guid = upload_job_data(ctx, args.input)
    job_guid = initiate_pkg_localization_import_job(ctx, data_guid)
    job_data_guid = loop_until_job_completed(ctx, job_guid)

    if job_data_guid:
        download_job_data(ctx, job_data_guid, args.output)


if __name__ == "__main__":
    main()
