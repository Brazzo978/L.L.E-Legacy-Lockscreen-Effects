# L.L.E. - Legacy Lockscreen Effects

L.L.E. brings selected legacy Samsung lockscreen effects to modern Android
devices through an accessibility overlay. The unified application package is
`com.codex.lle` and one shared source tree now serves both ARM32 and ARM64.

> **Early Alpha:** expect device-specific visual and lifecycle differences.
> Keep another unlock method available. These builds also consume proprietary
> Samsung firmware-derived components; see the redistribution notice below.

## Choose the correct APK

The two APKs are alternatives with the same package name and cannot coexist.

| Release asset | Use it when |
|---|---|
| `LLE-arm64-debug.apk` | The device is ARM64-only, or you want the reconstructed ARM64 effects and Note 5 ARM64 engines |
| `LLE-armeabi-v7a-debug.apk` | The device supports 32-bit ARM applications and you want the original patched Samsung ARM32 engines, including Geometric Mosaic |

Check the supported ABIs:

```shell
adb shell getprop ro.product.cpu.abilist
```

## Quick install

Download one APK from the GitHub release, connect exactly the intended Android
device with USB debugging enabled, then run one of:

```shell
adb install -r "LLE-arm64-debug.apk"
```

```shell
adb install -r "LLE-armeabi-v7a-debug.apk"
```

Open the control application:

```shell
adb shell am start -n com.codex.lle/.ControlActivity
```

Then:

1. Enable the **L.L.E. 64** accessibility service.
2. Enable the lockscreen effect master switch.
3. Select an effect and wait for its two-second apply delay.
4. Capture the lockscreen background from the Screenshot service section.
5. Configure the lockscreen touch box or the dual-panel Fold wizard.
6. Lock and wake the device, then test inside the saved touch region.

`INSTALL_FAILED_NO_MATCHING_ABIS` means that the selected APK cannot run on
that device.

## Current scope

- One shared Java/UI/preference trunk for both products.
- Original patched Samsung engines on compatible ARM32 processes.
- App-owned ARM64 Water Ripple, Watercolor and Abstract Tiles reconstructions.
- Note 5 ARM64 Colored Droplet, Gyro and Sparkling Bubbles integration.
- Transparent screenshot-backed lockscreen composition.
- Fold Cover/Main screenshot caches, touch boxes and per-panel effect/doodle
  routing.
- ABI-aware picker with safe Lens Flare fallback.

Geometric Mosaic remains ARM32-only. Blind and Ink in Water remain hidden WIP
slots. ARM64 reconstructions target recovered Samsung behavior but are not yet
claimed to be pixel-identical.

## Documentation

- [Complete setup, effect matrix and troubleshooting](LLEUnified/README.md)
- [Unified architecture](LLEUnified/ARCHITECTURE.md)
- [Fold dual-panel port](LLEUnified/FOLD-DISPLAY-PORT.md)
- [Abstract Tiles ARM64 port specification](LLEUnified/ports/abstract-tiles/docs/ABSTRACT_TILES_ARM64_PORT_SPEC.md)
- [Early Alpha release notes](LLEUnified/docs/RELEASE_NOTES_EARLY_ALPHA.md)

The canonical application source and build scripts live in `LLEUnified/`.
Frozen pre-unification trees are retained only as historical references.

## Firmware and redistribution notice

The project uses proprietary Samsung firmware-derived native libraries,
bytecode, sounds and/or assets. The repository does not grant redistribution
rights for those components. Anyone publishing forks or binary builds is
responsible for establishing the necessary rights separately.
