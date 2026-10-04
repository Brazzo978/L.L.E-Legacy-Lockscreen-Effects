#include "../native/abstract_tiles_internal.h"
#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void) {
    float defaults[AT_WORKSHOP_COUNT]={.4f,.16f,.02f,1,1,.8f,.016f,1,.3f,1,.8f,1,1};
    AtScene stock={0}, configured={0};
    at_scene_configure(&configured,defaults,AT_WORKSHOP_COUNT);
    at_scene_init(&stock,1080,1920);
    at_scene_init(&configured,1080,1920);
    srand(7); at_scene_touch(&stock,0,540,960,0);
    srand(7); at_scene_touch(&configured,0,540,960,0);
    srand(9); at_scene_step(&stock,.2f);
    srand(9); at_scene_step(&configured,.2f);
    float a[AT_MAX_VERTICES*AT_FLOATS_PER_VERTEX],b[AT_MAX_VERTICES*AT_FLOATS_PER_VERTEX];
    int count=at_scene_build_vertices(&stock,1080,1920,a,sizeof(a)/sizeof(float));
    assert(count>0);
    assert(count==at_scene_build_vertices(&configured,1080,1920,b,sizeof(b)/sizeof(float)));
    for (int i=0;i<count*AT_FLOATS_PER_VERTEX;i++) assert(a[i]==b[i]);
    float tuning[AT_WORKSHOP_COUNT];
    memcpy(tuning,defaults,sizeof(tuning));
    tuning[0]=1.5f; tuning[3]=0; tuning[5]=1; tuning[6]=1;
    at_scene_configure(&configured,tuning,AT_WORKSHOP_COUNT);
    at_scene_reset(&configured);
    at_scene_touch(&configured,0,540,960,0);
    int pops=0;
    for(int i=0;i<configured.triangle_count;i++) {
        AtTriangle *tri=&configured.triangles[i];
        if(tri->transform_kind==AT_TRANSFORM_POP) {
            pops++; assert(tri->duration==1.5f); assert(tri->strength==0);
        }
    }
    assert(pops>0);
    assert(!stock.workshop_enabled);
    tuning[0]=NAN; tuning[1]=-2; tuning[3]=999;
    at_scene_configure(&configured,tuning,AT_WORKSHOP_COUNT);
    assert(configured.workshop[0]==.4f);
    assert(configured.workshop[1]==.02f);
    assert(configured.workshop[3]==4);
    at_scene_configure(&configured,NULL,0);
    assert(!configured.workshop_enabled);
    puts("Abstract Tiles workshop: stock equivalence, actual pop tuning, reset persistence, native clamps and isolation passed");
    return 0;
}
