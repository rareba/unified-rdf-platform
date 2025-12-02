import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ObUnknownRouteModule } from '@oblique/oblique';

const routes: Routes = [
  {
    path: '', redirectTo: 'select',
    pathMatch: 'full'
  },
  {
    path: 'select',
    loadChildren: () => import('./feature/cube-selection/routes').then(m => m.routes)
  },
  {
    path: 'validate',
    loadChildren: () => import('./feature/validator/routes').then(m => m.routes)
  }
  // { path: '**', redirectTo: 'unknown-route' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {
    bindToComponentInputs: true,
    scrollOffset: [0, 0],
    scrollPositionRestoration: 'disabled',
    anchorScrolling: 'enabled'
  }), ObUnknownRouteModule],
  exports: [RouterModule]
})
export class AppRoutingModule { }
