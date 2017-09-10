#!/usr/bin/python

# =====================================
# Copyright 2017, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# This simple tool will read a JSON schema and will then generate
# some model objects that can be used to hold the data-structure
# in the C++ environment.

import json
import argparse
import os
import hdsjsonschemacommon as jscom


def hasanylistproperties(schema):
    for propname, propmetadata in schema['properties'].items():
        if propmetadata['type'] == 'array':
            return True
    return False


def writelistaccessors(outputfile, cppclassname, cppname, cppmembername, cppcontainertype):
    outputfile.write((
        '\n\nvoid\n'
        '%s::AddTo%s(%s* value) {\n'
        '    if (%s == NULL)\n'
        '        %s = new BList();\n'
        '    %s->AddItem(value);\n'
        '}\n') % (cppclassname, cppname, cppcontainertype, cppmembername, cppmembername, cppmembername))
    outputfile.write((
        '\n\nint32\n'
        '%s::Count%s() {\n'
        '    if (%s == NULL)\n'
        '        return 0;\n'
        '    return %s->CountItems();\n'
        '}\n') % (cppclassname, cppname, cppmembername, cppmembername))
    outputfile.write((
        '\n\n%s*\n'
        '%s::%sItemAt(int32 index) {\n'
        '    return static_cast<%s*>(\n'
        '        %s->ItemAt(index));\n'
        '}\n') % (cppcontainertype, cppclassname, cppname, cppcontainertype, cppmembername))
    outputfile.write((
        '\n\nbool\n'
        '%s::%sIsNull() {\n'
        '    return %s == NULL;\n'
        '}\n') % (cppclassname, cppname, cppmembername))


def writelistaccessorsheader(outputfile, cppname, cppcontainertype):
    outputfile.write('    void AddTo%s(%s* value);\n' % (cppname, cppcontainertype))
    outputfile.write('    int32 Count%s();\n' % cppname)
    outputfile.write('    %s* %sItemAt(int32 index);\n' % (cppcontainertype, cppname))
    outputfile.write('    bool %sIsNull();\n' % cppname)


def writetakeownershipaccessors(outputfile, cppclassname, cppname, cppmembername, cpptype):
    outputfile.write((
        '\n\n%s*\n'
        '%s::%s() {\n'
        '    return %s;\n'
        '}\n') % (cpptype, cppclassname, cppname, cppmembername))
    outputfile.write((
        '\n\nvoid\n'
        '%s::Set%s(%s* value) {\n'
        '        // takes ownership\n'
        '    %s = value;\n'
        '}\n') % (cppclassname, cppname, cpptype, cppmembername))
    outputfile.write((
        '\n\nbool\n'
        '%s::%sIsNull() {\n'
        '    return %s == NULL;\n'
        '}\n') % (cppclassname, cppname, cppmembername))


def writetakeownershipaccessorsheader(outputfile, cppname, cpptype):
    outputfile.write('    %s* %s();\n' % (cpptype, cppname))
    outputfile.write('    void Set%s(%s* value);\n' % (cppname, cpptype))
    outputfile.write('    void %sIsNull();\n' % cppname)


def writescalaraccessors(outputfile, cppclassname, cppname, cppmembername, cpptype):
    outputfile.write((
         '\n\n%s\n'
         '%s::%s() {\n'
         '    return %s;\n'
         '}\n') % (cpptype, cppclassname, cppname, cppmembername))
    outputfile.write((
         '\n\nvoid\n'
         '%s::Set%s(%s value) {\n'
         '    if (%sIsNull())\n'
         '        %s = new %s[1];\n'
         '    %s[0] = value\n'
         '}\n') % (cppclassname, cppname, cpptype, cppname, cppmembername, cppmembername, cpptype, cppmembername))
    outputfile.write((
         '\n\nvoid\n'
         '%s::%sIsNull() {\n'
         '    return %s == NULL;\n'
         '}\n') % (cppclassname, cppname, cppmembername))


def writescalaraccessorsheader(outputfile, cppname, cpptype):
    outputfile.write('    %s %s();\n' % (cpptype, cppname))
    outputfile.write('    void Set%s(%s value);\n' % (cppname, cpptype))
    outputfile.write('    void %sIsNull();\n' % cppname)


def writeaccessors(outputfile, cppclassname, propname, propmetadata):
    type = propmetadata['type']

    if type == 'array':
        writelistaccessors(outputfile,
                           cppclassname,
                           jscom.propnametocppname(propname),
                           jscom.propnametocppmembername(propname),
                           jscom.javatypetocppname(propmetadata['items']['javaType']))
    elif jscom.propmetadatatypeisscalar(propmetadata):
        writescalaraccessors(outputfile,
                             cppclassname,
                             jscom.propnametocppname(propname),
                             jscom.propnametocppmembername(propname),
                             jscom.propmetadatatocpptypename(propmetadata))
    else:
        writetakeownershipaccessors(outputfile,
                                    cppclassname,
                                    jscom.propnametocppname(propname),
                                    jscom.propnametocppmembername(propname),
                                    jscom.propmetadatatocpptypename(propmetadata))


def writeaccessorsheader(outputfile, propname, propmetadata):
    type = propmetadata['type']

    if type == 'array':
        writelistaccessorsheader(outputfile,
                                 jscom.propnametocppname(propname),
                                 jscom.javatypetocppname(propmetadata['items']['javaType']))
    elif jscom.propmetadatatypeisscalar(propmetadata):
        writescalaraccessorsheader(outputfile,
                                   jscom.propnametocppname(propname),
                                   jscom.propmetadatatocpptypename(propmetadata))
    else:
        writetakeownershipaccessorsheader(outputfile,
                                          jscom.propnametocppname(propname),
                                          jscom.propmetadatatocpptypename(propmetadata))


def writedestructorlogicforlist(outputfile, propname, propmetadata):
    outputfile.write((
        '        for (i = 0; i < %s.CountItems(); i++) {\n'
        '            delete static_cast<%s*>(\n'
        '            %s->ItemAt(i));\n'
        '        }\n') % (
        jscom.propnametocppmembername(propname),
        jscom.javatypetocppname(propmetadata['items']['javaType']),
        jscom.propnametocppmembername(propname)))


def writedestructor(outputfile, cppname, schema):
    outputfile.write('\n\n%s::~%s() {\n' % (cppname, cppname))

    if hasanylistproperties(schema):
        outputfile.write('    int32 i;\n')

    for propname, propmetadata in schema['properties'].items():
        propmembername = jscom.propnametocppmembername(propname)

        outputfile.write('    if (%s != NULL) {\n' % propmembername)

        if propmetadata['type'] == 'array':
            writedestructorlogicforlist(outputfile, propname, propmetadata)

        outputfile.write((
            '        delete %s;\n'
        ) % propmembername)

        outputfile.write('    }\n')

    outputfile.write('}\n')


def writeconstructor(outputfile, cppname, schema):
    outputfile.write('\n\n%s::%s() {\n' % (cppname, cppname))

    for propname, propmetadata in schema['properties'].items():
        outputfile.write('    %s = NULL;\n' % jscom.propnametocppmembername(propname))

    outputfile.write('}\n')


def schematocppmodels(inputfile, schema, outputdirectory):
    if schema['type'] != 'object':
        raise Exception('expecting object')

    javatype = schema['javaType']

    if not javatype or 0 == len(javatype):
        raise Exception('missing "javaType" field')

    cppclassname = jscom.javatypetocppname(javatype)
    cpphfilename = os.path.join(outputdirectory, cppclassname + '.h')
    cppifilename = os.path.join(outputdirectory, cppclassname + '.cpp')

    with open(cpphfilename, 'w') as cpphfile:

        jscom.writetopcomment(cpphfile, os.path.split(inputfile)[1], 'Model')
        guarddefname = 'GEN_JSON_SCHEMA_MODEL__%s_H' % (cppclassname.upper())

        cpphfile.write((
            '#ifndef %s\n'
            '#define %s\n'
            '\n'
            '#include "List.h"\n'
            '#include "String.h"\n'
            '\n'
            'class %s {\n'
            'public:\n'
        ) % (guarddefname, guarddefname, cppclassname))

        cpphfile.write((
            '    %s();\n'
            '    virtual ~%s();\n'
        ) % (cppclassname, cppclassname))

        cpphfile.write('\n')

        for propname, propmetadata in schema['properties'].items():
            writeaccessorsheader(cpphfile, propname, propmetadata)
            cpphfile.write('\n')

        # Now add the instance variables for the object as well.

        cpphfile.write('private:\n')

        for propname, propmetadata in schema['properties'].items():
            cpphfile.write('    %s* %s;\n' % (
                jscom.propmetadatatocpptypename(propmetadata),
                jscom.propnametocppmembername(propname)))

        cpphfile.write((
            '}\n\n'
            '#endif // %s'
        ) % guarddefname)

    with open(cppifilename, 'w') as cppifile:

        jscom.writetopcomment(cpphfile, os.path.split(inputfile)[1], 'Model')

        for propname, propmetadata in schema['properties'].items():
            writeaccessors(cppifile, cppclassname, propname, propmetadata)
            cppifile.write('\n')

    # Now write out any subordinate structures.

    for propname, propmetadata in schema['properties'].items():
        type = propmetadata['type']

        if type == 'array':
            schematocppmodels(inputfile, propmetadata['items'], outputdirectory)

        if type == 'object':
            schematocppmodels(inputfile, propmetadata, outputdirectory)


def main():
    parser = argparse.ArgumentParser(description='Convert JSON schema to Haiku C++ Models')
    parser.add_argument('-i', '--inputfile', required=True, help='The input filename containing the JSON schema')
    parser.add_argument('--outputdirectory', help='The output directory where the C++ files should be written')

    args = parser.parse_args()

    outputdirectory = args.outputdirectory

    if not outputdirectory:
        outputdirectory = '.'

    with open(args.inputfile) as inputfile:
        schema = json.load(inputfile)
        schematocppmodels(args.inputfile, schema, outputdirectory)

if __name__ == "__main__":
    main()

