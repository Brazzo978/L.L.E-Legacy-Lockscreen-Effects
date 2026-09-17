# L.L.E. repository rules

## Active development

- The active development branch is `main`.
- The only active Android application is `LLEUnified/`.
- The supported product is ARM64, package `com.codex.lle64`.
- Build with `powershell -ExecutionPolicy Bypass -File .\LLEUnified\build.ps1`.
- Do not recreate the removed ARM32 builder or depend on archived top-level apps.

## Historical material

The complete pre-cleanup repository, ARM32 product, reverse-engineering trees and
older applications remain available under `archive/*` branches. Treat those
branches as read-only historical references. Port a finding into `main`
deliberately; never merge a legacy application tree wholesale.

## Verification

- Preserve package identity, preference schema and signing lineage.
- Keep release keys outside the repository.
- Run the ARM64 build and relevant host tests after shared Java, resource,
  lifecycle or native changes.
- Verify user-visible behavior on a real device before calling a runtime change
  fixed.
- Releases are cut from `main`; update and verify the Companion feed as part
  of the release process.
