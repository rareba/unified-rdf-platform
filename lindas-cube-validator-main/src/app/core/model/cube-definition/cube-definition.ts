import { GraphPointer } from "clownface";
import { ClownfaceObject } from "../../rdf/clownface-object";
import { CubeDimension } from "../cube-dimension";
import { cube, schema, sh } from "../../rdf/namespace";
import { rdfEnvironment } from "../../rdf/rdf-environment";

import { ValidationProfile, ValidationProfiles } from "../../constant/validation-profile";
import { MultiLanguageCubeItem } from "../../service/endpoint/model/cube-item";

export class CubeDefinition extends MultiLanguageCubeItem {
    private _label: string[] | undefined = undefined;

    constructor(node: GraphPointer) {
        super(node);
    }



    getValidationProfile(): ValidationProfile {
        // get all work examples and check if there is a specific one Visualize or OpenDataSwiss
        const validationProfiles = this.getAvailableValidationProfiles();
        if (validationProfiles.length === 0) {
            return ValidationProfiles.getDefaultProfile();
        }
        if (validationProfiles.length > 1) {
            console.warn('Multiple work examples found. Using the first one.', validationProfiles.map(p => p.label).join(', '), 'use', validationProfiles[0].label, 'as validation profile.');
        }
        const profile = validationProfiles[0];
        return profile;
    }

    getAvailableValidationProfiles(): ValidationProfile[] {
        const configuredProfiles = ValidationProfiles.getProfiles();
        const workExamples = this._node.out(schema['workExample']).values;

        // filter configured profiles with available work examples
        const availableProfiles = configuredProfiles.filter(profile => workExamples.includes(profile.workNodeIri));

        // add default profile
        availableProfiles.push(ValidationProfiles.getDefaultProfile());


        return availableProfiles;
    }




    get dimensions(): CubeDimension[] {
        return this._node.out(cube['observationConstraint']).out(sh['property']).map(node => new CubeDimension(node));
    }

    describe(): string {
        // .filter(p => (p !== 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type') && !p.startsWith('https://cube.link/'))
        const predicateNodes = this.availablePredicates().sort((a, b) => a.localeCompare(b)).map(predicateIri => rdfEnvironment.namedNode(predicateIri));
        const whatever = predicateNodes.map(predicateNode => {
            return this._node.out(predicateNode).map(node => {
                const nodeProps: NodeProperty = this.describeNode(predicateNode.value, node);
                return nodeProps;

            });
        });

        return JSON.stringify(whatever, null, 2);
    }


    private describeNode(predicate: string, node: GraphPointer): NodeProperty {
        if (node.term.termType === 'NamedNode') {
            const nodeProperty: NodeProperty = {
                predicate: predicate,
                value: node.value
            };
            return nodeProperty;
        }

        if (node.term.termType === 'Literal') {
            const literalProperty: LiteralProperty = {
                predicate: predicate,
                value: node.value,
                language: node.term.language ?? null,
                datatype: node.term.datatype.value
            };
            return literalProperty;
        }

        // it's a blank node
        if (node.isList()) {
            const list = [...node.list()];
            const listItems = list.map(listItem => {
                if (listItem.term.termType === 'NamedNode') {
                    const nodeProperty: NodeProperty = {
                        predicate: predicate,
                        value: listItem.value
                    };
                    return nodeProperty;
                }
                if (listItem.term.termType === 'Literal') {
                    const literalProperty: LiteralProperty = {
                        predicate: predicate,
                        value: listItem.value,
                        language: listItem.term.language ?? null,
                        datatype: listItem.term.datatype.value
                    };
                    return literalProperty;
                }

                // it's a blank node
                const nodeProperty: NodeProperty = {
                    predicate: predicate,
                    value: listItem.value
                };
                return nodeProperty;
            });
            const ListProperty: ListProperty = {
                predicate: predicate,
                value: node.value,
                items: listItems
            };

            return ListProperty;

        }

        const blankNodeProperty: BlankNodeProperty = {
            predicate: predicate,
            value: node.value,
            properties: []
        };

        const clownfaceObject = new ClownfaceObject(node);
        const predicates = clownfaceObject.availablePredicates().map(p => rdfEnvironment.namedNode(p))
        const properties = predicates.flatMap(predicateNode => {
            const what = node.out(predicateNode).map(n => {
                return this.describeNode(predicateNode.value, n);
            });

            return what;
        });
        blankNodeProperty.properties = properties;
        return blankNodeProperty;
    }

}

interface NodeProperty {
    predicate: string;
    value: string;
}

interface LiteralProperty extends NodeProperty {
    language: string | null;
    datatype: string;
}

interface ListProperty extends NodeProperty {
    items: NodeProperty[];
}

interface BlankNodeProperty extends NodeProperty {
    properties: NodeProperty[];
}


interface LangString {
    value: string;
    lang?: string;
}