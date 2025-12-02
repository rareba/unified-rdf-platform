# trifid-plugin-i18n

## 4.0.0

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

## 3.0.3

### Patch Changes

- f0e3b13: Fix and improve types references

## 3.0.2

### Patch Changes

- 080f5d8: Harmonize author and keywords fields
- a97a6a0: Use Apache 2.0 license

## 3.0.1

### Patch Changes

- c5a7bd4: Remove some unused dev dependencies

## 3.0.0

### Major Changes

- 4b515f8: Use 'plugins' instead of 'middlewares'
- 0f21191: The plugin is now using the new Trifid factory, which is a breaking change.
