# =====================================
# Copyright 2024, Andrew Lindesay
# Distributed under the terms of the MIT License.
# =====================================

# This script is able to take the Cayenne data model expressed as an XML file and
# convert it into a Graphor file for use with the Graphor UML modelling tool. An
# image of this model appears in the documentation. You can find out more about
# Graphor here; https://gaphor.org/

# =====================================

# allows for forward references in the type hints
from __future__ import annotations

import argparse
import uuid
from xml.dom.minidom import parse
from ustache import ustache
from typing import List


GRAPHOR_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<gaphor xmlns="http://gaphor.sourceforge.net/model" version="3.0" gaphor-version="2.23.2">
    <Package id="{{package_guid}}">
        <name>
            <val>{{name}}</val>
        </name>
        <ownedDiagram>
            <reflist>
                <ref refid="{{diagram_guid}}"/>
            </reflist>
        </ownedDiagram>
        <ownedType>
            <reflist>{{#klasses}}
                <ref refid="{{guid}}"/>{{/klasses}}
            </reflist>
        </ownedType>
    </Package>
    <Diagram id="{{diagram_guid}}">
        <element>
            <ref refid="{{package_guid}}"/>
        </element>
        <name>
            <val>{{name}}</val>
        </name>
        <ownedPresentation>
            <reflist>{{#klasses}}<ref refid="{{item_guid}}"/>
            {{/klasses}}{{#associations}}<ref refid="{{item_guid}}"/>
            {{/associations}}</reflist>
        </ownedPresentation>
    </Diagram>
    {{#klasses}}
    <Class id="{{guid}}">
        <name>
            <val>{{name}}</val>
        </name>
        <ownedAttribute>
            <reflist>{{#properties}}
                <ref refid="{{guid}}"/>{{/properties}}
            </reflist>
        </ownedAttribute>
        <package>
            <ref refid="{{package_diagram.package_guid}}"/>
        </package>
        <presentation>
            <reflist>
                <ref refid="{{item_guid}}"/>
            </reflist>
        </presentation>
    </Class>
    <ClassItem id="{{item_guid}}">
        <diagram>
            <ref refid="{{package_diagram.diagram_guid}}"/>
        </diagram>
        <show_operations>
            <val>0</val>
        </show_operations>
        <subject>
            <ref refid="{{guid}}"/>
        </subject>
    </ClassItem>
    {{#properties}}
    <Property id="{{guid}}">
        <class_>
            <ref refid="{{klass.guid}}"/>
        </class_>
        <name>
            <val>{{name}}</val>
        </name>
    </Property>    
    {{/properties}}
    {{/klasses}}
    {{#associations}}
    <AssociationItem id="{{item_guid}}">
        <diagram>
            <ref refid="{{package_diagram.diagram_guid}}"/>
        </diagram>
        <horizontal>
            <val>0</val>
        </horizontal>
        <subject>
            <ref refid="{{guid}}"/>
        </subject>
        <!--
        <head_subject>
            <ref refid="{{source_property.guid}}"/>
        </head_subject>
        -->
        <!--
        <tail_subject>
            <ref refid="{{target_property.guid}}"/>
        </tail_subject>
        -->
        <head-connection>
            <ref refid="{{source_property.klass.item_guid}}"/>
        </head-connection>
        <tail-connection>
            <ref refid="{{target_property.klass.item_guid}}"/>
        </tail-connection>
    </AssociationItem>
    <Association id="{{guid}}">
        <name>
            <val>{{name}}</val>
        </name>
        <!--
        <memberEnd>
            <reflist>
                <ref refid="{{target_property.guid}}"/>
                <ref refid="{{source_property.guid}}"/>
            </reflist>
        </memberEnd>
        -->
        <!--
        <ownedEnd>
            <reflist>
                <ref refid="{{target_property.guid}}"/>
                <ref refid="{{source_property.guid}}"/>
            </reflist>
        </ownedEnd>
        -->
        <package>
            <ref refid="{{package_diagram.package_guid}}"/>
        </package>
        <presentation>
            <reflist>
                <ref refid="{{item_guid}}"/>
            </reflist>
        </presentation>
    </Association>
    {{/associations}}
</gaphor>
"""


class Klass:

    _name = None
    _properties = None
    _guid = None
    _item_guid = None
    _package_diagram = None

    def __init__(self, name: str):
        self._name = name
        self._guid = uuid.uuid4().hex
        self._item_guid = uuid.uuid4().hex
        self._properties = []

    def name(self):
        return self._name

    def properties(self):
        return self._properties

    def guid(self):
        return self._guid

    def item_guid(self):
        return self._item_guid

    def package_diagram(self):
        return self._package_diagram

    def set_package_diagram(self, value: PackageDiagram):
        self._package_diagram = value

    def add_property(self, property: Property):
        self._properties.append(property)
        property.set_klass(self)

    def property_for_name(self, name: str) -> Property|None:
        for property in self._properties:
            if name == property.name():
                return property
        raise RuntimeError("not able to find property [%s] in class [%s]" % (name, self._name))


class PackageDiagram:

    _name = None
    _package_guid = None
    _diagram_guid = None
    _klasses = None
    _associations = None

    def __init__(self, name: str):
        self._name = name
        self._package_guid = uuid.uuid4().hex
        self._diagram_guid = uuid.uuid4().hex
        self._klasses = []
        self._associations = []

    def name(self) -> str:
        return self._name

    def package_guid(self) -> str:
        return self._package_guid

    def diagram_guid(self) -> str:
        return self._diagram_guid

    def klasses(self) -> List[Klass]:
        return self._klasses

    def add_klass(self, klass: Klass):
        self._klasses.append(klass)
        klass.set_package_diagram(self)

    def klass_for_name(self, name: str) -> Klass|None:
        for klass in self._klasses:
            if name == klass.name():
                return klass
        raise RuntimeError("not able to find klass [%s]" % (name))

    def associations(self) -> List[Association]:
        return self._associations

    def add_association(self, association: Association):
        self._associations.append(association)
        association.set_package_diagram(self)


class Property:

    _name = None
    _guid = None
    _item_guid = None
    _klass = None

    def __init__(self, name: str):
        self._name = name
        self._guid = uuid.uuid4().hex
        self._item_guid = uuid.uuid4().hex

    def name(self) -> str:
        return self._name

    def guid(self) -> str:
        return self._guid

    def item_guid(self) -> str:
        return self._item_guid

    def klass(self) -> Klass:
        return self._klass

    def set_klass(self, value: Klass):
        self._klass = value


class Association:

    _name = None
    _guid = None
    _item_guid = None
    _source_property = None
    _target_property = None
    _package_diagram = None

    def __init__(
        self,
        name: str,
        source_property: Property,
        target_property: Property):
        self._name = name
        self._source_property = source_property
        self._target_property = target_property
        self._guid = uuid.uuid4().hex
        self._item_guid = uuid.uuid4().hex

    def name(self) -> str:
        return self._name

    def guid(self) -> str:
        return self._guid

    def item_guid(self) -> str:
        return self._item_guid

    def source_property(self) -> Property:
        return self._source_property

    def target_property(self) -> Property:
        return self._target_property

    def package_diagram(self) -> PackageDiagram:
        return self._package_diagram

    def set_package_diagram(self, value: PackageDiagram):
        self._package_diagram = value





def main():
    parser = argparse.ArgumentParser(description='Tool to render a Gaphor ERD document from the Cayenne model.')
    parser.add_argument(
        '--output-graphor-file',
        dest="output_graphor_file",
        help='the generated Gaphor file',
        required=True)
    parser.add_argument(
        '--input-cayenne-map-file',
        dest="input_cayenne_map_file",
        help='the input Cayenne map file',
        default="./haikudepotserver-core/src/main/resources/HaikuDepot.map.xml")
    pargs = parser.parse_args()

    document = parse(pargs.input_cayenne_map_file)
    dataMapEls = document.getElementsByTagName("data-map")

    if None is dataMapEls or 1 != len(dataMapEls):
        raise RuntimeError("missing expected top level element")

    dataMapEl = dataMapEls[0]

    package_diagram = PackageDiagram("model")

    for dataEntityEl in dataMapEl.getElementsByTagName("db-entity"):
        klass = Klass(dataEntityEl.getAttribute("name"))

        dbAttributeEls = dataEntityEl.getElementsByTagName("db-attribute")

        for dbAttributeEl in dbAttributeEls:
            prop = Property(dbAttributeEl.getAttribute("name"))
            klass.add_property(prop)

        package_diagram.add_klass(klass)

    for dbRelationshipEl in dataMapEl.getElementsByTagName("db-relationship"):
        name = dbRelationshipEl.getAttribute("name")

        if not dbRelationshipEl.getAttribute("toMany") == "true":

            sourceEntityName = dbRelationshipEl.getAttribute("source")
            targetEntityName = dbRelationshipEl.getAttribute("target")

            dbAttributePairEls = dbRelationshipEl.getElementsByTagName("db-attribute-pair")

            if 1 != len(dbAttributePairEls):
                raise RuntimeError("expected one `db-attribute-pair`")

            dbAttributePairEl = dbAttributePairEls[0]

            sourcePropertyName = dbAttributePairEl.getAttribute("source")
            targetPropertyName = dbAttributePairEl.getAttribute("target")

            association = Association(
                name,
                package_diagram.klass_for_name(sourceEntityName).property_for_name(sourcePropertyName),
                package_diagram.klass_for_name(targetEntityName).property_for_name(targetPropertyName)
            )

            package_diagram.add_association(association)

# write the output XML

    def ustache_getter(
            scope,
            scopes: List,
            key: str,
            default):
        keys = key.split(".")

        if 0 == len(keys):
            return scope

        sub_scope = getattr(scope, keys[0])()

        if 1 == len(keys):
            if None == sub_scope:
                return default
            return sub_scope

        sub_scopes = scopes.copy()
        sub_scopes.append(sub_scope)
        sub_key = ".".join(keys[1:])

        return ustache_getter(sub_scope, sub_scopes, sub_key, default)

    with open(pargs.output_graphor_file, "w") as f:
        f.write(ustache.render(
            GRAPHOR_TEMPLATE,
            package_diagram,
            getter=ustache_getter,
        ))
        f.flush()


if __name__ == "__main__":
    main()
