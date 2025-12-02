import { GraphPointer } from "clownface";
import { ClownfaceObject } from "../../../rdf/clownface-object";
import { schema } from "../../../rdf/namespace";

export class MultiLanguageCubeItem extends ClownfaceObject {
    private _name: string | null | undefined = undefined;
    private _nameEN: string | null | undefined = undefined;
    private _nameDE: string | null | undefined = undefined;
    private _nameFR: string | null | undefined = undefined;
    private _nameIT: string | null | undefined = undefined;
    private _description: string | null | undefined = undefined;
    private _descriptionEN: string | null | undefined = undefined;
    private _descriptionDE: string | null | undefined = undefined;
    private _descriptionFR: string | null | undefined = undefined;
    private _descriptionIT: string | null | undefined = undefined;
    private _datePublished: Date | null | undefined = undefined;

    constructor(node: GraphPointer) {
        super(node);
    }

    get name(): string | null {
        if (this._name === undefined) {
            const namesWithoutLang = this._node.out(schema`name`, { language: '' }).values;
            if (namesWithoutLang.length === 0) {
                this._name = null;
            } else {
                this._name = namesWithoutLang.join(', ');
            }
        }
        return this._name;
    }

    get nameEN(): string | null {
        if (this._nameEN === undefined) {
            const namesEN = this._node.out(schema`name`, { language: 'en' }).values;
            if (namesEN.length === 0) {
                this._nameEN = null;
            } else {
                this._nameEN = namesEN.join(', ');
            }
        }
        return this._nameEN;
    }

    get nameDE(): string | null {
        if (this._nameDE === undefined) {
            const namesDE = this._node.out(schema`name`, { language: 'de' }).values;
            if (namesDE.length === 0) {
                this._nameDE = null;
            } else {
                this._nameDE = namesDE.join(', ');
            }
        }
        return this._nameDE;
    }

    get nameFR(): string | null {
        if (this._nameFR === undefined) {
            const namesFR = this._node.out(schema`name`, { language: 'fr' }).values;
            if (namesFR.length === 0) {
                this._nameFR = null;
            } else {
                this._nameFR = namesFR.join(', ');
            }
        }
        return this._nameFR;
    }

    get nameIT(): string | null {
        if (this._nameIT === undefined) {
            const namesIT = this._node.out(schema`name`, { language: 'it' }).values;
            if (namesIT.length === 0) {
                this._nameIT = null;
            } else {
                this._nameIT = namesIT.join(', ');
            }
        }
        return this._nameIT;
    }

    get description(): string | null {
        if (this._description === undefined) {
            const descriptionsWithoutLang = this._node.out(schema`description`, { language: '' }).values;
            if (descriptionsWithoutLang.length === 0) {
                this._description = null;
            } else {
                this._description = descriptionsWithoutLang.join(', ');
            }
        }
        return this._description;
    }

    get descriptionEN(): string | null {
        if (this._descriptionEN === undefined) {
            const descriptionsEN = this._node.out(schema`description`, { language: 'en' }).values;
            if (descriptionsEN.length === 0) {
                this._descriptionEN = null;
            } else {
                this._descriptionEN = descriptionsEN.join(', ');
            }
        }
        return this._descriptionEN;
    }

    get descriptionDE(): string | null {
        if (this._descriptionDE === undefined) {
            const descriptionsDE = this._node.out(schema`description`, { language: 'de' }).values;
            if (descriptionsDE.length === 0) {
                this._descriptionDE = null;
            } else {
                this._descriptionDE = descriptionsDE.join(', ');
            }
        }
        return this._descriptionDE;
    }

    get descriptionFR(): string | null {
        if (this._descriptionFR === undefined) {
            const descriptionsFR = this._node.out(schema`description`, { language: 'fr' }).values;
            if (descriptionsFR.length === 0) {
                this._descriptionFR = null;
            } else {
                this._descriptionFR = descriptionsFR.join(', ');
            }
        }
        return this._descriptionFR;
    }

    get descriptionIT(): string | null {
        if (this._descriptionIT === undefined) {
            const descriptionsIT = this._node.out(schema`description`, { language: 'it' }).values;
            if (descriptionsIT.length === 0) {
                this._descriptionIT = null;
            } else {
                this._descriptionIT = descriptionsIT.join(', ');
            }
        }
        return this._descriptionIT;
    }

    get datePublished(): Date | null {
        if (this._datePublished === undefined) {
            const dates = this._node.out(schema['datePublished']).values;
            if (dates.length === 0) {
                this._datePublished = null;
            } else {
                const date = new Date(dates[0]);
                this._datePublished = date;
            }
        }
        return this._datePublished;
    }

}