
void _ZN8SPhysics18SPColourDropletApp7initAppEii(int param_1,int param_2,int param_3)

{
  undefined4 uVar1;
  int iVar2;
  int iVar3;
  uint uVar4;
  uint in_fpscr;
  float fVar5;
  float fVar6;
  float fVar7;
  undefined1 auStack_14 [4];
  undefined4 auStack_10 [2];
  
  if (*(int *)(param_1 + 0x10) < *(int *)(param_1 + 0x14)) {
    fVar6 = (float)VectorSignedToFloat(*(int *)(param_1 + 0x10),(byte)(in_fpscr >> 0x16) & 3);
  }
  else {
    fVar6 = (float)VectorSignedToFloat(*(int *)(param_1 + 0x14),(byte)(in_fpscr >> 0x16) & 3);
  }
  fVar5 = (float)VectorSignedToFloat(param_3,(byte)(in_fpscr >> 0x16) & 3);
  fVar7 = (float)VectorSignedToFloat(param_2,(byte)(in_fpscr >> 0x16) & 3);
  fVar7 = fVar5 / fVar7;
  *(float *)(param_1 + 0x12dc) = 7.2 / fVar6;
  *(float *)(param_1 + 0x12cc) = fVar7;
  if (fVar7 <= 1.0) {
    *(undefined4 *)(param_1 + 0x908) = 0x3f19999a;
    *(float *)(param_1 + 0x90c) = fVar7 * 0.6;
    *(float *)(param_1 + 0x12d0) = fVar7 * 0.6 * 0.16;
  }
  else {
    *(undefined4 *)(param_1 + 0x908) = 0x3ee66667;
    *(float *)(param_1 + 0x90c) = fVar7 * 0.45000002;
    *(undefined4 *)(param_1 + 0x12d0) = 0x3d9374bd;
  }
  *(undefined1 *)(param_1 + 0x1d) = 1;
  *(undefined4 *)(param_1 + 0x12e4) = 0x3fc00000;
  if (*(char *)(param_1 + 0x23) == '\0') {
    fVar6 = 0.00078125;
  }
  else {
    fVar6 = 0.0009765625;
  }
  auStack_10[0] = 0;
  fVar5 = fVar5 * fVar6;
  if (1.0 < fVar7) {
    uVar1 = 0x43870000;
  }
  else {
    uVar1 = 0x43b28000;
  }
  *(float *)(param_1 + 0x12c0) = fVar5;
  if (1.0 < fVar7) {
    fVar5 = 135.0;
  }
  *(undefined4 *)(param_1 + 0x3c) = uVar1;
  if (fVar7 <= 1.0) {
    fVar5 = 178.5;
  }
  iVar2 = *(int *)(param_1 + 0x94c);
  *(bool *)(param_1 + 0x21) = param_2 <= param_3;
  fVar7 = fVar7 * fVar5;
  *(float *)(param_1 + 0x34) = fVar5;
  uVar4 = iVar2 - *(int *)(param_1 + 0x948) >> 2;
  *(float *)(param_1 + 0x38) = fVar7;
  *(float *)(param_1 + 0x40) = fVar7 + fVar7;
  if (uVar4 < 6) {
    uVar4 = 5 - uVar4;
    if (uVar4 != 0) {
      if ((uint)(*(int *)(param_1 + 0x950) - iVar2 >> 2) < uVar4) {
        func_0x000296b4(param_1 + 0x948,iVar2,auStack_10,auStack_14,uVar4,0);
        func_0x000296cc(param_1);
        return;
      }
      func_0x000296c0(param_1 + 0x948,iVar2,uVar4,auStack_10,auStack_14);
    }
  }
  else {
    iVar3 = *(int *)(param_1 + 0x948) + 0x14;
    if (iVar2 != iVar3) {
      *(int *)(param_1 + 0x94c) = iVar3;
    }
  }
  func_0x000296cc(param_1);
  return;
}

