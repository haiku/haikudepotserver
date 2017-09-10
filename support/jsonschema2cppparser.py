#!/usr/bin/python

# =====================================
# Copyright 2017, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# This simple tool will read a JSON schema and will then generate a
# listener for use the 'BJson' class in the Haiku system.  This
# allows data conforming to the schema to be able to be parsed.

import json
import argparse
import os
import hdsjsonschemacommon as jscom


class CppParserSubSchemaTypeInfo:
    _cppmodelclassname = None
    _naming = None

    def __init__(self, schema, naming):
        javatype = schema['javaType']

        if not javatype or 0 == len(javatype):
            raise Exception('missing "javaType" field')

        self._cppmodelclassname = jscom.javatypetocppname(javatype)
        self._naming = naming

    def cppmodelclassname(self):
        return self._cppmodelclassname

    def stackedlistenerclassname(self):
        return self._cppmodelclassname + '_' + self._naming.cppstackedlistenerclassname()

    def stackedlistlistenerclassname(self):
        return self._cppmodelclassname + '_List_' + self._naming.cppstackedlistenerclassname()


class CppParserNaming:
    _schemaroot = None

    def __init__(self, schemaroot):
        self._schemaroot = schemaroot

    def cppmodelclassname(self):
        if self._schemaroot['type'] != 'object':
            raise Exception('expecting object')

        javatype = self._schemaroot['javaType']

        if not javatype or 0 == len(javatype):
            raise Exception('missing "javaType" field')

        return jscom.javatypetocppname(javatype)

    def cpplistenerclassname(self):
        return self.cppmodelclassname() + 'JsonListener'

    def cppstackedlistenerclassname(self):
        return 'Stacked' + self.cpplistenerclassname()

    def cppgeneralobjectstackedlistenerclassname(self):
        return 'GeneralObject_Stacked' + self.cpplistenerclassname()

    def cppgeneralarraystackedlistenerclassname(self):
        return 'GeneralArray_Stacked' + self.cpplistenerclassname()


class CppParserImplementationState:

    _interfacehandledcppnames = []
    _implementationhandledcppnames = []
    _outputfile = None
    _naming = None

    def __init__(self, outputfile, naming):
        self._outputfile = outputfile
        self._naming = naming

    def isinterfacehandledcppname(self, name):
        return name in self._interfacehandledcppnames

    def addinterfacehandledcppname(self, name):
        self._interfacehandledcppnames.append(name)

    def isimplementationhandledcppname(self, name):
        return name in self._implementationhandledcppnames

    def addimplementationhandledcppname(self, name):
        self._implementationhandledcppnames.append(name)

    def naming(self):
        return self._naming

    def outputfile(self):
        return self._outputfile


def writerootstackedlistenerinterface(istate):
    outfile = istate.outputfile()
    naming = istate.naming()

    outfile.write("""
class %s : public BJsonEventListener {
public:
    %s(
        %s* mainListener,
        %s* parent);
    ~%s();

    void HandleError(status_t status, int32 line, const char* message);
    void Complete();

    status_t ErrorStatus();

    %s* Parent();

protected:
    void SetStackedListenerOnMainListener(%s stackedListener);
    
    %s* fMainListener;
    %s* fParent;
};
""" % (naming.cppstackedlistenerclassname(),
       naming.cppstackedlistenerclassname(),
       naming.cpplistenerclassname(),
       naming.cppstackedlistenerclassname(),
       naming.cppstackedlistenerclassname(),
       naming.cppstackedlistenerclassname(),
       naming.cppstackedlistenerclassname(),
       naming.cpplistenerclassname(),
       naming.cppstackedlistenerclassname()))


def writerootstackedlistenerimplementation(istate):
    outfile = istate.outputfile()
    naming = istate.naming()

    outfile.write("""
%s:%s(%s* mainListener, %s* parent)
{
    fMainListener = mainListener;
    fParent = parent;
}
""" % (naming.cppstackedlistenerclassname(),
       naming.cppstackedlistenerclassname(),
       naming.cpplistenerclassname(),
       naming.cppstackedlistenerclassname()))

    outfile.write("""
%s::~%s()
{
}
""" % (naming.cppstackedlistenerclassname(),
       naming.cppstackedlistenerclassname()))

    outfile.write("""
void
%s::HandleError(status_t status, int32 line, const char* message)
{
    fMainListener->HandleError(status, line, message);
}
""" % naming.cppstackedlistenerclassname())

    outfile.write("""
void
%s::Complete()
{
   fMainListener->Complete();
}
""" % naming.cppstackedlistenerclassname())

    outfile.write("""
status_t
%s::ErrorStatus()
{
    fMainListener->ErrorStatus();
}
""" % naming.cppstackedlistenerclassname())

    outfile.write("""
%s*
%s::Parent()
{
    return fParent;
}
""" % (naming.cppstackedlistenerclassname(),
       naming.cppstackedlistenerclassname()))

    outfile.write("""
void
%s::SetStackedListenerOnMainListener(%s stackedListener)
{
    fMainListener->SetStackedListener(stackedListener);
}
""" % (naming.cppstackedlistenerclassname(),
       naming.cppstackedlistenerclassname()))


def writeageneralstackedlistenerinterface(istate, alistenerclassname):
    outfile = istate.outputfile()
    naming = istate.naming()

    outfile.write("""
class %s : public %s {
public:
    %s(
        %s* mainListener,
        %s* parent);
    ~%s();
    
    bool Handle(const BJsonEvent& event);
}
""" % (
        alistenerclassname,
        naming.cppstackedlistenerclassname(),
        alistenerclassname,
        naming.cpplistenerclassname(),
        naming.cppstackedlistenerclassname(),
        alistenerclassname
    ))


def writegeneralstackedlistenerinterface(istate):
    writeageneralstackedlistenerinterface(istate, istate.naming().cppgeneralarraystackedlistenerclassname())
    writeageneralstackedlistenerinterface(istate, istate.naming().cppgeneralobjectstackedlistenerclassname())


def writegeneralnooplistenerconstructordestructor(istate, cppclassname):
    naming = istate.naming()
    outfile = istate.outputfile()

    outfile.write("""
%s::%s(
    %s* mainListener,
    %s* parent)
    :
    %s(mainListener, parent)
{
}
""" % (cppclassname,
       cppclassname,
       naming.cpplistenerclassname(),
       naming.cppstackedlistenerclassname(),
       naming.cppstackedlistenerclassname()
       ))

    outfile.write("""
%s::~%s()
{
}
""" % (cppclassname, cppclassname))


def writegeneralstackedlistenerimplementation(istate):
    naming = istate.naming()
    outfile = istate.outputfile()
    generalobjectclassname = istate.naming().cppgeneralobjectstackedlistenerclassname();
    generalarrayclassname = istate.naming().cppgeneralobjectstackedlistenerclassname();

    writegeneralnooplistenerconstructordestructor(istate, naming.cppgeneralobjectstackedlistenerclassname())

    outfile.write("""
bool
%s::Handle(const BJsonEvent& event)
{
    if (ErrorStatus() != B_OK)
        return false;
        
    switch (event.EventType()) {

        case B_JSON_OBJECT_NAME:
        case B_JSON_NUMBER:
        case B_JSON_STRING:
        case B_JSON_TRUE:
        case B_JSON_FALSE:
        case B_JSON_NULL:
            // ignore
            break;
            
        case B_JSON_OBJECT_START:
            SetStackedListenerMainListener(new %s(fMainListener, this));
            break;
            
        case B_JSON_ARRAY_START:
            SetStackedListenerMainListener(new %s(fMainListener, this));
            break;
            
        case B_JSON_ARRAY_END:
            HandleError(B_NOT_ALLOWED, JSON_EVENT_LISTENER_ANY_LINE, "illegal state - unexpected end of array");');
            break;
            
        case B_JSON_OBJECT_END:
            SetStackedListenerMainListener(fParent);
            delete this;
            break;
        
    }
    
    return ErrorStatus() == B_OK;
}
""" % (generalobjectclassname, generalobjectclassname, generalarrayclassname))

    writegeneralnooplistenerconstructordestructor(istate, naming.cppgeneralarraystackedlistenerclassname())

    outfile.write("""
bool
%s::Handle(const BJsonEvent& event)
{
    if (ErrorStatus() != B_OK)
        return false;
        
    switch (event.EventType()) {

        case B_JSON_OBJECT_NAME:
        case B_JSON_NUMBER:
        case B_JSON_STRING:
        case B_JSON_TRUE:
        case B_JSON_FALSE:
        case B_JSON_NULL:
            // ignore
            break;
            
        case B_JSON_OBJECT_START:
            SetStackedListenerMainListener(new %s(fMainListener, this));
            break;
            
        case B_JSON_ARRAY_START:
            SetStackedListenerMainListener(new %s(fMainListener, this));
            break;
            
        case B_JSON_OBJECT_END:
            HandleError(B_NOT_ALLOWED, JSON_EVENT_LISTENER_ANY_LINE, "illegal state - unexpected end of object");');
            break;
            
        case B_JSON_ARRAY_END:
            SetStackedListenerMainListener(fParent);
            delete this;
            break;
        
    }
    
    return ErrorStatus() == B_OK;
}
""" % (generalarrayclassname, generalobjectclassname, generalarrayclassname))


def writestackedlistenerinterface(istate, subschema):
    naming = istate.naming()
    typeinfo = CppParserSubSchemaTypeInfo(subschema, naming)

    if not istate.isinterfacehandledcppname(typeinfo.cppmodelclassname()):
        istate.addinterfacehandledcppname(typeinfo.cppmodelclassname())

        outfile = istate.outputfile()

        outfile.write("""
class %s : public %s {
public:
    %s(
        %s* mainListener,
        %s* parent);
    ~%s();
    
    bool Handle(const BJsonEvent& event);
    
    %s* Target();
private:
    %s* fTarget;
    BString fNextItemName;
}
""" % (
            typeinfo.stackedlistenerclassname(),
            naming.cppstackedlistenerclassname(),
            typeinfo.stackedlistenerclassname(),
            naming.cppstackedlistenerclassname(),
            naming.cpplistenerclassname(),
            typeinfo.stackedlistenerclassname(),
            typeinfo.cppmodelclassname(),
            typeinfo.cppmodelclassname()
        ))

        outfile.write("""
class %s : public %s {
public:
    %s(
        %s* mainListener,
        %s* parent);
    ~%s();
    
    bool Handle(const BJsonEvent& event);
    
    BList* Target(); // list of %s pointers
    
private:
    BList* fTarget;
}
""" % (
            typeinfo.stackedlistlistenerclassname(),
            naming.cppstackedlistenerclassname(),
            typeinfo.stackedlistlistenerclassname(),
            naming.cppstackedlistenerclassname(),
            naming.cpplistenerclassname(),
            typeinfo.stackedlistenerclassname(),
            typeinfo.cppmodelclassname()
        ))

        for propname, propmetadata in subschema['properties'].items():
            if propmetadata['type'] == 'array':
                writestackedlistenerinterface(
                    istate,
                    propmetadata['items'])


def writestackedlistenerfieldimplementation(
        istate,
        propname,
        cppeventdataexpression):
    outfile = istate.outputfile()
    outfile.write('                    if (fNextItemName == "' + propname + '")\n')
    outfile.write('                        fTarget.Set' +
                  jscom.propnametocppname(propname) +
                  '(' + cppeventdataexpression + ');\n')


def writestackedlistenerfieldsimplementation(
        istate,
        schema,
        selectedcpptypename,
        jsoneventtypename,
        cppeventdataexpression):

    outfile = istate.outputfile()

    outfile.write('                case ' + jsoneventtypename + ':\n')

    for propname, propmetadata in schema['properties'].items():
        cpptypename = jscom.propmetadatatocpptypename(propmetadata)

        if cpptypename == selectedcpptypename:
            writestackedlistenerfieldimplementation(istate, propname, cppeventdataexpression)

    outfile.write('                    break\n')


def writestackedlistenerimplementation(istate, schema):
    naming = istate.naming()
    typeinfo = CppParserSubSchemaTypeInfo(schema, naming)

    if not istate.isimplementationhandledcppname(typeinfo.cppmodelclassname()):
        istate.addimplementationhandledcppname(typeinfo.cppmodelclassname())

        outfile = istate.outputfile()

        outfile.write("""
%s::%s(
    %s* mainListener,
    %s* parent)
    :
    %s(mainListener, parent)
{
    fTarget = new %s();
}
""" % (
            typeinfo.stackedlistenerclassname(),
            typeinfo.stackedlistenerclassname(),
            naming.cpplistenerclassname(),
            naming.cppstackedlistenerclassname(),
            naming.cppstackedlistenerclassname(),
            typeinfo.cppmodelclassname()))

        outfile.write("""
%s::~%s()
{
}
""" % (
            typeinfo.stackedlistenerclassname(),
            typeinfo.stackedlistenerclassname()
        ))

        outfile.write("""
bool
%s::Handle(const BJsonEvent& event)
{
    if (ErrorStatus() != B_OK)
        return false;
        
    switch (event.EventType()) {
        
        case B_JSON_ARRAY_END:
            HandleError(B_NOT_ALLOWED, JSON_EVENT_LISTENER_ANY_LINE, "illegal state - unexpected start of array");');
            break;
        
        case B_JSON_OBJECT_NAME:
            fNextItemName = event.Content();
            break;
            
        case B_JSON_OBJECT_END:
            SetStackedListenerMainListener(fParent);
            delete this;
            break;
        
        default:
        
            switch (event.EventType()) {
""" % (typeinfo.stackedlistenerclassname()))

# now extract the fields from the schema that need to be fed in.

        writestackedlistenerfieldsimplementation(
            istate, schema,
            jscom.CPP_TYPE_STRING, 'B_JSON_STRING', 'event.Content()')

        writestackedlistenerfieldsimplementation(
            istate, schema,
            jscom.CPP_TYPE_BOOLEAN, 'B_JSON_TRUE', 'true')

        writestackedlistenerfieldsimplementation(
            istate, schema,
            jscom.CPP_TYPE_BOOLEAN, 'B_JSON_FALSE', 'false')

        outfile.write('                case B_JSON_NULL:\n')

        for propname, propmetadata in schema['properties'].items():
            writestackedlistenerfieldimplementation(istate, propname, 'NULL')

        outfile.write('                    break\n')

# number type is a bit complex because it can either be a double or it can be an
# integral value.

        outfile.write('                case B_JSON_NUMBER:\n')

        for propname, propmetadata in schema['properties'].items():
            propcpptypename = jscom.propmetadatatocpptypename(propmetadata)
            if propcpptypename == jscom.CPP_TYPE_INTEGER:
                writestackedlistenerfieldimplementation(istate, propname, 'event.ContentInteger()')
            elif propcpptypename == jscom.CPP_TYPE_NUMBER:
                writestackedlistenerfieldimplementation(istate, propname, 'event.ContentDouble()')

        outfile.write('                    break\n')

# object type; could be a sub-type or otherwise just drop into a placebo consumer to keep the parse
# structure working.  This would most likely be additional sub-objects that are additional to the
# expected schema.

        outfile.write('                case B_JSON_OBJECT_START:\n')

        objectifclausekeyword = 'if'

        for propname, propmetadata in schema['properties'].items():
            if propmetadata['type'] == jscom.JSON_TYPE_OBJECT:
                subschematypeinfo = CppParserSubSchemaTypeInfo(propmetadata, naming)

                outfile.write('                    %s (fNextItemName == "%s") {\n' % (objectifclausekeyword, propname))
                outfile.write('                        %s* nextListener = new %s(fMainListener, this);\n' % (
                    naming.cppstackedlistenerclassname(),
                    subschematypeinfo.stackedlistenerclassname()))
                outfile.write('                        fTarget->Set%s(nextListener->Target());\n') % (
                    subschematypeinfo.cppmodelclassname())
                outfile.write('                        SetStackedListenerMainListener(nextListener);\n')
                outfile.write('                    }\n')

                objectifclausekeyword = 'else if'

        outfile.write('                    %s (1 == 1) {\n' % objectifclausekeyword)
        outfile.write('                        %s* nextListener = new %s(fMainListener, this);\n' % (
            naming.cppstackedlistenerclassname(),
            naming.cppgeneralobjectstackedlistenerclassname()))
        outfile.write('                        SetStackedListenerMainListener(nextListener);\n')
        outfile.write('                    }\n')

# array type; could be an array of objects or otherwise just drop into a placebo consumer to keep
# the parse structure working.

        #TODO; placebo array.

        outfile.write("""
            }
            
            fNextItemName.SetTo("");
            break;
    }
    
    return ErrorStatus() == B_OK;
}
""")


def schematocppparser(inputfile, schema, outputdirectory):
    naming = CppParserNaming(schema)
    cpphfilename = os.path.join(outputdirectory, naming.cpplistenerclassname() + '.h')
    cppifilename = os.path.join(outputdirectory, naming.cpplistenerclassname() + '.cpp')

    with open(cpphfilename, 'w') as cpphfile:
        jscom.writetopcomment(cpphfile, os.path.split(inputfile)[1], 'Listener')
        guarddefname = 'GEN_JSON_SCHEMA_PARSER__%s_H' % (naming.cpplistenerclassname().upper())

        cpphfile.write((
                           '#ifndef %s\n'
                           '#define %s\n'
                           '\n'
                           '#include <JsonEventListener.h>\n'
                           '\n'
                           '#include "%s.h"\n'
                           '\n'
                           'class %s;\n'
                           '\n'
                           'class %s : public BJsonEventListener {\n'
                           'friend class %s;\n'
                           'public:\n'
                       ) % (guarddefname, guarddefname,
                            naming.cppmodelclassname(),
                            naming.cppstackedlistenerclassname(),
                            naming.cpplistenerclassname(),
                            naming.cppstackedlistenerclassname()))

        cpphfile.write((
                           '    %s(%s& target);\n'
                           '    virtual ~%s();\n'
                       ) % (naming.cpplistenerclassname(),
                            naming.cppmodelclassname(),
                            naming.cpplistenerclassname()))

        cpphfile.write('\n')

        cpphfile.write((
            '    bool Handle(const BJsonEvent& event);\n'
            '    void HandleError(status_t status, int32 line, const char* message);\n'
            '    void Complete();\n'
            '    status_t ErrorStatus();\n'
        ))

        cpphfile.write('\n')

        cpphfile.write((
                           'protected:\n'
                           '    void SetStackedListener(%s *listener);\n'
                           '    %s Target();\n'
                       ) % (naming.cppstackedlistenerclassname(),
                            naming.cppmodelclassname()))

        cpphfile.write('\n')

        cpphfile.write((
                           'private:\n'
                           '    %s* fTarget;\n'
                           '    status_t fErrorStatus;\n'
                           '    %s* fStackedListener;\n'
                       ) % (naming.cppmodelclassname(),
                            naming.cppstackedlistenerclassname()))

        cpphfile.write((
                           '}\n\n'
                           '#endif // %s'
                       ) % guarddefname)

    with open(cppifilename, 'w') as cppifile:
        istate = CppParserImplementationState(cppifile, naming)
        jscom.writetopcomment(cppifile, os.path.split(inputfile)[1], 'Listener')
        cppifile.write('#include "%s"\n' % (naming.cpplistenerclassname() + '.h'))
        cppifile.write('#include "List.h"\n')

        writerootstackedlistenerinterface(istate)
        writegeneralstackedlistenerinterface(istate)
        writestackedlistenerinterface(istate, schema)

        writerootstackedlistenerimplementation(istate)
        writegeneralstackedlistenerimplementation(istate)
        writestackedlistenerimplementation(istate, schema)



def main():
    parser = argparse.ArgumentParser(description='Convert JSON schema to Haiku C++ Parsers')
    parser.add_argument('-i', '--inputfile', required=True, help='The input filename containing the JSON schema')
    parser.add_argument('--outputdirectory', help='The output directory where the C++ files should be written')

    args = parser.parse_args()

    outputdirectory = args.outputdirectory

    if not outputdirectory:
        outputdirectory = '.'

    with open(args.inputfile) as inputfile:
        schema = json.load(inputfile)
        schematocppparser(args.inputfile, schema, outputdirectory)

if __name__ == "__main__":
    main()