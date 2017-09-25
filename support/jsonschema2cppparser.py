#!/usr/bin/python

# =====================================
# Copyright 2017, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# This simple tool will read a JSON schema and will then generate a
# listener for use the 'BJson' class in the Haiku system.  This
# allows data conforming to the schema to be able to be parsed.

import string
import json
import argparse
import os
import hdsjsonschemacommon as jscom
import hdscommon

# This naming is related to a sub-type in the schema; maybe not the top-level.

class CppParserSubTypeNaming:
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

    def cppstackedlistenerclassname(self):
        return self._cppmodelclassname + '_' + self._naming.cppsuperstackedlistenerclassname()

    def cppstackedlistlistenerclassname(self):
        return self._cppmodelclassname + '_List_' + self._naming.cppsuperstackedlistenerclassname()

    def todict(self):
        return {
            'subtype_cppmodelclassname': self.cppmodelclassname(),
            'subtype_cppstackedlistenerclassname': self.cppstackedlistenerclassname(),
            'subtype_cppstackedlistlistenerclassname': self.cppstackedlistlistenerclassname()
        }


# This naming relates to the whole schema.  It's point of reference is the top level.

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

    def cppsupermainlistenerclassname(self):
        return 'Abstract' + self.cppmodelclassname() + 'JsonListener'

    def cppsinglemainlistenerclassname(self):
        return self.cppmodelclassname() + 'JsonListener'

    def cppbulkcontainermainlistenerclassname(self):
        return 'BulkContainer' + self.cppsinglemainlistenerclassname()

    def cppbulkcontainerstackedlistenerclassname(self):
        return 'BulkContainer_' + self.cppsuperstackedlistenerclassname()

    def cppbulkcontaineritemliststackedlistenerclassname(self):
        return 'BulkContainer_ItemList_' + self.cppsuperstackedlistenerclassname()

    def cppsuperstackedlistenerclassname(self):
        return 'Stacked' + self.cppsinglemainlistenerclassname()

    def cppstackeditemlistenerlistenerclassname(self):
        return 'ItemListener_' + self.cppsuperstackedlistenerclassname()

    def cppgeneralobjectstackedlistenerclassname(self):
        return 'GeneralObject_' + self.cppsuperstackedlistenerclassname()

    def cppgeneralarraystackedlistenerclassname(self):
        return 'GeneralArray_' + self.cppsuperstackedlistenerclassname()

    def cppitemlistenerclassname(self):
        return self.cppmodelclassname() + 'ItemListener'

    def todict(self):
        return {
            'cppmodelclassname' : self.cppmodelclassname(),
            'cppsuperlistenerclassname' : self.cppsupermainlistenerclassname(),
            'cpplistenerclassname': self.cppsinglemainlistenerclassname(),
            'cppbulkcontainerlistenerclassname': self.cppbulkcontainermainlistenerclassname(),
            'cppbulkcontainerstackedlistenerclassname': self.cppbulkcontainerstackedlistenerclassname(),
            'cppbulkcontaineritemliststackedlistenerclassname': self.cppbulkcontaineritemliststackedlistenerclassname(),
            'cppstackedlistenerclassname': self.cppsuperstackedlistenerclassname(),
            'cppstackeditemlistenerlistenerclassname': self.cppstackeditemlistenerlistenerclassname(),
            'cppgeneralobjectstackedlistenerclassname': self.cppgeneralobjectstackedlistenerclassname(),
            'cppgeneralarraystackedlistenerclassname': self.cppgeneralarraystackedlistenerclassname(),
            'cppitemlistenerclassname': self.cppitemlistenerclassname()
        }


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
    istate.outputfile().write(
        string.Template("""        
/*! This class is the top level of the stacked listeners.  The stack structure is
    maintained in a linked list and sub-classes implement specific behaviors
    depending where in the parse tree the stacked listener is working at.
*/        
class ${cppstackedlistenerclassname} : public BJsonEventListener {
public:
    ${cppstackedlistenerclassname}(
        ${cppsuperlistenerclassname}* mainListener,
        ${cppstackedlistenerclassname}* parent);
    ~${cppstackedlistenerclassname}();

    void HandleError(status_t status, int32 line, const char* message);
    void Complete();

    status_t ErrorStatus();

    ${cppstackedlistenerclassname}* Parent();

    void WillPop();

protected:
    ${cppsuperlistenerclassname}* fMainListener;

    void Pop();
    void Push(${cppstackedlistenerclassname}* stackedListener);
    
private:
    ${cppstackedlistenerclassname}* fParent;
};
""").substitute(istate.naming().todict()))


def writerootstackedlistenerimplementation(istate):
    istate.outputfile().write(
        string.Template("""
${cppstackedlistenerclassname}::${cppstackedlistenerclassname} (
    ${cppsuperlistenerclassname}* mainListener,
    ${cppstackedlistenerclassname}* parent)
{
    fMainListener = mainListener;
    fParent = parent;
}

${cppstackedlistenerclassname}::~${cppstackedlistenerclassname}()
{
}

void
${cppstackedlistenerclassname}::HandleError(status_t status, int32 line, const char* message)
{
    fMainListener->HandleError(status, line, message);
}

void
${cppstackedlistenerclassname}::Complete()
{
   fMainListener->Complete();
}

status_t
${cppstackedlistenerclassname}::ErrorStatus()
{
    return fMainListener->ErrorStatus();
}

${cppstackedlistenerclassname}*
${cppstackedlistenerclassname}::Parent()
{
    return fParent;
}

void
${cppstackedlistenerclassname}::Push(${cppstackedlistenerclassname}* stackedListener)
{
    fMainListener->SetStackedListener(stackedListener);
}

void
${cppstackedlistenerclassname}::WillPop()
{
}

void
${cppstackedlistenerclassname}::Pop()
{
    fMainListener->SetStackedListener(fParent);
}
""").substitute(istate.naming().todict()))


def writeageneralstackedlistenerinterface(istate, alistenerclassname):
    istate.outputfile().write(
        string.Template("""
class ${alistenerclassname} : public ${cppstackedlistenerclassname} {
public:
    ${alistenerclassname}(
        ${cppsuperlistenerclassname}* mainListener,
        ${cppstackedlistenerclassname}* parent);
    ~${alistenerclassname}();
    
    bool Handle(const BJsonEvent& event);
};
""").substitute(hdscommon.uniondicts(
            istate.naming().todict(),
            {'alistenerclassname': alistenerclassname}
        )))


def writegeneralstackedlistenerinterface(istate):
    writeageneralstackedlistenerinterface(istate, istate.naming().cppgeneralarraystackedlistenerclassname())
    writeageneralstackedlistenerinterface(istate, istate.naming().cppgeneralobjectstackedlistenerclassname())


def writegeneralnoopstackedlistenerconstructordestructor(istate, aclassname):

    istate.outputfile().write(
        string.Template("""
${aclassname}::${aclassname}(
    ${cppsuperlistenerclassname}* mainListener,
    ${cppstackedlistenerclassname}* parent)
    :
    ${cppstackedlistenerclassname}(mainListener, parent)
{
}

${aclassname}::~${aclassname}()
{
}
""").substitute(hdscommon.uniondicts(
            istate.naming().todict(),
            {'aclassname': aclassname}))
    )


def writegeneralstackedlistenerimplementation(istate):
    outfile = istate.outputfile()
    generalobjectclassname = istate.naming().cppgeneralobjectstackedlistenerclassname()
    generalarrayclassname = istate.naming().cppgeneralarraystackedlistenerclassname()
    substitutedict = {
        'generalobjectclassname': generalobjectclassname,
        'generalarrayclassname': generalarrayclassname
    }

# general object consumer that will parse-and-discard any json objects.

    writegeneralnoopstackedlistenerconstructordestructor(istate, generalobjectclassname)

    istate.outputfile().write(
        string.Template("""
bool
${generalobjectclassname}::Handle(const BJsonEvent& event)
{
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
            Push(new ${generalobjectclassname}(fMainListener, this));
            break;
            
        case B_JSON_ARRAY_START:
            Push(new ${generalarrayclassname}(fMainListener, this));
            break;
            
        case B_JSON_ARRAY_END:
            HandleError(B_NOT_ALLOWED, JSON_EVENT_LISTENER_ANY_LINE, "illegal state - unexpected end of array");
            break;
            
        case B_JSON_OBJECT_END:
            Pop();
            delete this;
            break;
        
    }
    
    return ErrorStatus() == B_OK;
}
""").substitute(substitutedict))

    # general array consumer that will parse-and-discard any json arrays.

    writegeneralnoopstackedlistenerconstructordestructor(istate, generalarrayclassname)

    outfile.write(
        string.Template("""
bool
${generalarrayclassname}::Handle(const BJsonEvent& event)
{
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
            Push(new ${generalobjectclassname}(fMainListener, this));
            break;
            
        case B_JSON_ARRAY_START:
            Push(new ${generalarrayclassname}(fMainListener, this));
            break;
            
        case B_JSON_OBJECT_END:
            HandleError(B_NOT_ALLOWED, JSON_EVENT_LISTENER_ANY_LINE, "illegal state - unexpected end of object");
            break;
            
        case B_JSON_ARRAY_END:
            Pop();
            delete this;
            break;
        
    }
    
    return ErrorStatus() == B_OK;
}
""").substitute(substitutedict))


def writestackedlistenerinterface(istate, subschema):
    naming = istate.naming()
    subtypenaming = CppParserSubTypeNaming(subschema, naming)

    if not istate.isinterfacehandledcppname(subtypenaming.cppmodelclassname()):
        istate.addinterfacehandledcppname(subtypenaming.cppmodelclassname())

        istate.outputfile().write(
            string.Template("""
class ${subtype_cppstackedlistenerclassname} : public ${cppstackedlistenerclassname} {
public:
    ${subtype_cppstackedlistenerclassname}(
        ${cppsuperlistenerclassname}* mainListener,
        ${cppstackedlistenerclassname}* parent);
    ~${subtype_cppstackedlistenerclassname}();
    
    bool Handle(const BJsonEvent& event);
    
    ${subtype_cppmodelclassname}* Target();
    
protected:
    ${subtype_cppmodelclassname}* fTarget;
    BString fNextItemName;
};

class ${subtype_cppstackedlistlistenerclassname} : public ${cppstackedlistenerclassname} {
public:
    ${subtype_cppstackedlistlistenerclassname}(
        ${cppsuperlistenerclassname}* mainListener,
        ${cppstackedlistenerclassname}* parent);
    ~${subtype_cppstackedlistlistenerclassname}();
    
    bool Handle(const BJsonEvent& event);
    
    List<${subtype_cppmodelclassname}*, true>* Target(); // list of %s pointers
    
private:
    List<${subtype_cppmodelclassname}*, true>* fTarget;
};
""").substitute(hdscommon.uniondicts(naming.todict(), subtypenaming.todict())))

        for propname, propmetadata in subschema['properties'].items():
            if propmetadata['type'] == 'array':
                writestackedlistenerinterface(istate, propmetadata['items'])
            elif propmetadata['type'] == 'object':
                writestackedlistenerinterface(istate, propmetadata)


def writebulkcontainerstackedlistenerinterface(istate, schema):
    naming = istate.naming()
    subtypenaming = CppParserSubTypeNaming(schema, naming)
    outfile = istate.outputfile()

# This is a sub-class of the main model object listener.  It will ping out to an item listener
# when parsing is complete.

    outfile.write(
        string.Template("""
class ${cppstackeditemlistenerlistenerclassname} : public ${subtype_cppstackedlistenerclassname} {
public:
    ${cppstackeditemlistenerlistenerclassname}(
        ${cppsuperlistenerclassname}* mainListener,
        ${cppstackedlistenerclassname}* parent,
        ${cppitemlistenerclassname}* itemListener);
    ~${cppstackeditemlistenerlistenerclassname}();
    
    void WillPop();
        
private:
    ${cppitemlistenerclassname}* fItemListener;
};


class ${cppbulkcontainerstackedlistenerclassname} : public ${cppstackedlistenerclassname} {
public:
    ${cppbulkcontainerstackedlistenerclassname}(
        ${cppsuperlistenerclassname}* mainListener,
        ${cppstackedlistenerclassname}* parent,
        ${cppitemlistenerclassname}* itemListener);
    ~${cppbulkcontainerstackedlistenerclassname}();
    
    bool Handle(const BJsonEvent& event);
        
private:
    BString fNextItemName;
    ${cppitemlistenerclassname}* fItemListener;
};


class ${cppbulkcontaineritemliststackedlistenerclassname} : public ${cppstackedlistenerclassname} {
public:
    ${cppbulkcontaineritemliststackedlistenerclassname}(
        ${cppsuperlistenerclassname}* mainListener,
        ${cppstackedlistenerclassname}* parent,
        ${cppitemlistenerclassname}* itemListener);
    ~${cppbulkcontaineritemliststackedlistenerclassname}();
    
    bool Handle(const BJsonEvent& event);
        
private:
    ${cppitemlistenerclassname}* fItemListener;
};
""").substitute(hdscommon.uniondicts(naming.todict(), subtypenaming.todict())))


def writestackedlistenerfieldimplementation(
        istate,
        propname,
        cppeventdataexpression):

    istate.outputfile().write(
        string.Template("""
            if (fNextItemName == "${propname}")
                fTarget->Set${cpppropname}(${cppeventdataexpression});
        """).substitute({
            'propname': propname,
            'cpppropname': jscom.propnametocppname(propname),
            'cppeventdataexpression': cppeventdataexpression
        }))


def writestackedlistenerfieldsimplementation(
        istate,
        schema,
        selectedcpptypename,
        jsoneventtypename,
        cppeventdataexpression):

    outfile = istate.outputfile()

    outfile.write('        case ' + jsoneventtypename + ':\n')

    for propname, propmetadata in schema['properties'].items():
        cpptypename = jscom.propmetadatatocpptypename(propmetadata)

        if cpptypename == selectedcpptypename:
            writestackedlistenerfieldimplementation(istate, propname, cppeventdataexpression)

    outfile.write('            fNextItemName.SetTo("");\n')
    outfile.write('            break;\n')


def writestackedlistenertypedobjectimplementation(istate, schema):
    outfile = istate.outputfile()
    naming = istate.naming();
    subtypenaming = CppParserSubTypeNaming(schema, naming)

    outfile.write(
        string.Template("""
${subtype_cppstackedlistenerclassname}::${subtype_cppstackedlistenerclassname}(
    ${cppsuperlistenerclassname}* mainListener,
    ${cppstackedlistenerclassname}* parent)
    :
    ${cppstackedlistenerclassname}(mainListener, parent)
{
    fTarget = new ${subtype_cppmodelclassname}();
}


${subtype_cppstackedlistenerclassname}::~${subtype_cppstackedlistenerclassname}()
{
}


${subtype_cppmodelclassname}*
${subtype_cppstackedlistenerclassname}::Target()
{
    return fTarget;
}


bool
${subtype_cppstackedlistenerclassname}::Handle(const BJsonEvent& event)
{
    switch (event.EventType()) {
        
        case B_JSON_ARRAY_END:
            HandleError(B_NOT_ALLOWED, JSON_EVENT_LISTENER_ANY_LINE, "illegal state - unexpected start of array");
            break;
        
        case B_JSON_OBJECT_NAME:
            fNextItemName = event.Content();
            break;
            
        case B_JSON_OBJECT_END:
            Pop();
            delete this;
            break;

""").substitute(hdscommon.uniondicts(
            naming.todict(),
            subtypenaming.todict())))

    # now extract the fields from the schema that need to be fed in.

    writestackedlistenerfieldsimplementation(
        istate, schema,
        jscom.CPP_TYPE_STRING, 'B_JSON_STRING', 'new BString(event.Content())')

    writestackedlistenerfieldsimplementation(
        istate, schema,
        jscom.CPP_TYPE_BOOLEAN, 'B_JSON_TRUE', 'true')

    writestackedlistenerfieldsimplementation(
        istate, schema,
        jscom.CPP_TYPE_BOOLEAN, 'B_JSON_FALSE', 'false')

    outfile.write('        case B_JSON_NULL:\n')
    outfile.write('        {\n')

    for propname, propmetadata in schema['properties'].items():
        # TODO; deal with array case somehow.
        if 'array' != propmetadata['type']:
            writestackedlistenerfieldimplementation(istate, propname, 'NULL')
    outfile.write('            fNextItemName.SetTo("");\n')
    outfile.write('            break;\n')
    outfile.write('        }\n')

    # number type is a bit complex because it can either be a double or it can be an
    # integral value.

    outfile.write('        case B_JSON_NUMBER:\n')
    outfile.write('        {\n')

    for propname, propmetadata in schema['properties'].items():
        propcpptypename = jscom.propmetadatatocpptypename(propmetadata)
        if propcpptypename == jscom.CPP_TYPE_INTEGER:
            writestackedlistenerfieldimplementation(istate, propname, 'event.ContentInteger()')
        elif propcpptypename == jscom.CPP_TYPE_NUMBER:
            writestackedlistenerfieldimplementation(istate, propname, 'event.ContentDouble()')
    outfile.write('            fNextItemName.SetTo("");\n')
    outfile.write('            break;\n')
    outfile.write('        }\n')

    # object type; could be a sub-type or otherwise just drop into a placebo consumer to keep the parse
    # structure working.  This would most likely be additional sub-objects that are additional to the
    # expected schema.

    outfile.write('        case B_JSON_OBJECT_START:\n')
    outfile.write('        {\n')

    objectifclausekeyword = 'if'

    for propname, propmetadata in schema['properties'].items():
        if propmetadata['type'] == jscom.JSON_TYPE_OBJECT:
            subtypenaming = CppParserSubTypeNaming(propmetadata, naming)

            outfile.write('            %s (fNextItemName == "%s") {\n' % (objectifclausekeyword, propname))
            outfile.write('                %s* nextListener = new %s(fMainListener, this);\n' % (
                subtypenaming.cppstackedlistenerclassname(),
                subtypenaming.cppstackedlistenerclassname()))
            outfile.write('                fTarget->Set%s(nextListener->Target());\n' % (
                subtypenaming.cppmodelclassname()))
            outfile.write('                Push(nextListener);\n')
            outfile.write('            }\n')

            objectifclausekeyword = 'else if'

    outfile.write('            %s (1 == 1) {\n' % objectifclausekeyword)
    outfile.write('                %s* nextListener = new %s(fMainListener, this);\n' % (
        naming.cppgeneralobjectstackedlistenerclassname(),
        naming.cppgeneralobjectstackedlistenerclassname()))
    outfile.write('                Push(nextListener);\n')
    outfile.write('            }\n')
    outfile.write('            fNextItemName.SetTo("");\n')
    outfile.write('            break;\n')
    outfile.write('        }\n')

    # array type; could be an array of objects or otherwise just drop into a placebo consumer to keep
    # the parse structure working.

    outfile.write('        case B_JSON_ARRAY_START:\n')
    outfile.write('        {\n')

    objectifclausekeyword = 'if'

    for propname, propmetadata in schema['properties'].items():
        if propmetadata['type'] == jscom.JSON_TYPE_ARRAY:
            subtypenaming = CppParserSubTypeNaming(propmetadata['items'], naming)

            outfile.write('            %s (fNextItemName == "%s") {\n' % (objectifclausekeyword, propname))
            outfile.write('                %s* nextListener = new %s(fMainListener, this);\n' % (
                subtypenaming.cppstackedlistlistenerclassname(),
                subtypenaming.cppstackedlistlistenerclassname()))
            outfile.write('                fTarget->Set%s(nextListener->Target());\n' % (
                jscom.propnametocppname(propname)))
            outfile.write('                Push(nextListener);\n')
            outfile.write('            }\n')

            objectifclausekeyword = 'else if'

    outfile.write('            %s (1 == 1) {\n' % objectifclausekeyword)
    outfile.write('                %s* nextListener = new %s(fMainListener, this);\n' % (
        naming.cppsuperstackedlistenerclassname(),
        naming.cppgeneralarraystackedlistenerclassname()))
    outfile.write('                Push(nextListener);\n')
    outfile.write('            }\n')
    outfile.write('            fNextItemName.SetTo("");\n')
    outfile.write('            break;\n')
    outfile.write('        }\n')

    outfile.write("""
    }
    
    return ErrorStatus() == B_OK;
}
""")


def writestackedlistenertypedobjectlistimplementation(istate, schema):
    naming = istate.naming()
    subtypenaming = CppParserSubTypeNaming(schema, naming)
    outfile = istate.outputfile()

    outfile.write(
        string.Template("""
${subtype_cppstackedlistlistenerclassname}::${subtype_cppstackedlistlistenerclassname}(
    ${cppsuperlistenerclassname}* mainListener,
    ${cppstackedlistenerclassname}* parent)
    :
    ${cppstackedlistenerclassname}(mainListener, parent)
{
    fTarget = new List<${subtype_cppmodelclassname}*, true>();
}


${subtype_cppstackedlistlistenerclassname}::~${subtype_cppstackedlistlistenerclassname}()
{
}


List<${subtype_cppmodelclassname}*, true>*
${subtype_cppstackedlistlistenerclassname}::Target()
{
    return fTarget;
}


bool
${subtype_cppstackedlistlistenerclassname}::Handle(const BJsonEvent& event)
{
    switch (event.EventType()) {
        
        case B_JSON_ARRAY_END:
            Pop();
            delete this;
            break;   
        
        case B_JSON_OBJECT_START:
        {
            ${subtype_cppstackedlistenerclassname}* nextListener =
                new ${subtype_cppstackedlistenerclassname}(fMainListener, this);
            fTarget->Add(nextListener->Target());
            Push(nextListener);
            break;
        }
            
        default:
            HandleError(B_NOT_ALLOWED, JSON_EVENT_LISTENER_ANY_LINE,
                "illegal state - unexpected json event parsing an array of ${subtype_cppmodelclassname}");
            break;
    }
    
    return ErrorStatus() == B_OK;
}
""").substitute(hdscommon.uniondicts(naming.todict(), subtypenaming.todict())))


def writebulkcontainerstackedlistenerimplementation(istate, schema):
    naming = istate.naming()
    subtypenaming = CppParserSubTypeNaming(schema, naming)
    outfile = istate.outputfile()

    outfile.write(
        string.Template("""
        //AAA
${cppstackeditemlistenerlistenerclassname}::${cppstackeditemlistenerlistenerclassname}(
    ${cppsuperlistenerclassname}* mainListener, ${cppstackedlistenerclassname}* parent,
    ${cppitemlistenerclassname}* itemListener)
:
${subtype_cppstackedlistenerclassname}(mainListener, parent)
{
    fItemListener = itemListener;
}


${cppstackeditemlistenerlistenerclassname}::~${cppstackeditemlistenerlistenerclassname}()
{
}


void
${cppstackeditemlistenerlistenerclassname}::WillPop()
{
    fItemListener->Handle(fTarget);
    delete fTarget;
}


${cppbulkcontainerstackedlistenerclassname}::${cppbulkcontainerstackedlistenerclassname}(
    ${cppsuperlistenerclassname}* mainListener, ${cppstackedlistenerclassname}* parent,
    ${cppitemlistenerclassname}* itemListener)
:
${cppstackedlistenerclassname}(mainListener, parent)
{
    fItemListener = itemListener;
}


${cppbulkcontainerstackedlistenerclassname}::~${cppbulkcontainerstackedlistenerclassname}()
{
}


bool
${cppbulkcontainerstackedlistenerclassname}::Handle(const BJsonEvent& event)
{
    switch (event.EventType()) {
        
        case B_JSON_ARRAY_END:
            HandleError(B_NOT_ALLOWED, JSON_EVENT_LISTENER_ANY_LINE, "illegal state - unexpected start of array");
            break;
        
        case B_JSON_OBJECT_NAME:
            fNextItemName = event.Content();
            break;
            
        case B_JSON_OBJECT_START:
            Push(new ${cppgeneralobjectstackedlistenerclassname}(fMainListener, this));
            break;
            
        case B_JSON_ARRAY_START:
            if (fNextItemName == "items")
                Push(new ${cppbulkcontaineritemliststackedlistenerclassname}(fMainListener, this, fItemListener));
            else
                Push(new ${cppgeneralarraystackedlistenerclassname}(fMainListener, this));
            break;
            
        case B_JSON_OBJECT_END:
            Pop();
            delete this;
            break;
            
        default:
                // ignore
            break;
    }
    
    return ErrorStatus() == B_OK;
}


${cppbulkcontaineritemliststackedlistenerclassname}::${cppbulkcontaineritemliststackedlistenerclassname}(
    ${cppsuperlistenerclassname}* mainListener, ${cppstackedlistenerclassname}* parent,
    ${cppitemlistenerclassname}* itemListener)
:
${cppstackedlistenerclassname}(mainListener, parent)
{
    fItemListener = itemListener;
}


${cppbulkcontaineritemliststackedlistenerclassname}::~${cppbulkcontaineritemliststackedlistenerclassname}()
{
}


bool
${cppbulkcontaineritemliststackedlistenerclassname}::Handle(const BJsonEvent& event)
{
    switch (event.EventType()) {
        
        case B_JSON_OBJECT_START:
            Push(new ${cppstackeditemlistenerlistenerclassname}(fMainListener, this, fItemListener));
            break;
            
        case B_JSON_ARRAY_END:
            Pop();
            delete this;
            break;
            
        default:
            HandleError(B_NOT_ALLOWED, JSON_EVENT_LISTENER_ANY_LINE, "illegal state - unexpected json event");
            break;
    }
    
    return ErrorStatus() == B_OK;
}


""").substitute(hdscommon.uniondicts(naming.todict(), subtypenaming.todict())))


def writestackedlistenerimplementation(istate, schema):
    subtypenaming = CppParserSubTypeNaming(schema, istate.naming())

    if not istate.isimplementationhandledcppname(subtypenaming.cppmodelclassname()):
        istate.addimplementationhandledcppname(subtypenaming.cppmodelclassname())

        writestackedlistenertypedobjectimplementation(istate, schema)
        writestackedlistenertypedobjectlistimplementation(istate, schema)  # TODO; only if necessary.

        # now create the parser types for any subordinate objects descending.

        for propname, propmetadata in schema['properties'].items():
            if propmetadata['type'] == 'array':
                writestackedlistenerimplementation(istate, propmetadata['items'])
            elif propmetadata['type'] == 'object':
                writestackedlistenerimplementation(istate, propmetadata)


def writemainlistenerimplementation(istate, schema, supportbulkcontainer):
    outfile = istate.outputfile()
    naming = istate.naming()
    subtypenaming = CppParserSubTypeNaming(schema, istate.naming())

# super (abstract) listener

    outfile.write(
        string.Template("""
${cppsuperlistenerclassname}::${cppsuperlistenerclassname}()
{
    fErrorStatus = B_OK;
}


${cppsuperlistenerclassname}::~${cppsuperlistenerclassname}()
{
}


void
${cppsuperlistenerclassname}::HandleError(status_t status, int32 line, const char* message)
{
    fprintf(stderr, "an error has arisen processing json for '${cppmodelclassname}'; %s", message);
    fErrorStatus = status;
}


void
${cppsuperlistenerclassname}::Complete()
{
}


status_t
${cppsuperlistenerclassname}::ErrorStatus()
{
    return fErrorStatus;
}


void
${cppsuperlistenerclassname}::SetStackedListener(
    ${cppstackedlistenerclassname}* stackedListener)
{
    if (fStackedListener != NULL)
        fStackedListener->WillPop();
    fStackedListener = stackedListener;
}

""").substitute(naming.todict()))

# single parser

    outfile.write(
        string.Template("""
${cpplistenerclassname}::${cpplistenerclassname}()
:
${cppsuperlistenerclassname}()
{
    fTarget = NULL;
}


${cpplistenerclassname}::~${cpplistenerclassname}()
{
}


bool
${cpplistenerclassname}::Handle(const BJsonEvent& event)
{
    if (fErrorStatus != B_OK)
       return false;
       
    if (fStackedListener != NULL)
        return fStackedListener->Handle(event);
    
    switch (event.EventType()) {
        
        case B_JSON_OBJECT_START:
        {
            ${subtype_cppstackedlistenerclassname}* nextListener = new ${subtype_cppstackedlistenerclassname}(
                this, NULL);
            fTarget = nextListener->Target();
            SetStackedListener(nextListener);
            break;
        }
              
        default:
            HandleError(B_NOT_ALLOWED, JSON_EVENT_LISTENER_ANY_LINE,
                "illegal state - unexpected json event parsing top level for ${cppmodelclassname}");
            break;
    }
    
    return ErrorStatus() == B_OK;
}


${cppmodelclassname}*
${cpplistenerclassname}::Target()
{
    return fTarget;
}

""").substitute(hdscommon.uniondicts(naming.todict(), subtypenaming.todict())))

    if supportbulkcontainer:

        # create a main listener that can work through the list of top level model objects and ping the listener

        outfile.write(
            string.Template("""
${cppbulkcontainerlistenerclassname}::${cppbulkcontainerlistenerclassname}(
    ${cppitemlistenerclassname}* itemListener) : ${cppsuperlistenerclassname}()
{
    fItemListener = itemListener;
}


${cppbulkcontainerlistenerclassname}::~${cppbulkcontainerlistenerclassname}()
{
}


bool
${cppbulkcontainerlistenerclassname}::Handle(const BJsonEvent& event)
{
    if (fErrorStatus != B_OK)
       return false;
       
    if (fStackedListener != NULL)
        return fStackedListener->Handle(event);
    
    switch (event.EventType()) {
        
        case B_JSON_OBJECT_START:
        {
            ${cppstackeditemlistenerlistenerclassname}* nextListener =
                new ${cppstackeditemlistenerlistenerclassname}(
                    this, NULL, fItemListener);
            SetStackedListener(nextListener);
            return true;
            break;
        }
              
        default:
            HandleError(B_NOT_ALLOWED, JSON_EVENT_LISTENER_ANY_LINE,
                "illegal state - unexpected json event parsing top level for ${cppbulkcontainerlistenerclassname}");
            break;
    }
    
    return ErrorStatus() == B_OK;
}

""").substitute(hdscommon.uniondicts(naming.todict(), subtypenaming.todict())))


def schematocppparser(inputfile, schema, outputdirectory, supportbulkcontainer):
    naming = CppParserNaming(schema)
    cpphfilename = os.path.join(outputdirectory, naming.cppsinglemainlistenerclassname() + '.h')
    cppifilename = os.path.join(outputdirectory, naming.cppsinglemainlistenerclassname() + '.cpp')

    with open(cpphfilename, 'w') as cpphfile:
        jscom.writetopcomment(cpphfile, os.path.split(inputfile)[1], 'Listener')
        guarddefname = 'GEN_JSON_SCHEMA_PARSER__%s_H' % (naming.cppsinglemainlistenerclassname().upper())

        cpphfile.write(
            string.Template("""
#ifndef ${guarddefname}
#define ${guarddefname}
""").substitute({'guarddefname': guarddefname}))

        cpphfile.write(
            string.Template("""
#include <JsonEventListener.h>

#include "${cppmodelclassname}.h"

class ${cppstackedlistenerclassname};

class ${cppsuperlistenerclassname} : public BJsonEventListener {
friend class ${cppstackedlistenerclassname};
public:
    ${cppsuperlistenerclassname}();
    virtual ~${cppsuperlistenerclassname}();
    
    void HandleError(status_t status, int32 line, const char* message);
    void Complete();
    status_t ErrorStatus();
    
protected:
    void SetStackedListener(${cppstackedlistenerclassname}* listener);
    status_t fErrorStatus;
    ${cppstackedlistenerclassname}* fStackedListener;
};


class ${cpplistenerclassname} : ${cppsuperlistenerclassname} {
friend class ${cppstackedlistenerclassname};
public:
    ${cpplistenerclassname}();
    virtual ~${cpplistenerclassname}();
    
    bool Handle(const BJsonEvent& event);
    ${cppmodelclassname}* Target();
    
private:
    ${cppmodelclassname}* fTarget;
};

""").substitute(naming.todict()))

# class interface for concrete class of single listener


        # If bulk enveloping is selected then also output a listener and an interface
        # which can deal with call-backs.

        if supportbulkcontainer:
            cpphfile.write(
                string.Template("""
class ${cppitemlistenerclassname} {
public:
    virtual void Handle(${cppmodelclassname}* item) = 0;
    virtual void Complete() = 0;
};
""").substitute(naming.todict()))

#             cpphfile.write(
#                 string.Template("""
# class ${cppbulk : %s {
# friend class %s;
# public:
#     %s(%s& itemListener);
#     ~%s();
#
#     bool Handle(const BJsonEvent& event);
#
# private:
#     %s* fItemListener;
# };
# """).substitute(naming.todict()))

            cpphfile.write(
                string.Template("""
class ${cppbulkcontainerlistenerclassname} : ${cppsuperlistenerclassname} {
friend class ${cppstackedlistenerclassname};
public:
    ${cppbulkcontainerlistenerclassname}(${cppitemlistenerclassname}* itemListener);
    ~${cppbulkcontainerlistenerclassname}();
    
    bool Handle(const BJsonEvent& event);
    
private:
    ${cppitemlistenerclassname}* fItemListener;
};
""").substitute(naming.todict()))

        cpphfile.write('\n#endif // %s' % guarddefname)

    with open(cppifilename, 'w') as cppifile:
        istate = CppParserImplementationState(cppifile, naming)
        jscom.writetopcomment(cppifile, os.path.split(inputfile)[1], 'Listener')
        cppifile.write('#include "%s"\n' % (naming.cppsinglemainlistenerclassname() + '.h'))
        cppifile.write('#include "List.h"\n\n')
        cppifile.write('#include <stdio.h>\n\n')

        cppifile.write('// #pragma mark - private interfaces for the stacked listeners\n\n')

        writerootstackedlistenerinterface(istate)
        writegeneralstackedlistenerinterface(istate)
        writestackedlistenerinterface(istate, schema)

        if supportbulkcontainer:
            writebulkcontainerstackedlistenerinterface(istate, schema)

        cppifile.write('// #pragma mark - implementations for the stacked listeners\n\n')

        writerootstackedlistenerimplementation(istate)
        writegeneralstackedlistenerimplementation(istate)
        writestackedlistenerimplementation(istate, schema)

        if supportbulkcontainer:
            writebulkcontainerstackedlistenerimplementation(istate, schema)

        cppifile.write('// #pragma mark - implementations for the main listeners\n\n')

        writemainlistenerimplementation(istate, schema, supportbulkcontainer)


def main():
    parser = argparse.ArgumentParser(description='Convert JSON schema to Haiku C++ Parsers')
    parser.add_argument('-i', '--inputfile', required=True, help='The input filename containing the JSON schema')
    parser.add_argument('--outputdirectory', help='The output directory where the C++ files should be written')
    parser.add_argument('--supportbulkcontainer', help='Produce a parser that deals with a bulk envelope of items',
                        action='store_true')

    args = parser.parse_args()

    outputdirectory = args.outputdirectory

    if not outputdirectory:
        outputdirectory = '.'

    with open(args.inputfile) as inputfile:
        schema = json.load(inputfile)
        schematocppparser(args.inputfile, schema, outputdirectory, args.supportbulkcontainer or False)

if __name__ == "__main__":
    main()