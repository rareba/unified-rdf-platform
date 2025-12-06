import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/dashboard/dashboard').then(m => m.Dashboard),
    canActivate: [authGuard]
  },
  {
    path: 'pipelines',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/pipeline/pipeline-list/pipeline-list').then(m => m.PipelineList)
      },
      {
        path: 'new',
        loadComponent: () => import('./features/pipeline/pipeline-designer/pipeline-designer').then(m => m.PipelineDesigner)
      },
      {
        path: ':id',
        loadComponent: () => import('./features/pipeline/pipeline-designer/pipeline-designer').then(m => m.PipelineDesigner)
      }
    ]
  },
  {
    path: 'jobs',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/job/job-list/job-list').then(m => m.JobList)
      },
      {
        path: ':id',
        loadComponent: () => import('./features/job/job-monitor/job-monitor').then(m => m.JobMonitor)
      }
    ]
  },
  {
    path: 'shacl',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/shacl/shacl-studio/shacl-studio').then(m => m.ShaclStudio)
      },
      {
        path: ':id',
        loadComponent: () => import('./features/shacl/shape-editor/shape-editor').then(m => m.ShapeEditor)
      }
    ]
  },
  {
    path: 'cubes',
    loadComponent: () => import('./features/cube/cube-wizard/cube-wizard').then(m => m.CubeWizard),
    canActivate: [authGuard]
  },
  {
    path: 'data',
    loadComponent: () => import('./features/data/data-manager/data-manager').then(m => m.DataManager),
    canActivate: [authGuard]
  },
  {
    path: 'dimensions',
    loadComponent: () => import('./features/dimension/dimension-manager/dimension-manager').then(m => m.DimensionManager),
    canActivate: [authGuard]
  },
  {
    path: 'triplestore',
    loadComponent: () => import('./features/triplestore/triplestore-browser/triplestore-browser').then(m => m.TriplestoreBrowser),
    canActivate: [authGuard]
  },
  {
    path: 'settings',
    loadComponent: () => import('./features/settings/settings').then(m => m.Settings),
    canActivate: [authGuard]
  },
  {
    path: 'admin',
    canActivate: [authGuard, adminGuard],
    children: [
      {
        path: '',
        redirectTo: 'users',
        pathMatch: 'full'
      },
      {
        path: 'users',
        loadComponent: () => import('./features/admin/user-management/user-management').then(m => m.UserManagement)
      },
      {
        path: 'roles',
        loadComponent: () => import('./features/admin/role-management/role-management').then(m => m.RoleManagement)
      },
      {
        path: 'tokens',
        loadComponent: () => import('./features/admin/token-management/token-management').then(m => m.TokenManagement)
      },
      {
        path: 'system',
        loadComponent: () => import('./features/admin/system-settings/system-settings').then(m => m.SystemSettings)
      },
      {
        path: 'git-sync',
        loadComponent: () => import('./features/admin/git-sync/git-sync').then(m => m.GitSyncComponent)
      }
    ]
  },
  {
    path: '**',
    redirectTo: ''
  }
];
