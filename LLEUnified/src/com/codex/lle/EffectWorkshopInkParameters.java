package com.codex.lle;

import java.util.ArrayList;

/** Parameters consumed by the reconstructed Samsung ripple/ink/watercolor engines. */
final class EffectWorkshopInkParameters {
    private EffectWorkshopInkParameters() {}
    private static EffectWorkshopConfig.Parameter n(String k,String l,String g,float d,float a,float b,float s,String u,String description) {
        return EffectWorkshopConfig.Parameter.number(k,l,g,d,a,b,s,u,description);
    }
    static EffectWorkshopConfig.Parameter[] parametersFor(int effect) {
        ArrayList<EffectWorkshopConfig.Parameter> p = new ArrayList<EffectWorkshopConfig.Parameter>();
        if (effect == 10 || effect == 12 || effect == 27) {
            p.add(n("wave_damping","Wave persistence","Waves",.94f,.80f,.995f,.005f,"","Retains this fraction of wave velocity at each stock simulation step; higher values make ripples last longer. In the legacy wave solver, exactly 0.94 also selects the stock stronger smoothing pass."));
            // The five-point wave stencil is stable at coefficient <= .5.
            p.add(n("wave_coefficient","Wave propagation","Waves",.5f,.1f,.5f,.01f,"","Controls how strongly neighboring height differences accelerate the waves. Higher values spread ripples more strongly; the upper limit keeps the wave stencil within its supported range."));
            p.add(n("wave_intensity","Wave impulse strength","Waves",1f,.25f,2f,.05f,"x","Multiplies the height or velocity impulse created by touch input. Higher values produce stronger ripples."));
            p.add(n("refraction","Refraction","Optics",.93f,.5f,1.2f,.01f,"","Sets the refractive index used to bend the background image through the rippled surface. Its visible effect depends on the current wave slopes."));
            p.add(n("reflection","Reflection strength","Optics",.13f,0f,.6f,.01f,"","Controls the contribution of the reflection texture to the water surface. Higher values make reflected highlights and texture more prominent."));
            p.add(n("fresnel","Fresnel strength","Optics",.1f,0f,.8f,.01f,"","Controls how much reflection changes with the viewing angle of the rippled surface. Higher values emphasize reflections on tilted parts of a wave."));
            p.add(n("specular","Specular strength","Optics",.5f,0f,2f,.05f,"","Controls the brightness of the sharp light reflection on the water surface. Set it to zero to remove this highlight contribution."));
            p.add(n("exponent","Highlight concentration","Optics",20f,2f,80f,1f,"","Sets the exponent used for the specular highlight. Higher values make the highlight narrower and more concentrated."));
        }
        if (effect == 12 || effect == 27) {
            p.add(n("ink_radius","Ink emission radius","Ink",1f,.25f,2f,.05f,"x","Multiplies the radius of pigment emission selected by the current touch preset. Larger values deposit ink over a wider area."));
            p.add(n("ink_impulse","Pigment amount","Ink",1f,.1f,3f,.05f,"x","Multiplies the pigment deposited by the current touch preset. It changes the amount of ink emitted, rather than the rate at which old ink fades."));
            p.add(n("ink_velocity","Pressure impulse strength","Ink",1f,0f,2f,.05f,"x","Multiplies the pressure impulse used by the fluid projection step when the current touch preset supplies one. Direct drag flow is a separate contribution, so setting this to zero does not stop all ink motion."));
            p.add(n("ink_advection","Ink transport speed","Ink",1f,.25f,2f,.05f,"x","Multiplies the step used to carry pigment along the fluid flow. Ripple Ink also applies this multiplier to velocity self-advection."));
            p.add(n("ink_decay","Pigment dissipation","Ink",1f,.25f,3f,.05f,"x","Multiplies the fraction of pigment lost at each simulation step relative to the current touch preset. Higher values make the ink fade sooner."));
            p.add(n("velocity_decay","Velocity dissipation","Ink",1f,.25f,2f,.05f,"x","Multiplies the fraction of fluid velocity lost at each simulation step relative to the current touch preset. Higher values make the flow settle sooner."));
            p.add(EffectWorkshopConfig.Parameter.integer("jacobi_iterations","Pressure solver iterations","Ink",effect == 12 ? 20 : 10,4,effect == 12 ? 40 : 20,1,"","Sets the number of pressure-relaxation iterations in each fluid projection step. More iterations can improve the flow projection but require more processing."));
        }
        if (effect == 27) p.add(n("ink_opacity","Ink tint strength","Optics",.02f,.005f,.08f,.005f,"","Sets the strength of the final ink color mixed over the water image. Higher values make the pigment more visible without adding more pigment to the simulation."));
        if (effect == 3) {
            p.add(n("brush_scale","Brush size","Brush",.8f,.2f,1.6f,.05f,"","Scales the watercolor brush stamp relative to the shorter screen dimension. Higher values paint a wider area."));
            p.add(n("drag_threshold","Movement threshold","Brush",.025f,.005f,.1f,.005f,"screen width","Sets the minimum drag distance as a fraction of screen width, scaled by the current brush movement state. Lower values allow watercolor strokes after smaller finger movements."));
            p.add(n("stamp_spacing","Stroke stamp spacing","Brush",.05f,.015f,.12f,.005f,"screen width","Sets the distance between brush stamps along a drag, as a fraction of screen width. Lower values place stamps closer together for a denser stroke."));
            p.add(n("noise_scalar","Grain flow strength","Fluid",425f,0f,850f,5f,"","Controls how strongly the noise texture moves the watercolor pigment. Higher values produce more irregular grain-driven spreading."));
            p.add(n("radial_scalar","Radial flow strength","Fluid",66.69f,0f,150f,1f,"","Controls how strongly the radial texture moves pigment around each brush stamp. Higher values increase the radial contribution to spreading."));
            p.add(n("saturation","Saturation","Color",1.2f,0f,2.5f,.05f,"","Controls how far pigment colors differ from the brightness reference computed from the channel weights. Zero gives a grayscale pigment result; higher values increase color separation."));
            p.add(n("red_saturation","Red brightness weight","Color",1.3f,0f,2.5f,.05f,"","Weights the red channel when computing the brightness reference used by the saturation adjustment. It affects the color mix rather than directly multiplying the red output."));
            p.add(n("green_saturation","Green brightness weight","Color",.4f,0f,2.5f,.05f,"","Weights the green channel when computing the brightness reference used by the saturation adjustment. It affects the color mix rather than directly multiplying the green output."));
            p.add(n("blue_saturation","Blue brightness weight","Color",.4f,0f,2.5f,.05f,"","Weights the blue channel when computing the brightness reference used by the saturation adjustment. It affects the color mix rather than directly multiplying the blue output."));
            p.add(n("brightness","Pigment brightness","Color",1.35f,.2f,2.5f,.05f,"","Multiplies the pigment density color before the saturation adjustment and final composition. Higher values make the painted color brighter and can increase its visible strength."));
        }
        return p.toArray(new EffectWorkshopConfig.Parameter[p.size()]);
    }
}
