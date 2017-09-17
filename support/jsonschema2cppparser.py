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

    def cppsuperlistenerclassname(self):
        return 'Abstract' + self.cppmodelclassname() + 'JsonListener'

    def cpplistenerclassname(self):
        return self.cppmodelclassname() + 'JsonListener'

    def cppbulkcontainerlistenerclassname(self):
        return 'BulkContainer' + self.cppmodelclassname() + 'JsonListener'

    def cppbulkcontainerstackedlistenerclassname(self):
        return 'BulkContainer' + self.cppstackedlistenerclassname() + 'JsonListener'

    def cppbulkcontaineritemliststackedlistenerclassname(self):
        return 'BulkContainer_ItemList_' + self.cppstackedlistenerclassname() + 'JsonListener'

    def cppstackedlistenerclassname(self):
        return 'Stacked' + self.cpplistenerclassname()

    def cppstackeditemlistenerlistenerclassname(self):
        return 'ItemListener_' + self.cppstackedlistenerclassname()

    def cppgeneralobjectstackedlistenerclassname(self):
        return 'GeneralObject_Stacked' + self.cpplistenerclassname()

    def cppgeneralarraystackedlistenerclassname(self):
        return 'GeneralArray_Stacked' + self.cpplistenerclassname()

    def cppitemlistenerclassname(self):
        return self.cppmodelclassname() + 'ItemListener'


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
    void Pop();
    void Push(%s stackedListener);
    void WillPop();
    
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
%s::Push(%s stackedListener)
{
    fMainListener->SetStackedListener(stackedListener);
}
""" % (naming.cppstackedlistenerclassname(),
       naming.cppstackedlistenerclassname()))

    outfile.write("""
void
%s::WillPop()
{
}
""" % (naming.cppstackedlistenerclassname()))

    outfile.write("""
void
%s::Pop()
{
    fMainListener->SetStackedListener(fParent);
}
""" % naming.cppstackedlistenerclassname())


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
    generalobjectclassname = istate.naming().cppgeneralobjectstackedlistenerclassname()
    generalarrayclassname = istate.naming().cppgeneralobjectstackedlistenerclassname()

    writegeneralnooplistenerconstructordestructor(istate, naming.cppgeneralobjectstackedlistenerclassname())

    outfile.write("""
bool
%s::Handle(const BJsonEvent& event)
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
            Push(new %s(fMainListener, this));
            break;
            
        case B_JSON_ARRAY_START:
            Push(new %s(fMainListener, this));
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
""" % (generalobjectclassname, generalobjectclassname, generalarrayclassname))

    writegeneralnooplistenerconstructordestructor(istate, naming.cppgeneralarraystackedlistenerclassname())

    outfile.write("""
bool
%s::Handle(const BJsonEvent& event)
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
            Push(new %s(fMainListener, this));
            break;
            
        case B_JSON_ARRAY_START:
            Push(new %s(fMainListener, this));
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
    %s(
        %s* mainListener,
        %s* parent,
        %s* target);
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
            naming.cppstackedlistenerclassname(),
            naming.cpplistenerclassname(),
            typeinfo.cppmodelclassname(),
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
                writestackedlistenerinterface(istate, propmetadata['items'])
            elif propmetadata['type'] == 'object':
                writestackedlistenerinterface(istate, propmetadata)


def writebulkcontainerstackedlistenerinterface(istate, schema):
    naming = istate.naming()
    typeinfo = CppParserSubSchemaTypeInfo(schema, naming)
    outfile = istate.outputfile()

# This is a sub-class of the main model object listener.  It will ping out to an item listener
# when parsing is complete.

    outfile.write("""
class %s : public %s {
public:
    %s(
        %s* mainListener,
        %s* parent,
        %s* itemListener);
    ~%s();
    
    void WillPop();
        
private:
    %s* fItemListener;
}
""" % (
        naming.cppstackeditemlistenerlistenerclassname(),
        typeinfo.stackedlistenerclassname(),
        naming.cppstackeditemlistenerlistenerclassname(),
        naming.cpplistenerclassname(),
        naming.cppstackedlistenerclassname(),
        naming.cppitemlistenerclassname(),
        naming.cppstackeditemlistenerlistenerclassname(),
        naming.cppitemlistenerclassname()))

# This stacked listener is for handling the bulk data container.

    outfile.write("""
class %s : public %s {
public:
    %s(
        %s* mainListener,
        %s* parent,
        %s* itemListener);
    ~%s();
    
    bool Handle(const BJsonEvent& event);
        
private:
    BString fNextItemName;
    %s* fItemListener;
}
""" % (
        naming.cppbulkcontainerstackedlistenerclassname(),
        naming.cppstackedlistenerclassname(),
        naming.cppbulkcontainerstackedlistenerclassname(),
        naming.cpplistenerclassname(),
        naming.cppstackedlistenerclassname(),
        naming.cppitemlistenerclassname(),
        naming.cppbulkcontainerstackedlistenerclassname(),
        naming.cppitemlistenerclassname()))

    outfile.write("""
class %s : public %s {
public:
    %s(
        %s* mainListener,
        %s* parent,
        %s* itemListener);
    ~%s();
    
    bool Handle(const BJsonEvent& event);
        
private:
    %s* fItemListener;
}
""" % (
        naming.cppbulkcontaineritemliststackedlistenerclassname(),
        naming.cppstackedlistenerclassname(),
        naming.cppbulkcontaineritemliststackedlistenerclassname(),
        naming.cpplistenerclassname(),
        naming.cppstackedlistenerclassname(),
        naming.cppitemlistenerclassname(),
        naming.cppbulkcontaineritemliststackedlistenerclassname(),
        naming.cppitemlistenerclassname()))


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


def writestackedlistenertypedobjectimplementation(istate, schema):
    naming = istate.naming()
    typeinfo = CppParserSubSchemaTypeInfo(schema, naming)
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
%s::%s(
    %s* mainListener,
    %s* parent,
    %s* target)
    :
    %s(mainListener, parent)
{
    fTarget = target;
}
""" % (
        typeinfo.stackedlistenerclassname(),
        typeinfo.stackedlistenerclassname(),
        naming.cpplistenerclassname(),
        naming.cppstackedlistenerclassname(),
        typeinfo.cppmodelclassname(),
        naming.cppstackedlistenerclassname()
    ))

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
            outfile.write('                        fTarget->Set%s(nextListener->Target());\n' % (
                subschematypeinfo.cppmodelclassname()))
            outfile.write('                        Push(nextListener);\n')
            outfile.write('                    }\n')

            objectifclausekeyword = 'else if'

    outfile.write('                    %s (1 == 1) {\n' % objectifclausekeyword)
    outfile.write('                        %s* nextListener = new %s(fMainListener, this);\n' % (
        naming.cppstackedlistenerclassname(),
        naming.cppgeneralobjectstackedlistenerclassname()))
    outfile.write('                        Push(nextListener);\n')
    outfile.write('                    }\n')

    outfile.write('                    break\n')

    # array type; could be an array of objects or otherwise just drop into a placebo consumer to keep
    # the parse structure working.

    outfile.write('                case B_JSON_ARRAY_START:\n')

    objectifclausekeyword = 'if'

    for propname, propmetadata in schema['properties'].items():
        if propmetadata['type'] == jscom.JSON_TYPE_ARRAY:
            subschematypeinfo = CppParserSubSchemaTypeInfo(propmetadata['items'], naming)

            outfile.write('                    %s (fNextItemName == "%s") {\n' % (objectifclausekeyword, propname))
            outfile.write('                        %s* nextListener = new %s(fMainListener, this);\n' % (
                naming.cppstackedlistenerclassname(),
                subschematypeinfo.stackedlistlistenerclassname()))
            outfile.write('                        fTarget->Set%s(nextListener->Target());\n' % (
                subschematypeinfo.cppmodelclassname()))
            outfile.write('                        Push(nextListener);\n')
            outfile.write('                    }\n')

            objectifclausekeyword = 'else if'

    outfile.write('                    %s (1 == 1) {\n' % objectifclausekeyword)
    outfile.write('                        %s* nextListener = new %s(fMainListener, this);\n' % (
        naming.cppstackedlistenerclassname(),
        naming.cppgeneralarraystackedlistenerclassname()))
    outfile.write('                        Push(nextListener);\n')
    outfile.write('                    }\n')

    outfile.write('                    break\n')

    outfile.write("""
            }
            
            fNextItemName.SetTo("");
            break;
    }
    
    return ErrorStatus() == B_OK;
}
""")


def writestackedlistenertypedobjectlistimplementation(istate, schema):
    naming = istate.naming()
    typeinfo = CppParserSubSchemaTypeInfo(schema, naming)
    outfile = istate.outputfile()

    outfile.write("""
%s::%s(
    %s* mainListener,
    %s* parent)
    :
    %s(mainListener, parent)
{
    fTarget = new BList();
}
""" % (
        typeinfo.stackedlistenerclassname(),
        typeinfo.stackedlistenerclassname(),
        naming.cpplistenerclassname(),
        naming.cppstackedlistenerclassname(),
        naming.cppstackedlistenerclassname()))

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
    switch (event.EventType()) {
        
        case B_JSON_ARRAY_END:
            Pop();
            delete this;
            break;   
        
        case B_JSON_OBJECT_START:
            %s* nextListener = new %s(fMainListener, this);
            fTarget->AddItem(nextListener->Target());
            Push(nextListener);
            break;
            
        default:
            HandleError(B_NOT_ALLOWED, JSON_EVENT_LISTENER_ANY_LINE, "illegal state - unexpected json event parsing an array of %s");
            break;
    }
    
    return ErrorStatus() == B_OK;
}
""" % (typeinfo.stackedlistlistenerclassname(),
       typeinfo.stackedlistenerclassname(),
       typeinfo.stackedlistenerclassname(),
       typeinfo.stackedlistenerclassname()))


def writebulkcontainerstackedlistenerimplementation(istate, schema):
    naming = istate.naming()
    typeinfo = CppParserSubSchemaTypeInfo(schema, naming)
    outfile = istate.outputfile()

    outfile.write("""
%s::%s(%s* mainListener, %s* parent, %s* itemListener)
:
%s(mainListener, parent)
{
    fItemListener = itemListener;
}
""" % (
        naming.cppstackeditemlistenerlistenerclassname(),
                  naming.cppstackeditemlistenerlistenerclassname(),
        naming.cpplistenerclassname(),
        naming.cppstackedlistenerclassname(),
        naming.cppitemlistenerclassname(),
        typeinfo.stackedlistenerclassname()
    ))

    outfile.write("""
%s::~%s()
{
}
""" % (
        naming.cppstackeditemlistenerlistenerclassname(),
        naming.cppstackeditemlistenerlistenerclassname()
    ))

    outfile.write("""
%s::WillPop()
{
    fItemListener->Handle(fTarget);
    delete fTarget;
}
""" % (
        naming.cppstackeditemlistenerlistenerclassname(),
    ))

    outfile.write("""
%s::%s(%s* mainListener, %s* parent, %s* itemListener)
:
%s(mainListener, parent)
{
    fItemListener = itemListener;
}
""" % (
        naming.cppbulkcontainerstackedlistenerclassname(),
        naming.cppbulkcontainerstackedlistenerclassname(),
        naming.cpplistenerclassname(),
        naming.cppstackedlistenerclassname(),
        naming.cppitemlistenerclassname(),
        typeinfo.stackedlistenerclassname()
    ))

    outfile.write("""
%s::~%s()
{
}
""" % (
        naming.cppbulkcontainerstackedlistenerclassname(),
        naming.cppbulkcontainerstackedlistenerclassname()
    ))

    outfile.write("""
bool
%s::Handle(const BJsonEvent& event)
{
    switch (event.EventType()) {
        
        case B_JSON_ARRAY_END:
            HandleError(B_NOT_ALLOWED, JSON_EVENT_LISTENER_ANY_LINE, "illegal state - unexpected start of array");
            break;
        
        case B_JSON_OBJECT_NAME:
            fNextItemName = event.Content();
            break;
            
        case B_JSON_OBJECT_START:
            Push(new %s(fMainListener, this));
            break;
            
        case B_JSON_ARRAY_START:
            if (fNextItemName == "items")
                Push(new %s(fMainListener, this, fItemListener));
            else
                Push(new %s(fMainListener, this));
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
""" % (
        naming.cppbulkcontainerstackedlistenerclassname(),
        naming.cppgeneralobjectstackedlistenerclassname(),
        naming.cppbulkcontaineritemliststackedlistenerclassname(),
        naming.cppgeneralarraystackedlistenerclassname()

    ))

    outfile.write("""
%s::%s(%s* mainListener, %s* parent, %s* itemListener)
:
%s(mainListener, parent)
{
    fItemListener = itemListener;
}
""" % (
        naming.cppbulkcontaineritemliststackedlistenerclassname(),
        naming.cppbulkcontaineritemliststackedlistenerclassname(),
        naming.cpplistenerclassname(),
        naming.cppstackedlistenerclassname(),
        naming.cppitemlistenerclassname(),
        typeinfo.stackedlistenerclassname()
    ))

    outfile.write("""
%s::~%s()
{
}
""" % (
        naming.cppbulkcontaineritemliststackedlistenerclassname(),
        naming.cppbulkcontaineritemliststackedlistenerclassname()
    ))

    outfile.write("""
bool
%s::Handle(const BJsonEvent& event)
{
    switch (event.EventType()) {
        
        case B_JSON_OBJECT_START:
            Push(new %s(fMainListener, this, fItemListener));
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
""" % (
        naming.cppbulkcontainerstackedlistenerclassname(),
        naming.cppstackeditemlistenerlistenerclassname()
    ))


def writestackedlistenerimplementation(istate, schema):
    typeinfo = CppParserSubSchemaTypeInfo(schema, istate.naming())

    if not istate.isimplementationhandledcppname(typeinfo.cppmodelclassname()):
        istate.addimplementationhandledcppname(typeinfo.cppmodelclassname())

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
    typeinfo = CppParserSubSchemaTypeInfo(schema, istate.naming())

# super (abstract) listener

    outfile.write("""
%s::%s()
{
    fErrorStatus = B_OK;
}
""" % (
        naming.cppsuperlistenerclassname(),
        naming.cppsuperlistenerclassname()))

    outfile.write("""
%s::~%s()
{
}
""" % (
        naming.cppsuperlistenerclassname(),
        naming.cppsuperlistenerclassname()))

    outfile.write("""
void
%s::HandleError(status_t status, int32 line, const char* message)
{
    fprintf(stderr, "an error has arisen processing json for '%s'; %%s", message);
    fErrorStatus = status;
}
""" % (
        naming.cppsuperlistenerclassname(),
        naming.cppmodelclassname()
    ))

    outfile.write("""
void
%s::Complete()
{
}
""" % naming.cppsuperlistenerclassname())

    outfile.write("""
status_t
%s::ErrorStatus()
{
    return fErrorStatus;
}
""" % naming.cppsuperlistenerclassname())

    outfile.write("""
void
%s::SetStackedListener(%s* stackedListener)
{
    if (fStackedListener != NULL)
        fStackedListener->WillPop();
    fStackedListener = stackedListener;
}
""" % (naming.cppsuperlistenerclassname(), naming.cppstackedlistenerclassname()))

# single parser

    outfile.write("""
%s::%s() : %s()
{
    fTarget = NULL;
}
""" % (
        naming.cpplistenerclassname(),
        naming.cpplistenerclassname(),
        naming.cppsuperlistenerclassname()))

    outfile.write("""
%s::~%s()
{
}
""" % (
        naming.cpplistenerclassname(),
        naming.cpplistenerclassname()))

    outfile.write("""
bool
%s::Handle(const BJsonEvent& event)
{
    if (fErrorStatus != B_OK)
       return false;
       
    if (fStackedListener != NULL)
        return fStackedListener->Handle(event);
    
    switch (event.EventType()) {
        
        case B_JSON_OBJECT_START:
            %s* nextListener = new %s();
            fTarget = nextListener->Target();
            Push(fTarget);
            return true;
            break;
              
        default:
            HandleError(B_NOT_ALLOWED, JSON_EVENT_LISTENER_ANY_LINE, "illegal state - unexpected json event parsing top level for %s");
            break;
    }
    
    return ErrorStatus() == B_OK;
}
""" % (
        naming.cpplistenerclassname(),
        typeinfo.stackedlistenerclassname(),
        typeinfo.stackedlistenerclassname(),
        typeinfo.cppmodelclassname()
    ))

    outfile.write("""
%s*
%s::Target()
{
    return fTarget;
}
""" % (naming.cppmodelclassname(),
       naming.cpplistenerclassname()))

    if supportbulkcontainer:

        # create a main listener that can work through the list of top level model objects and ping the listener

        outfile.write("""
%s::%s(%s& itemListener) : %s()
{
    fItemListener = itemListener;
}

%s::~%s()
{
}
""" % (
            naming.cppbulkcontainerlistenerclassname(),
            naming.cppbulkcontainerlistenerclassname(),
            naming.cppsuperlistenerclassname(),
            naming.cppitemlistenerclassname(),
            naming.cppbulkcontainerlistenerclassname(),
            naming.cppbulkcontainerlistenerclassname()
        ))

    outfile.write("""
%s::Handle(const BJsonEvent& event)
{
    if (fErrorStatus != B_OK)
       return false;
       
    if (fStackedListener != NULL)
        return fStackedListener->Handle(event);
    
    switch (event.EventType()) {
        
        case B_JSON_OBJECT_START:
            %s* nextListener = new %s();
            fTarget = nextListener->Target();
            Push(fTarget);
            return true;
            break;
              
        default:
            HandleError(B_NOT_ALLOWED, JSON_EVENT_LISTENER_ANY_LINE, "illegal state - unexpected json event parsing top level for %s");
            break;
    }
    
    return ErrorStatus() == B_OK;
}
""" % (
        naming.cppbulkcontainerlistenerclassname(),
        naming.cppstackeditemlistenerlistenerclassname(),
        naming.cppstackeditemlistenerlistenerclassname(),
        naming.cppbulkcontainerlistenerclassname()
    ))


def schematocppparser(inputfile, schema, outputdirectory, supportbulkcontainer):
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
                       ) % (guarddefname, guarddefname,
                            naming.cppmodelclassname(),
                            naming.cppstackedlistenerclassname()))

# class interface for superclass of main listeners

        cpphfile.write((
                           'class %s : public BJsonEventListener {\n'
                           'friend class %s;\n'
                           'public:\n'
                       ) % (naming.cppsuperlistenerclassname(),
                            naming.cppstackedlistenerclassname()))

        cpphfile.write((
                           '    %s();\n'
                           '    virtual ~%s();\n'
                       ) % (naming.cppsuperlistenerclassname(),
                            naming.cppsuperlistenerclassname()
                            ))

        cpphfile.write('\n')

        cpphfile.write((
            '    void HandleError(status_t status, int32 line, const char* message);\n'
            '    void Complete();\n'
            '    status_t ErrorStatus();\n'
        ))

        cpphfile.write('\n')

        cpphfile.write((
                           'protected:\n'
                           '    void SetStackedListener(%s *listener);\n'
                       ) % (
                           naming.cppstackedlistenerclassname()
                       ))

        cpphfile.write('\n')

        cpphfile.write((
                           'private:\n'
                           '    status_t fErrorStatus;\n'
                           '    %s* fStackedListener;\n'
                       ) % naming.cppstackedlistenerclassname())

        cpphfile.write('}\n\n')

# class interface for concrete class of single listener

        cpphfile.write((
                           'class %s : public %s {\n'
                           'friend class %s;\n'
                           'public:\n'
                       ) % (naming.cpplistenerclassname(),
                            naming.cppsuperlistenerclassname(),
                            naming.cppstackedlistenerclassname()))

        cpphfile.write((
                           '    %s();\n'
                           '    virtual ~%s();\n'
                       ) % (naming.cpplistenerclassname(),
                            naming.cpplistenerclassname()
                            ))

        cpphfile.write('\n')

        cpphfile.write((
            '    bool Handle(const BJsonEvent& event);\n'
        ))

        cpphfile.write('    %s Target();\n' % naming.cppmodelclassname())

        cpphfile.write('\n')

        cpphfile.write((
                           'private:\n'
                           '    %s* fTarget;\n'
                       ) % naming.cppmodelclassname())

        cpphfile.write('}\n')

        # If bulk enveloping is selected then also output a listener and an interface
        # which can deal with call-backs.

        if supportbulkcontainer:
            cpphfile.write("""
class %s : %s {
public:
    virtual void Handle(%s item) = 0;
    virtual void Complete() = 0;
}
""" % (naming.cppitemlistenerclassname(),
       naming.cppsuperlistenerclassname(),
       naming.cppmodelclassname()))

            cpphfile.write("""
class %s : %s {
friend class %s;
public:
    %s(%s& itemListener);
    ~%s();
    
    bool Handle(const BJsonEvent& event);
    
private:
    %s* fItemListener;
}
""" % (naming.cppbulkcontainerlistenerclassname(),
       naming.cppsuperlistenerclassname(),
       naming.cppstackedlistenerclassname(),
       naming.cppbulkcontainerlistenerclassname(),
       naming.cppitemlistenerclassname(),
       naming.cppbulkcontainerlistenerclassname(),
       naming.cppitemlistenerclassname()))

        cpphfile.write('\n#endif // %s' % guarddefname)

    with open(cppifilename, 'w') as cppifile:
        istate = CppParserImplementationState(cppifile, naming)
        jscom.writetopcomment(cppifile, os.path.split(inputfile)[1], 'Listener')
        cppifile.write('#include "%s"\n' % (naming.cpplistenerclassname() + '.h'))
        cppifile.write('#include "List.h"\n')

        writerootstackedlistenerinterface(istate)
        writegeneralstackedlistenerinterface(istate)
        writestackedlistenerinterface(istate, schema)

        if supportbulkcontainer:
            writebulkcontainerstackedlistenerinterface(istate, schema)

        writerootstackedlistenerimplementation(istate)
        writegeneralstackedlistenerimplementation(istate)
        writestackedlistenerimplementation(istate, schema)

        if supportbulkcontainer:
            writebulkcontainerstackedlistenerimplementation(istate, schema)

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