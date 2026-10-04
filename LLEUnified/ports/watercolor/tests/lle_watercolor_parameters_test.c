#include "../native/watercolor_parameters.h"
#include <assert.h>
#include <stdio.h>
int main(void) {
    LleWatercolorParameters stock = lle_watercolor_parameters_defaults();
    LleWatercolorParameters tuned = stock;
    assert(stock.brush_scale == .8f && stock.drag_threshold == .025f);
    assert(stock.stamp_spacing == .05f && stock.noise_scalar == 425.f);
    assert(stock.radial_scalar == 66.69f && stock.saturation == 1.2f);
    assert(stock.red_saturation == 1.3f && stock.green_saturation == .4f);
    assert(stock.blue_saturation == .4f && stock.brightness == 1.35f);
    for (int id=1; id<=10; ++id) lle_watercolor_parameters_set(&tuned,id,0.f);
    assert(tuned.brush_scale == .2f && tuned.drag_threshold == .005f);
    assert(tuned.stamp_spacing == .015f && tuned.noise_scalar == 0.f);
    assert(tuned.radial_scalar == 0.f && tuned.saturation == 0.f);
    assert(tuned.red_saturation == 0.f && tuned.green_saturation == 0.f);
    assert(tuned.blue_saturation == 0.f && tuned.brightness == .2f);
    lle_watercolor_parameters_set(&tuned,1,INFINITY);
    lle_watercolor_parameters_set(&tuned,1,NAN);
    assert(tuned.brush_scale == .2f);
    lle_watercolor_parameters_set(&tuned,1,1000.f);
    assert(tuned.brush_scale == 1.6f);
    lle_watercolor_parameters_set(&tuned,-1,0.f);
    assert(tuned.brush_scale == 1.6f);
    assert(stock.brush_scale == .8f && stock.brightness == 1.35f);
    LleWatercolorParameters next = lle_watercolor_parameters_defaults();
    assert(next.brush_scale == .8f && next.saturation == 1.2f);
    puts("Watercolor parameter tests passed");
    return 0;
}
