#include "../native/lle_colour_sim.h"
#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

/* Portable core test: the GPU-independent defaults hook matches the refresh test. */
void lle_colour_gles_default_params(LleColourDrawParams *params) {
    memset(params, 0, sizeof(*params));
    params->restore_ratio = 1.0f;
}

static const float defaults[LLE_COLOUR_WORKSHOP_COUNT] = {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.15f, 1.0f, 1.0f, 1.0f, 0.6f, 1.3f, 1.3f, 0.15f};
int main(void) {
    LleColourSim *stock=lle_colour_sim_create(1440,2560,0,1);
    LleColourSim *tuned=lle_colour_sim_create(1440,2560,0,1);
    float v[LLE_COLOUR_WORKSHOP_COUNT];
    for(size_t i=0;i<LLE_COLOUR_WORKSHOP_COUNT;i++) v[i]=defaults[i];
    v[0]=1.2;
    v[1]=.8;
    v[5]=1.2;
    v[9]=.6;
    v[10]=.9;
    v[11]=.7;
    v[12]=1.8;
    v[13]=.3;
    lle_colour_sim_set_workshop(tuned,v,LLE_COLOUR_WORKSHOP_COUNT);
    LleColourDrawParams a,b;
    lle_colour_sim_get_draw_params(stock,&a); lle_colour_sim_get_draw_params(tuned,&b);
    assert(a.color_saturation==1.3f && b.color_saturation==.7f);
    assert(b.color_brightness==1.8f && b.inner_shadow_width==.9f && b.color_min_value==.3f);
    assert(fabsf(b.refraction_ratio-a.refraction_ratio*.6f)<1e-6f);
    lle_colour_sim_touch(stock,0,720,1280,0); lle_colour_sim_touch(tuned,0,720,1280,0);
    for(int i=0;i<20;i++){lle_colour_sim_tick(stock);lle_colour_sim_tick(tuned);}
    LleColourDrawParticle pa[480],pb[480];
    size_t na=lle_colour_sim_export_draw_particles(stock,pa,480);
    size_t nb=lle_colour_sim_export_draw_particles(tuned,pb,480);
    assert(na>0 && nb>0);
    assert(pa[0].density_size_px!=pb[0].density_size_px);
    lle_colour_sim_reset(tuned); lle_colour_sim_set_surface(tuned,2560,1440,2560,1440);
    lle_colour_sim_get_draw_params(tuned,&b); assert(b.color_brightness==1.8f);
    v[12]=INFINITY; v[11]=-100; v[10]=100;
    lle_colour_sim_set_workshop(tuned,v,LLE_COLOUR_WORKSHOP_COUNT);
    lle_colour_sim_get_draw_params(tuned,&b); assert(b.color_brightness==1.3f && b.color_saturation==0 && b.inner_shadow_width==1.2f);
    lle_colour_sim_destroy(stock); lle_colour_sim_destroy(tuned);
    const float corners[2][LLE_COLOUR_WORKSHOP_COUNT] = {
        {0.5f, 0.5f, 0.0f, 0.0f, 0.0f, 0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 0.0f},
        {1.5f, 1.5f, 2.0f, 1.5f, 1.5f, 1.5f, 0.6f, 2.0f, 2.0f, 2.0f, 1.2f, 2.0f, 2.0f, 0.5f}
    };
    for(int corner=0;corner<2;corner++) {
        LleColourSim *sim=lle_colour_sim_create(1968,2184,1,1);
        lle_colour_sim_set_workshop(sim,corners[corner],LLE_COLOUR_WORKSHOP_COUNT);
        lle_colour_sim_touch(sim,0,980,1092,0);
        for(int frame=0;frame<120;frame++) {
            lle_colour_sim_touch(sim,2,frame%2 ? 1900 : 20,frame%3 ? 2100 : 20,frame);
            lle_colour_sim_sensor(sim,0,10,-10,0);
            lle_colour_sim_tick_scaled(sim,2);
            LleColourDrawParticle q[480];
            size_t count=lle_colour_sim_export_draw_particles(sim,q,480);
            assert(count<=480);
            for(size_t i=0;i<count;i++) assert(isfinite(q[i].x) && isfinite(q[i].y) && isfinite(q[i].density_size_px));
        }
        lle_colour_sim_destroy(sim);
    }
    puts("PASS colour workshop clamps, mapping, behavior and isolation");
    return 0;
}
