# Note5 Colored Droplet reverse notes

Source library: `extracted/note5_aoj4_system_files/lib/libColourDropletEffect.so`.

Ghidra program: `/note5/libColourDropletEffect.so`, ARM 32-bit.

Important entry points:

- `PhysicsEngineJNI::Init_PhysicsEngine` at `00059c18`
- `PhysicsEngineJNI::Draw_PhysicsEngine` at `00059ce0`
- `PhysicsEngineJNI::onTouchEvent` at `00059d1c`
- `SPhysics::SPColourDropletApp::initApp` at `00063b08`
- `SPhysics::SPColourDropletApp::onEventTouch` at `00061e98`
- `SPhysics::SPColourDropletApp::updateTouchEvent` at `00064e60`
- `SPhysics::SPColourDropletApp::addSPHParticles` at `00066bf8`
- `SPhysics::SPColourDropletApp::updateSPH` at `00066d98`
- `SPhysics::SPColourDropletApp::addSubParticles` at `00060b98`
- `SPhysics::SPColourDropletApp::updateSubParticle` at `00061358`
- `SPhysics::SPColourDropletApp::addAffordanceParticles` at `00062200`
- `SPhysics::SPColourDropletApp::updateUnlock` at `0005f7a0`
- `SPDrawColourDroplet::drawRender` at `0005af08`
- `SPDrawColourDroplet::createWaterShader` at `0005b244`

Behavior extracted:

- Touch events are queued as triples: screen `x`, flipped `y`, and touch type.
- `DOWN` marks the touch stream active and stores the starting point.
- `MOVE` updates the previous/current touch points, derives velocity, and injects SPH particles.
- `UP` clears active touch and leaves existing particles to decay.
- The native draw path is multi-pass and opaque/fullscreen in Samsung's pipeline, so it cannot be used directly in the accessibility overlay without blackening the lockscreen.
- The native app has a center/affordance particle path and an unlock fade path. `updateBreath` advances by `0.02`; `updateUnlock` advances by `0.05` after its initial countdown.
- Original unlock threshold checks distance from initial touch greater than about `0.75 * width`, but the touch app keeps its existing service-level unlock threshold.

Transparent app port:

- Implemented as `ColourDropletEffectView`, a pure app-owned `View`.
- It keeps the original high-level model: down burst, drag-fed droplet/SPH-like blobs, sub-particles, center affordance, and unlock burst/fade.
- It never draws the screenshot or a full framebuffer. It samples the cached lockscreen screenshot only for color selection.
- Alpha is synthesized from local droplet energy, so pixels outside droplet/ring/spark regions remain transparent.
