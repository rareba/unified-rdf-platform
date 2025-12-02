import { Injectable, inject } from '@angular/core';
import { GraphResult, SparqlService } from '../sparql/sparql.service';
import { ProfileService } from '../profile/profile.service';
import { Observable, forkJoin, map, switchMap, expand, EMPTY, tap, takeUntil, catchError } from 'rxjs';

// queries
import { CONSTRUCT_CUBE_ITEMS } from './query/get-cube-items';
import { MultiLanguageCubeItem } from './model/cube-item';
import { rdfEnvironment } from '../../rdf/rdf-environment';
import { cube } from '../../rdf/namespace';
import { getShapeGraphForCube } from './query/get-cube-shapes';
import { Dataset } from '@zazuko/env/lib/DatasetExt';
import Validator from 'rdf-validate-shacl';
import ValidationReport from 'rdf-validate-shacl/src/validation-report';
import { ValidationProfile } from '../../constant/validation-profile';
import { getOneObservation } from './query/get-one-observation';

@Injectable({
  providedIn: 'root'
})
export class EndpointService {
  private readonly sparqlService = inject(SparqlService);
  private readonly profileService = inject(ProfileService);


  readonly maxValidationErrors = 20;
  readonly maxPages = 10;

  public lastUsedEndpoint: string = '';

  constructor() { }

  isOnline(endpointUrl: string): Observable<boolean> {
    return this.sparqlService.select(endpointUrl, 'SELECT * WHERE { ?s ?p ?o } LIMIT 1').pipe(
      map(result => result.results.bindings.length > 0),
    );
  }

  getCubes(endpointUrl: string): Observable<MultiLanguageCubeItem[]> {
    this.lastUsedEndpoint = endpointUrl;
    return this.sparqlService.construct(endpointUrl, CONSTRUCT_CUBE_ITEMS).pipe(
      map(cubeItemDataset => {
        const cubeItems = rdfEnvironment.clownface({ dataset: cubeItemDataset.dataset }).node(cube['Cube']).in().map(node => new MultiLanguageCubeItem(node));
        return cubeItems;

      }),
    );
  }

  getCube(endpointUrl: string, cubeIri: string): Observable<GraphResult> {
    this.lastUsedEndpoint = endpointUrl;
    return this.sparqlService.construct(endpointUrl, getShapeGraphForCube(cubeIri))

  }

  getValidationReportForProfile(endpointUrl: string, cubeIri: string, profile: ValidationProfile): Observable<CubeValidationResult> {
    this.lastUsedEndpoint = endpointUrl;
    const shapeGraphQuery = this.profileService.getProfile(profile);
    const dataGraphQuery = this.sparqlService.construct(endpointUrl, getShapeGraphForCube(cubeIri));
    const singleObservationQuery = this.sparqlService.construct(endpointUrl, getOneObservation(cubeIri));

    return forkJoin([shapeGraphQuery, dataGraphQuery, singleObservationQuery]).pipe(
      map(([shapeGraph, dataGraph, singleObservationGraph]) => {
        if (shapeGraph.error) {
          return {
            shapeGraph: rdfEnvironment.dataset(),
            shapeGraphSerialized: '',
            dataGraph: rdfEnvironment.dataset(),
            dataGraphSerialized: '',
            report: null,
            error: shapeGraph.error
          }
        }
        // add a single observation to the dataGraph in order to avoid empty observation sets
        // this is a workaround to avoid a warning in the validator that the observation set is empty
        dataGraph.dataset.addAll(singleObservationGraph.dataset);
        const validator = new Validator(shapeGraph.dataset, { factory: rdfEnvironment });
        const report = validator.validate(dataGraph.dataset);
        return {
          shapeGraph: shapeGraph.dataset,
          shapeGraphSerialized: shapeGraph.profileSerialized,
          dataGraph: dataGraph.dataset,
          dataGraphSerialized: dataGraph.serialized,
          report: report
        }
      }
      )
    );
  }

  /**
   * The sh:targetClass is not set in the cube constraint dataset. To avoid matching all observations
   * in the triple store. This method sets the sh:targetClass to https://cube.link/Observation
   * 
   * @param shape the cube constraint dataset
   */
  private _updateDatasetWithTarget(shape: Dataset): void {
    const constraint = rdfEnvironment.clownface({ dataset: shape, term: rdfEnvironment.namedNode('https://cube.link/Constraint') }).in(rdfEnvironment.ns.rdf.type)
    if (!constraint.term) {
      console.warn('could not find a constraint. This is means that the cube does not have a constraint. This is not a mistake but the cube is not validated against a constraint.');
      return;
    }
    constraint.addOut(rdfEnvironment.ns.sh.targetClass, rdfEnvironment.namedNode('https://cube.link/Observation'));
  }

  /**
   * ValidationReport
   */
  getObservationValidationJunkedReport(endpointUrl: string, cubeIri: string, chunkSize: number): Observable<CubeValidationResult> {
    this.lastUsedEndpoint = endpointUrl;
    const shapeGraphQuery = this.sparqlService.construct(endpointUrl, getShapeGraphForCube(cubeIri));

    let page = 0;
    const shapeGraph = rdfEnvironment.dataset();
    let validator: Validator;
    let countValidationErrors = 0;

    return shapeGraphQuery.pipe(
      switchMap(x => {
        shapeGraph.addAll(x.dataset);
        this._updateDatasetWithTarget(shapeGraph);
        validator = new Validator(shapeGraph, { factory: rdfEnvironment });
        return this.fetchObservablePageQuery(endpointUrl, cubeIri, 0, chunkSize);
      }),
      expand(response => {
        if (response.dataset.size > 1 && countValidationErrors < this.maxValidationErrors && page < this.maxPages) {
          return this.fetchObservablePageQuery(endpointUrl, cubeIri, ++page, chunkSize);
        }
        return EMPTY;
      }
      ),
      map(result => {
        const report = validator.validate(result.dataset);
        countValidationErrors += report.results.length;
        return {
          shapeGraph: shapeGraph,
          shapeGraphSerialized: shapeGraph.toCanonical(),
          dataGraph: result.dataset,
          dataGraphSerialized: result.serialized,
          report: report
        }
      }),
    )

  }




  fetchObservablePageQuery(endpointUrl: string, cubeIri: string, page: number, chunkSize: number): Observable<GraphResult> {
    this.lastUsedEndpoint = endpointUrl;

    const query = `
    PREFIX cube: <https://cube.link/>
  
    CONSTRUCT {
      ?observation ?p ?o.
      } WHERE {
        {
          SELECT ?observation WHERE {
            <${cubeIri}> cube:observationSet/ cube:observation ?observation.
          } LIMIT ${chunkSize} OFFSET ${page * chunkSize} 
        }
        ?observation ?p ?o.
      }
      
      `;

    return this.sparqlService.construct(endpointUrl, query)
  }
}



export interface CubeValidationResult {
  shapeGraph: Dataset;
  shapeGraphSerialized: string;
  dataGraph: Dataset;
  dataGraphSerialized: string;
  report: ValidationReport | null;
  error?: string
}



