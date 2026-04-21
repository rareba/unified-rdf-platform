# Pipeline designer — ngx-graph migration

## TL;DR
The pipeline designer no longer uses `@swimlane/ngx-graph`.
A vertical step list with dependency badges renders the same pipeline
graph data (`nodes()` + `links()`) without a third-party graph library.
The visual DAG is temporarily unavailable; pipeline creation, editing,
validation, and execution all still work.

## Why
Angular 21 landed; `@swimlane/ngx-graph` (11.0.0 → 12.0.0-alpha) still
declares `18.x || 19.x || 20.x` in its Angular peer dependencies.

Two earlier approaches failed:

1. **Direct install**: `npm install` rejects the peer range, requiring
   `--legacy-peer-deps`. Explicitly disallowed by the production-
   readiness charter for this repo (no flag bypasses).
2. **npm `overrides`**: the install succeeds and `ng build` emits a
   bundle, but at runtime the designer route renders a blank page.
   A Playwright smoke (`e2e/tests/pipeline-designer-live.spec.ts`) that
   tried to locate `<ngx-graph>` timed out — no element mounts.
   Screenshots confirmed the route was visually empty.

## Current state
- `@swimlane/ngx-graph` and `dagre` / `@types/dagre` removed from
  `package.json`. The npm `overrides` block was removed with them.
- `pipeline-designer.ts`: import of `NgxGraphModule` / `GraphComponent`
  / `NgxGraphZoomOptions` removed. The `@ViewChild('graph')` reference
  was commented out. Legacy `center$` / `zoomToFit$` / `update$`
  subjects are kept as no-op `Subject<boolean>` fields so any callers
  still invoking `.next()` don't error.
- `pipeline-designer.html`: the `<ngx-graph>` block is replaced with
  an ordered list of `<li data-testid="pipeline-node">`. Each item
  shows the operation type, name, id and upstream dependencies; click
  still calls `onNodeSelect(node)`, delete button still calls
  `removeNode(node.id)`. `upstreamOf(nodeId)` was added to the
  component to feed the template.
- No Angular peer-dependency warning remains. `npm install` succeeds
  with no flags and 0 vulnerabilities.

## Migration plan
1. Wait for an Angular 21-compatible `@swimlane/ngx-graph` release
   (tracked upstream as `swimlane/ngx-graph#1141`). If it lands, this
   file will capture the revert.
2. If `ngx-graph` does not add Angular 21 support in a reasonable
   window, re-render the DAG using a library with Angular 21 in its
   peer deps — candidates worth evaluating:
   - `@vflow-core/vue-flow` (Vue-only, skip)
   - `@swc-ui/ngx-dag` (if it ever exists)
   - `vis-network` + a small Angular wrapper — no Angular peer deps
   - Build a minimal SVG renderer using D3 + `dagre` for layout only
     (dagre has no Angular coupling)
3. The list view we ship now is the honest fallback — it doesn't lie
   about what renders. Step authoring, dependency editing, run, and
   validation all still work; only the pretty picture is deferred.

## Verification
- `npm install` — 0 vulnerabilities, no peer warnings, no flags.
- `npm run build` — clean, no warnings.
- `npm run test:ci` — 1255/1255 green.
- Playwright: `e2e/tests/pipeline-designer-live.spec.ts` now targets
  the list view (`[data-testid="pipeline-step-list"]`) and passes
  against the dev server.
