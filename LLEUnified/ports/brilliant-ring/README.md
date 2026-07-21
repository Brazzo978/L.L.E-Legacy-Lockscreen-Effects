# Brilliant Ring port

This directory documents Samsung's direct-stock ARM32 path and the app-owned
ARM64 port of the Galaxy S5 / Note 4 Brilliant Ring unlock effect.

## Runtime implementation

- ARM64 renderer: `src/com/codex/lle/BrilliantRingEffectView.java`
- ARM32 stock host: `SamsungLockBgEffectView.brilliantRing()`
- Internal LLE effect id: `14`
- Samsung native effect id: `7`
- Core texture: `res/drawable-nodpi/brilliantring_diamond_pt.png`
- Audio: `res/raw/brilliantring_{tap,drag,unlock,lock}.ogg`

The renderer retains the native frame-based record model: initial age 12,
19-simulation-cell movement threshold, 61-update timeout, seven active records,
the 0.05/update oldest-record overflow fade, the type-0 inner/outer curves and
the 56-update type-1 unlock expansion. Samsung's piecewise quadratic easing
tables and API-21 Bionic TYPE_3 RNG are copied rather than approximated.

ARM64 reproduces the stock screen-sized radial pass, 5x simulation advection
pass and final Ring shader, including the original UV orientation, blend state,
texture filtering/wrapping and unguarded shader divisions. Portrait and
landscape use Samsung's respective DiamondPT coordinate layouts.

The stock TextureView was opaque and redrew the wallpaper fullscreen. LLE uses
the cached lockscreen bitmap only inside the active ring band, leaving all other
pixels transparent so SystemUI remains authoritative.

The staged ARM32 path executes Samsung's original `libsecveBrilliantRing.so`
against the original `libsecveSrkCommon.so` pipeline. Its build-time common
patch changes only the final Ring shader alpha output: the inactive stock
`alpha != 0.0` branch emits zero alpha, while the active branch retains
`uAlpha`. Stock RGB, CPU fields, radial FBO and advect shader are unchanged.
Service selection routes ARM32 to this stock factory and ARM64 to the app-owned
GLES reconstruction. Both use the same final local-alpha rule.

## Reverse-engineering record

The full Ghidra address map, shaders, FBO order, texture roles and CPU field
equations are recorded in:

`../../unlock-effects-test/analysis/BRILLIANT_RING_NATIVE_REVERSE_2026-07-20.md`

Primary reference binaries:

- `libsecveBrilliantRing.so` SHA-256
  `17F059922AFB2B15103EDAF817C7663890F99CDE9C153B55AC3E0CBAD27E3A79`
- `libsecveSrkCommon.so` SHA-256
  `5DBE95670EAE329DF47BF746D50FE9ED250CADC3FFFA9B2255A64F50C7AD6C36`
- `diamond_pt.png` SHA-256
  `0C57F43B62D9103B8C0D124AD8696BBB2AC0606690CEE5440E812F03C751C19B`

The PNG is pixel-identical after RGBA decode to the KitKat
`brilliantring_diamond_pt.webp` resource.
