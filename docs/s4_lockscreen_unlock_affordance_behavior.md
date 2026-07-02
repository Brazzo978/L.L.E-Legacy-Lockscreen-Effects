# Galaxy S4 lockscreen unlock-affordance behavior

Reverse target: `AP_I9505XXUHPK2_CL5978264_QB11574267_REV06_user_low_ship_MULTI_CERT.tar.md5`

This file is a behavioral specification only. It intentionally does not include Samsung APKs,
ODEX files, native libraries, resources, or decompiled source.

## Firmware components inspected

- `system.img.ext4`
- `priv-app/SystemUI/SystemUI.apk`
- `priv-app/SystemUI/arm/SystemUI.odex.xz`
- `framework/secvisualeffect.jar`
- `framework/arm/secvisualeffect.odex`
- `app/secvisualeffect-res/secvisualeffect-res.apk`

The relevant runtime code is split between SystemUI lockscreen wrappers and the
`com.samsung.android.visualeffect` effect engine.

## Command and effect ids

`com.samsung.android.visualeffect.EffectCmdType`:

- `SETBG = 0`
- `LOCKAFFORDANCE = 1`
- `UNLOCK = 2`
- `CUSTOM_CMD = 3`
- `CLEAR = "clear"`
- `TOUCH = "touch"`

`com.samsung.android.visualeffect.EffectType`:

- `POPPING_COLOUR = 3`
- `LENSFLARE = 11`

## Common lockscreen trigger

The center hint is not triggered by touch. It is fired by the lockscreen resume path after
screen-on.

SystemUI class:

- `com.android.keyguard.sec.KeyguardUnlockView`

Observed flow:

1. `onResume(int reason)` checks the resume reason.
2. When `reason == 1`, it records `mResumedTimeMillis = System.currentTimeMillis()`.
3. It calls `showUnlockAffordance()`.
4. `showUnlockAffordance()` creates an empty `Rect`.
5. It calls `getGlobalVisibleRect(rect)` on the full `KeyguardUnlockView`.
6. If the rect is valid, it calls:

```text
mUnlockView.showUnlockAffordance(500, rect)
```

The normal screen-on hint therefore starts after a 500 ms delay and uses the center of the
global visible lockscreen view:

```text
centerX = rect.left + ((rect.right - rect.left) / 2)
centerY = rect.top  + ((rect.bottom - rect.top) / 2)
```

There is also a bouncer path:

- `showBouncer(int duration)` gets `mBouncerFrameView.getGlobalVisibleRect(rect)`.
- It calls `mUnlockView.showUnlockAffordance(0, rect)`.

That path shows the hint immediately and uses the bouncer frame rect instead of the full
unlock view rect.

## Lens Flare

SystemUI wrapper:

- `com.android.keyguard.sec.KeyguardEffectViewLensFlare`

Engine class:

- `com.samsung.android.visualeffect.lock.lensflare.LensFlareEffect`

### Wrapper setup

The wrapper creates an `EffectView`, sets effect id `11`, builds `LensFlareData`, then
initializes the engine.

Resource names passed to `LensFlareData`:

- `keyguard_flare_hexagon_blue`
- `keyguard_flare_hexagon_green`
- `keyguard_flare_hexagon_orange`
- `keyguard_flare_hoverlight`
- `keyguard_flare_light_00040`
- `keyguard_flare_long`
- `keyguard_flare_particle`
- `keyguard_flare_rainbow`
- `keyguard_flare_ring`
- `keyguard_flare_vignetting`
- `lens_flare_tap`
- `lens_flare_unlock`

Unlock delay reported by the wrapper:

```text
getUnlockDelay() = 250 ms
```

### Screen-on and show commands

The wrapper sends custom commands to the engine:

- `screenTurnedOn()` sends `CUSTOM_CMD` with key `"screenTurnedOn"`.
- `show()` sends `CUSTOM_CMD` with key `"show"`.

Inside `LensFlareEffect`, `"screenTurnedOn"` sets:

```text
isPlayAffordance = true
```

### Unlock-affordance command

`KeyguardEffectViewLensFlare.showUnlockAffordance(long startDelay, Rect rect)` sends:

```text
command = LOCKAFFORDANCE
map["startDelay"] = startDelay
map["rect"] = rect
```

Important: Lens Flare uses lowercase map keys.

The engine receives command `1`, reads those keys, computes the rect center, and posts a
runnable after `startDelay`.

### Lens Flare hint animation

When the delayed runnable fires, the engine runs `playUnlockAffordance()`.

Exact behavior:

1. If the engine is still before init, return without playing.
2. Copy the affordance center into:

```text
showStartX = affordancePoint.x
showStartY = affordancePoint.y
```

3. Randomize the hexagon tap targets with `setHexagonRandomTarget(false)`.
4. Center these visual objects at `(showStartX, showStartY)`:

- `ring`
- `longLight`
- `particle`
- `lightFog`

5. Cancel any existing tap animator.
6. Start the tap animator.
7. Start the affordance-on animator.

Affordance alpha animation:

```text
on:  0.0 -> 0.6 over 200 ms, linear
off: 0.6 -> 0.0 over 1100 ms, linear
```

Only `lightFog` receives the affordance alpha directly:

```text
fogAlpha = clamp(affordanceAnimationValue, 0.0, 1.0)
lightFog.alpha = fogAlpha
```

Other useful Lens Flare constants observed in the engine:

- `SHOW_ANIMATION_DURATION = 6000 ms`
- `TAP_ANIMATION_DURATION = 4000 ms`
- `UNLOCK_ANIMATION_DURATION = 1200 ms`
- `HOVER_DURATION = 100000 ms`
- `HOVER_LIGHT_IN_DURATION = 500 ms`
- `HOVER_LIGHT_OUT_DURATION = 300 ms`
- `AFFORDANCE_ON_DURATION = 200 ms`
- `AFFORDANCE_OFF_DURATION = 1100 ms`
- `FOG_MAX_ALPHA = 0.6`
- `TAP_AREA_RADIUS = 600`
- `X_OFFSET = 0`
- `Y_OFFSET = -80`
- `FINGER_HOVER_Y_OFFSET = -80`
- `PEN_HOVER_Y_OFFSET = 0`

### Lens Flare implementation notes

For our app, the screen-on center hint should be:

```text
after 500 ms:
  center = center of visible lockscreen area
  start tap burst at center
  fade center fog/light from alpha 0.0 to 0.6 in 200 ms
  fade it from alpha 0.6 to 0.0 in 1100 ms
```

If matching the S4 unlock timing too:

```text
unlock visual delay = 250 ms
unlock animation duration = 1200 ms
```

## Popping Colour

SystemUI wrapper:

- `com.android.keyguard.sec.KeyguardEffectViewParticleSpace`

Engine classes:

- `com.samsung.android.visualeffect.lock.particle.ParticleSpaceEffect`
- `com.samsung.android.visualeffect.lock.particle.ParticleEffect`

### Wrapper setup

The wrapper creates an `EffectView`, sets effect id `3`, and initializes the particle engine.

Unlock delay reported by the wrapper:

```text
getUnlockDelay() = 300 ms
```

Sound constants in the wrapper:

- tap sound id: `0`
- unlock sound id: `1`
- drag sound id: `2`
- unlock sound play time: `2000 ms`
- tap volume: `0.3`
- unlock volume: `0.3`
- drag volume: `0.3`
- drag sound count start point: `40`
- drag sound count interval: `60`

### Screen-on command

The wrapper sends:

```text
particleSpaceEffect.handleCustomEvent(CUSTOM_CMD, null)
```

In the engine, command `3` starts the screen-on animation:

```text
screenOnAnimation: 1.0 -> 0.0 over 700 ms, CubicEaseOut
widget scale target: 1.2
background scale target: 1.05
```

This is separate from the center particle hint. The center hint still comes from the common
`LOCKAFFORDANCE` path described above.

### Unlock-affordance command

Before sending the hint command, the wrapper refreshes the wallpaper bitmap if available:

```text
command = SETBG
map["BGBitmap"] = wallpaperBitmap
```

Then it sends the affordance command:

```text
command = LOCKAFFORDANCE
map["StartDelay"] = startDelay
map["Rect"] = rect
```

Important: Popping Colour uses uppercase map keys.

The engine receives command `1`, reads those keys, computes the rect center, samples the
wallpaper color at that point, and posts a runnable after `startDelay`.

### Popping Colour hint animation

When the delayed runnable fires, the engine runs:

```text
particleEffect.addDots(15, affordanceX, affordanceY, affordanceColor)
```

Exact behavior:

1. Compute the affordance center from the provided rect.
2. Sample the effect color at that center with `getColor(x, y)`.
3. After `startDelay`, add 15 particles at that center with the sampled color.

Particle engine limits and defaults:

- initial created particle pool: `250`
- alive particle max: `150`
- drawing delay time: `2`
- unlock dot speed: `5`

`addDots(amount, x, y, color)` refuses to add particles if:

```text
aliveCount + amount > 150
```

It stores the last added position/color, converts the sampled RGB color to HSV, randomizes
saturation/value per particle, and emits the requested particles.

### Popping Colour implementation notes

For our app, the screen-on center hint should be:

```text
after 500 ms:
  center = center of visible lockscreen area
  color = wallpaper/background color sampled at center
  spawn 15 popping particles at center
```

If matching the S4 unlock timing too:

```text
unlock visual delay = 300 ms
unlock fills remaining particles up to max alive count at last point/color
unlock particle speed = 5
```

## Minimal integration checklist

To match the missing S4 behavior in our package:

1. On screen-on lockscreen resume, call each active effect's affordance method after `500 ms`.
2. Use the center of the visible lockscreen view, not the last touch point.
3. Lens Flare: play the tap/flare affordance at center and fade `lightFog` `0.0 -> 0.6 -> 0.0`
   with `200 ms` on and `1100 ms` off.
4. Popping Colour: spawn `15` particles at center using the wallpaper/background sampled color.
5. Keep unlock delays separate: Lens Flare `250 ms`, Popping Colour `300 ms`.
