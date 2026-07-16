# =====================================
# Copyright 2014-2026, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

"""
This script will copy the localization from the Polygot system to the HDS source.

See the documentation under /docs for more information.
"""

import argparse
import dataclasses
import pathlib
import tempfile
import zipfile
import re
import shutil

_LEAF_MALFORMED_PATTERN = re.compile(r"^([a-z0-9-]+)_([a-z]{2,3})_([A-Za-z0-9]+)\.(ftl|html|properties)$")

_LEAF_LOCALIZED_PROPERTIES_FILE = re.compile(r"^([a-z0-9]+_)([A-Za-z0-9-]+).properties$")

_PROPERTY_LINE_PATTERN = re.compile(r"^([A-z0-9_.-]+)=(.*)$")
_PROPERTY_CONTINUATION_LINE_PATTERN = re.compile(r"^\s+(.*)$")


def parse_properties_to_dict(path: pathlib.Path) -> dict[str, str]:
    """Parses a Java `.properties` file to Python dict of strings."""

    assembly: dict[str,str] = dict()
    current_key: str = ""
    current_value_parts: list[str] = []

    with open(path, encoding="utf-8") as f:
        for line in f:
            if not line.startswith("#"):
                if current_value_parts:

                    # This is where there is a continuation on the prior line.

                    if not current_key:
                        raise RuntimeError("missing key")

                    m = _PROPERTY_CONTINUATION_LINE_PATTERN.match(line)

                    if not m:
                        raise RuntimeError(f"the continuation line [{line}] is invalid")

                    # not quite right, but probably OK
                    current_value_parts.append(m.group(1).rstrip("\\"))

                    if not m.group(1).endswith("\\"):
                        assembly[current_key] = "".join(current_value_parts)
                        current_key = ""
                        current_value_parts.clear()
                else:
                    if current_key or current_value_parts:
                        raise RuntimeError("key and value should be missing")

                    if line.strip():
                        m = _PROPERTY_LINE_PATTERN.match(line)

                        if not m:
                            raise RuntimeError(f"the line [{line}] is invalid")

                        current_key = m.group(1)
                        current_value_parts.append(m.group(2).rstrip("\\"))

                        if not m.group(2).endswith("\\"):
                            assembly[current_key] = "".join(current_value_parts)
                            current_key = ""
                            current_value_parts.clear()

        return assembly


def rationalize_non_reference_properties_files(root: pathlib.Path, glob: str):
    """Reorganizes the Java properties files that are non-reference

    The `_en` file is the reference file in the directory and others are non-reference localized files. This
    function will reformat the non-reference ones so that any unknown keys, non-localized keys are identified.
    """

    @dataclasses.dataclass
    class RationalizeNonReferencePropertiesFileResult:
        localized: dict[str, str]
        """Key-value pairs where the non-reference value differs from the reference"""

        unrecognized: list[str]
        """Keys that are not observed in the reference"""

        unlocalized: list[str]
        """Keys that are absent from the non-reference or where the value is the same as the reference"""

        def total(self) -> int:
            return len(self.localized) + len(self.unlocalized)

        def percentage_localized(self) -> int:
            return int((100 * len(self.localized)) / self.total())


    def reference_and_non_reference_properties_files() -> tuple[pathlib.Path, list[pathlib.Path]]:
        files = root.glob(glob)
        reference_file_: pathlib.Path|None = None
        non_reference_files_: list[pathlib.Path] = []

        for f in files:
            f_match = _LEAF_LOCALIZED_PROPERTIES_FILE.match(f.name)

            if not f_match:
                raise RuntimeError(f"bad properties file found [{f.name}]")

            if f_match.group(2) == 'en':
                reference_file_ = f
            else:
                non_reference_files_.append(f)

        if not reference_file_:
            raise RuntimeError("unable to find the reference file")

        if not non_reference_files_:
            raise RuntimeError("unable to find any non-reference file")

        return reference_file_, non_reference_files_


    def rationalize_non_reference_dict(d: dict) -> RationalizeNonReferencePropertiesFileResult:
        result = RationalizeNonReferencePropertiesFileResult(localized = dict(), unrecognized = list(), unlocalized = list())

        for key in reference_dict.keys():
            if key not in d:
                result.unlocalized.append(key)

        for key in d.keys():
            if key not in reference_dict:
                result.unrecognized.append(key)
            else:
                if reference_dict[key] == d[key]:
                    result.unlocalized.append(key)
                else:
                    result.localized[key] = d[key]

        return result


    def write_rationalized_result(file: pathlib.Path, result: RationalizeNonReferencePropertiesFileResult):
        with open(file, "w", encoding="utf-8") as f:
            f.write(f"# localizations ({result.percentage_localized()}%)\n")
            for key in sorted(result.localized.keys()):
                f.write(f"{key}={result.localized[key]}\n")
            f.write("\n# unlocalized keys\n")
            for key in sorted(result.unlocalized):
                f.write(f"#{key}\n")
            f.write("\n# unrecognized keys\n")
            for key in sorted(result.unrecognized):
                f.write(f"#{key}\n")


    print(f"will strip unlocalized properties for [{glob}]")

    reference_file, non_reference_files = reference_and_non_reference_properties_files()
    reference_dict = parse_properties_to_dict(reference_file)

    if not reference_dict:
        raise RuntimeError(f"the file [{reference_file.name}] contains no key-value pairs")

    print(f"reference file [{reference_file.name}] with {len(reference_dict)} entries")

    for non_reference_file in non_reference_files:
        non_reference_dict = parse_properties_to_dict(non_reference_file)

        if not reference_dict:
            print(f"the file [{non_reference_file}] contains no key-value pairs")
        else:
            non_reference_result: RationalizeNonReferencePropertiesFileResult = rationalize_non_reference_dict(non_reference_dict)
            print(f"non reference file [{non_reference_file.name}] has {len(non_reference_result.localized)} localized, {len(non_reference_result.unrecognized)} unrecognized, {len(non_reference_result.unlocalized)} unlocalized")
            write_rationalized_result(non_reference_file, non_reference_result)

    print(f"did strip unlocalized properties for [{glob}]")


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

    rationalize_non_reference_properties_files(
        output_hds_project_path,
        "haikudepotserver-webapp/src/main/resources/webmessages_*.properties")
    rationalize_non_reference_properties_files(
        output_hds_project_path,
        "haikudepotserver-core/src/main/resources/messages_*.properties")

if __name__ == '__main__':
    main()