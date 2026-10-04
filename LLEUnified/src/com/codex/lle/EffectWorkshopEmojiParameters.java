package com.codex.lle;

final class EffectWorkshopEmojiParameters {
    private EffectWorkshopEmojiParameters() {}
    static EffectWorkshopConfig.Parameter[] parameters() {
        return new EffectWorkshopConfig.Parameter[] {
            i("max_particles", "Live particle limit", 72, 8, 256, "particles","Sets the maximum number of emoji particles kept on screen. When the limit is reached, the oldest particle is removed to make room for a new one."),
            i("burst_count", "Touch burst count", 4, 1, 32, "particles","Sets how many emoji particles are emitted when a touch begins. The live particle limit still applies."),
            n("spawn_distance", "Trail spacing", "Emission", 12, .5f, 100, .5f, "dp","Sets the minimum finger movement between trail emissions. Smaller values create a denser emoji trail."),
            n("extra_probability", "Extra trail particle probability", "Emission", .25f, 0, 1, .01f, "","Sets the probability of emitting one extra emoji at each accepted trail position. Zero disables the extra particle; one always adds it."),
            n("burst_spread_x", "Burst horizontal spread", "Geometry", 46, 0, 200, 1, "dp","Sets the total horizontal width over which a touch burst starts. Particles begin at random positions centered on the touch."),
            n("burst_spread_y", "Burst vertical spread", "Geometry", 38, 0, 200, 1, "dp","Sets the total vertical height over which a touch burst starts. Particles begin at random positions centered on the touch."),
            n("trail_spread_x", "Trail horizontal spread", "Geometry", 24, 0, 200, 1, "dp","Sets the total horizontal width over which trail particles start. Particles begin at random positions centered on the finger."),
            n("trail_spread_y", "Trail vertical spread", "Geometry", 18, 0, 200, 1, "dp","Sets the total vertical height over which trail particles start. Particles begin at random positions centered on the finger."),
            n("burst_travel_x", "Burst horizontal motion", "Motion", 96, 0, 400, 1, "dp","Sets the total range of sideways travel for burst particles, centered on zero. Each particle can move left or right by up to half this value."),
            n("trail_travel_x", "Trail horizontal motion", "Motion", 72, 0, 400, 1, "dp","Sets the total range of sideways travel for trail particles, centered on zero. Each particle can move left or right by up to half this value."),
            n("travel_y_min", "Minimum upward motion", "Motion", 64, 0, 400, 1, "dp","Sets the minimum upward distance traveled by both burst and trail particles. Their random upward range is added to this distance."),
            n("burst_travel_y", "Burst upward motion range", "Motion", 92, 0, 400, 1, "dp","Sets the additional random upward distance for touch-burst particles. Each particle adds a value between zero and this range to the minimum upward motion."),
            n("trail_travel_y", "Trail upward motion range", "Motion", 68, 0, 400, 1, "dp","Sets the additional random upward distance for trail particles. Each particle adds a value between zero and this range to the minimum upward motion."),
            n("start_scale", "Initial size", "Appearance", .24f, .05f, 3, .05f, "x","Sets the base emoji size at the start of a particle animation. The initial size variation is added independently for each particle."),
            n("start_scale_range", "Initial size variation", "Appearance", .16f, 0, 2, .05f, "x","Sets the random size added to the base initial size. Zero makes every particle start at the same scale."),
            n("peak_scale", "Peak size", "Appearance", .72f, .05f, 3, .05f, "x","Sets the base emoji size at the end of the growth phase. The peak size variation is added independently for each particle."),
            n("peak_scale_range", "Peak size variation", "Appearance", .52f, 0, 2, .05f, "x","Sets the random size added to the base peak size. Zero makes every particle reach the same peak scale."),
            n("end_scale", "Final size", "Appearance", .46f, .05f, 3, .05f, "x","Sets the base emoji size at the end of its lifetime. The final size variation is added independently for each particle."),
            n("end_scale_range", "Final size variation", "Appearance", .24f, 0, 2, .05f, "x","Sets the random size added to the base final size. Zero makes every particle end at the same scale."),
            n("rotation", "Rotation strength", "Motion", 1, 0, 4, .05f, "x","Multiplies both the random initial angle and the rotation during the animation. Zero keeps emojis upright."),
            EffectWorkshopConfig.Parameter.integer("lifetime_min", "Minimum lifetime", "Animation", 850, 100, 5000, 1, "ms","Sets the minimum lifetime of an emoji particle. A random duration from the lifetime range is added to this value."),
            EffectWorkshopConfig.Parameter.integer("lifetime_range", "Random lifetime range", "Animation", 500, 0, 5000, 1, "ms","Sets the maximum random duration added to the minimum lifetime. Zero gives all particles the same lifetime."),
            n("growth_phase", "Growth phase fraction", "Animation", .22f, .05f, .8f, .01f, "","Sets the fraction of a particle lifetime spent changing from initial size to peak size. The remaining time changes the size from peak to final."),
            n("alpha_in", "Fade-in phase fraction", "Animation", .12f, .01f, .5f, .01f, "","Sets the fraction of a particle lifetime used to fade in from transparent to full opacity. Higher values make the fade-in slower."),
            n("alpha_out", "Fade-out start fraction", "Animation", .58f, .2f, .95f, .01f, "","Sets the lifetime fraction at which fade-out starts, or waits until fade-in ends if that takes longer. Higher values delay fading toward the end of the particle lifetime."),
            n("opacity", "Opacity", "Appearance", 1, 0, 1, .01f, "","Multiplies the opacity of every emoji particle throughout its fade animation. Zero makes the particles invisible."),
            EffectWorkshopConfig.Parameter.integer("sound_interval", "Drag sound interval", "Audio", 240, 80, 2000, 1, "ms","Sets the minimum time between drag sounds. Higher values make drag sounds less frequent."),
            n("sound_gain", "Sound gain", "Audio", 1, 0, 3, .05f, "x","Multiplies the emoji effect sound volume when effect sounds are enabled. Playback volume is capped at the platform maximum.")
        };
    }
    private static EffectWorkshopConfig.Parameter i(String key,String label,float value,float min,float max,String unit,String description) {
        return EffectWorkshopConfig.Parameter.integer(key,label,"Emission",value,min,max,1,unit,description);
    }
    private static EffectWorkshopConfig.Parameter n(String key,String label,String group,float value,float min,float max,float step,String unit,String description) {
        return EffectWorkshopConfig.Parameter.number(key,label,group,value,min,max,step,unit,description);
    }
}
