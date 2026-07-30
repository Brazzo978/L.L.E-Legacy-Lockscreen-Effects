# L.L.E. - Legacy Lockscreen Effects

L.L.E. brings a collection of legacy lockscreen effects to modern Android
devices. The project is currently available as a co-installable ARM32 and ARM64
Beta.

> **Beta:** behavior can vary between devices and Android versions. Keep another
> unlock method available. ARM64 Abstract Tiles is still marked **Alpha**.

> **Disclaimer:** This tool is only for Samsung Devices, it does **not** bring effects to other devices nor does it bring back Legacy Effects from/for Non-Samsung Devices. (actually works on a lot of other devices)

## Download

Download the APKs from the
[L.L.E. 1.0.1 Beta 1 release](https://github.com/Brazzo978/L.L.E-Legacy-Lockscreen-Effects/releases/tag/v1.0.1-beta.1).

| APK | Choose it when |
|---|---|
| `LLE-1.0.1-Beta-1-32-bit.apk` | The device supports 32-bit ARM applications; this is the recommended daily build on compatible phones |
| `LLE-1.0.1-Beta-1-64-bit.apk` | The device is ARM64-only or you want to test the ARM64 Beta |

Check the device ABI list with:

```shell
adb shell getprop ro.product.cpu.abilist
```

## Install

```shell
adb install --no-incremental -r "LLE-1.0.1-Beta-1-32-bit.apk"
```

```shell
adb install --no-incremental -r "LLE-1.0.1-Beta-1-64-bit.apk"
```

Both applications appear as **L.L.E.** and can remain installed together.
Keep only one L.L.E. accessibility service enabled at a time.

Open the ARM32 application:

```shell
adb shell am start -n com.codex.lle/.ControlActivity
```

Open the ARM64 application:

```shell
adb shell am start -n com.codex.lle.arm64dev/com.codex.lle.ControlActivity
```

## First setup

1. Open L.L.E. and enable its accessibility service.
2. Enable the main switch and **Unlock effect on lockscreen**.
3. Select an effect and wait for it to be applied.
4. Capture the lockscreen background from **Screenshot service**.
5. Configure the touch box or the Fold dual-panel wizard.
6. Lock and wake the device, then test inside the saved touch region.

## Effect availability

| Effect | ARM32 | ARM64 |
|---|:---:|:---:|
| S4 Lens Flare | Yes | Yes |
| S3 Water Ripple | Yes | Yes |
| S5 Popping Colours | Yes | Yes |
| N3 Watercolor | Yes | Yes |
| N4 Abstract Tiles | Yes | **Alpha - refinement pending** |
| N4 Geometric Mosaic | Yes | No |
| N5 Colored Droplet | Yes | Yes |
| N5 Colored Droplet + Gyro | Yes | Yes |
| N5 Sparkling Bubbles | Yes | Yes |

Water Ripple and Watercolor are Beta effects. Geometric Mosaic is currently
available only in the ARM32 build.

## Fold support

Fold mode provides separate Cover and Main screenshot caches, touch boxes and
per-panel switches. Effects and doodles can be enabled independently for each
screen.

## Documentation

- [Complete setup and troubleshooting](LLEUnified/README.md)
- [1.0.1 Beta 1 release notes](LLEUnified/docs/RELEASE_NOTES_1.0.1_BETA_1.md)
- [Developer documentation](LLEUnified/)

## Third-party components

Some builds contain **legacy third-party compatibility components**. Rights in
those components remain with their respective owners. This project does not
claim ownership of them and is not affiliated with or endorsed by their
owners. Anyone redistributing binary builds is responsible for confirming the
applicable permissions.
