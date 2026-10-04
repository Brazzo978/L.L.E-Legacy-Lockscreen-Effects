#include "../native/lle_s6_water_sim.h"
#include <assert.h>
#include <math.h>
#include <stdio.h>

static const float defaults[LLE_S6_WATER_WORKSHOP_COUNT] = {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.15f, 1.0f, 1.0f, 1.0f, 0.5f, 0.075f, 0.15f, 12.0f, 0.75001875f, 0.075f};
int main(void) {
    LleS6WaterSim *stock=lle_s6_water_sim_create(1440,2560,0,2,1);
    LleS6WaterSim *tuned=lle_s6_water_sim_create(1440,2560,0,2,1);
    float v[LLE_S6_WATER_WORKSHOP_COUNT];
    for(size_t i=0;i<LLE_S6_WATER_WORKSHOP_COUNT;i++) v[i]=defaults[i];
    v[0]=1.2;
    v[1]=.8;
    v[5]=1.2;
    v[9]=.6;
    v[10]=.7;
    v[12]=.2;
    v[13]=20;
    v[14]=.8;
    v[15]=.1;
    lle_s6_water_sim_set_workshop(tuned,v,LLE_S6_WATER_WORKSHOP_COUNT);
    LleS6WaterRenderState a,b;
    lle_s6_water_sim_get_render_state(stock,&a); lle_s6_water_sim_get_render_state(tuned,&b);
    assert(a.shadow_range==12 && b.shadow_range==20 && b.density_threshold==.7f);
    assert(b.refraction_eta==.8f && b.refraction_amplitude==.1f);
    assert(fabsf(b.refraction_ratio-a.refraction_ratio*.6f)<1e-6f);
    lle_s6_water_sim_queue_touch(stock,0,720,1280,0); lle_s6_water_sim_queue_touch(tuned,0,720,1280,0);
    for(int i=0;i<20;i++){lle_s6_water_sim_tick(stock);lle_s6_water_sim_tick(tuned);}
    LleS6WaterDensityParticle pa[500],pb[500];
    size_t na=lle_s6_water_sim_export_density_particles(stock,pa,500);
    size_t nb=lle_s6_water_sim_export_density_particles(tuned,pb,500);
    assert(na>0 && nb>0 && pa[0].diameter_px!=pb[0].diameter_px);
    lle_s6_water_sim_request_reset(tuned); assert(lle_s6_water_sim_consume_deferred_reset(tuned));
    lle_s6_water_sim_set_surface(tuned,2560,1440,2560,1440);
    lle_s6_water_sim_get_render_state(tuned,&b);assert(b.shadow_range==20);
    v[14]=NAN; v[13]=1000; v[10]=-1;
    lle_s6_water_sim_set_workshop(tuned,v,LLE_S6_WATER_WORKSHOP_COUNT);
    lle_s6_water_sim_get_render_state(tuned,&b);assert(b.refraction_eta==defaults[14] && b.shadow_range==24 && b.density_threshold==.25f);
    lle_s6_water_sim_destroy(stock); lle_s6_water_sim_destroy(tuned);
    const float corners[2][LLE_S6_WATER_WORKSHOP_COUNT] = {
        {0.5f, 0.5f, 0.0f, 0.0f, 0.0f, 0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.25f, 0.0f, 0.0f, 0.0f, 0.5f, 0.0f},
        {1.5f, 1.5f, 2.0f, 1.5f, 1.5f, 1.5f, 0.6f, 2.0f, 2.0f, 2.0f, 0.8f, 0.15f, 0.3f, 24.0f, 1.0f, 0.15f}
    };
    for(int corner=0;corner<2;corner++) {
        LleS6WaterSim *sim=lle_s6_water_sim_create(1968,2184,1,2,1);
        lle_s6_water_sim_set_workshop(sim,corners[corner],LLE_S6_WATER_WORKSHOP_COUNT);
        lle_s6_water_sim_queue_touch(sim,0,980,1092,0);
        for(int frame=0;frame<120;frame++) {
            lle_s6_water_sim_queue_touch(sim,2,frame%2 ? 1900 : 20,frame%3 ? 2100 : 20,frame);
            lle_s6_water_sim_queue_tilt(sim,10,-10,frame);
            lle_s6_water_sim_tick_native_refresh(sim,2);
            LleS6WaterDensityParticle q[500];
            size_t count=lle_s6_water_sim_export_density_particles(sim,q,500);
            assert(count<=500);
            for(size_t i=0;i<count;i++) assert(isfinite(q[i].center_x_px) && isfinite(q[i].center_y_px) && isfinite(q[i].diameter_px));
        }
        lle_s6_water_sim_destroy(sim);
    }
    puts("PASS s6_water workshop clamps, mapping, behavior and isolation");
    return 0;
}
