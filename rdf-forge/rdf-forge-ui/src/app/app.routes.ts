import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./features/dashboard/dashboard').then(m => m.Dashboard),
    canActivate: [authGuard]
  },
  {
    path: 'projects',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/project/project-list/project-list').then(m => m.ProjectList)
      },
      {
        path: 'new',
        loadComponent: () =>
          import('./features/project/project-form/project-form').then(m => m.ProjectForm)
      },
      {
        path: ':id/edit',
        loadComponent: () =>
          import('./features/project/project-form/project-form').then(m => m.ProjectForm)
      },
      {
        path: ':id',
        loadComponent: () =>
          import('./features/project/project-workspace/project-workspace').then(
            m => m.ProjectWorkspace
          ),
        children: [
          { path: '', redirectTo: 'overview', pathMatch: 'full' },
          {
            path: 'overview',
            loadComponent: () =>
              import('./features/project/tabs/overview-tab/overview-tab').then(m => m.OverviewTab)
          },
          {
            path: 'data',
            loadComponent: () =>
              import('./features/project/tabs/data-tab/data-tab').then(m => m.DataTab)
          },
          {
            path: 'ontology',
            loadComponent: () =>
              import('./features/project/tabs/ontology-tab/ontology-tab').then(m => m.OntologyTab)
          },
          {
            path: 'mapping',
            loadComponent: () =>
              import('./features/project/tabs/mapping-tab/mapping-tab').then(m => m.MappingTab)
          },
          {
            path: 'validation',
            loadComponent: () =>
              import('./features/project/tabs/validation-tab/validation-tab').then(
                m => m.ValidationTab
              )
          },
          {
            path: 'publish',
            loadComponent: () =>
              import('./features/project/tabs/publish-tab/publish-tab').then(m => m.PublishTab)
          },
          {
            path: 'lineage',
            loadComponent: () =>
              import('./features/project/tabs/lineage-tab/lineage-tab').then(m => m.LineageTab)
          },
          {
            path: 'docs',
            loadComponent: () =>
              import('./features/project/tabs/docs-tab/docs-tab').then(m => m.DocsTab)
          }
        ]
      }
    ]
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
        loadComponent: () => import('./features/shacl/shacl-studio/shacl-studio').then(m => m.ShaclStudioComponent)
      },
      {
        path: ':id',
        loadComponent: () => import('./features/shacl/shape-editor/shape-editor').then(m => m.ShapeEditor)
      }
    ]
  },
  {
    path: 'cubes',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/cube/cube-list/cube-list').then(m => m.CubeList)
      },
      {
        path: 'new',
        loadComponent: () => import('./features/cube/cube-project/cube-project').then(m => m.CubeProject)
      },
      {
        path: ':id',
        loadComponent: () => import('./features/cube/cube-project/cube-project').then(m => m.CubeProject)
      }
    ]
  },
  {
    path: 'mappings',
    canActivate: [authGuard],
    children: [
      {
        path: ':id',
        loadComponent: () =>
          import('./features/mapping/mapping-studio').then(m => m.MappingStudio)
      }
    ]
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
    path: 'ontologies',
    canActivate: [authGuard],
    children: [
      {
        path: ':id',
        loadComponent: () => import('./features/ontology/ontology-detail').then(m => m.OntologyDetail)
      }
    ]
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
    loadComponent: () => import('./features/not-found/not-found').then(m => m.NotFound)
  }
];
