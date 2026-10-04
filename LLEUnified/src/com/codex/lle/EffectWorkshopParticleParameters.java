package com.codex.lle;

import java.util.ArrayList;

/** Workshop schema for the app-owned Canvas particle renderers. */
final class EffectWorkshopParticleParameters {
    private EffectWorkshopParticleParameters() {}

    static EffectWorkshopConfig.Parameter[] parametersFor(int effect) {
        ArrayList<EffectWorkshopConfig.Parameter> p = new ArrayList<EffectWorkshopConfig.Parameter>();
        if (effect >= 28 && effect <= 30) {
            p.add(EffectWorkshopConfig.Parameter.integer("spawn_count", "Touch burst count", "Geometry", 5, 1, 40, 1, "particles","Sets the number of particles attempted when a new touch begins. Particles outside the sampled background are skipped."));
            p.add(n("interpolation", "Gesture divisions", "Geometry", 20, 4, 80, 1, "divisions","Divides the screen dimensions into emission intervals for a drag. Higher values create more intermediate particles along a long movement."));
            p.add(n("spawn_distance", "Minimum spacing", "Geometry", 1, .25f, 4, .05f, "×","Multiplies the minimum movement needed to emit a particle when a drag has not crossed a full gesture division. Higher values reduce emission during small movements."));
            p.add(n("size", "Particle size", "Appearance", 1, .2f, 4, .05f, "×","Multiplies the rendered size of the particles or seasonal sprites. It does not change the size of the input gesture."));
            p.add(EffectWorkshopConfig.Parameter.integer("color_jitter", "RGB variation", "Appearance", 20, 0, 100, 1, "RGB","Sets the range of a random brightness offset added equally to all three RGB channels of the sampled background color. Zero keeps the sampled color without this offset."));
            p.add(n("velocity_x", "Horizontal velocity", "Physics", 1, .1f, 4, .05f, "×","Multiplies the initial horizontal motion of each particle. Higher values make particles travel sideways faster."));
            p.add(n("velocity_y", effect == 30 ? "Bounce impulse" : "Vertical velocity", "Physics", 1, .1f, 4, .05f, "×","Multiplies vertical motion; for Bouncing, it scales the initial vertical acceleration and subsequent bounce impulses. Higher values make vertical movement stronger."));
            if (effect == 29) p.add(n("rotation", "Angular velocity", "Physics", 1, 0, 4, .05f, "×","Multiplies particle rotation relative to its original motion. Zero removes the adjustable rotation contribution."));
            if (effect == 30) {
                p.add(n("gravity", "Gravity", "Physics", 1, .1f, 4, .05f, "×","Multiplies the downward acceleration of Bouncing particles. Higher values pull particles down more strongly."));
                p.add(n("friction", "Horizontal friction", "Physics", 1, 0, 4, .05f, "×","Multiplies the horizontal deceleration of Bouncing particles. Higher values slow sideways motion sooner; zero removes this deceleration."));
            }
            p.add(n("sound_gain", "Sound gain", "Audio", 1, 0, 3, .05f, "×","Multiplies the effect sound volume when effect sounds are enabled. Playback volume is capped at the platform maximum."));
        } else if (effect == 25) {
            p.add(n("distance_scale", "Gesture distance", "Geometry", 1, .5f, 2, .05f, "×","Multiplies the gesture distance used to determine stretching and snap thresholds. Higher values require a longer drag to reach the same response."));
            p.add(n("between_factor", "Anchor resistance", "Physics", 40, 5, 100, 1, "factor","Divides the finger displacement to position the inner anchor. Higher values keep the anchor closer to the point where the gesture began."));
            p.add(n("line_gap", "Line end spacing", "Geometry", 20, 0, 60, 1, "dp","Sets the extra gap removed from the visible line near its endpoint. Higher values shorten the connecting line."));
            p.add(n("circle_adjust", "Circle spacing correction", "Geometry", 5, 0, 20, 1, "px","Subtracts this distance from the radius used to position the dragged circle near the outer ring. Higher values bring that circle closer to the gesture origin."));
            p.add(n("outer_alpha", "Outer opacity response", "Appearance", .8f, 0, 2, .05f, "factor","Controls how quickly the outer ring becomes opaque as the drag distance increases. The result is limited to full opacity."));
            p.add(EffectWorkshopConfig.Parameter.integer("outer_min_alpha", "Outer minimum opacity", "Appearance", 50, 0, 255, 1, "alpha","Sets the minimum opacity of the outer ring during an active gesture, from 0 to 255. The distance response can increase it up to full opacity."));
            p.add(n("fade_duration", "Short release fade", "Animation", 1, .25f, 1, .05f, "×","Multiplies the short-release animation and the outer-ring fade duration. Lower values make both finish sooner without changing the final unlock handoff."));
            p.add(n("line_retraction", "Line retraction duration", "Animation", 250, 50, 250, 10, "ms","Sets how long the connecting line takes to retract during the snap animation. Lower values make the line retract sooner."));
            p.add(n("sound_gain", "Sound gain", "Audio", 1, 0, 2, .05f, "×","Multiplies the effect sound volume when effect sounds are enabled. Playback volume is capped at the platform maximum."));
        } else if (effect >= 16 && effect <= 20) {
            p.add(n("size", "Sprite size", "Appearance", 1, .25f, 3, .05f, "×","Multiplies the rendered size of the particles or seasonal sprites. It does not change the size of the input gesture."));
            p.add(n("emission", "Emission probability", "Geometry", 1, 0, 2, .05f, "×","Multiplies the seasonal particle emission probability, capped at certainty. Zero prevents seasonal particle emission; higher values make emissions more frequent."));
            p.add(n("spread", "Particle spread", "Geometry", 1, 0, 3, .05f, "×","Multiplies the random diagonal offset where seasonal sprites appear around the finger. Higher values place sprites farther from the touch position."));
            p.add(n("spawn_distance", "Minimum movement", "Geometry", 1, .25f, 30, .25f, "dp","Sets the minimum finger movement between seasonal particle emission attempts. Lower values allow more frequent attempts during a drag."));
            p.add(n("rotation", "Particle rotation", "Animation", 1, 0, 3, .05f, "×","Multiplies particle rotation relative to its original motion. Zero removes the adjustable rotation contribution."));
            p.add(n("lifetime", "Particle lifetime", "Animation", 1, .25f, 4, .05f, "×","Multiplies seasonal particle timing, including growth, holding, fading and rotation phases. Higher values let particles remain on screen longer."));
            p.add(n("touch_duration", "Touch animation duration", "Animation", 1, .25f, 4, .05f, "×","Multiplies the duration of the seasonal touch animation. It changes the touch decoration timing without changing the final unlock handoff."));
            p.add(n("sound_gain", "Sound gain", "Audio", 1, 0, 3, .05f, "×","Multiplies the effect sound volume when effect sounds are enabled. Playback volume is capped at the platform maximum."));
        }
        return p.toArray(new EffectWorkshopConfig.Parameter[p.size()]);
    }

    private static EffectWorkshopConfig.Parameter n(String key, String label, String group,
            float value, float min, float max, float step, String unit, String description) {
        return EffectWorkshopConfig.Parameter.number(key, label, group, value, min, max, step, unit, description);
    }
}
