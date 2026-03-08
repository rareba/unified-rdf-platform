import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { ObliqueModule } from '@oblique/oblique';

import { JobListComponent } from './job-list/job-list';
import { JobMonitorComponent } from './job-monitor/job-monitor';
import { SharedModule } from '../../shared/shared.module';

@NgModule({
  declarations: [
    JobListComponent,
    JobMonitorComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    ObliqueModule,
    SharedModule
  ],
  exports: [
    JobListComponent,
    JobMonitorComponent
  ]
})
export class JobModule { }
