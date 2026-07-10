
void _ZN8SPhysics28SPDrawColourDropletDirection10drawRenderEv(int param_1)

{
  float fVar1;
  float fVar2;
  float fVar3;
  float fVar4;
  
  fVar4 = *(float *)(param_1 + 0xc4);
  fVar3 = *(float *)(param_1 + 200);
  fVar2 = fVar4 + (*(float *)(param_1 + 0xbc) - fVar4) * 0.99;
  fVar1 = fVar3 + (*(float *)(param_1 + 0xc0) - fVar3) * 0.99;
  if ((1.3 < SQRT(fVar2 * fVar2 + fVar1 * fVar1)) || ((fVar4 == 0.0 && (fVar3 == 0.0)))) {
    fVar2 = *(float *)(param_1 + 0xbc) * 0.99;
    fVar1 = *(float *)(param_1 + 0xc0) * 0.99;
  }
  *(float *)(param_1 + 0xc0) = fVar1;
  *(float *)(param_1 + 0xbc) = fVar2;
  func_0x00028e08(param_1,&UNK_00069d40);
  func_0x00029240(param_1,&UNK_0006f55c);
  func_0x000291d4(param_1,&UNK_0006ba30);
  func_0x00029318(param_1,&UNK_0006f56c);
  func_0x00028e20(param_1,&UNK_00069d54);
  func_0x00028e38(param_1,&UNK_0006bef0,*(undefined4 *)(param_1 + 0xd4));
  func_0x00028e38(param_1,&UNK_0006ba48,*(undefined4 *)(param_1 + 0xd8));
  func_0x000291ec(param_1,&UNK_0006be68,param_1 + 0xac,2,1);
  func_0x000291ec(param_1,&UNK_0006f574,param_1 + 0xb4,2,1);
  func_0x000291ec(param_1,&UNK_0006f580,param_1 + 0xbc,2,1);
  return;
}

