import { GraphPointer } from "clownface";
import { ClownfaceObject } from "../rdf/clownface-object";

import { rdfEnvironment } from "../rdf/rdf-environment";
import { cube, sh } from "../rdf/namespace";


export class CubeDimension extends ClownfaceObject {
    constructor(node: GraphPointer) {
        super(node);
    }

    toRdf(): string {
        const dataset = rdfEnvironment.dataset([...this._node.dataset.match(this._node.term)]);
        return dataset.toCanonical();
    }
}

