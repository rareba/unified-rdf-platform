# RDF Forge — Git sync

## Current capability

Git sync is already wired for **pipelines, shapes, and settings** under
`rdf-forge-pipeline-service`:

- `GitSyncController` — `GET/POST /api/v1/git-sync/configs`
- `GitSyncService` — push/pull against GitHub / GitLab
- Storage: `git_sync_configs` table (`V4__add_git_sync_configs.sql`)

Each config record carries:

| Field             | Purpose                                               |
|-------------------|-------------------------------------------------------|
| `provider`        | `GITHUB`, `GITLAB`                                    |
| `repositoryUrl`   | HTTPS URL                                             |
| `branch`          | default `main`                                        |
| `accessToken`     | encrypted PAT — never returned in GET responses       |
| `configPath`      | prefix in repo where configs live                     |
| `syncPipelines`   | include pipelines                                     |
| `syncShapes`      | include shapes                                        |
| `syncSettings`    | include settings                                      |
| `autoSync`        | periodic pull toggle                                  |

## Phase 10 follow-ups

The current sync flow operates at pipeline / shape granularity. Phase 10 widens
the scope to **semantic asset** level so comments, releases, ontologies, and
mappings can be versioned together.

### Proposed additions

1. **`GitSyncAssetKind`** enum matching `CommentEntity.AssetKind`.
2. **`POST /api/v1/git-sync/export?projectId={id}&kind={kind}`** — export a
   single asset kind to the configured repository. Wiring:
   - ontologies -> `rdf-forge-shacl-service` WebClient call
   - mappings -> local repository
   - cubes / dimensions -> `rdf-forge-dimension-service`
3. **`POST /api/v1/git-sync/import?projectId={id}&branch={branch}`** —
   reverse direction with conflict detection.
4. **Diff preview endpoint** — `GET /api/v1/git-sync/diff?projectId={id}`
   returns a structured per-asset diff the UI can render before a push.
5. **Asset-level commit metadata** — each exported artefact ships with
   `RDFFORGE.md` metadata (asset UUIDs, author, parent commit, schema version)
   so round-tripping stays safe.

### Temporary placeholder

Until the new endpoints exist, callers should invoke the existing
`POST /api/v1/git-sync/configs/{id}/push` route, which already commits every
pipeline + shape configured in the scope flags.

TODO(phase-10): see `GitSyncService.push(...)` to extend the commit builder
with ontology / mapping / release asset writers.
