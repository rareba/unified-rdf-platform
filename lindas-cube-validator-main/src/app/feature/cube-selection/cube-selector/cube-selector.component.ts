import { Component, inject } from '@angular/core';
import { ValidatorInputComponent } from "../validator-input/validator-input.component";
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { FadeInOut } from '../../../core/animation/fade-in-out';

@Component({
  selector: 'cube-validator',
  standalone: true,
  templateUrl: './cube-selector.component.html',
  styleUrl: './cube-selector.component.scss',
  imports: [ValidatorInputComponent, TranslateModule],
  animations: [FadeInOut(300, 200)],
})
export class CubeSelectorComponent {

  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  validateCube(cube: CubeInfo) {

    this.router.navigate(['../', 'validate', cube.endpoint, cube.cubeIri], { relativeTo: this.route });
  }
}

export interface CubeInfo {
  cubeIri: string;
  endpoint: string;
}

