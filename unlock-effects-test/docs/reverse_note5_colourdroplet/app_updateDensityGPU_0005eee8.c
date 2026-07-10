
void _ZN8SPhysics18SPColourDropletApp22updateDensityField_GPUEv(float param_1)

{
  int iVar1;
  int iVar2;
  int iVar3;
  int iVar4;
  undefined4 *puVar5;
  int *piVar6;
  int *piVar7;
  int *piVar8;
  undefined4 *puVar9;
  undefined4 *puVar10;
  float *pfVar11;
  uint in_fpscr;
  undefined4 uVar12;
  float fVar13;
  float fVar14;
  float fVar15;
  uint uStack_a0;
  int aiStack_90 [2];
  float fStack_88;
  int iStack_84;
  float fStack_80;
  float fStack_7c;
  float fStack_78;
  undefined4 uStack_74;
  undefined4 uStack_70;
  undefined4 uStack_6c;
  float fStack_68;
  int *piStack_64;
  undefined4 *puStack_60;
  float *pfStack_5c;
  float fStack_58;
  int *piStack_54;
  undefined4 *puStack_50;
  float *pfStack_4c;
  float fStack_48;
  int *piStack_44;
  undefined4 *puStack_40;
  float *pfStack_3c;
  
  iVar3 = (int)param_1 + 0x9c0;
  func_0x00028838(0x8d40,*(undefined4 *)((int)param_1 + 0x954));
  func_0x00028844(0,0,(int)*(float *)((int)param_1 + 0x34),(int)*(float *)((int)param_1 + 0x38));
  fStack_58 = 1.079e-42;
  uStack_74 = 1;
  fStack_68 = 0.0;
  fStack_48 = 0.0;
  func_0x00029204(iVar3,&uStack_74,&fStack_68,&fStack_58,&fStack_48);
  func_0x00029540(iVar3,*(undefined4 *)(*(int *)((int)param_1 + 0x948) + 8));
  uStack_70 = 0x3f800000;
  fStack_68 = 1.0;
  piStack_64 = (int *)0x3f800000;
  fStack_48 = 1.0;
  uStack_74 = 0;
  uStack_6c = 0;
  puStack_60 = (undefined4 *)0x0;
  fStack_58 = 0.0;
  piStack_54 = (int *)0x0;
  puStack_50 = (undefined4 *)0x0;
  piStack_44 = (int *)0x0;
  puStack_40 = (undefined4 *)0x0;
  func_0x0002954c(iVar3,&uStack_74,&fStack_68,&fStack_58,&fStack_48);
  func_0x00029558(iVar3);
  fStack_58 = 1.079e-42;
  fStack_48 = 1.0804e-42;
  func_0x00029360(iVar3,&fStack_58,&fStack_48);
  aiStack_90[0] = *(int *)((int)param_1 + 0x774) - *(int *)((int)param_1 + 0x770) >> 2;
  for (iVar3 = *(int *)((int)param_1 + 0x77c); iVar3 != *(int *)((int)param_1 + 0x780);
      iVar3 = iVar3 + 0x14) {
    aiStack_90[0] = aiStack_90[0] + (*(int *)(iVar3 + 0xc) - *(int *)(iVar3 + 8) >> 2);
  }
  aiStack_90[0] =
       aiStack_90[0] + (*(int *)((int)param_1 + 0x928) - *(int *)((int)param_1 + 0x924) >> 2) * 2;
  if (aiStack_90[0] == 0) {
    func_0x00028838(0x8d40,0);
    func_0x00028844(0,0,*(undefined4 *)((int)param_1 + 0x10),*(undefined4 *)((int)param_1 + 0x14));
    return;
  }
  iVar3 = (int)param_1 + 0xc2c;
  func_0x00029564(iVar3,aiStack_90);
  uVar12 = VectorSignedToFloat(aiStack_90[0],(byte)(in_fpscr >> 0x16) & 3);
  pfVar11 = (float *)((int)param_1 + 0x12e0);
  func_0x00029570((int)param_1 + 0x10a4,uVar12);
  puStack_40 = &uStack_74;
  pfStack_3c = &fStack_88;
  puVar5 = *(undefined4 **)((int)param_1 + 0x928);
  iStack_84 = 0;
  uStack_74 = 0;
  uStack_70 = 0;
  fStack_88 = *pfVar11 * *(float *)((int)param_1 + 0x12fc) * 1.25;
  puVar10 = *(undefined4 **)((int)param_1 + 0x924);
  fStack_48 = param_1;
  piStack_44 = &iStack_84;
  if (*(undefined4 **)((int)param_1 + 0x924) == puVar5) {
    puStack_60 = &uStack_74;
    pfStack_5c = &fStack_88;
    fStack_68 = param_1;
    piStack_64 = &iStack_84;
  }
  else {
    do {
      puVar9 = puVar10 + 1;
      func_0x0005da78(&fStack_48,*puVar10);
      puVar10 = puVar9;
    } while (puVar5 != puVar9);
    puStack_60 = &uStack_74;
    pfStack_5c = &fStack_88;
    puVar5 = *(undefined4 **)((int)param_1 + 0x928);
    piStack_64 = &iStack_84;
    fStack_68 = param_1;
    for (puVar10 = *(undefined4 **)((int)param_1 + 0x924); puVar10 != puVar5; puVar10 = puVar10 + 1)
    {
      func_0x0005da78(&fStack_68,*puVar10);
    }
  }
  iVar2 = *(int *)((int)param_1 + 0x77c);
  iVar1 = *(int *)((int)param_1 + 0x780);
  fStack_88 = *pfVar11;
  if ((iVar1 - iVar2 >> 2) * -0x33333333 != 0) {
    uStack_a0 = 0;
    do {
      iVar4 = iVar2 + uStack_a0 * 0x14;
      piVar7 = *(int **)(iVar4 + 8);
      piVar6 = *(int **)(iVar4 + 0xc);
      if (piVar7 != piVar6) {
        do {
          piVar8 = piVar7 + 1;
          iVar1 = *piVar7;
          fStack_80 = (float)func_0x0002942c(*(undefined4 *)(iVar1 + 0x5c));
          fVar15 = *(float *)(iVar1 + 0x2c);
          fVar14 = (float)((ulonglong)*(undefined8 *)(iVar1 + 0x9c) >> 0x20);
          fVar13 = (float)*(undefined8 *)(iVar1 + 0x9c) + *(float *)(iVar1 + 0x28);
          fStack_80 = fStack_80 * fStack_88;
          func_0x00029438(iVar3,&iStack_84,&fStack_80);
          fStack_58 = 0.0;
          fStack_78 = (fVar14 + *(float *)((int)param_1 + 0x90c)) - fVar15;
          fStack_7c = fVar13;
          func_0x00029444(iVar3,&iStack_84,&fStack_7c,&fStack_78,&fStack_58);
          piStack_54 = (int *)(fVar15 - fVar14);
          fStack_78 = fStack_80 * 100.0;
          fStack_58 = fVar13;
          func_0x00029450((int)param_1 + 0x10a4,&iStack_84,&fStack_58,&fStack_78);
          iStack_84 = iStack_84 + 1;
          piVar7 = piVar8;
        } while (piVar6 != piVar8);
        iVar2 = *(int *)((int)param_1 + 0x77c);
        iVar1 = *(int *)((int)param_1 + 0x780);
      }
      uStack_a0 = uStack_a0 + 1;
    } while (uStack_a0 < (uint)((iVar1 - iVar2 >> 2) * -0x33333333));
    fStack_88 = *pfVar11;
  }
  pfStack_4c = &fStack_88;
  puStack_50 = &uStack_74;
  puVar5 = *(undefined4 **)((int)param_1 + 0x774);
  piStack_54 = &iStack_84;
  fStack_58 = param_1;
  for (puVar10 = *(undefined4 **)((int)param_1 + 0x770); puVar10 != puVar5; puVar10 = puVar10 + 1) {
    func_0x0005da78(&fStack_58,*puVar10);
  }
  func_0x00029558(iVar3);
  func_0x00028838(0x8d40,0);
  func_0x00028844(0,0,*(undefined4 *)((int)param_1 + 0x10),*(undefined4 *)((int)param_1 + 0x14));
  return;
}

