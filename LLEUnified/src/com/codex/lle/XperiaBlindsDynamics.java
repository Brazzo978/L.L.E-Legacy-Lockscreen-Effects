package com.codex.lle;

/** Shared runtime and host-test math for the Blinds strip geometry and analytic spring. */
final class XperiaBlindsDynamics {
    private XperiaBlindsDynamics() {}
    static float distance(int strip,float y,int count,float range) {
        return 2f * (((strip + .5f) / count) - y) / range;
    }
    static int start(float y,int count,float range) {
        return Math.max(0,Math.min(count-1,(int)Math.floor((clamp(y,0f,1f)-range*.5f)*count+.5f)));
    }
    static int end(float y,int count,float range) {
        return Math.max(0,Math.min(count,(int)Math.ceil((clamp(y,0f,1f)+range*.5f)*count-.5f)));
    }
    static int bandTop(int height,int strip,int count) { return (int)(height*(strip/(float)count)); }
    static void spring(float position,float velocity,float target,float seconds,
            float stiffness,float damping,float[] output) {
        float dt=clamp(seconds,0f,.05f);
        float omega=(float)Math.sqrt(stiffness);
        float dampedOmega=omega*(float)Math.sqrt(1f-damping*damping);
        float offset=position-target;
        float exp=(float)Math.exp(-damping*omega*dt);
        float cos=(float)Math.cos(dampedOmega*dt);
        float sin=(float)Math.sin(dampedOmega*dt);
        float velocityTerm=(velocity+damping*omega*offset)/dampedOmega;
        float displacement=offset*cos+velocityTerm*sin;
        output[0]=target+exp*displacement;
        output[1]=exp*(-damping*omega*displacement-offset*dampedOmega*sin+velocityTerm*dampedOmega*cos);
    }
    private static float clamp(float v,float a,float b) { return Math.max(a,Math.min(b,v)); }
}
