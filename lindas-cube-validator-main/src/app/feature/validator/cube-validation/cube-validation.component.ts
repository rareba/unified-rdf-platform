import { Component, computed, input } from '@angular/core';

import { createPlaygroundUrl } from '@zazuko/shacl-playground'

import { TranslateModule } from '@ngx-translate/core';

import { ObAlertModule, ObSpinnerModule } from '@oblique/oblique';

import { ValidationReportComponent } from '../validation-report/validation-report.component';
import { CubeValidationResult } from '../../../core/service/endpoint/endpoint.service';
import { ProfileSelectorComponent } from "../../../core/component/profile-selector/profile-selector.component";
import { CubeDescriptionComponent } from "../../../core/component/cube-description/cube-description.component";
import { FadeInOut } from '../../../core/animation/fade-in-out';
import { ConformsIndicatorComponent } from "../conforms-indicator/conforms-indicator.component";
import { CubeConstraintsValidationReportComponent } from "../cube-constraints-validation-report/cube-constraints-validation-report.component";
import { PlaygroundLinkComponent } from '../../../core/component/playground-link/playground-link.component';
import { CubeValidationBarnard59CliCommandComponent } from '../../../core/component/cub-validation-barnard59-cli-command/cub-validation-barnard59-cli-command.component';

@Component({
  selector: 'cube-validation',
  standalone: true,
  templateUrl: './cube-validation.component.html',
  styleUrl: './cube-validation.component.scss',
  animations: [FadeInOut(300, 200)],
  imports: [
    ObSpinnerModule,
    ObAlertModule,
    ObSpinnerModule,
    TranslateModule,
    ValidationReportComponent,
    ProfileSelectorComponent,
    CubeDescriptionComponent,
    ConformsIndicatorComponent,
    CubeConstraintsValidationReportComponent,
    PlaygroundLinkComponent,
    CubeValidationBarnard59CliCommandComponent
  ]
})
export class CubeValidationComponent {
  report = input.required<CubeValidationResult | undefined>();

  playgroundLink = computed<string | undefined>(() => {
    const report = this.report();
    if (!report) {
      return undefined;
    }
    const playgroundLink = createPlaygroundUrl(report.shapeGraph, report.dataGraph);
    return playgroundLink;
  });

}
