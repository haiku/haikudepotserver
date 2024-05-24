# client-tools

These are small tools that are used as client to operate on the HDS system. These tools are written with the Python 3 scripting language.

## Setup

Install Python version 3.10 or better.

To use these tools, from the top-level of the source tree, run the `prepare.sh` script. This will perform the code-generation for the Python client and will copy it to this directory where it can be used.

You should setup a Python virtual environment and then import the required packages with;

```
pip install -r ./support/client-tools/requirements.txt
```

It should now be possible to run the tools.

## Run package localization import

This tool will upload a bulk pkg localization change document, run a job to process it and then pull down the results.

```
python3 \
run_pkg_localization_import.py \
--username somebody \
-i /x/y/z/input.json \
-o /x/y/z/output.json.gz
```

You can specify the HDS system to communicate with using the `-u` switch.