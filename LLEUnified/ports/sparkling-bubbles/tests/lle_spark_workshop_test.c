#include "../native/lle_spark_sim.h"
#include <assert.h>
#include <math.h>
#include <stdio.h>

static const float defaults[LLE_SPARK_WORKSHOP_COUNT] = {1100.0f, 1100.0f, 40.0f, 250.0f, 10.0f, 220.0f, 10.0f, 240.0f, 30.0f, 250.0f, 10.0f, 220.0f, 10.0f, 200.0f, 4.0f, 10.0f, 12.0f, 18.0f, 12.0f, 25.0f, 24.0f, 28.0f, 28.0f, 43.0f, 2.2f, 2.5f, 2.0f, 2.5f, 1.8f, 2.3f, 1.6f, 2.1f, 0.3f, 0.5f, 0.3f, 0.7f, 0.35f, 0.6f, 0.25f, 0.6f, 0.5f, 0.6f, 0.01f, 0.03f, 0.6f, 1.0f, 0.03f, 0.12f};
int main(void) {
    LleSparkSim *stock = lle_spark_sim_create(1440,2560,1);
    LleSparkSim *tuned = lle_spark_sim_create(1440,2560,1);
    LleSparkSim *explicit_default = lle_spark_sim_create(1440,2560,1);
    lle_spark_sim_set_workshop(explicit_default, defaults, LLE_SPARK_WORKSHOP_COUNT);
    assert(lle_spark_sim_touch_begin(stock,720,1280));
    assert(lle_spark_sim_touch_begin(explicit_default,720,1280));
    assert(lle_spark_sim_state_hash(stock)==lle_spark_sim_state_hash(explicit_default));
    float v[LLE_SPARK_WORKSHOP_COUNT];
    for (size_t i=0;i<LLE_SPARK_WORKSHOP_COUNT;i++) v[i]=defaults[i];
    v[0]=100;
    v[1]=10;
    v[14]=7;
    v[15]=7;
    v[36]=.1;
    v[37]=.1;
    v[24]=1;
    v[25]=1;
    v[42]=0;
    v[43]=0;
    v[44]=0;
    v[2]=5;
    v[3]=2;
    lle_spark_sim_set_workshop(tuned,v,LLE_SPARK_WORKSHOP_COUNT);
    assert(lle_spark_sim_touch_begin(tuned,720,1280));
    assert(lle_spark_sim_active_particle_count(tuned)==100);
    assert(lle_spark_sim_active_particle_count(stock)==1100);
    LleSparkParticleSnapshot p;
    assert(lle_spark_sim_get_particle(tuned,0,0,&p));
    assert(p.size==7 && fabsf(p.alpha-.1f)<1e-6f && p.max_lifetime_ticks==60);
    assert(p.velocity_x==0 && p.velocity_y==0 && p.acceleration_x==0);
    assert(fabsf(hypotf(p.x-720,p.y-1280)-5)<.001f); /* inverted radius range ordered */
    lle_spark_sim_reset(tuned);
    assert(lle_spark_sim_hint(tuned,720,1280)==4);
    assert(lle_spark_sim_active_particle_count(tuned)==40); /* reset retains config */
    lle_spark_sim_reset(tuned);
    v[0]=50000; v[14]=NAN; v[15]=INFINITY;
    lle_spark_sim_set_workshop(tuned,v,LLE_SPARK_WORKSHOP_COUNT);
    lle_spark_sim_touch_begin(tuned,720,1280);
    assert(lle_spark_sim_active_particle_count(tuned)==LLE_SPARK_PARTICLES_PER_GROUP);
    lle_spark_sim_get_particle(tuned,0,0,&p);
    assert(p.size>=4 && p.size<=10 && isfinite(p.size));
    lle_spark_sim_set_workshop(tuned,NULL,0);
    lle_spark_sim_reset(tuned);
    lle_spark_sim_touch_begin(tuned,720,1280);
    assert(lle_spark_sim_active_particle_count(tuned)==1100);
    lle_spark_sim_destroy(stock); lle_spark_sim_destroy(tuned); lle_spark_sim_destroy(explicit_default);
    const float corners[2][LLE_SPARK_WORKSHOP_COUNT] = {
        {1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.1f, 0.1f, 0.1f, 0.1f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.01f, 0.01f},
        {1100.0f, 1100.0f, 400.0f, 400.0f, 400.0f, 400.0f, 400.0f, 400.0f, 400.0f, 400.0f, 400.0f, 400.0f, 400.0f, 400.0f, 80.0f, 80.0f, 80.0f, 80.0f, 80.0f, 80.0f, 80.0f, 80.0f, 80.0f, 80.0f, 5.0f, 5.0f, 5.0f, 5.0f, 5.0f, 5.0f, 5.0f, 5.0f, 2.0f, 2.0f, 2.0f, 2.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.08f, 0.08f, 2.0f, 2.0f, 0.3f, 0.3f}
    };
    for(int corner=0;corner<2;corner++) {
        LleSparkSim *sim=lle_spark_sim_create(1440,2560,1);
        lle_spark_sim_set_workshop(sim,corners[corner],LLE_SPARK_WORKSHOP_COUNT);
        for(int frame=0;frame<180;frame++) {
            if(frame%10==0) lle_spark_sim_touch_begin(sim,frame%20==0 ? 20 : 1420,1280);
            lle_spark_sim_advance_adaptive(sim,2);
            for(size_t group=0;group<LLE_SPARK_GROUP_CAPACITY;group++) {
                LleSparkParticleSnapshot q;
                if(lle_spark_sim_get_particle(sim,group,0,&q) && q.active)
                    assert(isfinite(q.x) && isfinite(q.y) && isfinite(q.size) && isfinite(q.alpha));
            }
            assert(lle_spark_sim_active_particle_count(sim)<=LLE_SPARK_GROUP_CAPACITY*LLE_SPARK_PARTICLES_PER_GROUP);
        }
        lle_spark_sim_destroy(sim);
    }
    puts("PASS spark workshop clamps, mapping, behavior and isolation");
    return 0;
}
