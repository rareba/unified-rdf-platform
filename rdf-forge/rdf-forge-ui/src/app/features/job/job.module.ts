import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

import { JobList } from './job-list/job-list';
import { JobMonitor } from './job-monitor/job-monitor';

@NgModule({
  imports: [
    CommonModule,
    RouterModule,
    JobList,
    JobMonitor
  ],
  exports: [
    JobList,
    JobMonitor
  ]
})
export class JobModule { }
