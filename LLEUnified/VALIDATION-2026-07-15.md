# Unified bootstrap validation (2026-07-15)

## Build result

`build.ps1 -Target All` completed both target pipelines from the shared source
tree.

| Product | SHA-256 | Native ABI |
|---|---|---|
| `LLE-armeabi-v7a-debug.apk` | `4E99C2979356C1E3FC1FDB10AE960A021E872503305AF7B93E39C0BAA7CC954E` | `armeabi-v7a` only |
| `LLE-arm64-debug.apk` | `CD802B52F3DEC0B4D9181C352A38D586E9EF07BA6864A0E1120FCE5964AD6DE4` | `arm64-v8a` only |

Both report package `com.codex.lle`, version code `13`, version name
`1.1.0-unified-alpha.1` and signer certificate SHA-256
`5fe55abf389a3c681c61e56790dd1c85fc750f24e33e757d1f9f0e3ea45cb08f`.

## Shared application identity

The packaged shared application artifacts are byte-identical:

| APK entry | Size | SHA-256 |
|---|---:|---|
| `classes.dex` | 414,972 | `9B670CBBA983968812FBCABC7F0098D340D40476AA315D52FB3616F71A4E8A85` |
| `resources.arsc` | 16,524 | `126031D17C3A348773DD18D547B92FA2C9F8135494BE8D717C20D3F466B428E6` |
| `AndroidManifest.xml` | 5,380 | `B18BAEEEF5B9D555C6D991F98FBCC3D916BA30BF29B4C950E55DC466CEA668B7` |

Target-specific Samsung bytecode and native engines intentionally differ.
ARM32 additionally contains `classes3.dex` for the original S3 renderer.

## Native packaging checks

- ARM32 contains the eight expected `armeabi-v7a` libraries and no ARM64/x86
  entry. Its original Ripple, Watercolor, Abstract Tiles and Geometric Mosaic
  transparency patches completed.
- ARM64 contains the seven expected `arm64-v8a` libraries and no ARM32/x86
  entry. ELF machine, SONAME, dependencies, JNI exports, staged hashes and
  bounded Samsung lifecycle dex checks completed.
- Both APKs pass v1, v2 and v3 signature verification.
- All three PowerShell entry points parse without syntax errors.

## Device status

The intended Fold7 ARM64 smoke-test installation was attempted after the local
validation, but the device disconnected from ADB before installation status
could be observed. No device-runtime result is claimed for this unified APK yet.
The prior `LLE64` ARM64 engines remain device-validated; the new shared picker
and renderer routing still require a short smoke test when ADB is available.
