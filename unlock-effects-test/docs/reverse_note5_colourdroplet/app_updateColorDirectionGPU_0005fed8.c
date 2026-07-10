
void _ZN8SPhysics18SPColourDropletApp25updateColorNDirection_GPUEv(uint param_1)

{
  uint *puVar1;
  float *pfVar2;
  uint uVar3;
  float fVar4;
  int iVar5;
  uint *puVar6;
  uint uVar7;
  float *pfVar8;
  uint *puVar9;
  float fVar10;
  uint uVar11;
  uint uVar12;
  int iVar13;
  int iVar14;
  int *piVar15;
  float *pfVar16;
  float *pfVar17;
  uint *puVar18;
  undefined4 *puVar19;
  uint uVar20;
  uint uVar21;
  float *pfVar22;
  int *piVar23;
  float *pfVar24;
  uint *puVar25;
  undefined4 *puVar26;
  int *piVar27;
  undefined4 *puVar28;
  float *pfVar29;
  uint *puVar30;
  float fVar31;
  float fVar32;
  uint uStack_154;
  uint *puStack_14c;
  uint uStack_130;
  uint uStack_12c;
  char acStack_128 [8];
  float afStack_120 [2];
  uint uStack_118;
  uint uStack_114;
  uint uStack_10c;
  float **ppfStack_108;
  char *pcStack_104;
  uint *puStack_100;
  float *pfStack_fc;
  uint uStack_f8;
  float **ppfStack_f4;
  char *pcStack_f0;
  uint *puStack_ec;
  float *pfStack_e8;
  float *pfStack_e0;
  float *pfStack_dc;
  float *pfStack_d8;
  float *pfStack_c8;
  float *pfStack_c4;
  float *pfStack_c0;
  uint *puStack_bc;
  uint *puStack_b8;
  uint *puStack_b4;
  float *pfStack_44;
  float *pfStack_40;
  float *pfStack_3c;
  
  func_0x00028838(0x8d40,*(undefined4 *)(param_1 + 0x964));
  func_0x00028844(0,0,(int)*(float *)(param_1 + 0x3c),(int)*(float *)(param_1 + 0x40));
  func_0x00028850(0,0,0,0);
  func_0x0002885c(0x4000);
  acStack_128[0] = '\0';
  func_0x00028e5c(&pfStack_e0);
  afStack_120[0] = *(float *)(param_1 + 0x12e0);
  uStack_118 = 0;
  pcStack_104 = acStack_128;
  uStack_114 = 0;
  puStack_100 = &uStack_118;
  ppfStack_108 = &pfStack_e0;
  pfStack_fc = afStack_120;
  puVar19 = *(undefined4 **)(param_1 + 0x928);
  fVar31 = afStack_120[0] * *(float *)(param_1 + 0x12fc);
  uStack_10c = param_1;
  if (*(undefined4 **)(param_1 + 0x924) != puVar19) {
    puVar28 = *(undefined4 **)(param_1 + 0x924);
    afStack_120[0] = fVar31 + fVar31;
    do {
      puVar26 = puVar28 + 1;
      func_0x0005dca0(&uStack_10c,*puVar28);
      puVar28 = puVar26;
    } while (puVar19 != puVar26);
    afStack_120[0] = *(float *)(param_1 + 0x12e0);
  }
  acStack_128[0] = '\x01';
  iVar14 = *(int *)(param_1 + 0x77c);
  if ((*(int *)(param_1 + 0x780) - iVar14 >> 2) * -0x33333333 != 0) {
    uStack_154 = 0;
    do {
      func_0x000295dc(&uStack_130,param_1,iVar14 + uStack_154 * 0x14 + 8);
      iVar14 = *(int *)(param_1 + 0x77c);
      iVar13 = iVar14 + uStack_154 * 0x14;
      piVar15 = *(int **)(iVar13 + 8);
      uStack_118 = uStack_130;
      uStack_114 = uStack_12c;
      piVar23 = *(int **)(iVar13 + 0xc);
      if (piVar15 != piVar23) {
code_r0x00060108:
        do {
          pfVar24 = pfStack_dc;
          piVar27 = piVar15 + 1;
          iVar14 = *piVar15;
          fVar31 = *(float *)(iVar14 + 0x9c) + *(float *)(iVar14 + 0x28);
          fVar32 = *(float *)(iVar14 + 0x2c) - *(float *)(iVar14 + 0xa0);
          if (pfStack_dc == pfStack_d8) {
            iVar13 = (int)pfStack_dc - (int)pfStack_e0 >> 2;
            uVar20 = iVar13 * -0x55555555;
            if (uVar20 == 0) {
              uVar21 = 1;
            }
            else {
              uVar21 = iVar13 * 0x55555556;
            }
            if ((uVar21 < 0x15555556) && (uVar20 <= uVar21)) {
              if (uVar21 != 0) {
                uStack_f8 = uVar21 * 0xc;
                if (0x80 < uStack_f8) goto code_r0x000603b4;
                pfVar8 = (float *)func_0x00027d94(&uStack_f8);
                goto code_r0x000603bc;
              }
              pfVar16 = (float *)0xc;
              pfVar8 = (float *)0x0;
              pfVar22 = (float *)0x0;
            }
            else {
              uStack_f8 = 0xfffffffc;
code_r0x000603b4:
              pfVar8 = (float *)func_0x00027d7c();
code_r0x000603bc:
              pfVar16 = pfVar8 + 3;
              pfVar22 = pfVar8 + (uStack_f8 / 0xc) * 3;
              uVar20 = ((int)pfVar24 - (int)pfStack_e0 >> 2) * -0x55555555;
              pfVar24 = pfStack_d8;
            }
            pfVar17 = pfVar8;
            uVar21 = uVar20;
            pfVar29 = pfStack_e0;
            if (0 < (int)uVar20) {
              do {
                fVar10 = pfVar29[1];
                fVar4 = *pfVar29;
                uVar21 = uVar21 - 1;
                pfVar17[2] = pfVar29[2];
                pfVar17[1] = fVar10;
                *pfVar17 = fVar4;
                pfVar17 = pfVar17 + 3;
                pfVar29 = pfVar29 + 3;
              } while (uVar21 != 0);
              pfVar16 = pfVar8 + uVar20 * 3 + 3;
              pfVar17 = pfVar8 + uVar20 * 3;
            }
            *pfVar17 = fVar31;
            pfVar17[2] = 0.0;
            pfVar17[1] = fVar32;
            if (pfStack_e0 != (float *)0x0) {
              if ((uint)(((int)pfVar24 - (int)pfStack_e0 >> 2) * 4) < 0x81) {
                func_0x00027d1c();
              }
              else {
                func_0x00027d28(pfStack_e0);
              }
            }
          }
          else {
            *pfStack_dc = fVar31;
            pfStack_dc[2] = 0.0;
            pfStack_dc[1] = fVar32;
            pfVar8 = pfStack_e0;
            pfVar16 = pfStack_dc + 3;
            pfVar22 = pfStack_d8;
          }
          pfStack_d8 = pfVar22;
          pfStack_dc = pfVar16;
          pfStack_e0 = pfVar8;
          fVar31 = (float)func_0x0002942c(*(undefined4 *)(iVar14 + 0x5c));
          pfVar24 = pfStack_40;
          fVar31 = fVar31 * afStack_120[0] * 1.35;
          if (pfStack_40 == pfStack_3c) {
            iVar13 = (int)pfStack_40 - (int)pfStack_44;
            uVar20 = iVar13 >> 2;
            if (uVar20 == 0) {
              uVar21 = 1;
            }
            else {
              uVar21 = uVar20 * 2;
            }
            if ((uVar21 < 0x40000000) && (uVar20 <= uVar21)) {
              if (uVar21 != 0) {
                uStack_f8 = uVar21 * 4;
                if (0x80 < uStack_f8) goto code_r0x0006042c;
                pfVar8 = (float *)func_0x00027d94(&uStack_f8);
                goto code_r0x00060434;
              }
              pfVar24 = (float *)0x0;
              pfVar22 = pfVar24;
              if (iVar13 != 0) goto code_r0x00060458;
              pfVar8 = (float *)0x0;
            }
            else {
              uStack_f8 = 0xfffffffc;
code_r0x0006042c:
              pfVar8 = (float *)func_0x00027d7c(uStack_f8);
code_r0x00060434:
              iVar13 = (int)pfVar24 - (int)pfStack_44;
              pfVar24 = (float *)((int)pfVar8 + (uStack_f8 & 0xfffffffc));
              pfVar22 = pfVar8;
              if (iVar13 != 0) {
code_r0x00060458:
                iVar5 = func_0x00027da0(pfVar22,pfStack_44,iVar13);
                pfVar8 = (float *)(iVar5 + iVar13);
              }
            }
            *pfVar8 = fVar31;
            if (pfStack_44 != (float *)0x0) {
              if ((uint)(((int)pfStack_3c - (int)pfStack_44 >> 2) * 4) < 0x81) {
                func_0x00027d1c();
              }
              else {
                func_0x00027d28(pfStack_44);
              }
            }
          }
          else {
            *pfStack_40 = fVar31;
            pfVar22 = pfStack_44;
            pfVar24 = pfStack_3c;
            pfVar8 = pfStack_40;
          }
          pfStack_3c = pfVar24;
          pfStack_44 = pfVar22;
          pfVar22 = pfStack_c4;
          pfVar24 = pfStack_dc;
          pfStack_40 = pfVar8 + 1;
          piVar15 = piVar27;
          if (acStack_128[0] == '\0') {
            if (pfStack_c4 == pfStack_c0) {
              iVar13 = (int)pfStack_c4 - (int)pfStack_c8 >> 2;
              uVar20 = iVar13 * -0x55555555;
              if (uVar20 == 0) {
                uVar21 = 1;
              }
              else {
                uVar21 = iVar13 * 0x55555556;
              }
              if ((uVar21 < 0x15555556) && (uVar20 <= uVar21)) {
                if (uVar21 != 0) {
                  uStack_f8 = uVar21 * 0xc;
                  if (0x80 < uStack_f8) goto code_r0x00060678;
                  pfVar8 = (float *)func_0x00027d94(&uStack_f8);
                  goto code_r0x00060680;
                }
                pfVar17 = (float *)0xc;
                pfVar8 = (float *)0x0;
                pfVar16 = (float *)0x0;
              }
              else {
                uStack_f8 = 0xfffffffc;
code_r0x00060678:
                pfVar8 = (float *)func_0x00027d7c(uStack_f8);
code_r0x00060680:
                pfVar17 = pfVar8 + 3;
                pfVar16 = pfVar8 + (uStack_f8 / 0xc) * 3;
                uVar20 = ((int)pfVar22 - (int)pfStack_c8 >> 2) * -0x55555555;
                pfVar22 = pfStack_c0;
              }
              pfVar29 = pfVar8;
              uVar21 = uVar20;
              pfVar2 = pfStack_c8;
              if (0 < (int)uVar20) {
                do {
                  fVar32 = pfVar2[1];
                  fVar31 = *pfVar2;
                  uVar21 = uVar21 - 1;
                  pfVar29[2] = pfVar2[2];
                  pfVar29[1] = fVar32;
                  *pfVar29 = fVar31;
                  pfVar29 = pfVar29 + 3;
                  pfVar2 = pfVar2 + 3;
                } while (uVar21 != 0);
                pfVar17 = pfVar8 + uVar20 * 3 + 3;
                pfVar29 = pfVar8 + uVar20 * 3;
              }
              fVar32 = pfVar24[-1];
              fVar31 = pfVar24[-2];
              *pfVar29 = pfVar24[-3];
              pfVar29[1] = fVar31;
              pfVar29[2] = fVar32;
              if (pfStack_c8 != (float *)0x0) {
                if ((uint)(((int)pfVar22 - (int)pfStack_c8 >> 2) * 4) < 0x81) {
                  func_0x00027d1c();
                }
                else {
                  func_0x00027d28(pfStack_c8);
                }
              }
            }
            else {
              fVar32 = pfStack_dc[-1];
              fVar31 = pfStack_dc[-2];
              *pfStack_c4 = pfStack_dc[-3];
              pfStack_c4[1] = fVar31;
              pfStack_c4[2] = fVar32;
              pfVar8 = pfStack_c8;
              pfVar17 = pfStack_c4 + 3;
              pfVar16 = pfStack_c0;
            }
            pfStack_c0 = pfVar16;
            pfStack_c4 = pfVar17;
            pfStack_c8 = pfVar8;
            puVar25 = puStack_b8;
            uVar21 = *(uint *)(iVar14 + 0x20);
            uVar20 = *(uint *)(iVar14 + 0x24);
            if (puStack_b8 == puStack_b4) {
              iVar14 = (int)puStack_b8 - (int)puStack_bc >> 2;
              uVar3 = iVar14 * -0x55555555;
              if (uVar3 == 0) {
                uVar11 = 1;
              }
              else {
                uVar11 = iVar14 * 0x55555556;
              }
              if ((uVar11 < 0x15555556) && (uVar3 <= uVar11)) {
                if (uVar11 != 0) {
                  uStack_f8 = uVar11 * 0xc;
                  if (0x80 < uStack_f8) goto code_r0x000606e4;
                  puVar9 = (uint *)func_0x00027d94(&uStack_f8);
                  goto code_r0x000606ec;
                }
                puVar18 = (uint *)0xc;
                puVar9 = (uint *)0x0;
                puVar30 = (uint *)0x0;
              }
              else {
                uStack_f8 = 0xfffffffc;
code_r0x000606e4:
                puVar9 = (uint *)func_0x00027d7c(uStack_f8);
code_r0x000606ec:
                puVar18 = puVar9 + 3;
                puVar30 = puVar9 + (uStack_f8 / 0xc) * 3;
                uVar3 = ((int)puVar25 - (int)puStack_bc >> 2) * -0x55555555;
                puVar25 = puStack_b4;
              }
              puVar6 = puVar9;
              uVar11 = uVar3;
              puVar1 = puStack_bc;
              if (0 < (int)uVar3) {
                do {
                  uVar12 = puVar1[1];
                  uVar7 = *puVar1;
                  uVar11 = uVar11 - 1;
                  puVar6[2] = puVar1[2];
                  puVar6[1] = uVar12;
                  *puVar6 = uVar7;
                  puVar6 = puVar6 + 3;
                  puVar1 = puVar1 + 3;
                } while (uVar11 != 0);
                puVar18 = puVar9 + uVar3 * 3 + 3;
                puVar6 = puVar9 + uVar3 * 3;
              }
              puVar6[1] = uVar20;
              *puVar6 = uVar21;
              puVar6[2] = 0;
              iVar14 = (int)puVar25 - (int)puStack_bc;
              goto joined_r0x0006053c;
            }
            *puStack_b8 = uVar21;
            puStack_b8[1] = uVar20;
            puStack_b8[2] = 0;
            puVar9 = puStack_bc;
            puVar18 = puStack_b8 + 3;
            puVar30 = puStack_b4;
          }
          else {
            pcStack_f0 = (char *)0x0;
            uStack_f8 = uStack_118;
            ppfStack_f4 = (float **)uStack_114;
            func_0x0005fd18(&pfStack_c8,&uStack_f8);
            puVar25 = puStack_b8;
            uVar21 = uStack_114;
            uVar20 = uStack_118;
            if (puStack_b8 != puStack_b4) {
              puVar25 = puStack_b8 + 3;
              *puStack_b8 = uStack_118;
              puStack_b8[1] = uStack_114;
              puStack_b8[2] = 0;
              puStack_b8 = puVar25;
              if (piVar23 == piVar27) break;
              goto code_r0x00060108;
            }
            iVar14 = (int)puStack_b8 - (int)puStack_bc >> 2;
            uVar3 = iVar14 * -0x55555555;
            if (uVar3 == 0) {
              uVar11 = 1;
            }
            else {
              uVar11 = iVar14 * 0x55555556;
            }
            if ((uVar11 < 0x15555556) && (uVar3 <= uVar11)) {
              if (uVar11 != 0) {
                uStack_f8 = uVar11 * 0xc;
                if (0x80 < uStack_f8) goto code_r0x00060850;
                puVar9 = (uint *)func_0x00027d94(&uStack_f8);
                goto code_r0x00060858;
              }
              puVar18 = (uint *)0xc;
              puVar9 = (uint *)0x0;
              puVar30 = (uint *)0x0;
            }
            else {
              uStack_f8 = 0xfffffffc;
code_r0x00060850:
              puVar9 = (uint *)func_0x00027d7c(uStack_f8);
code_r0x00060858:
              puVar18 = puVar9 + 3;
              puVar30 = puVar9 + (uStack_f8 / 0xc) * 3;
              uVar3 = ((int)puVar25 - (int)puStack_bc >> 2) * -0x55555555;
              puVar25 = puStack_b4;
            }
            puVar6 = puVar9;
            uVar11 = uVar3;
            puVar1 = puStack_bc;
            if (0 < (int)uVar3) {
              do {
                uVar12 = puVar1[1];
                uVar7 = *puVar1;
                uVar11 = uVar11 - 1;
                puVar6[2] = puVar1[2];
                puVar6[1] = uVar12;
                *puVar6 = uVar7;
                puVar6 = puVar6 + 3;
                puVar1 = puVar1 + 3;
              } while (uVar11 != 0);
              puVar18 = puVar9 + uVar3 * 3 + 3;
              puVar6 = puVar9 + uVar3 * 3;
            }
            *puVar6 = uVar20;
            puVar6[1] = uVar21;
            puVar6[2] = 0;
            iVar14 = (int)puVar25 - (int)puStack_bc;
joined_r0x0006053c:
            if (puStack_bc != (uint *)0x0) {
              if ((uint)((iVar14 >> 2) * 4) < 0x81) {
                func_0x00027d1c(puStack_bc);
              }
              else {
                func_0x00027d28();
              }
            }
          }
          puStack_b4 = puVar30;
          puStack_b8 = puVar18;
          puStack_bc = puVar9;
        } while (piVar23 != piVar27);
        iVar14 = *(int *)(param_1 + 0x77c);
      }
      uStack_154 = uStack_154 + 1;
    } while (uStack_154 < (uint)((*(int *)(param_1 + 0x780) - iVar14 >> 2) * -0x33333333));
  }
  puStack_14c = &uStack_130;
  func_0x000295dc(puStack_14c,param_1,param_1 + 0x770);
  pcStack_f0 = acStack_128;
  puStack_ec = &uStack_118;
  puVar19 = *(undefined4 **)(param_1 + 0x774);
  pfStack_e8 = afStack_120;
  uStack_f8 = param_1;
  ppfStack_f4 = &pfStack_e0;
  if (*(undefined4 **)(param_1 + 0x770) != puVar19) {
    puVar28 = *(undefined4 **)(param_1 + 0x770);
    do {
      puVar26 = puVar28 + 1;
      func_0x0005dca0(&uStack_f8,*puVar28);
      puVar28 = puVar26;
    } while (puVar19 != puVar26);
  }
  func_0x00028dfc(param_1 + 0xd98,&pfStack_e0);
  func_0x00029558(param_1 + 0xd98);
  func_0x00028b5c(0,0,0,1);
  func_0x0002885c(0x4000);
  func_0x00028dfc(param_1 + 0xe54,&pfStack_e0);
  func_0x00029558(param_1 + 0xe54);
  func_0x00028b5c(1,1,1,1);
  func_0x00028838(0x8d40,0);
  func_0x00028844(0,0,*(undefined4 *)(param_1 + 0x10),*(undefined4 *)(param_1 + 0x14));
  func_0x00028dd8(&pfStack_e0);
  return;
}

