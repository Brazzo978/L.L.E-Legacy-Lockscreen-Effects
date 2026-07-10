
void _ZN8SPhysics18SPColourDropletApp10initializeEv(int param_1)

{
  int iVar1;
  uint in_fpscr;
  float fVar2;
  float fVar3;
  undefined4 uStack_48;
  undefined4 uStack_44;
  undefined4 uStack_40;
  undefined1 uStack_31;
  int iStack_30;
  int aiStack_2c [2];
  
  func_0x00028d0c(0xcf5,1);
  aiStack_2c[0] = (uint)(0.0 < *(float *)(param_1 + 0x38)) * (int)*(float *)(param_1 + 0x38);
  iStack_30 = (uint)(0.0 < *(float *)(param_1 + 0x34)) * (int)*(float *)(param_1 + 0x34);
  uStack_31 = 0;
  func_0x0005e738(&uStack_48,&iStack_30,aiStack_2c,&uStack_31,0x1401,0x1908,0x1908,0x2601);
  iStack_30 = (uint)(0.0 < *(float *)(param_1 + 0x3c)) * (int)*(float *)(param_1 + 0x3c);
  aiStack_2c[0] = (uint)(0.0 < *(float *)(param_1 + 0x40)) * (int)*(float *)(param_1 + 0x40);
  *(undefined4 *)(param_1 + 0x954) = uStack_48;
  *(undefined4 *)(param_1 + 0x958) = uStack_44;
  *(undefined4 *)(param_1 + 0x95c) = uStack_40;
  uStack_31 = 0;
  func_0x0005e738(&uStack_48,&iStack_30,aiStack_2c,&uStack_31,0x1401,0x1908,0x1908,0x2601);
  fVar2 = (float)VectorSignedToFloat(*(undefined4 *)(param_1 + 0x10),(byte)(in_fpscr >> 0x16) & 3);
  iVar1 = param_1 + 0xa90;
  fVar3 = (float)VectorSignedToFloat(*(undefined4 *)(param_1 + 0x14),(byte)(in_fpscr >> 0x16) & 3);
  uStack_31 = 0;
  fVar2 = fVar2 * *(float *)(param_1 + 0x12dc);
  fVar3 = fVar3 * *(float *)(param_1 + 0x12dc);
  *(undefined4 *)(param_1 + 0x964) = uStack_48;
  *(undefined4 *)(param_1 + 0x968) = uStack_44;
  *(undefined4 *)(param_1 + 0x96c) = uStack_40;
  iStack_30 = (uint)(0.0 < fVar2) * (int)fVar2;
  aiStack_2c[0] = (uint)(0.0 < fVar3) * (int)fVar3;
  func_0x0005e738(&uStack_48,&iStack_30,aiStack_2c,&uStack_31,0x1401,0x1908,0x1908,0x2601);
  *(undefined4 *)(param_1 + 0x970) = uStack_48;
  *(undefined4 *)(param_1 + 0x974) = uStack_44;
  *(undefined4 *)(param_1 + 0x978) = uStack_40;
  func_0x000294e0(iVar1,param_1 + 0x12c0);
  func_0x000294ec(iVar1,param_1 + 0x958);
  func_0x000294f8(iVar1,param_1 + 0x34);
  func_0x00029504(iVar1,param_1 + 0x968);
  func_0x00029510(param_1 + 0xd98,param_1 + 0x974);
  func_0x0002951c(param_1 + 0xe54,param_1 + 0x958);
  func_0x00029528(param_1 + 0xe54,param_1 + 0x3c);
  return;
}

