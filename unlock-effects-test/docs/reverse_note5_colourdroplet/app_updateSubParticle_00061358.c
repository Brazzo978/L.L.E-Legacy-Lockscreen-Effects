
void _ZN8SPhysics18SPColourDropletApp17updateSubParticleEv(int param_1)

{
  uint uVar1;
  byte bVar2;
  int *piVar3;
  float *pfVar4;
  int iVar5;
  int *piVar6;
  int *piVar7;
  int *piVar8;
  float *pfVar9;
  int *piVar10;
  int iVar11;
  float *pfVar12;
  uint in_fpscr;
  float fVar13;
  float fVar14;
  int aiStack_30 [2];
  int aiStack_28 [2];
  float fStack_20;
  float fStack_1c;
  
  piVar6 = *(int **)(param_1 + 0x924);
  piVar8 = *(int **)(param_1 + 0x928);
  iVar5 = (int)piVar8 - (int)piVar6;
  iVar11 = iVar5 >> 4;
  aiStack_30[0] = param_1;
  if (0 < iVar11) {
    pfVar4 = (float *)(param_1 + 0x91c);
    pfVar9 = (float *)(param_1 + 0x910);
    pfVar12 = (float *)(param_1 + 0x920);
    piVar7 = piVar6;
    do {
      iVar5 = *piVar7;
      fVar14 = *pfVar9;
      fVar13 = *pfVar12;
      *(float *)(iVar5 + 0x28) =
           *(float *)(iVar5 + 0x28) + *(float *)(iVar5 + 0x30) + fVar14 * *pfVar4;
      *(float *)(iVar5 + 0x2c) =
           *(float *)(iVar5 + 0x2c) + *(float *)(iVar5 + 0x34) + fVar14 * fVar13;
      piVar3 = (int *)*piVar7;
      iVar5 = piVar3[0x1b];
      piVar3[0x1b] = iVar5 + 1;
      if (iVar5 + 1 < 0x14) {
        fVar13 = (float)piVar3[0x17] + ((float)piVar3[0x18] - (float)piVar3[0x17]) * 0.19;
      }
      else {
        fVar13 = (float)piVar3[0x17] - (float)piVar3[0x1d] * 0.016;
      }
      uVar1 = in_fpscr & 0xfffffff;
      in_fpscr = uVar1 | (uint)(fVar13 < 0.0) << 0x1f;
      piVar3[0x17] = (int)fVar13;
      if (SUB41(in_fpscr >> 0x1f,0)) {
        (**(code **)(*piVar3 + 4))();
        goto code_r0x00061688;
      }
      iVar5 = piVar6[1];
      fVar13 = *pfVar9;
      fVar14 = *pfVar12;
      *(float *)(iVar5 + 0x28) =
           *(float *)(iVar5 + 0x28) + *(float *)(iVar5 + 0x30) + fVar13 * *pfVar4;
      *(float *)(iVar5 + 0x2c) =
           *(float *)(iVar5 + 0x2c) + *(float *)(iVar5 + 0x34) + fVar13 * fVar14;
      piVar3 = (int *)piVar6[1];
      iVar5 = piVar3[0x1b];
      piVar3[0x1b] = iVar5 + 1;
      if (iVar5 + 1 < 0x14) {
        fVar13 = (float)piVar3[0x17] + ((float)piVar3[0x18] - (float)piVar3[0x17]) * 0.19;
        in_fpscr = uVar1 | (uint)(fVar13 < 0.0) << 0x1f;
        piVar3[0x17] = (int)fVar13;
        bVar2 = (byte)(in_fpscr >> 0x18);
      }
      else {
        fVar13 = (float)piVar3[0x17] - (float)piVar3[0x1d] * 0.016;
        in_fpscr = uVar1 | (uint)(fVar13 < 0.0) << 0x1f;
        piVar3[0x17] = (int)fVar13;
        bVar2 = (byte)(in_fpscr >> 0x18);
      }
      if ((bool)(bVar2 >> 7)) {
        piVar6 = piVar6 + 1;
        (**(code **)(*piVar3 + 4))();
        goto code_r0x00061688;
      }
      iVar5 = piVar6[2];
      fVar13 = *pfVar9;
      fVar14 = *pfVar12;
      *(float *)(iVar5 + 0x28) =
           *(float *)(iVar5 + 0x28) + *(float *)(iVar5 + 0x30) + fVar13 * *pfVar4;
      *(float *)(iVar5 + 0x2c) =
           *(float *)(iVar5 + 0x2c) + *(float *)(iVar5 + 0x34) + fVar13 * fVar14;
      piVar3 = (int *)piVar6[2];
      iVar5 = piVar3[0x1b];
      piVar3[0x1b] = iVar5 + 1;
      if (iVar5 + 1 < 0x14) {
        fVar13 = (float)piVar3[0x17] + ((float)piVar3[0x18] - (float)piVar3[0x17]) * 0.19;
        in_fpscr = in_fpscr & 0xfffffff | (uint)(fVar13 < 0.0) << 0x1f;
        piVar3[0x17] = (int)fVar13;
        bVar2 = (byte)(in_fpscr >> 0x18);
      }
      else {
        fVar13 = (float)piVar3[0x17] - (float)piVar3[0x1d] * 0.016;
        in_fpscr = in_fpscr & 0xfffffff | (uint)(fVar13 < 0.0) << 0x1f;
        piVar3[0x17] = (int)fVar13;
        bVar2 = (byte)(in_fpscr >> 0x18);
      }
      if ((bool)(bVar2 >> 7)) {
        piVar6 = piVar6 + 2;
        (**(code **)(*piVar3 + 4))();
        goto code_r0x00061688;
      }
      iVar5 = piVar6[3];
      fVar13 = *pfVar9;
      fVar14 = *pfVar12;
      *(float *)(iVar5 + 0x28) =
           *(float *)(iVar5 + 0x28) + *(float *)(iVar5 + 0x30) + fVar13 * *pfVar4;
      *(float *)(iVar5 + 0x2c) =
           *(float *)(iVar5 + 0x2c) + *(float *)(iVar5 + 0x34) + fVar13 * fVar14;
      piVar3 = (int *)piVar6[3];
      iVar5 = piVar3[0x1b];
      piVar3[0x1b] = iVar5 + 1;
      if (iVar5 + 1 < 0x14) {
        fVar13 = (float)piVar3[0x17] + ((float)piVar3[0x18] - (float)piVar3[0x17]) * 0.19;
        in_fpscr = in_fpscr & 0xfffffff | (uint)(fVar13 < 0.0) << 0x1f;
        piVar3[0x17] = (int)fVar13;
        bVar2 = (byte)(in_fpscr >> 0x18);
      }
      else {
        fVar13 = (float)piVar3[0x17] - (float)piVar3[0x1d] * 0.016;
        in_fpscr = in_fpscr & 0xfffffff | (uint)(fVar13 < 0.0) << 0x1f;
        piVar3[0x17] = (int)fVar13;
        bVar2 = (byte)(in_fpscr >> 0x18);
      }
      if ((bool)(bVar2 >> 7)) {
        piVar6 = piVar6 + 3;
        (**(code **)(*piVar3 + 4))();
        goto code_r0x00061688;
      }
      iVar11 = iVar11 + -1;
      piVar6 = piVar6 + 4;
      piVar7 = piVar7 + 4;
    } while (iVar11 != 0);
    iVar5 = (int)piVar8 - (int)piVar6;
  }
  iVar5 = iVar5 >> 2;
  piVar7 = piVar8;
  if (iVar5 == 2) {
code_r0x00061788:
    iVar11 = func_0x0005d998(aiStack_30,piVar6);
    if (iVar11 == 0) {
      piVar6 = piVar6 + 1;
code_r0x000617a0:
      iVar11 = func_0x0005d998(aiStack_30,piVar6);
      if (iVar11 == 0) goto code_r0x000615a8;
    }
  }
  else {
    if (iVar5 != 3) {
      if (iVar5 != 1) goto code_r0x000615a8;
      goto code_r0x000617a0;
    }
    iVar11 = func_0x0005d998(aiStack_30,piVar6);
    if (iVar11 == 0) {
      piVar6 = piVar6 + 1;
      goto code_r0x00061788;
    }
  }
code_r0x00061688:
  piVar7 = piVar6;
  if ((piVar8 != piVar6) && (aiStack_28[0] = param_1, piVar8 != piVar6 + 1)) {
    piVar3 = piVar6 + 1;
    do {
      iVar11 = func_0x0005d998(aiStack_28,piVar3);
      piVar10 = piVar3 + 1;
      piVar7 = piVar6;
      if (iVar11 == 0) {
        piVar7 = piVar6 + 1;
        *piVar6 = *piVar3;
      }
      piVar6 = piVar7;
      piVar3 = piVar10;
    } while (piVar8 != piVar10);
  }
code_r0x000615a8:
  if (*(int **)(param_1 + 0x928) != piVar7) {
    *(int **)(param_1 + 0x928) = piVar7;
  }
  if ((*(int *)(param_1 + 0x2c) == 1) && (*(char *)(param_1 + 0x1f) == '\0')) {
    fVar14 = *(float *)(param_1 + 0x1304);
    iRam000881c4 = iRam000881c4 + 1;
    fVar13 = (float)VectorSignedToFloat(iRam000881c4,(byte)(in_fpscr >> 0x16) & 3);
    uVar1 = in_fpscr & 0xfffffff | (uint)(fVar13 < fVar14) << 0x1f |
            (uint)(fVar13 == fVar14) << 0x1e;
    bVar2 = (byte)(uVar1 >> 0x18);
    if (!(bool)(bVar2 >> 6 & 1) && (bool)(bVar2 >> 7) == (NAN(fVar13) || NAN(fVar14))) {
      fVar13 = (float)VectorSignedToFloat(*(undefined4 *)(param_1 + 0x10),(byte)(uVar1 >> 0x16) & 3)
      ;
      fVar14 = (float)VectorSignedToFloat(*(undefined4 *)(param_1 + 0x14),(byte)(uVar1 >> 0x16) & 3)
      ;
      fStack_20 = (*(float *)(param_1 + 0x9ac) / fVar13) * *(float *)(param_1 + 0x908);
      fStack_1c = (*(float *)(param_1 + 0x9b0) / fVar14) * *(float *)(param_1 + 0x90c);
      func_0x000295e8(param_1,&fStack_20);
      iRam000881c4 = 0;
    }
  }
  return;
}

