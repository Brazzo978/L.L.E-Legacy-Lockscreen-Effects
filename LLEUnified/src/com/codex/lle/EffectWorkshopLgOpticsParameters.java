package com.codex.lle;

/** App-owned LG optical/motion controls; Hula keys are specific to V1 or V2. */
final class EffectWorkshopLgOpticsParameters {
    private EffectWorkshopLgOpticsParameters() {}
    static EffectWorkshopConfig.Parameter[] parametersFor(int effect) {
        switch(effect) {
        case 37: return new EffectWorkshopConfig.Parameter[] {
            EffectWorkshopConfig.Parameter.number("minimum_radius", "Minimum radius", "Geometry", 54.0f, 20.0f, 100.0f, 1.0f, "dp", "Sets the smallest white-hole radius in density-independent pixels."),
            EffectWorkshopConfig.Parameter.number("drag_threshold", "Drag threshold", "Geometry", 0.31f, 0.15f, 0.5f, 0.01f, "short side", "Sets the gesture threshold as a fraction of the shorter screen edge. Higher values require a longer drag."),
            EffectWorkshopConfig.Parameter.number("band_width", "Warp band width", "Optics", 100.0f, 30.0f, 200.0f, 5.0f, "dp", "Sets the width of the optical warp band in density-independent pixels."),
            EffectWorkshopConfig.Parameter.number("absorb_strength", "Absorption strength", "Optics", 0.48f, 0.0f, 0.8f, 0.02f, "factor", "Controls the texture displacement toward the white hole within its warp band. Zero removes this contribution."),
            EffectWorkshopConfig.Parameter.number("edge_strength", "Edge distortion strength", "Optics", 0.14f, 0.0f, 0.4f, 0.01f, "factor", "Controls the additional distortion at the white hole's edge. Zero removes this contribution."),
            EffectWorkshopConfig.Parameter.number("corona_scale", "Corona scale", "Geometry", 1.515f, 1.0f, 2.0f, 0.025f, "factor", "Sets the corona size relative to the white-hole radius."),
            EffectWorkshopConfig.Parameter.number("corona_rotation", "Corona rotation speed", "Motion", 0.0072f, 0.0f, 0.03f, 0.001f, "degrees/ms", "Sets corona rotation in degrees per millisecond. Zero stops the corona rotation."),
        };
        case 38: return new EffectWorkshopConfig.Parameter[] {
            EffectWorkshopConfig.Parameter.number("minimum_radius", "Minimum radius", "Geometry", 44.0f, 20.0f, 90.0f, 1.0f, "dp", "Sets the smallest soda gesture radius in density-independent pixels."),
            EffectWorkshopConfig.Parameter.number("drag_threshold", "Drag threshold", "Geometry", 113.32999f, 90.0f, 200.0f, 1.0f, "dp", "Sets the soda gesture boundary in density-independent pixels. Higher values require a wider gesture for the same progress."),
            EffectWorkshopConfig.Parameter.number("halo_radius", "Halo radius ratio", "Geometry", 1.1875f, 1.0f, 1.5f, 0.025f, "factor", "Sets the halo radius relative to the gesture circle."),
            EffectWorkshopConfig.Parameter.number("cutout_radius", "Cutout radius ratio", "Geometry", 1.2f, 1.0f, 1.5f, 0.025f, "factor", "Sets the central cutout radius relative to the gesture circle."),
            EffectWorkshopConfig.Parameter.number("glow_radius", "Glow radius ratio", "Geometry", 0.92f, 0.7f, 1.2f, 0.02f, "factor", "Sets the radius of the shader glow ring relative to the circle."),
            EffectWorkshopConfig.Parameter.number("glow_width", "Glow width", "Geometry", 0.022f, 0.005f, 0.06f, 0.002f, "factor", "Sets the width of the shader glow ring in normalized radial coordinates."),
            EffectWorkshopConfig.Parameter.integer("center_count", "Center particles per texture", "Emission", 8.0f, 0.0f, 16.0f, 1.0f, "per texture", "Sets the number of central particles for each texture. Zero removes central particles."),
            EffectWorkshopConfig.Parameter.integer("rising_count", "Rising particles per texture", "Emission", 7.0f, 0.0f, 14.0f, 1.0f, "per texture", "Sets the number of rising particles for each texture. Zero removes this group."),
            EffectWorkshopConfig.Parameter.integer("column_count", "Particles per column", "Emission", 10.0f, 0.0f, 20.0f, 1.0f, "per column", "Sets the number of particles in each vertical column. Zero removes column particles."),
            EffectWorkshopConfig.Parameter.number("center_life_scale", "Center cycle duration", "Motion", 1.0f, 0.4f, 2.0f, 0.1f, "x", "Multiplies the central particle cycle duration. Higher values make each cycle last longer."),
            EffectWorkshopConfig.Parameter.number("center_delay_scale", "Center start delay", "Motion", 1.0f, 0.0f, 2.0f, 0.1f, "x", "Multiplies the initial delay of central particles. Zero removes that delay."),
            EffectWorkshopConfig.Parameter.number("rising_hold_scale", "Rising particle wait", "Motion", 1.0f, 0.0f, 2.0f, 0.1f, "x", "Multiplies the wait before rising particles begin moving. Zero removes the wait."),
            EffectWorkshopConfig.Parameter.number("rise_life_scale", "Rise cycle duration", "Motion", 1.0f, 0.5f, 2.0f, 0.1f, "x", "Multiplies rising particle travel duration calculated from distance and speed. Higher values slow each cycle."),
            EffectWorkshopConfig.Parameter.number("small_size_scale", "Small particle size", "Geometry", 1.0f, 0.5f, 2.0f, 0.1f, "x", "Multiplies the size of small particle sprites."),
            EffectWorkshopConfig.Parameter.number("large_size_scale", "Large particle size", "Geometry", 1.0f, 0.5f, 2.0f, 0.1f, "x", "Multiplies the size of large particle sprites."),
            EffectWorkshopConfig.Parameter.number("column_size_scale", "Column particle size", "Geometry", 1.0f, 0.5f, 2.0f, 0.1f, "x", "Multiplies the size of column particle sprites."),
            EffectWorkshopConfig.Parameter.number("particle_alpha", "Particle opacity", "Optics", 1.0f, 0.0f, 1.0f, 0.05f, "x", "Multiplies the opacity of every soda particle. Zero hides the particles."),
            EffectWorkshopConfig.Parameter.number("wander_spread", "Motion angle spread", "Motion", 0.942478f, 0.0f, 1.6f, 0.05f, "rad", "Sets the angular spread of wandering particle motion in radians. Zero removes this random angular spread."),
            EffectWorkshopConfig.Parameter.number("cycle_angle", "Cycle angle increment", "Motion", 2.3999631f, 0.0f, 6.28f, 0.1f, "rad", "Sets the angular increment between successive particle cycles in radians."),
            EffectWorkshopConfig.Parameter.number("rise_speed_min", "Minimum rise speed", "Motion", 80.0f, 20.0f, 200.0f, 5.0f, "dp/s", "Sets the lower end of random small-particle rise speed in density-independent pixels per second."),
            EffectWorkshopConfig.Parameter.number("rise_speed_max", "Maximum rise speed", "Motion", 320.0f, 200.0f, 500.0f, 10.0f, "dp/s", "Sets the upper end of random small-particle rise speed in density-independent pixels per second."),
            EffectWorkshopConfig.Parameter.number("large_speed_min", "Minimum large-particle speed", "Motion", 80.0f, 20.0f, 200.0f, 5.0f, "dp/s", "Sets the lower end of random large-particle rise speed in density-independent pixels per second."),
            EffectWorkshopConfig.Parameter.number("large_speed_max", "Maximum large-particle speed", "Motion", 300.0f, 200.0f, 500.0f, 10.0f, "dp/s", "Sets the upper end of random large-particle rise speed in density-independent pixels per second."),
            EffectWorkshopConfig.Parameter.number("burst_scale", "Burst motion scale", "Motion", 1.0f, 0.0f, 2.0f, 0.1f, "x", "Multiplies the particle displacement during the outward burst. Zero removes this burst displacement."),
            EffectWorkshopConfig.Parameter.number("cancel_drift_scale", "Cancel drift scale", "Motion", 1.0f, 0.0f, 2.0f, 0.1f, "x", "Multiplies particle drift during cancellation. Zero removes that drift."),
            EffectWorkshopConfig.Parameter.number("large_travel_ratio", "Large-particle travel ratio", "Geometry", 0.72f, 0.3f, 1.2f, 0.05f, "factor", "Sets the screen-width contribution to large-particle travel distance, which also includes screen height and sprite size."),
            EffectWorkshopConfig.Parameter.number("column_travel_ratio", "Column travel ratio", "Geometry", 0.62f, 0.3f, 1.2f, 0.05f, "factor", "Sets the screen-width contribution to column-particle travel distance, which also includes screen height and sprite size."),
        };
        case 39: return new EffectWorkshopConfig.Parameter[] {
            EffectWorkshopConfig.Parameter.number("minimum_radius", "Minimum radius", "Geometry", 44.0f, 20.0f, 90.0f, 1.0f, "dp", "Sets the smallest dewdrop radius in density-independent pixels."),
            EffectWorkshopConfig.Parameter.number("drag_threshold", "Drag threshold", "Geometry", 113.33f, 90.0f, 200.0f, 1.0f, "dp", "Sets the dewdrop gesture boundary in density-independent pixels. Higher values require a wider gesture for the same progress."),
            EffectWorkshopConfig.Parameter.number("refraction_index", "Refraction index", "Optics", 3.0f, 1.1f, 5.0f, 0.1f, "factor", "Sets the index used to calculate refracted texture sampling through the drop."),
            EffectWorkshopConfig.Parameter.number("ellipse_cap", "Maximum ellipse height", "Optics", 90.0f, 30.0f, 180.0f, 5.0f, "dp", "Caps the drop's elliptical height in density-independent pixels before it tapers toward full-screen expansion."),
            EffectWorkshopConfig.Parameter.number("ellipse_ratio", "Ellipse height ratio", "Optics", 0.4f, 0.1f, 0.7f, 0.02f, "factor", "Sets elliptical height relative to drop radius before the height cap is reached."),
            EffectWorkshopConfig.Parameter.number("optical_overlay_scale", "Optical overlay scale", "Optics", 1.0f, 0.5f, 1.5f, 0.05f, "x", "Multiplies the size of the optical overlay texture along its radius-dependent growth curve."),
        };
        case 42: return new EffectWorkshopConfig.Parameter[] {
            EffectWorkshopConfig.Parameter.number("v1_minimum_radius", "V1 minimum radius", "V1 Geometry", 50.2f, 20.0f, 100.0f, 1.0f, "dp", "Sets the minimum ring radius in revision V1 only, scaled by display density."),
            EffectWorkshopConfig.Parameter.number("v1_drag_threshold", "V1 drag threshold", "V1 Geometry", 128.0f, 100.0f, 220.0f, 2.0f, "dp", "Sets the gesture boundary in revision V1 only, scaled by display density."),
            EffectWorkshopConfig.Parameter.number("v1_layer_base", "V1 base layer scale", "V1 Geometry", 1.3f, 1.0f, 1.6f, 0.02f, "factor", "Sets the initial layered-ring scale in revision V1. The layer-change value reduces it as the gesture expands."),
            EffectWorkshopConfig.Parameter.number("v1_layer_change", "V1 layer scale reduction", "V1 Geometry", 0.1f, 0.0f, 0.25f, 0.01f, "factor", "Sets how much revision V1's layered-ring scale decreases with gesture progress."),
            EffectWorkshopConfig.Parameter.number("v1_pivot_ratio", "V1 pivot offset ratio", "V1 Geometry", 0.5f, 0.0f, 1.0f, 0.05f, "factor", "Sets revision V1's pivot offset relative to the difference between layer and gesture radii."),
            EffectWorkshopConfig.Parameter.number("v1_fast_velocity", "V1 fast-motion threshold", "V1 Motion", 16.0f, 4.0f, 60.0f, 2.0f, "dp/s", "Sets the movement speed that triggers fast-motion behavior in revision V1, scaled by display density."),
            EffectWorkshopConfig.Parameter.number("v1_trail_scale", "V1 trail displacement scale", "V1 Motion", 1.0f, 0.0f, 3.0f, 0.1f, "x", "Multiplies revision V1's motion trail displacement. Zero removes this displacement."),
            EffectWorkshopConfig.Parameter.number("v1_trail_decay", "V1 trail retention", "V1 Motion", 0.967741935f, 0.8f, 0.995f, 0.005f, "factor", "Sets the fraction of revision V1's trail retained per update. Higher values make the trail last longer."),
            EffectWorkshopConfig.Parameter.number("v1_rotation_min", "V1 minimum rotation period", "V1 Motion", 700.0f, 300.0f, 1800.0f, 50.0f, "ms", "Sets the short end of revision V1's rotation period, interpolated according to gesture radius. Lower values allow faster rotation."),
            EffectWorkshopConfig.Parameter.number("v1_rotation_max", "V1 maximum rotation period", "V1 Motion", 2500.0f, 1800.0f, 5000.0f, 100.0f, "ms", "Sets the long end of revision V1's rotation period, interpolated according to gesture radius. Higher values allow slower rotation."),
            EffectWorkshopConfig.Parameter.number("v1_pivot_speed", "V1 pivot rotation speed", "V1 Motion", 5.0f, 1.0f, 15.0f, 0.5f, "degrees/16ms", "Sets revision V1's pivot rotation increment per nominal 16-millisecond update."),
            EffectWorkshopConfig.Parameter.number("v1_intro_ms", "V1 introduction duration", "V1 Motion", 300.0f, 100.0f, 600.0f, 20.0f, "ms", "Sets how long revision V1's ring introduction lasts."),
            EffectWorkshopConfig.Parameter.number("v1_intro_tension", "V1 introduction overshoot", "V1 Motion", 2.0f, 0.0f, 4.0f, 0.1f, "factor", "Sets the overshoot tension of revision V1's introduction curve. Higher values increase overshoot."),
            EffectWorkshopConfig.Parameter.number("v1_outer_ring", "V1 outer ring radius", "V1 Geometry", 128.0f, 80.0f, 220.0f, 2.0f, "dp", "Sets revision V1's outer-ring size in density-independent pixels."),
            EffectWorkshopConfig.Parameter.number("v1_outer_rotation", "V1 outer ring rotation scale", "V1 Motion", 0.35f, 0.0f, 1.0f, 0.05f, "x", "Multiplies revision V1's outer-ring rotation. Zero stops this rotation."),
            EffectWorkshopConfig.Parameter.number("v1_reflection_size", "V1 reflection size", "V1 Optics", 1.0f, 0.5f, 2.0f, 0.1f, "x", "Multiplies revision V1's reflection sprite sizes."),
            EffectWorkshopConfig.Parameter.number("v1_reflection_alpha", "V1 reflection opacity gain", "V1 Optics", 1.0f, 0.0f, 2.0f, 0.1f, "x", "Multiplies revision V1's reflection opacity, capped at fully opaque. Zero hides the reflections."),
            EffectWorkshopConfig.Parameter.number("v1_reflection_rotation", "V1 reflection rotation scale", "V1 Motion", 1.0f, 0.2f, 2.0f, 0.1f, "x", "Multiplies revision V1's reflection rotation."),
            EffectWorkshopConfig.Parameter.number("v1_layer_trail_0", "V1 layer 0 trail scale", "V1 Geometry", 1.3f, 0.0f, 2.0f, 0.1f, "x", "Multiplies the trail displacement of layer 0 in revision V1 only. Zero removes that layer's trail displacement."),
            EffectWorkshopConfig.Parameter.number("v1_layer_trail_1", "V1 layer 1 trail scale", "V1 Geometry", 1.2f, 0.0f, 2.0f, 0.1f, "x", "Multiplies the trail displacement of layer 1 in revision V1 only. Zero removes that layer's trail displacement."),
            EffectWorkshopConfig.Parameter.number("v1_layer_trail_2", "V1 layer 2 trail scale", "V1 Geometry", 1.1f, 0.0f, 2.0f, 0.1f, "x", "Multiplies the trail displacement of layer 2 in revision V1 only. Zero removes that layer's trail displacement."),
            EffectWorkshopConfig.Parameter.number("v1_layer_trail_3", "V1 layer 3 trail scale", "V1 Geometry", 0.9f, 0.0f, 2.0f, 0.1f, "x", "Multiplies the trail displacement of layer 3 in revision V1 only. Zero removes that layer's trail displacement."),
            EffectWorkshopConfig.Parameter.number("v1_layer_angle_0", "V1 layer 0 angle", "V1 Geometry", 0.0f, 0.0f, 360.0f, 5.0f, "degrees", "Sets the initial angular offset of layer 0 in revision V1 only, in degrees."),
            EffectWorkshopConfig.Parameter.number("v1_layer_angle_1", "V1 layer 1 angle", "V1 Geometry", 90.0f, 0.0f, 360.0f, 5.0f, "degrees", "Sets the initial angular offset of layer 1 in revision V1 only, in degrees."),
            EffectWorkshopConfig.Parameter.number("v1_layer_angle_2", "V1 layer 2 angle", "V1 Geometry", 180.0f, 0.0f, 360.0f, 5.0f, "degrees", "Sets the initial angular offset of layer 2 in revision V1 only, in degrees."),
            EffectWorkshopConfig.Parameter.number("v1_layer_angle_3", "V1 layer 3 angle", "V1 Geometry", 270.0f, 0.0f, 360.0f, 5.0f, "degrees", "Sets the initial angular offset of layer 3 in revision V1 only, in degrees."),
            EffectWorkshopConfig.Parameter.number("v2_minimum_radius", "V2 minimum radius", "V2 Geometry", 50.199982f, 20.0f, 100.0f, 1.0f, "dp", "Sets the minimum ring radius in revision V2 only, scaled by display density."),
            EffectWorkshopConfig.Parameter.number("v2_ring_stride", "V2 ring spacing", "V2 Geometry", 15.0f, 0.0f, 40.0f, 1.0f, "dp", "Sets revision V2's maximum outer-ring spacing in density-independent pixels."),
            EffectWorkshopConfig.Parameter.number("v2_stretch_speed", "V2 stretch speed threshold", "V2 Motion", 0.2f, 0.05f, 0.8f, 0.025f, "px/ms", "Sets the radial drag-speed threshold for stretching in revision V2, in pixels per millisecond. Higher values require faster movement to stretch."),
            EffectWorkshopConfig.Parameter.integer("v2_stretch_delay", "V2 stretch delay", "V2 Motion", 5.0f, 0.0f, 12.0f, 1.0f, "frames", "Sets revision V2's frame delay when changing between stretched and rotating states."),
            EffectWorkshopConfig.Parameter.number("v2_max_stretch", "V2 maximum stretch", "V2 Geometry", 2.0f, 1.0f, 3.0f, 0.1f, "x", "Caps revision V2's stretching multiplier. Higher values allow a longer shape."),
            EffectWorkshopConfig.Parameter.number("v2_spring", "V2 spring stiffness", "V2 Motion", 0.01f, 0.002f, 0.04f, 0.002f, "factor", "Sets the restoring force of revision V2's stretch spring. Higher values pull it back more strongly."),
            EffectWorkshopConfig.Parameter.number("v2_damping", "V2 spring damping", "V2 Motion", 0.03f, 0.01f, 0.15f, 0.005f, "factor", "Sets damping of revision V2's stretch spring. Higher values reduce oscillation faster."),
            EffectWorkshopConfig.Parameter.number("v2_hermite_tangent", "V2 curve tangent", "V2 Geometry", 1.6568542f, 1.2f, 2.0f, 0.05f, "factor", "Sets the Hermite tangent used to construct revision V2's curved ring mesh."),
            EffectWorkshopConfig.Parameter.number("v2_ring_ratio", "V2 ring spacing ratio", "V2 Geometry", 0.12f, 0.0f, 0.25f, 0.01f, "factor", "Sets revision V2's outer-ring spacing relative to the current radius, capped by the ring-spacing distance."),
            EffectWorkshopConfig.Parameter.number("v2_ring_pivot", "V2 ring pivot ratio", "V2 Geometry", 0.25f, 0.0f, 0.6f, 0.025f, "factor", "Sets revision V2's pivot displacement relative to ring radius and each ring's delay."),
            EffectWorkshopConfig.Parameter.number("v2_rotation_scale", "V2 rotation scale", "V2 Motion", 1.0f, 0.0f, 3.0f, 0.1f, "x", "Multiplies revision V2's ring rotation. Zero stops this rotation."),
            EffectWorkshopConfig.Parameter.number("v2_angular_offset", "V2 angular offset", "V2 Geometry", 8.0f, 0.0f, 25.0f, 1.0f, "degrees", "Sets the angular separation of revision V2's colored rings in degrees."),
            EffectWorkshopConfig.Parameter.number("v2_alpha", "V2 ring opacity", "V2 Optics", 0.8f, 0.1f, 1.0f, 0.05f, "factor", "Sets revision V2's base ring opacity."),
            EffectWorkshopConfig.Parameter.number("v2_stretch_min", "V2 minimum random stretch", "V2 Geometry", 0.7f, 0.5f, 1.0f, 0.05f, "x", "Sets the lower end of revision V2's randomly selected stretch multiplier."),
            EffectWorkshopConfig.Parameter.number("v2_stretch_max", "V2 maximum random stretch", "V2 Geometry", 1.1f, 1.0f, 1.4f, 0.05f, "x", "Sets the upper end of revision V2's randomly selected stretch multiplier."),
        };
        default: return new EffectWorkshopConfig.Parameter[0];
        }
    }
    /** Direct translation of dewdrop_vs.glsl for a positive radial coordinate. */
    static float dewdropSourceRadius(EffectWorkshopConfig.Values workshop, float position, float a, float b) {
        if (position <= 0.0001f || a <= 0.0001f || b <= 0.0001f) return 0f;
        float clamped = Math.min(position, a);
        float z = b * (float) Math.sqrt(Math.max(0f, 1f - clamped * clamped / (a * a)));
        float slope = (float) Math.atan((a * a * z) / (b * b * clamped));
        float incidence = (float) (Math.PI * 0.5) - slope;
        float refraction = (float) Math.asin(Math.sin(incidence) / workshop.get("refraction_index"));
        float newM = (float) Math.tan(slope + refraction);
        if (Math.abs(newM) < 0.0001f) return position;
        return Math.abs(position - z / newM);
    }

    static float dewdropEllipseHeight(EffectWorkshopConfig.Values workshop, float a, float density, float full) {
        float cap = workshop.get("ellipse_cap") * density;
        float b = workshop.get("ellipse_ratio") * a;
        if (b > cap) {
            float taperStart = cap / workshop.get("ellipse_ratio");
            b = cap * ((full - a) / Math.max(1f, full - taperStart));
        }
        return Math.max(0f, b);
    }

    /** Direct translation of the donor m180b() optical-overlay scale curve. */
    static float dewdropOverlayDiameter(EffectWorkshopConfig.Values workshop, float r, float density) {
        float scale;
        if (r <= 47.342f * density) {
            scale = 0.23f / (47.342f * density) * r;
        } else if (r <= 123.865f * density) {
            scale = 0.00108987f + 0.37f / (76.522f * density) * r;
        } else if (r <= 205.346f * density) {
            scale = 0.4f / (81.48f * density) * r - 0.00807484f;
        } else if (r <= 265.969f * density) {
            scale = 0.7f / (142.103f * density) * r - 0.01016121f;
        } else if (r <= 373.158f * density) {
            scale = 0.05934662f + 0.5f / (107.189f * density) * r;
        } else {
            scale = 0.7f / (145.098f * density) * r - 0.000226446f;
        }
        // BitmapFactory scaled the original hdpi resource to the device density.
        float decodedArchiveWidth = 720f * density / 1.5f;
        return Math.max(0f, decodedArchiveWidth * scale * workshop.get("optical_overlay_scale"));
    }

    static float sodaRiseSpeed(EffectWorkshopConfig.Values values, float randomUnit, boolean large) {
        String prefix = large ? "large_speed_" : "rise_speed_";
        return values.get(prefix + "min") + randomUnit
                * (values.get(prefix + "max") - values.get(prefix + "min"));
    }

    static long sodaRiseCycle(EffectWorkshopConfig.Values values, float travel, float speed) {
        return Math.max(1L, Math.round(travel / Math.max(1f, speed)
                * 1000f * values.get("rise_life_scale")));
    }

    static float sodaSpriteSize(EffectWorkshopConfig.Values values, int kind, float baseSize) {
        return baseSize * values.get(kind == 2 ? "large_size_scale"
                : kind == 3 ? "column_size_scale" : "small_size_scale");
    }

    static float sodaParticleAlpha(EffectWorkshopConfig.Values values, float alpha, float frameAlpha) {
        return Math.max(0f, Math.min(1f, alpha * frameAlpha * values.get("particle_alpha")));
    }
}
