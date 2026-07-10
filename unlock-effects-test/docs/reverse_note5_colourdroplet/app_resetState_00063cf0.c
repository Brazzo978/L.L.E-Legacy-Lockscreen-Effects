
void _ZN8SPhysics18SPColourDropletApp10resetStateEv(int param_1)

{
  uint uVar1;
  int iVar2;
  int iVar3;
  undefined4 *puVar4;
  int iVar6;
  int iVar7;
  int iVar8;
  undefined4 *puVar9;
  int *piVar10;
  bool bVar11;
  uint in_fpscr;
  uint uVar12;
  float fVar13;
  float fVar14;
  undefined1 auStack_3c [4];
  undefined4 auStack_38 [3];
  undefined4 *puVar5;
  
  iVar7 = *(int *)(param_1 + 0x98c);
  iVar3 = *(int *)(param_1 + 0x97c);
  while (iVar7 != iVar3) {
    while (iVar3 == *(int *)(param_1 + 0x984) + -0xc) {
      if (*(int *)(param_1 + 0x980) != 0) {
        func_0x00027d1c(*(int *)(param_1 + 0x980),0x78);
        iVar7 = *(int *)(param_1 + 0x98c);
      }
      iVar3 = *(int *)(param_1 + 0x988);
      *(int *)(param_1 + 0x988) = iVar3 + 4;
      iVar3 = *(int *)(iVar3 + 4);
      *(int *)(param_1 + 0x980) = iVar3;
      *(int *)(param_1 + 0x97c) = iVar3;
      *(int *)(param_1 + 0x984) = iVar3 + 0x78;
      if (iVar7 == iVar3) goto code_r0x00063d74;
    }
    *(int *)(param_1 + 0x97c) = iVar3 + 0xc;
    iVar3 = iVar3 + 0xc;
  }
code_r0x00063d74:
  fVar14 = (float)VectorSignedToFloat(*(undefined4 *)(param_1 + 0x10),(byte)(in_fpscr >> 0x16) & 3);
  auStack_38[0] = 0;
  fVar13 = (float)VectorSignedToFloat(*(undefined4 *)(param_1 + 0x14),(byte)(in_fpscr >> 0x16) & 3);
  fVar14 = fVar14 * 0.5;
  fVar13 = fVar13 * 0.5;
  *(float *)(param_1 + 0x9a4) = fVar14;
  *(float *)(param_1 + 0x9a8) = fVar13;
  *(float *)(param_1 + 0x9ac) = fVar14;
  *(float *)(param_1 + 0x9b0) = fVar13;
  *(undefined1 *)(param_1 + 0x9bc) = 0;
  *(float *)(param_1 + 0x9b4) = fVar14;
  *(float *)(param_1 + 0x9b8) = fVar13;
  iVar3 = *(int *)(param_1 + 0x44);
  fVar14 = *(float *)(param_1 + 0x38) * *(float *)(param_1 + 0x34);
  if (iVar3 != *(int *)(param_1 + 0x48)) {
    *(int *)(param_1 + 0x48) = iVar3;
  }
  uVar12 = (uint)(0.0 < fVar14) * (int)fVar14;
  if (uVar12 != 0) {
    uVar1 = *(int *)(param_1 + 0x4c) - iVar3 >> 2;
    if (uVar12 < uVar1 || uVar12 - uVar1 == 0) {
      func_0x000281a8(param_1 + 0x44,iVar3,uVar12,auStack_38,auStack_3c);
    }
    else {
      func_0x00028160(param_1 + 0x44,iVar3,auStack_38,auStack_3c,uVar12,0);
    }
  }
  iVar3 = param_1 + 0x770;
  *(undefined4 *)(param_1 + 0x12e8) = 0;
  *(undefined4 *)(param_1 + 0x12e0) = 0x430c0000;
  *(undefined4 *)(param_1 + 0x2c) = 0;
  *(undefined1 *)(param_1 + 0x1f) = 0;
  *(undefined1 *)(param_1 + 0x9bc) = 0;
  auStack_38[0] = 0;
  func_0x000296d8(param_1 + 0x50,auStack_38,iVar3);
  func_0x00028520(param_1 + 0x50,iVar3);
  auStack_38[0] = 0;
  func_0x000296d8(param_1 + 0x3e0,auStack_38,iVar3);
  func_0x00028520(param_1 + 0x3e0,iVar3);
  puVar9 = *(undefined4 **)(param_1 + 0x928);
  if (*(int *)(param_1 + 0x770) != *(int *)(param_1 + 0x774)) {
    *(int *)(param_1 + 0x774) = *(int *)(param_1 + 0x770);
  }
  puVar4 = *(undefined4 **)(param_1 + 0x924);
  if (*(undefined4 **)(param_1 + 0x924) != puVar9) {
    do {
      puVar5 = puVar4 + 1;
      piVar10 = (int *)*puVar4;
      if (piVar10 != (int *)0x0) {
        (**(code **)(*piVar10 + 4))(piVar10);
      }
      puVar4 = puVar5;
    } while (puVar9 != puVar5);
    if (*(int *)(param_1 + 0x924) != *(int *)(param_1 + 0x928)) {
      *(int *)(param_1 + 0x928) = *(int *)(param_1 + 0x924);
    }
  }
  iVar7 = *(int *)(param_1 + 0x77c);
  iVar3 = *(int *)(param_1 + 0x780);
  if (iVar7 != iVar3) {
    iVar6 = iVar7;
    iVar8 = iVar7 + 8;
code_r0x00063f20:
    do {
      iVar2 = *(int *)(iVar7 + 8 + (iVar6 - iVar7));
      if (iVar2 != 0) {
        if (0x80 < (*(int *)(iVar8 + 8) - iVar2 & 0xfffffffcU)) {
          iVar6 = iVar6 + 0x14;
          iVar8 = iVar8 + 0x14;
          func_0x00027d28();
          if (iVar3 == iVar6) break;
          goto code_r0x00063f20;
        }
        func_0x00027d1c(iVar2);
      }
      iVar6 = iVar6 + 0x14;
      iVar8 = iVar8 + 0x14;
    } while (iVar3 != iVar6);
    *(int *)(param_1 + 0x780) = iVar7;
  }
  fVar14 = (float)VectorSignedToFloat(*(undefined4 *)(param_1 + 0x10),(byte)(in_fpscr >> 0x16) & 3);
  iVar3 = param_1 + 0xa90;
  fVar13 = (float)VectorSignedToFloat(*(undefined4 *)(param_1 + 0x14),(byte)(in_fpscr >> 0x16) & 3);
  auStack_38[0] = 0x3f800000;
  *(float *)(param_1 + 0x12a8) = fVar14 * 0.5;
  *(float *)(param_1 + 0x12b0) = fVar14 * 0.5;
  *(float *)(param_1 + 0x12ac) = fVar13 * 0.5;
  *(float *)(param_1 + 0x12b4) = fVar13 * 0.5;
  *(undefined4 *)(param_1 + 0x12b8) = 10;
  *(undefined4 *)(param_1 + 0x12bc) = 0x3c;
  *(undefined4 *)(param_1 + 0x12a0) = 0;
  *(undefined4 *)(param_1 + 0x12a4) = 0;
  *(undefined1 *)(param_1 + 0x20) = 0;
  *(undefined4 *)(param_1 + 0x938) = 0x3d4ccccd;
  func_0x00029594(iVar3,auStack_38);
  auStack_38[0] = 0x3f800000;
  func_0x000295ac(iVar3,auStack_38);
  auStack_38[0] = 0x3f800000;
  func_0x000295b8(iVar3,auStack_38);
  bVar11 = *(char *)(param_1 + 0x23) != '\0';
  iVar3 = 0;
  if (bVar11) {
    iVar3 = param_1 + 0x12c0;
  }
  if (bVar11) {
    *(undefined4 *)(iVar3 + 0x2c) = 0x3f000000;
  }
  func_0x000296e4(param_1 + 0xe54);
  return;
}

