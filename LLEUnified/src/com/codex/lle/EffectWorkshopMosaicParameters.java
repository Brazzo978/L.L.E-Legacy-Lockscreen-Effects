package com.codex.lle;

import java.util.ArrayList;

/** Explicit controls for the app-owned Abstract Tiles and Geometric Mosaic pipelines. */
final class EffectWorkshopMosaicParameters {
    private EffectWorkshopMosaicParameters() {}
    static EffectWorkshopConfig.Parameter[] parametersFor(int effect) {
        ArrayList<EffectWorkshopConfig.Parameter> p = new ArrayList<EffectWorkshopConfig.Parameter>();
        if (effect == 7) {
            p.add(n("pop_duration", "Tile pop duration", "Animation", .4f, .05f, 3, .05f, "s", "Higher values make each tile pop deformation last longer. Lower values return tiles to their resting shape sooner."));
            p.add(n("pop_interval", "Held burst interval", "Animation", .16f, .02f, 2, .01f, "s", "Higher values wait longer between repeated tile bursts while a touch is held. Lower values create more frequent bursts."));
            p.add(n("stagger", "Tile stagger", "Animation", .02f, 0, .2f, .005f, "s", "Higher values delay each successive tile in a burst more, spreading the reaction over time. Zero starts the selected tiles together."));
            p.add(n("pop_strength", "Tile deformation", "Geometry", 1, 0, 4, .05f, "x", "Higher values deform the selected tiles more strongly during their pop. Zero removes this deformation while other tile colour and opacity changes can remain."));
            p.add(n("pop_radius", "Burst region", "Geometry", 1, .1f, 5, .05f, "x", "Higher values enlarge the nearby burst-selection region around the touch. Tiles within and outside that region still use their separate selection probabilities."));
            p.add(n("probability_near", "Nearby tile probability", "Geometry", .8f, 0, 1, .01f, "", "Higher values make nearby tiles more likely to join a touch burst. Zero excludes them from this random selection."));
            p.add(n("probability_far", "Distant tile probability", "Geometry", .016f, 0, 1, .001f, "", "Higher values make distant tiles more likely to join a touch burst. Lower values keep the reaction concentrated near the touch."));
            p.add(n("brightness_variation", "Random brightness", "Appearance", 1, 0, 3, .05f, "x", "Higher values give reacting tiles stronger random lightening and darkening. Zero removes that random brightness change."));
            p.add(n("tile_alpha", "Tile opacity", "Appearance", .3f, 0, 1, .01f, "", "Higher values make reacting tile surfaces more opaque. Zero hides those surfaces while other highlights can remain visible."));
            p.add(n("proximity_alpha", "Touch highlight opacity", "Appearance", 1, 0, 3, .05f, "x", "Higher values strengthen the highlight around the touch. Zero removes that proximity highlight without changing tile deformation."));
            p.add(n("ray_stop", "Ray stop distance squared", "Geometry", .8f, .05f, 3, .05f, "", "Higher values allow the animated tile rays to travel farther before their distance cutoff stops them. Lower values keep the paths closer to the touch."));
            p.add(n("ray_reach", "Ray reach", "Geometry", 1, .1f, 3, .05f, "x", "Higher values extend the search reach used to build the animated tile-ray paths. Lower values shorten that reach; the actual path also depends on tile intersections and the stop distance."));
            p.add(n("ray_radius", "Ray influence area", "Geometry", 1, .1f, 3, .05f, "x area", "Higher values widen the area considered when finding tiles along a ray path. Lower values keep the ray selection closer to its centre line."));
        } else if (effect == 8) {
            p.add(n("touch_grow", "Touch growth duration", "Animation", .15f, .02f, 2, .01f, "s", "Higher values make each touch circle take longer to grow from its initial radius to its peak. Lower values make it expand sooner."));
            p.add(n("touch_shrink", "Touch shrink duration", "Animation", .6f, .05f, 4, .05f, "s", "Higher values make released touch circles shrink and clear more slowly. Lower values remove them sooner after release."));
            p.add(n("touch_start", "Touch initial radius", "Geometry", .3f, .01f, 2, .01f, "screen units", "Higher values start each touch circle at a larger radius. The peak-radius setting controls where its growth ends."));
            p.add(n("touch_peak", "Touch peak radius", "Geometry", .8f, .01f, 3, .01f, "screen units", "Higher values let each touch circle grow to a larger peak radius. Lower values keep the revealed mosaic region smaller."));
            p.add(n("touch_spacing", "Touch sample spacing", "Geometry", .0085f, .001f, .1f, .001f, "normalized", "Higher values require more finger travel before another touch circle is recorded. Lower values make the circle trail denser."));
            p.add(n("hint_duration", "Hint duration", "Animation", 2, .1f, 6, .1f, "s", "Higher values slow the expansion of the automatic hint circle. Lower values complete that hint sweep sooner."));
            p.add(n("hint_from", "Hint initial radius", "Geometry", -.8f, -2, 2, .05f, "screen units", "Sets the starting radius of the automatic hint sweep. Higher values start it farther into the reveal; negative values postpone the first visible circle until the sweep grows above zero."));
            p.add(n("hint_to", "Hint final radius", "Geometry", 3, .1f, 6, .1f, "screen units", "Higher values let the automatic hint sweep end at a larger radius. Lower values keep its final revealed area smaller."));
            p.add(n("ring_scale", "Circle lattice radius", "Geometry", 1, .2f, 3, .05f, "x", "Higher values enlarge the circle lattice and its animated rings. Lower values make its circles smaller while keeping the lattice layout."));
            float[] from = {.6f, .2f, 0, .6f, 0};
            float[] delay = {0, 0, .6f, 0, 0};
            float[] duration = {1.2f, 2.4f, 3, 1.2f, 3};
            for (int i = 0; i < 5; i++) {
                p.add(n("ring_from_" + i, "Ring " + (i + 1) + " initial radius", "Ring animation", from[i], 0, 2, .05f, "x radius", "Sets the starting radius of this animated ring relative to its target radius. Higher values start it larger; lower values leave more room for outward growth."));
                p.add(n("ring_delay_" + i, "Ring " + (i + 1) + " delay", "Ring animation", delay[i], 0, 3, .05f, "s", "Higher values make this ring wait longer before its animation starts. Zero starts it without an extra delay; the other rings have independent delays."));
                p.add(n("ring_duration_" + i, "Ring " + (i + 1) + " duration", "Ring animation", duration[i], .05f, 10, .05f, "s", "Higher values make this ring grow toward its target radius more slowly. Lower values let this ring finish its growth sooner."));
            }
            p.add(n("blur_radius", "Wallpaper blur spread", "Appearance", 1, 0, 4, .05f, "x", "Higher values spread wallpaper blur over a wider area in the mosaic background pass. Zero samples without that blur spread."));
            p.add(n("brightness_threshold", "Dark wallpaper threshold", "Appearance", .2f, 0, 1, .01f, "", "Higher values classify more wallpaper samples as dark in the background pass. Only those samples receive the dark-wallpaper brightness lift."));
            p.add(n("brightness_lift", "Dark wallpaper lift", "Appearance", .3f, 0, 1, .01f, "", "Higher values brighten wallpaper samples below the dark threshold more strongly. Zero removes that conditional lift."));
            p.add(n("circle_blend", "First circle blend", "Appearance", .75f, 0, 1, .01f, "", "Higher values strengthen the first circle layer added over the wallpaper. Zero removes this layer contribution while the other mosaic layers remain."));
            p.add(n("soft_blend", "Second circle blend", "Appearance", .4f, 0, 1, .01f, "", "Higher values strengthen the soft-light blend of the second circle layer. Zero removes that blend contribution without changing the first circle layer."));
            p.add(n("mosaic_blend", "Mosaic colour blend", "Appearance", .75f, 0, 1, .01f, "", "Higher values strengthen the coloured mosaic layer over the combined circle image. Zero removes that colour blend contribution."));
            int[] colors = {0x8470FF, 0xADFF2F, 0xFFD700, 0xCD5C5C, 0xCDB6C1,
                    0x830BFF, 0x43CD80, 0xFFC0C0, 0xCD853F, 0xFF3030};
            for (int i = 0; i < colors.length; i++) {
                String[] channels = {"r", "g", "b"};
                for (int c = 0; c < 3; c++) p.add(EffectWorkshopConfig.Parameter.integer(
                        "palette_" + i + "_" + channels[c], "Colour " + (i + 1) + " " + channels[c].toUpperCase(java.util.Locale.ROOT),
                        "Palette", (colors[i] >> (16 - c * 8)) & 255, 0, 255, 1, "RGB", "Sets this red, green or blue channel for the selected mosaic palette colour. Higher values add more of that channel; the result combines with its other two channels."));
            }
        } else return new EffectWorkshopConfig.Parameter[0];
        p.add(n("sound_gain", "Sound volume", "Audio", 1, 0, 1, .05f, "", "Higher values make the effect sound louder; zero mutes it. Sound also depends on the app sound setting."));
        return p.toArray(new EffectWorkshopConfig.Parameter[p.size()]);
    }
    private static EffectWorkshopConfig.Parameter n(String key, String label, String group,
            float value, float min, float max, float step, String unit, String description) {
        return EffectWorkshopConfig.Parameter.number(key, label, group, value, min, max, step, unit, description);
    }
}
