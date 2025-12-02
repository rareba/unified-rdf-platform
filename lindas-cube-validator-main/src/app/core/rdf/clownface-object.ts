import { GraphPointer } from 'clownface';

import { rdfEnvironment } from './rdf-environment';

export class ClownfaceObject {

    static getPredicatesForNode(node: GraphPointer): string[] {

        const predicateSet = new Set<string>([...node.dataset.match(node.term)].map(quad => quad.predicate.value));
        return [...predicateSet];

    }

    protected readonly _node: GraphPointer;

    constructor(node: GraphPointer) {
        this._node = node;
    }

    get node(): GraphPointer {
        return this._node;
    }

    get iri(): string {
        return this._node.value;
    }

    availablePredicates(): string[] {
        return ClownfaceObject.getPredicatesForNode(this._node);
    }


    toTable(): ObjectTripleTable[] {
        const subject = `<${this._node.term.value}>`;
        const table = this.availablePredicates().flatMap(predicateString => {
            const namedNodePredicate = rdfEnvironment.namedNode(predicateString);
            const objects = this._node.out(namedNodePredicate).toArray().map(object => {
                const termType = object.term.termType;
                switch (termType) {
                    case 'NamedNode':
                        return `<${object.term.value}>`;
                    case 'Literal':
                        const value = `"${object.term.value}"`;
                        const language = object.term.language ?? '';
                        const datatype = object.term.datatype.value ?? '';

                        return `${value}${language ? `@${language}` : ''}${datatype ? `^^<${datatype}>` : ''}`;
                    case 'BlankNode':
                        return `_:${object.term.value}`;
                    default:
                        return object.term.value;
                }
            });
            return objects.map(object => ({ subject, predicate: `<${predicateString}>`, object }));
        });
        return table;

    }
}




export interface ObjectTripleTable {
    subject: string;
    predicate: string;
    object: string;

}