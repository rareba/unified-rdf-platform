import { GraphPointer } from "clownface";

import { ClownfaceObject } from "../../../../core/rdf/clownface-object";
import { cube, rdf, sh } from "../../../../core/rdf/namespace";



export class ValidationResult extends ClownfaceObject {
    private _focusNode: ClownfaceObject | null | undefined = undefined;
    private _sourceShape: ClownfaceObject | null | undefined = undefined;
    private _severity: string | undefined | null = undefined;
    private _path: string | undefined = undefined;
    private _literalValue: string | undefined | null = undefined;
    private _nodeValue: ClownfaceObject | null | undefined = undefined;
    private _dimensionValue: ResultDimension | undefined | null = undefined;
    private _detail: ValidationResultDetail[] | null = null;

    constructor(node: GraphPointer) {
        super(node);
    }

    get focusNode(): ClownfaceObject | null {
        if (this._focusNode === undefined) {
            const focusNodes = this._node.out(sh['focusNode']);
            if (focusNodes.values.length === 0) {
                this._focusNode = null;
            } else {
                if (focusNodes.values.length > 1) {
                    console.warn('Multiple focus nodes found for validation result. Using the first one.');
                }
                this._focusNode = new ClownfaceObject(focusNodes.toArray()[0]);
            }
        }
        return this._focusNode;
    }

    get resultMessage(): string {
        return this._node.out(sh['resultMessage']).values.join(' ');
    }

    get sourceShape(): ClownfaceObject | null {
        if (this._sourceShape === undefined) {
            const sourceShapes = this._node.out(sh['sourceShape']);
            if (sourceShapes.values.length === 0) {
                this._sourceShape = null;
            } else {
                if (sourceShapes.values.length > 1) {
                    console.warn('Multiple source shapes found for validation result. Using the first one.');
                }
                this._sourceShape = new ClownfaceObject(sourceShapes.toArray()[0]);
            }
        }
        return this._sourceShape;
    }

    get detail(): ValidationResult[] {
        if (this._detail === null) {
            this._detail = this._node.out(sh['detail']).map(n => new ValidationResultDetail(n));
        }
        return this._detail;
    }

    get severity(): string | null {
        if (this._severity === undefined) {
            const severities = this._node.out(sh['resultSeverity']).values;
            if (severities.length === 0) {
                this._severity = null;
            }
            else {
                if (severities.length > 1) {
                    console.warn('Multiple severities found for validation result. Using the first one.');
                }
                this._severity = severities[0];
            }
        }
        return this._severity;
    }

    get path(): string {
        if (this._path === undefined) {
            const paths = this._node.out(sh['resultPath']).values;
            if (paths.length === 0) {
                this._path = '';
            } else {
                if (paths.length > 1) {
                    console.warn('Multiple paths found for validation result. Using the first one.');
                }
                this._path = paths[0];
            }
        }
        return this._path;
    }

    get literalValue(): string | null {
        if (this._literalValue === undefined) {
            const literalValues = this._node.out(sh['value']).values;
            if (literalValues.length === 0) {
                this._literalValue = null;
            } else {
                if (literalValues.length > 1) {
                    console.warn('Multiple literal values found for validation result. Using the first one.');
                }
                this._literalValue = literalValues[0];
            }
        }
        return this._literalValue;
    }

    get objectValue(): ClownfaceObject | null {
        if (this._nodeValue === undefined) {
            const nodeValues = this._node.out(sh['value']);
            if (nodeValues.values.length === 0) {
                this._nodeValue = null;
            } else {
                if (nodeValues.values.length > 1) {
                    console.warn('Multiple node values found for validation result. Using the first one.');
                }
                this._nodeValue = new ClownfaceObject(nodeValues.toArray()[0]);
            }
        }
        return this._nodeValue;
    }

    get dimensionValue(): ResultDimension | null {
        if (this._dimensionValue === undefined) {
            const dimensionValues = this._node.out(sh['value']);
            if (dimensionValues.values.length === 0) {
                this._dimensionValue = null;
            } else {
                if (dimensionValues.values.length > 1) {
                    console.warn('Multiple dimension values found for validation result. Using the first one.');
                }
                this._dimensionValue = new ResultDimension(dimensionValues.toArray()[0]);
            }
        }
        return this._dimensionValue;
    }





    isAboutCube(): boolean {
        return this._node.out(sh['focusNode']).has(rdf['type'], cube['Cube']).values.length > 0;
    }

    isAboutDimensions(): boolean {
        const isCubeConstraint = this._node.out(sh['focusNode']).has(rdf['type'], cube['Constraint']).values.length > 0;
        const isDimension = this._node.out(sh['focusNode']).in(sh['property']).has(rdf['type'], cube['Constraint']).values.length > 0;
        return isCubeConstraint || isDimension;
    }


}

export class ValidationResultDetail extends ValidationResult {
    private _otherDimensionValue: ResultDimension | undefined | null = undefined;

    constructor(node: GraphPointer) {
        super(node);
    }
    override get dimensionValue(): ResultDimension | null {
        if (this._otherDimensionValue === undefined) {
            const dimensionValues = this._node.out(sh['focusNode']);
            if (dimensionValues.values.length === 0) {
                this._otherDimensionValue = null;
            } else {
                if (dimensionValues.values.length > 1) {
                    console.warn('Multiple dimension values found for validation result. Using the first one.');
                }
                this._otherDimensionValue = new ResultDimension(dimensionValues.toArray()[0]);
            }
        }
        return this._otherDimensionValue;
    }


}


export class ResultDimension extends ClownfaceObject {
    private _path: string | undefined = undefined;

    constructor(node: GraphPointer) {
        super(node);
    }

    get path(): string {
        if (this._path === undefined) {
            const paths = this._node.out(sh['path']).values;
            if (paths.length === 0) {
                this._path = '';
            } else {
                if (paths.length > 1) {
                    console.warn('Multiple paths found for validation result. Using the first one.');
                }
                this._path = paths[0];
            }
        }
        return this._path;
    }

    get dimensionLabel(): string {
        return this._node.out(cube['dimension']).out(rdf['label']).values.join(' ');
    }

    get resultMessage(): string {
        return this._node.out(sh['resultMessage']).values.join(' ');
    }

    get severity(): string {
        return this._node.out(sh['resultSeverity']).values.join(' ');
    }
}
