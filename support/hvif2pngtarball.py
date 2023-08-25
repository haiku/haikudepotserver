# =====================================
# Copyright 2023, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# --------------------------------------------
# Haiku comes with a small command line tool called `hvif2png`. This is used to
# render Haiku HVIF icon files to PNGs for display in browsers; ie in HDS.
# After building the `hvif2png` tool for linux x86_64 using the command...
#
#    jam -q "<build>hvif2png"
#
# ...it has to be bundled up for use with the HDS docker image. This script will
# do this. This script should be run with a single argument which is the
# "genenerated" directory for the Haiku build.
# --------------------------------------------

import re
import os
import tempfile
import shutil
import subprocess
import argparse


class Params:
    generate_dir = None


def parse_args():
    parser = argparse.ArgumentParser(description='Tool to package the `hvif2png` executable into a tarball for deployment with HDS')
    parser.add_argument('generate_dir', help='the `generated` directory where the `hvif2png` was generated')
    pargs = parser.parse_args()
    params.generate_dir = pargs.generate_dir


def validate_args():
    if not os.path.isdir(params.generate_dir):
        raise RuntimeError(f'unable to find the generate dir [{params.generate_dir}]')


# The hrev is determined by running a tool within the source code which then writes
# it out to a file which can subsequently be read into a python variable.
def determine_hrev():

    def determine_haiku_top():
        with open(os.path.join(params.generate_dir, 'Jamfile'), 'r') as jamfile:
            for line in jamfile:
                match = re.match('^HAIKU_TOP\s*=\s*([^\s]+)\s*;$', line)
                if match:
                    return os.path.join(params.generate_dir, match.group(1))
            raise RuntimeError('unable to determine the haiku top')

    haiku_top = determine_haiku_top()
    determine_haiku_revision_bin = os.path.normpath(os.path.join(haiku_top, "build", "scripts", "determine_haiku_revision"))

    with tempfile.NamedTemporaryFile(mode="w+t", encoding='utf-8') as f:
        p = subprocess.run([determine_haiku_revision_bin, haiku_top, f.name])
        if 0 != p.returncode:
            raise RuntimeError(f'unable to execute [{determine_haiku_revision_bin}]')
        result = f.read().strip()

        if not result or not len(result):
            raise RuntimeError('unable to determine the hrev')

        return result


def build_tarball():

    hrev = determine_hrev()

    with tempfile.TemporaryDirectory() as d:
        uname = os.uname()

        assembly_dir = os.path.join(d, f'hvif2png-{hrev}')
        assembly_dir_bin = os.path.join(assembly_dir, 'bin')
        assembly_dir_lib = os.path.join(assembly_dir, 'lib')

        os.mkdir(assembly_dir)
        os.mkdir(assembly_dir_bin)
        os.mkdir(assembly_dir_lib)

        objects_path = os.path.join(params.generate_dir, 'objects', uname.sysname.lower())

        for leafname in ['libbe_build.so', 'libroot_build.so']:
            shutil.copy(
                os.path.join(objects_path, 'lib', leafname),
                os.path.join(assembly_dir_lib, leafname))

        shutil.copy(
            os.path.join(objects_path, uname.machine, 'release', 'tools', 'hvif2png', 'hvif2png'),
            os.path.join(assembly_dir_bin, 'hvif2png'))

        launch_script_path = os.path.join(assembly_dir_bin, 'hvif2png.sh')

        with open(launch_script_path, "w+t", encoding='ascii') as f:
            f.write('''#!/bin/sh
HVIF2PNG_HOME="$(dirname $0)/.."
LD_LIBRARY_PATH="${HVIF2PNG_HOME}/lib:${LD_LIBRARY_PATH}"
export LD_LIBRARY_PATH
"${HVIF2PNG_HOME}/bin/hvif2png" "$@"
''')

        os.chmod(launch_script_path, mode=0o700)

        # now execute the tarball command to wrap this up.

        tarball_file = os.path.join(params.generate_dir, f'hvif2png-{hrev}-{uname.sysname.lower()}-{uname.machine}.tgz')

        p = subprocess.run(['tar', '-C', d, '-czf', tarball_file, os.path.basename(assembly_dir)])
        if 0 != p.returncode:
            raise RuntimeError(f'unable to tarball at [{tarball_file}]')

        print(f'did create tar-ball at [{tarball_file}]')


params = Params()
parse_args()
validate_args()
build_tarball()
