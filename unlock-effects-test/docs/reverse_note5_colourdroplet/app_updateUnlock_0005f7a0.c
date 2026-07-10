
void _ZN8SPhysics18SPColourDropletApp12updateUnlockEv(int param_1)

{
  float *pfVar1;
  int iVar2;
  float fVar3;
  float fVar4;
  undefined4 auStack_24 [2];
  
  fVar4 = *(float *)(param_1 + 0x12e0);
  if (fVar4 < 2000.0) {
    if (*(char *)(param_1 + 0x20) == '\0') {
      fVar3 = *(float *)(param_1 + 0x12f4);
    }
    else {
      fVar3 = *(float *)(param_1 + 0x12f8);
    }
    *(float *)(param_1 + 0x12e0) = fVar3 * fVar4;
  }
  iVar2 = *(int *)(param_1 + 0x12b8) + -1;
  *(float *)(param_1 + 0x12a0) = *(float *)(param_1 + 0x12a0) * 0.99;
  *(int *)(param_1 + 0x12b8) = iVar2;
  if (iVar2 < 1) {
    pfVar1 = (float *)(param_1 + 0x12e8);
    iVar2 = param_1 + 0xa90;
    auStack_24[0] = func_0x00029588(1.0 - *pfVar1);
    func_0x00029594(iVar2,auStack_24);
    fVar4 = (float)func_0x000295a0(*pfVar1,0x3e000000);
    auStack_24[0] = func_0x00029588(1.0 - fVar4);
    func_0x000295ac(iVar2,auStack_24);
    fVar4 = (float)func_0x000295a0(*pfVar1,0x3d4ccccd);
    auStack_24[0] = func_0x00029588(1.0 - fVar4);
    func_0x000295b8(iVar2,auStack_24);
    if (*pfVar1 < 1.0) {
      fVar4 = *pfVar1 + 0.05;
      *pfVar1 = fVar4;
      if (fVar4 < 1.0) {
        return;
      }
    }
    else {
      *(int *)(param_1 + 0x12bc) = *(int *)(param_1 + 0x12bc) + -1;
    }
    *pfVar1 = 1.0;
    return;
  }
  return;
}

