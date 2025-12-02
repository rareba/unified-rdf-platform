import { Component, computed, effect, input } from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';

import ValidationReport from 'rdf-validate-shacl/src/validation-report';
import { rdfEnvironment } from '../../../core/rdf/rdf-environment';
import { SeverityComponent } from "../severity/severity.component";
import { rdf, sh } from '../../../core/rdf/namespace';
import { ValidationResult } from '../validation-report/model/validation-result';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { ObButtonModule, ObIconModule } from '@oblique/oblique';
import { MatButtonModule } from '@angular/material/button';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { JsonPipe } from '@angular/common';

@Component({
  selector: 'cube-constraints-validation-report',
  standalone: true,
  templateUrl: './cube-constraints-validation-report.component.html',
  styleUrl: './cube-constraints-validation-report.component.scss',
  imports: [
    SeverityComponent,
    MatTableModule,
    MatIconModule,
    ObIconModule,
    MatButtonModule,
    ObButtonModule,
    MatExpansionModule,
  ],
  animations: [
    trigger('detailExpand', [
      state('collapsed,void', style({ height: '0px', minHeight: '0' })),
      state('expanded', style({ height: '*' })),
      transition('expanded <=> collapsed', animate('225ms cubic-bezier(0.4, 0.0, 0.2, 1)')),
    ]),
  ],
})
export class CubeConstraintsValidationReportComponent {
  report = input.required<ValidationReport>();


  cubeColumnsToDisplay = ['Message', 'Path', 'Value', 'Severity'];

  dimensionColumnsToDisplay = ['Message', 'Dimension', 'Severity'];

  dimensionColumnsToDisplayWithExpand = [...this.dimensionColumnsToDisplay, 'expand'];
  expandedElement: ValidationResult | null = null;


  cubeValidationResult = computed<ValidationResult[]>(() => {
    const dataset = this.report().dataset;
    if (!dataset) {
      return [];
    }

    const results = rdfEnvironment.clownface({ dataset }).node(sh['ValidationResult']).in(rdf['type']).map(n => new ValidationResult(n)).filter(result => result.isAboutCube());
    const resultCubeDetails = rdfEnvironment.clownface({ dataset }).node(sh['ValidationResult']).in(rdf['type']).out(sh['detail']).map(n => new ValidationResult(n)).filter(result => result.isAboutCube());
    const resultWithDetails = [...results, ...resultCubeDetails];
    return resultWithDetails;
  }
  );


  dimensionValidationResult = computed<ValidationResult[]>(() => {
    const dataset = this.report().dataset;
    if (!dataset) {
      return [];
    }

    const results = rdfEnvironment.clownface({ dataset }).node(sh['ValidationResult']).in(rdf['type']).map(n => new ValidationResult(n)).filter(result => result.isAboutDimensions());
    const detailIris = results.flatMap(result => result.detail.map(detail => detail.iri));
    const resultWithDetails = results.filter(r => !detailIris.includes(r.iri)).flatMap(result => [result, ...result.detail]);
    return resultWithDetails;
  }
  );
}
