# trifid-plugin-graph-explorer

## 3.0.0

### Major Changes

- 732a9b5: Rename packages to LINDAS namespace and remove Zazuko branding

  Remove all references to Zazuko and rebrand all packages under the LINDAS/Swiss Federal Archives namespace. This allows the fork to be published to npm independently and clearly indicates these are the LINDAS customizations of Trifid, not the original Zazuko packages.

  Package name changes:

  - trifid → lindas-trifid
  - trifid-core → lindas-trifid-core
  - trifid-handler-fetch → lindas-trifid-handler-fetch
  - trifid-plugin-_ → lindas-trifid-plugin-_
  - @zazuko/trifid-_ → lindas-trifid-_

  Author updated to: Swiss Federal Archives / Lindas

## 2.1.2

### Patch Changes

- f0e3b13: Fix and improve types references

## 2.1.1

### Patch Changes

- 724f2ed: Fix `requestPort` value, to handle `null` cases and simplify the logic

## 2.1.0

### Minor Changes

- 007e201: Upgrade Fastify to v5.

### Patch Changes

- 080f5d8: Harmonize author and keywords fields
- a97a6a0: Use Apache 2.0 license

## 2.0.4

### Patch Changes

- 4e21e24: Add integrity hash to external resources

## 2.0.3

### Patch Changes

- 1cafa55: Return `reply` in the `routeHandler`, in order to be compatible with the support for compression.

## 2.0.2

### Patch Changes

- e8faa76: Internally use the new `render` function, that takes the `request` as first argument.

## 2.0.1

### Patch Changes

- 195cb7b: Bump import-meta-resolve from 2.2.2 to 4.0.0

## 2.0.0

### Major Changes

- 6583c86: The plugin is now using the new Trifid factory, which is a breaking change.

  Assets are served under `/graph-explorer/assets/` and `/graph-explorer/static/` instead of `/graph-explorer-assets/` and `/graph-explorer-static/`.

- 4b515f8: Use 'plugins' instead of 'middlewares'
