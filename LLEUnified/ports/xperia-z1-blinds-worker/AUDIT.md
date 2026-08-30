# Xperia Z1 Blinds oracle audit

## Donor

- APK: `C:\Users\Manu\Downloads\com.othlocks.xperia.blinds+v1.0.3.apk`
- SHA-256: `B63FF88AE610868FA74084AA2F582A4E42BACB2E20F24B2BC94C5EF6CFDD13D0`
- Renderer: `com.othlocks.xperia.blinds.a.m`, created by the normal `BlindsLockscreen` path through `a.d`.
- Settings provider: `com.othlocks.xperia.blinds.a.q`.

## Demonstrated normal-renderer facts

The donor is an Android Canvas/Camera effect, not an OpenGL shader effect. It takes a single full-screen bitmap and divides it into **17 horizontal bands**. The ordinary touch path stores normalized X/Y, selects the affected band window from Y, and draws unaffected bands untransformed.

- Total bands: `17` (`q.o()` and `q.j()`).
- Active window width: `5 / 17`; the start/end equations are `floor((y - 0.5 * 5/17) * 17 + 0.5)` and `ceil((y + 0.5 * 5/17) * 17 - 0.5)`, clamped to `[0,17]`.
- Per-band fold signal: `sin(PI * (2 * (bandCenter - touchY) / (5/17)))`.
- Fold intensity: `(1 + cos(PI * normalizedDistance)) * springPosition`.
- Camera: `translateZ(3 * fold / 2)` and `rotateX(3 * wave * springPosition)`.
- The source and destination rectangles are the same band rectangle. Therefore the donor does not crop or zoom the bitmap while drawing a band.
- Positive fold shading uses `PorterDuffColorFilter(argb(i,255,255,255), OVERLAY)`; negative fold shading uses `LightingColorFilter(rgb(255-i), 0)`; `i` is bounded to `0..99`.
- It adds a black linear-gradient shadow at the upper edge of each folded band and seam lines at both edges. Default shadow length is `50 px` times `1 - abs(normalizedDistance)`.
- Default background is `0x9f000000`; seam/shadow colour is `0xbb000000`; seams are 2 px.
- Spring defaults are stiffness `400`, damping ratio `0.85`, target `1000` while drawing and `0` when released.
- On unlock, the donor computes per-band alpha deficits from touch-Y (`255 + 600 * normalizedDistanceToTouch`), decreases them over 300 ms, starts global alpha fade at 40 ms, and completes the transition at 200 ms.

## Gesture and lifecycle facts

- `ACTION_DOWN` enables blinds, records normalized touch X/Y, and drives the spring to its drawn target.
- `ACTION_MOVE` updates normalized X/Y.
- `ACTION_UP`/cancel stops drawing. A successful unlock starts the per-band/global fade; an unsuccessful release springs back to rest.
- Multi-touch is rejected by the donor lockscreen container. LLE receives normalized renderer callbacks rather than raw MotionEvents, so the staged renderer has deterministic reset/cancel handling but cannot reproduce that container-level rejection itself.
- Donor is Canvas-only and needs no effect shader asset.

## Audio evidence

The APK contains `res/raw/blind.wav` (30,878 bytes, SHA-256 `E11578519C13C9E9B6A4E6B117E5FE20499D099AE2ABA48BBC8EBE842B0476CF`) and `res/raw/lock.ogg` (4,661 bytes, SHA-256 `B04BFE78452B1BEFD257920F612CA9EA117BE8ED1FE538452A5EAC410583E381`). The donor's normal renderer does not own the sound path: the enclosing lockscreen loads/plays it. The staged `UnlockEffectRenderer` similarly has no sound API, so no duplicate asset is staged; integration should retain LLE's central `LockSoundPlayer` ownership and separately compare any existing Blind sound mapping before replacing audio.

## Deliberate port decisions

- `setBackgroundSourceBitmap` uses an exact full-rectangle source-to-render mapping, never a centre crop. For an unexpected dimension mismatch it scales the complete bitmap into the render rectangle rather than discarding edges; the normal LLE capture path is expected to be screen-sized.
- The donor's old container invalidates continuously and owns unlock completion. The staged view maps this to `postInvalidateOnAnimation`, `finishGesture(completed)`, readiness callbacks, and 200 ms bounded exit animation.
- No claim is made here about exact GPU/pixel output on modern Android: `Camera` projection and legacy `PorterDuff.Mode.OVERLAY` still require device comparison.
