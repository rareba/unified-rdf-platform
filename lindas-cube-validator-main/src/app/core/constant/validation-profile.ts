

export interface ValidationProfile {
    key: string;
    value: string;
    label: string;
    workNodeIri: string
}

const PROFILES = [
    {
        key: 'visualize',
        value: 'https://cube.link/ref/main/shape/profile-visualize',
        workNodeIri: 'https://ld.admin.ch/application/visualize',
        label: 'Visualize'
    },
    {
        key: 'opendataswiss',
        value: 'https://cube.link/ref/main/shape/profile-opendataswiss',
        label: 'OpenDataSwiss',
        workNodeIri: 'https://ld.admin.ch/application/opendataswiss'
    },
    {
        key: 'default',
        value: 'https://cube.link/ref/main/shape/standalone-cube-constraint',
        label: 'Basic',
        workNodeIri: ''
    },
];

class ValidationProfilesMap {
    private profileMap = new Map<string, ValidationProfile>();

    constructor() {
        PROFILES.forEach(profile => this.profileMap.set(profile.key, profile));
    }

    get(key: string): ValidationProfile | undefined {
        return this.profileMap.get(key);
    }

    getProfiles(): ValidationProfile[] {
        return PROFILES;
    }

    getDefaultProfile(): ValidationProfile {
        return this.get('default')!;
    }


}

export const ValidationProfiles = new ValidationProfilesMap();
