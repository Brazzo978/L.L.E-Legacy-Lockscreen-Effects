
void _ZN8SPhysics18SPColourDropletApp9updateAppEv(int param_1)

{
  float fVar1;
  float fVar2;
  
  fVar1 = *(float *)(param_1 + 0x940);
  if (fVar1 < 1.0) {
    fVar2 = *(float *)(param_1 + 0x938);
    fVar1 = fVar1 + 0.01325;
    if (1.0 < fVar1) {
      fVar1 = 1.0;
    }
    *(float *)(param_1 + 0x940) = fVar1;
    fVar1 = (float)func_0x00029480(fVar1);
    *(float *)(param_1 + 0x93c) = (1.0 - fVar1) * fVar2;
  }
  if (((*(char *)(param_1 + 0x25) == '\0') &&
      (((*(char *)(param_1 + 0x23) != '\0' || (*(char *)(param_1 + 0x21) != '\0')) &&
       (*(char *)(param_1 + 0x1d) == '\0')))) && (*(char *)(param_1 + 0x1e) == '\0')) {
    if (*(char *)(param_1 + 0x1f) == '\0') {
      if (*(int *)(param_1 + 0x30) == 0) {
        func_0x0002987c(param_1);
      }
      func_0x00029840(param_1);
      func_0x0002984c(param_1);
    }
    else {
      func_0x00029870(param_1);
    }
    func_0x00029858(param_1);
    _ZN8SPhysics18SPColourDropletApp17updateSubParticleEv(param_1);
    return;
  }
  return;
}

