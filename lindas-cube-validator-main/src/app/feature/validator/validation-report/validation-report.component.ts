import { Component, input } from '@angular/core';

import { ValidationReport } from 'rdf-validate-shacl/src/validation-report';
import { SeverityComponent } from "../severity/severity.component";

@Component({
  selector: 'cube-validation-report',
  standalone: true,
  templateUrl: './validation-report.component.html',
  styleUrl: './validation-report.component.scss',
  imports: [SeverityComponent]
})
export class ValidationReportComponent {
  report = input.required<ValidationReport>();
}


