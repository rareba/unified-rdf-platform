import { Injectable, inject } from '@angular/core';
import { Observable, from, map, switchMap } from 'rxjs';
import { HttpClient, HttpHeaders } from '@angular/common/http';

import { rdfEnvironment } from '../../rdf/rdf-environment'
import { Dataset } from '@zazuko/env/lib/DatasetExt';
import { ValidationProfile } from '../../constant/validation-profile';
import transform from 'rdf-transform-graph-imports'

const RDF_MIME_TYPE = 'text/turtle';

@Injectable({
  providedIn: 'root'
})
export class ProfileService {
  readonly #http = inject(HttpClient);


  getProfile(profile: ValidationProfile): Observable<ValidationProfileData> {
    return this._get(profile.value);
  }


  private _get(profileUrl: string): Observable<ValidationProfileData> {

    const fetchProfile = async () => {

      const response = await rdfEnvironment.fetch(profileUrl);
      if (!response.ok) {
        console.error(response);
        return { dataset: rdfEnvironment.dataset(), profileSerialized: '', error: `Failed to fetch profile: ${response.status}` }
      }
      const parsed = await response.quadStream() as any;
      const transformed = parsed.pipe(transform(rdfEnvironment))
      const dataset = await rdfEnvironment.dataset().import(transformed)
      const profileSerialized = dataset.toCanonical()
      return { dataset, profileSerialized }
    }


    return from(fetchProfile())

  }
}

export interface ValidationProfileData {
  dataset: Dataset;
  profileSerialized: string;
  error?: string;
}
