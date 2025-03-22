# =====================================
# Copyright 2014-2022, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

"""
This script will copy the localization from the Polygot system to the HDS source.

See the documentation under /docs for more information.
"""

import argparse
import pathlib
import tempfile
import zipfile
import re
import shutil

_LEAF_MALFORMED_PATTERN = re.compile(r"^([a-z0-9-]+)_([a-z]{2,3})_([A-Za-z0-9]+)\.(ftl|html|properties)$")

def copy_localization_file(input_path: pathlib.Path, output_path: pathlib.Path):
    input_name_malformed_match = _LEAF_MALFORMED_PATTERN.match(input_path.name)

    if input_path.suffix in [".ftl", ".html", ".properties"]:
        if input_name_malformed_match:
            prefix = input_name_malformed_match.group(1)
            language_code = input_name_malformed_match.group(2)
            script_or_country_code = input_name_malformed_match.group(3)
            extension = input_name_malformed_match.group(4)
            corrected_output_name = f"{prefix}_{language_code}-{script_or_country_code}.{extension}"
            output_path = output_path.parent / corrected_output_name
            print(f"did correct input leafname [{input_path.name}] --> [{corrected_output_name}]")

        shutil.move(input_path, output_path)
        print(f"copied file [{str(input_path)}] --> [{str(output_path)}]")
    else:
        print(f"ignoring file [{str(input_path)}] with extension [{input_path.suffix}]")

def copy_localization_recursively(input_path: pathlib.Path, output_path: pathlib.Path):
    """Copies the localization file from the exploded ZIP file into the HDS source.

    This will take into account that the Polygot filenames are not quite correctly formatted in
    cases where there is a script on the code for the language. For example the file
    `unsupported_en_GB.html` should be `unsupported_eb-GB.html`. Confusingly they are sometimes
    correct. This may be corrected at some point at which time this file should be corrected.
    """

    if input_path.is_dir():
        if not output_path.is_dir():
            print(f"for input [{str(input_path)}], expected to find output [{str(output_path)}]")
        print(f"copying material from [{str(input_path)}] -> [{str(output_path)}]")
        for f in input_path.iterdir():
            copy_localization_recursively(f, output_path / f.name)
    else:
        copy_localization_file(input_path, output_path)


def main():
    parser = argparse.ArgumentParser(description="Tool to import Polygot localization into HDS")
    parser.add_argument("-i", dest="input_file", help="input zip file from Polygot download", required = True)
    parser.add_argument("-o", dest="output_hds_project", help="root of HDS project to output to", required = True)
    pargs = parser.parse_args()

    input_file_path = pathlib.Path(pargs.input_file)
    output_hds_project_path = pathlib.Path(pargs.output_hds_project)

    if not input_file_path.exists():
        raise RuntimeError(f"the input path [{str(input_file_path)}] does not exist")

    if not output_hds_project_path.exists():
        raise RuntimeError(f"the output path [{str(input_file_path)}] does not exist")

    if not output_hds_project_path.is_dir():
        raise RuntimeError(f"the output path [{str(input_file_path)}] is not a directory")

    if not (output_hds_project_path / "pom.xml").is_file():
        raise RuntimeError(f"the output path [{str(input_file_path)}] does not look like an HDS project")

    # normalize the paths

    output_hds_project_path = output_hds_project_path.resolve()
    input_file_path = input_file_path.resolve()

    # unpack the input ZIP file into a temporary directory

    print(f"will copy localization from [{str(input_file_path)}] --> [{str(output_hds_project_path)}]")

    with tempfile.TemporaryDirectory() as scratch_dir, zipfile.ZipFile(input_file_path, "r") as input_file_zip:
        input_file_zip.extractall(scratch_dir)
        copy_localization_recursively(pathlib.Path(scratch_dir), output_hds_project_path)

    print("did copy localization to target")


if __name__ == '__main__':
    main()