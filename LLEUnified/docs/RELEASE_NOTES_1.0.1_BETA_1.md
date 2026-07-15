# L.L.E. 1.0.1 Beta 1

## Release identity

- Tag: `v1.0.1-beta.1`
- Android version code: `14`
- Android version name: `1.0.1-beta.1`
- Launcher label: `L.L.E.` on both builds
- ARM32 application ID: `com.codex.lle`
- ARM64 application ID: `com.codex.lle.arm64dev`

The separate application IDs allow both APKs to remain installed. Their
preferences and screenshot caches are intentionally isolated. Enable only one
L.L.E. accessibility service at a time to prevent competing overlays.

## Assets

| Asset | ABI | Bytes | SHA-256 |
|---|---|---:|---|
| `LLE-1.0.1-Beta-1-32-bit.apk` | `armeabi-v7a` | 14,440,875 | `F210CF9C03174AE083A30C70AF4292D09DE5C899FFD55A740C8745369068CBD3` |
| `LLE-1.0.1-Beta-1-64-bit.apk` | `arm64-v8a` | 14,203,226 | `9F1F94764CDAF4698303048D29698EFDE40450068BF817F8F2A1ACC14A38D539` |

Both APKs are debug-signed and verified with Android v1, v2 and v3 signature
schemes. Use `adb install --no-incremental -r` for reliable native-library
installation.

## Effect status

- Water Ripple and Watercolor are now presented as Beta effects; the old
  `Early Alpha` picker suffix has been removed.
- ARM64 `N4 Abstract Tiles (Alpha)` is included but remains explicitly Alpha
  while its recovered scatter channels and animation curves receive further
  1:1 fidelity work.
- ARM32 Abstract Tiles continues to use the original patched Samsung engine.
- Geometric Mosaic remains ARM32-only and is hidden in the ARM64 process.
- Colored Droplet, Colored Droplet + Gyro, Sparkling Bubbles, Lens Flare and
  Popping Colours remain available according to the ABI registry.

## Device validation

Validated on an SM-S918B:

- Both application IDs installed simultaneously and reported the expected
  `armeabi-v7a` and `arm64-v8a` primary process ABIs.
- Both reported version code `14`, version name `1.0.1-beta.1` and launched
  without a fatal exception.
- The ARM32 Abstract Tiles runtime profile completed successfully, including
  native initialization, cached `1440x3088` background upload and overlay
  attach/destroy lifecycle.
- The ARM64 accessibility service connected and its profiler exposed the
  correct `N4 Abstract Tiles (Alpha)` identity. The renderer portion was not
  forced because Charging Doodle was enabled in that installation's private
  preferences during the locked-device test.
- The phone was returned to the ARM32 daily service after validation; the ARM64
  service remained disabled.

## Known limitations

- ARM64 Abstract Tiles is Alpha and is not claimed to be pixel-identical yet.
- ARM64 Water Ripple and Watercolor are reconstructed Beta renderers and can
  still differ subtly in timing, blur, distortion or shader output by GPU.
- Fold panel classification and screenshot capture remain device-sensitive.
- These APKs include proprietary Samsung firmware-derived components. The
  repository does not grant redistribution rights for those components.
