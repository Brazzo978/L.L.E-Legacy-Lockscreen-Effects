# Unified bootstrap validation (2026-07-15)

## Build result

`build.ps1 -Target All` completed both target pipelines from the shared source
tree.

| Product | SHA-256 | Native ABI |
|---|---|---|
| `LLE-armeabi-v7a-debug.apk` | `58BE6EC1C097479D250D43D8183FAB4DA6B0F3227E05DE230EBFD4C440E32349` | `armeabi-v7a` only |
| `LLE-arm64-debug.apk` | `582BEF3E9C0C69999DE911F9B9A62CB132D8507177394286AE9CB44EC96FDC70` | `arm64-v8a` only |

Both report package `com.codex.lle`, version code `13`, version name
`1.1.0-unified-alpha.1` and signer certificate SHA-256
`5fe55abf389a3c681c61e56790dd1c85fc750f24e33e757d1f9f0e3ea45cb08f`.

## Shared application identity

The packaged shared application artifacts are byte-identical:

| APK entry | Size | SHA-256 |
|---|---:|---|
| `classes.dex` | 442,932 | `8CA0A45D51164E992DB73ED6CC85AC0054D88F12740E0A8504F717957DF27815` |
| `resources.arsc` | 16,524 | `126031D17C3A348773DD18D547B92FA2C9F8135494BE8D717C20D3F466B428E6` |
| `AndroidManifest.xml` | 5,380 | `B18BAEEEF5B9D555C6D991F98FBCC3D916BA30BF29B4C950E55DC466CEA668B7` |

Target-specific Samsung bytecode and native engines intentionally differ.
ARM32 additionally contains `classes3.dex` for the original S3 renderer.

## Native packaging checks

- ARM32 contains the eight expected `armeabi-v7a` libraries and no ARM64/x86
  entry. Its original Ripple, Watercolor, Abstract Tiles and Geometric Mosaic
  transparency patches completed. It now starts from the same bounded Samsung
  lifecycle DEX as ARM64 before adding its two target-specific pacing methods.
- ARM64 contains the seven expected `arm64-v8a` libraries and no ARM32/x86
  entry. ELF machine, SONAME, dependencies, JNI exports, staged hashes and
  bounded Samsung lifecycle dex checks completed.
- Both APKs pass v1, v2 and v3 signature verification.
- All three PowerShell entry points parse without syntax errors.

## Device status

The final ARM64 APK was installed in place on the Fold7 with preferences and
both panel caches preserved. The final accessibility service rebound as PID `32310`
with event types `WINDOW_STATE`, `WINDOW_CONTENT`, `WINDOWS_CHANGED` and a
`32 ms` notification timeout.

On the closed Cover (`1080x2520`), Lens Flare loaded the exact shared screenshot
cache with `decodeMs=0` and `applyMs=0-1`. Controlled Watercolor and Popping
Colours preload/destroy cycles completed without a lifecycle timeout, fatal
signal, recycled bitmap or orphan Samsung GL thread. Five Popping/Lens switch
cycles plateaued rather than growing linearly; only the active LLE effect
surface and the process RenderThread remained. ARM32 was build- and
signature-validated but cannot be runtime-tested on this ARM64-only Fold.

The final ARM32 and ARM64 products were then tested on a Galaxy S23 Ultra
(`SM-S918B`, Android 16) whose runtime reports both `arm64-v8a` and
`armeabi-v7a`. The first ARM32 launch exposed a unified-bootstrap defect:
`Lle64Abi` attempted to load the intentionally ARM64-only marker library in a
32-bit process. Runtime detection now uses `Process.is64Bit()`; ARM64 still
loads and validates the native marker, while ARM32 reports `armeabi-v7a`
without requesting it. The corrected ARM32 accessibility service rebound with
`connected abi=armeabi-v7a` and Android reported no crashed service.

All seven effects shared by the products completed normalized screen-off
prearm samples without a fatal signal, ANR, recycled-bitmap failure or bounded
lifecycle timeout. The full PSS, Graphics, latency and thread comparison is in
`ABI-COMPARISON-S23U-2026-07-15.md`. The S23 Ultra was restored to the final
ARM64 APK with S4 Lens Flare selected after testing.
