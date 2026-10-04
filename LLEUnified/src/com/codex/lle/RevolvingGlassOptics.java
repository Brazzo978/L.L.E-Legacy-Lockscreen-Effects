package com.codex.lle;

/** Shader substitutions used by the renderer; disabled source is returned unchanged. */
final class RevolvingGlassOptics {
    private RevolvingGlassOptics() {}
    private static String scalar(EffectWorkshopConfig.Values workshop,String key) { return Float.toString(workshop.get(key)); }
    static String vertex(String VERTEX,EffectWorkshopConfig.Values workshop) {
            if (!workshop.enabled) return VERTEX;
            return VERTEX.replace("float camera=2.6;", "float camera="+scalar(workshop,"camera_distance")+";");
        }
    static String fragment(String FRAGMENT,EffectWorkshopConfig.Values workshop) {
            if (!workshop.enabled) return FRAGMENT;
            String tint="vec3("+scalar(workshop,"tint_r")+","+scalar(workshop,"tint_g")+","+scalar(workshop,"tint_b")+")";
            String edge="vec3("+scalar(workshop,"edge_r")+","+scalar(workshop,"edge_g")+","+scalar(workshop,"edge_b")+")";
            return FRAGMENT.replace("*.035;", "*"+scalar(workshop,"border_rounding")+";")
                .replace("smoothstep(-7.,-1.,d)","smoothstep(-"+Float.toString(workshop.get("border_width")+1f)+",-1.,d)")
                .replace(".72+.28*abs",scalar(workshop,"face_ambient")+"+"+scalar(workshop,"face_angular")+"*abs")
                .replace("vec3(.94,.98,1.)",tint)
                .replace("border*.76","border*"+scalar(workshop,"front_rim"))
                .replace("border*.68","border*"+scalar(workshop,"back_rim"))
                .replace("uTexel*1.25","uTexel*"+scalar(workshop,"blur_radius"))
                .replace("mix(sharp,soft,.20)","mix(sharp,soft,"+scalar(workshop,"blur_mix")+")")
                .replace("vUv.x*55.+uAngle*3.","vUv.x*"+scalar(workshop,"glint_frequency")+"+uAngle*"+scalar(workshop,"glint_rotation"))
                .replace(")),10.)",")),"+scalar(workshop,"glint_power")+")")
                .replace(".78+.22*abs",scalar(workshop,"edge_ambient")+"+"+scalar(workshop,"edge_angular")+"*abs")
                .replace("float a=.88;","float a="+scalar(workshop,"edge_alpha")+";")
                .replace("vec3(.78,.92,1.)",edge)
                .replace("(.82+.18*glint)","("+scalar(workshop,"edge_base")+"+"+scalar(workshop,"edge_glint")+"*glint)");
        }

}
