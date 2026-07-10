
void _ZN8SPhysics18SPColourDropletApp12updateBreathEv(int param_1)

{
  float fVar1;
  float *pfVar2;
  float fVar3;
  
  pfVar2 = (float *)(param_1 + 0x12a4);
  fVar3 = *pfVar2 + 0.02;
  *pfVar2 = fVar3;
  fVar1 = (float)func_0x0002957c(fVar3);
  *(float *)(param_1 + 0x12a0) = fVar1 + *(float *)(param_1 + 0x12a0);
  if (6.2831855 <= fVar3) {
    *pfVar2 = 0.0;
    *(undefined4 *)(param_1 + 0x12a0) = 0;
  }
  return;
}

