
/* WARNING: Globals starting with '_' overlap smaller symbols at the same address */

void _ZN8SPhysics18SPColourDropletApp9updateSPHEv(float *param_1)

{
  uint uVar1;
  byte bVar2;
  undefined **ppuVar3;
  undefined **ppuVar4;
  int *piVar5;
  undefined4 uVar6;
  int iVar7;
  float *pfVar8;
  float *pfVar9;
  float *pfVar10;
  undefined1 *puVar11;
  float fVar12;
  float *pfVar13;
  undefined **ppuVar14;
  float *pfVar15;
  undefined **ppuVar16;
  int iVar17;
  uint uVar18;
  float *pfVar19;
  undefined *puVar20;
  int *piVar21;
  float *pfVar22;
  double *pdVar23;
  int iVar24;
  bool bVar25;
  uint in_fpscr;
  uint uVar26;
  uint uVar27;
  float extraout_s5;
  float extraout_s5_00;
  float fVar28;
  float fVar29;
  float fVar30;
  float fVar31;
  float fVar32;
  float fVar33;
  double unaff_d8;
  float fVar34;
  undefined8 unaff_d14;
  ulonglong unaff_d15;
  float fVar35;
  double dVar36;
  double dVar37;
  double dVar38;
  double dVar39;
  float *pfStack_16c;
  float *pfStack_168;
  float *pfStack_164;
  float *pfStack_15c;
  float *pfStack_158;
  float *pfStack_150;
  float *pfStack_148;
  float fStack_138;
  float fStack_134;
  float fStack_130;
  float fStack_12c;
  float fStack_128;
  undefined1 auStack_124 [36];
  float fStack_100;
  float fStack_fc;
  undefined4 uStack_80;
  undefined1 auStack_7c [16];
  int iStack_6c;
  
  pfVar15 = param_1 + 0x14;
  iStack_6c = ___stack_chk_guard;
  func_0x00029804(pfVar15);
  func_0x00029804(pfVar15);
  if (param_1[0xc] != 4.2039e-45) {
    func_0x00029804(param_1 + 0xf8);
    func_0x00029804(param_1 + 0xf8);
  }
  piVar5 = (int *)param_1[0x1dc];
  if (param_1[0xb] == 1.4013e-45) {
    piVar21 = (int *)param_1[0x1dd];
    if ((uint)((int)piVar21 - (int)piVar5) < 0x50) {
      fVar35 = (float)VectorSignedToFloat(param_1[4],(byte)(in_fpscr >> 0x16) & 3);
      fVar30 = (float)VectorSignedToFloat(param_1[5],(byte)(in_fpscr >> 0x16) & 3);
      fStack_138 = (param_1[0x26b] / fVar35) * param_1[0x242];
      fStack_134 = (param_1[0x26c] / fVar30) * param_1[0x243];
      if (piVar5 != piVar21) {
        iVar17 = *piVar5;
        fVar35 = param_1[0x242] * 0.1;
        fVar30 = fStack_134 - *(float *)(iVar17 + 0x2c);
        fVar12 = fStack_138 - *(float *)(iVar17 + 0x28);
        unaff_d8 = (double)CONCAT44(fVar12,fVar30);
        fVar30 = SQRT(fVar30 * fVar30 + fVar12 * fVar12);
        uVar18 = in_fpscr & 0xfffffff | (uint)(fVar30 < fVar35) << 0x1f |
                 (uint)(fVar30 == fVar35) << 0x1e;
        in_fpscr = uVar18 | (uint)(NAN(fVar30) || NAN(fVar35)) << 0x1c;
        bVar2 = (byte)(uVar18 >> 0x18);
        if (!(bool)(bVar2 >> 6 & 1) && bVar2 >> 7 == ((byte)(in_fpscr >> 0x1c) & 1)) {
          fStack_138 = *(float *)(iVar17 + 0x28);
          fStack_134 = *(float *)(iVar17 + 0x2c);
          fStack_130 = fStack_138;
          fStack_12c = fStack_134;
        }
      }
      func_0x00029834(param_1,&fStack_138);
      piVar5 = (int *)param_1[0x1dc];
      piVar21 = (int *)param_1[0x1dd];
    }
  }
  else {
    piVar21 = (int *)param_1[0x1dd];
  }
  pfStack_148 = &fStack_138;
  if (piVar5 != piVar21) {
    param_1[0x1dd] = (float)piVar5;
  }
  fStack_138 = 0.0;
  func_0x000296d8(pfVar15,pfStack_148,param_1 + 0x1dc);
  puVar11 = (undefined1 *)param_1[0x1dc];
  pfVar22 = (float *)param_1[0x1dd];
  iVar17 = (int)pfVar22 - (int)puVar11 >> 4;
  pfStack_164 = (float *)((int)pfVar22 - (int)puVar11 >> 2);
  pfVar19 = (float *)puVar11;
  pfVar13 = pfStack_164;
  pfVar8 = (float *)puVar11;
  if (iVar17 < 1) {
code_r0x00066f24:
    if (pfVar13 == (float *)0x2) {
code_r0x00067e28:
      in_fpscr = in_fpscr & 0xfffffff | (uint)(*(float *)((int)*pfVar19 + 0x68) < 0.0) << 0x1f |
                 (uint)(*(float *)((int)*pfVar19 + 0x68) == 0.0) << 0x1e;
      if (!(bool)((byte)(in_fpscr >> 0x1e) & 1)) goto code_r0x00066f4c;
      pfVar19 = pfVar19 + 1;
    }
    else {
      if (pfVar13 == (float *)0x3) {
        in_fpscr = in_fpscr & 0xfffffff | (uint)(*(float *)((int)*pfVar19 + 0x68) < 0.0) << 0x1f |
                   (uint)(*(float *)((int)*pfVar19 + 0x68) == 0.0) << 0x1e;
        if (!(bool)((byte)(in_fpscr >> 0x1e) & 1)) goto code_r0x00066f4c;
        pfVar19 = pfVar19 + 1;
        goto code_r0x00067e28;
      }
      if (pfVar13 != (float *)0x1) goto code_r0x00066fa0;
    }
    in_fpscr = in_fpscr & 0xfffffff | (uint)(*(float *)((int)*pfVar19 + 0x68) < 0.0) << 0x1f |
               (uint)(*(float *)((int)*pfVar19 + 0x68) == 0.0) << 0x1e;
    if ((bool)((byte)(in_fpscr >> 0x1e) & 1)) goto code_r0x00066fa0;
  }
  else {
    uVar18 = in_fpscr & 0xfffffff;
    in_fpscr = uVar18 | (uint)(*(float *)((int)*(float *)puVar11 + 0x68) < 0.0) << 0x1f |
               (uint)(*(float *)((int)*(float *)puVar11 + 0x68) == 0.0) << 0x1e;
    if ((bool)((byte)(in_fpscr >> 0x1e) & 1)) {
      in_fpscr = uVar18 | (uint)(*(float *)((int)*(float *)((int)puVar11 + 4) + 0x68) < 0.0) << 0x1f
                 | (uint)(*(float *)((int)*(float *)((int)puVar11 + 4) + 0x68) == 0.0) << 0x1e;
      if ((bool)((byte)(in_fpscr >> 0x1e) & 1)) {
        in_fpscr = uVar18 | (uint)(*(float *)((int)*(float *)((int)puVar11 + 8) + 0x68) < 0.0) <<
                            0x1f |
                   (uint)(*(float *)((int)*(float *)((int)puVar11 + 8) + 0x68) == 0.0) << 0x1e;
        if ((bool)((byte)(in_fpscr >> 0x1e) & 1)) {
          in_fpscr = uVar18 | (uint)(*(float *)((int)*(float *)((int)puVar11 + 0xc) + 0x68) < 0.0)
                              << 0x1f |
                     (uint)(*(float *)((int)*(float *)((int)puVar11 + 0xc) + 0x68) == 0.0) << 0x1e;
          bVar2 = (byte)(in_fpscr >> 0x18);
          pfVar13 = (float *)puVar11;
          while ((bool)(bVar2 >> 6 & 1)) {
            iVar17 = iVar17 + -1;
            pfVar19 = pfVar13 + 4;
            if (iVar17 == 0) {
              pfVar13 = (float *)((int)pfVar22 - (int)pfVar19 >> 2);
              goto code_r0x00066f24;
            }
            uVar18 = in_fpscr & 0xfffffff;
            in_fpscr = uVar18 | (uint)(*(float *)((int)pfVar13[4] + 0x68) < 0.0) << 0x1f |
                       (uint)(*(float *)((int)pfVar13[4] + 0x68) == 0.0) << 0x1e;
            if (!(bool)((byte)(in_fpscr >> 0x1e) & 1)) goto code_r0x00066f4c;
            fVar35 = *(float *)((int)pfVar13[5] + 0x68);
            unaff_d14 = CONCAT44((int)((ulonglong)unaff_d14 >> 0x20),fVar35);
            in_fpscr = uVar18 | (uint)(fVar35 < 0.0) << 0x1f | (uint)(fVar35 == 0.0) << 0x1e;
            if (!(bool)((byte)(in_fpscr >> 0x1e) & 1)) {
              pfVar19 = pfVar13 + 5;
              goto code_r0x00066f4c;
            }
            fVar30 = *(float *)((int)pfVar13[6] + 0x68);
            unaff_d14 = CONCAT44(fVar30,fVar35);
            in_fpscr = uVar18 | (uint)(fVar30 < 0.0) << 0x1f | (uint)(fVar30 == 0.0) << 0x1e;
            if (!(bool)((byte)(in_fpscr >> 0x1e) & 1)) goto code_r0x00067dcc;
            fVar35 = *(float *)((int)pfVar13[7] + 0x68);
            unaff_d15 = (ulonglong)(uint)fVar35;
            in_fpscr = uVar18 | (uint)(fVar35 < 0.0) << 0x1f | (uint)(fVar35 == 0.0) << 0x1e;
            pfVar13 = pfVar19;
            bVar2 = (byte)(in_fpscr >> 0x18);
          }
          pfVar19 = pfVar13 + 3;
        }
        else {
code_r0x00067dcc:
          pfVar19 = pfVar19 + 2;
        }
      }
      else {
        pfVar19 = (float *)((int)puVar11 + 4);
      }
    }
  }
code_r0x00066f4c:
  if (pfVar22 != pfVar19) {
    pfVar8 = pfVar19;
    pfVar13 = pfVar19 + 1;
    if (pfVar22 != pfVar19 + 1) {
      do {
        pfVar9 = pfVar13 + 1;
        fVar35 = *(float *)((int)*pfVar13 + 0x68);
        unaff_d15 = CONCAT44(fVar35,(int)unaff_d15);
        in_fpscr = in_fpscr & 0xfffffff | (uint)(fVar35 < 0.0) << 0x1f |
                   (uint)(fVar35 == 0.0) << 0x1e;
        pfVar19 = pfVar8;
        if ((bool)((byte)(in_fpscr >> 0x1e) & 1)) {
          pfVar19 = pfVar8 + 1;
          *pfVar8 = *pfVar13;
        }
        pfVar8 = pfVar19;
        pfVar13 = pfVar9;
      } while (pfVar22 != pfVar9);
      if (pfVar19 == (float *)param_1[0x1dd]) {
        puVar11 = (undefined1 *)((int)pfVar19 - (int)param_1[0x1dc] >> 2);
        pfVar8 = (float *)param_1[0x1dc];
        pfStack_164 = (float *)puVar11;
        goto code_r0x00066fa0;
      }
    }
    param_1[0x1dd] = (float)pfVar19;
    pfVar8 = (float *)param_1[0x1dc];
    pfStack_164 = (float *)((int)pfVar19 - (int)param_1[0x1dc] >> 2);
  }
code_r0x00066fa0:
  pfVar19 = param_1 + 0x4bc;
  fVar35 = *pfVar19;
  if (pfStack_164 != (float *)0x0) {
    pfStack_16c = param_1 + 0x242;
    unaff_d8 = 0.024166667461395314;
    pfVar13 = (float *)0x0;
    fVar30 = extraout_s5;
    while( true ) {
      fVar12 = pfVar8[(int)pfVar13];
      fVar33 = param_1[0x26b];
      fVar32 = param_1[0x26c];
      fVar29 = (float)VectorSignedToFloat(param_1[4],(byte)(in_fpscr >> 0x16) & 3);
      fVar28 = (float)VectorSignedToFloat(param_1[5],(byte)(in_fpscr >> 0x16) & 3);
      unaff_d15 = *(ulonglong *)pfStack_16c;
      uVar18 = in_fpscr & 0xfffffff | (uint)(*(float *)((int)fVar12 + 0x5c) < 1.0) << 0x1f;
      bVar25 = SUB41(uVar18 >> 0x1f,0);
      fVar34 = (fVar33 / fVar29) * (float)unaff_d15 - *(float *)((int)fVar12 + 0x28);
      if (!bVar25) {
        fVar30 = fVar32;
      }
      fVar32 = (fVar32 / fVar28) * (float)(unaff_d15 >> 0x20) - *(float *)((int)fVar12 + 0x2c);
      if (bVar25) {
        fVar30 = *(float *)((int)fVar12 + 0x5c) + 0.024166668;
        uVar18 = in_fpscr & 0xfffffff | (uint)(fVar30 == 1.0) << 0x1e |
                 (uint)(1.0 <= fVar30) << 0x1d;
        bVar2 = (byte)(uVar18 >> 0x18);
        if ((bool)(bVar2 >> 5 & 1) && !(bool)(bVar2 >> 6)) {
          fVar30 = 1.0;
        }
        *(float *)((int)fVar12 + 0x5c) = fVar30;
        fVar30 = (float)func_0x00029480(fVar30);
        *(float *)((int)fVar12 + 0x60) =
             *(float *)((int)fVar12 + 0x60) +
             fVar30 * (param_1[0x228] - *(float *)((int)fVar12 + 0x60));
        *(float *)((int)fVar12 + 0x70) =
             *(float *)((int)fVar12 + 0x70) +
             fVar30 * (param_1[0x22c] - *(float *)((int)fVar12 + 0x70));
        fVar33 = param_1[0x22f] - *(float *)((int)fVar12 + 0x7c);
        fVar28 = param_1[0x22e] - *(float *)((int)fVar12 + 0x78);
        unaff_d14 = CONCAT44(fVar28,fVar33);
        *(float *)((int)fVar12 + 0x7c) = *(float *)((int)fVar12 + 0x7c) + fVar30 * fVar33;
        *(float *)((int)fVar12 + 0x78) = *(float *)((int)fVar12 + 0x78) + fVar30 * fVar28;
        unaff_d15 = *(ulonglong *)pfStack_16c;
        fVar33 = param_1[0x26b];
        fVar29 = (float)VectorSignedToFloat(param_1[4],(byte)(uVar18 >> 0x16) & 3);
        fVar30 = param_1[0x26c];
        fVar28 = (float)VectorSignedToFloat(param_1[5],(byte)(uVar18 >> 0x16) & 3);
      }
      fVar28 = *(float *)((int)fVar12 + 0x3c) +
               ((fVar30 - param_1[0x26a]) / fVar28) * (float)(unaff_d15 >> 0x20);
      fVar33 = *(float *)((int)fVar12 + 0x38) +
               ((fVar33 - param_1[0x269]) / fVar29) * (float)unaff_d15;
      *(float *)((int)fVar12 + 0x3c) = fVar28;
      *(float *)((int)fVar12 + 0x38) = fVar33;
      in_fpscr = uVar18 & 0xfffffff |
                 (uint)(param_1[0x4b4] < SQRT(fVar32 * fVar32 + fVar34 * fVar34)) << 0x1f;
      if (SUB41(in_fpscr >> 0x1f,0)) {
        *(float *)((int)fVar12 + 0x38) = fVar33 + fVar34 * (2.5 - fVar35);
        *(float *)((int)fVar12 + 0x3c) = fVar28 + fVar32 * (2.5 - fVar35);
        fVar33 = *pfVar19;
        *(float *)((int)fVar12 + 0x34) =
             (1.6 - fVar33) * (float)((ulonglong)*(undefined8 *)((int)fVar12 + 0x30) >> 0x20);
        *(float *)((int)fVar12 + 0x30) = (float)*(undefined8 *)((int)fVar12 + 0x30) * (1.6 - fVar33)
        ;
      }
      pfVar13 = (float *)((int)pfVar13 + 1);
      puVar11 = (undefined1 *)pfStack_164;
      if (pfVar13 == pfStack_164) break;
      pfVar8 = (float *)param_1[0x1dc];
    }
  }
  pfVar8 = (float *)param_1[0x1df];
  pfStack_164 = (float *)0x0;
  pfStack_150 = (float *)0xccccccd;
  fVar35 = (float)unaff_d15;
  ppuVar16 = (undefined **)((int)param_1[0x1e0] - (int)pfVar8);
  ppuVar3 = (undefined **)(((int)ppuVar16 >> 2) * -0x33333333);
  ppuVar14 = ppuVar3;
  pfVar13 = pfVar8;
  pfVar22 = pfVar19;
  pfStack_158 = pfVar19;
  if (pfVar8 == (float *)param_1[0x1e0]) goto code_r0x00067500;
  do {
    pfVar13 = param_1;
    ppuVar16 = ppuVar3;
    if ((int)ppuVar3 < (int)pfStack_164) {
      pfVar22 = (float *)0x8ab8;
      param_1 = (float *)0x1bf0;
      func_0x00028370(6,&UNK_000698bc,&UNK_00070780);
      *(undefined1 *)((int)pfVar13 + 0x1e) = 1;
      goto code_r0x00067500;
    }
    puVar11 = (undefined1 *)*pfVar8;
    pfStack_168 = pfVar8 + 2;
    fVar30 = pfVar8[2];
    fVar12 = pfVar8[3];
    puVar20 = (undefined *)((int)fVar12 - (int)fVar30 >> 2);
    param_1 = pfVar13;
    if ((float *)puVar11 != (float *)0x0) {
      if ((float *)puVar11 == (float *)0x1) {
        unaff_d8 = (double)CONCAT44(pfVar8[1],SUB84(unaff_d8,0));
        fVar35 = pfVar8[1] + 0.015;
        in_fpscr = in_fpscr & 0xfffffff | (uint)(fVar35 < 1.0) << 0x1f |
                   (uint)(fVar35 == 1.0) << 0x1e | (uint)(1.0 <= fVar35) << 0x1d;
        bVar2 = (byte)(in_fpscr >> 0x18);
        if ((bool)(bVar2 >> 5 & 1) && !(bool)(bVar2 >> 6 & 1)) {
          fVar35 = 1.0;
        }
        pfVar8[1] = fVar35;
        if (puVar20 != (undefined *)0x0) {
          ppuVar14 = (undefined **)0x0;
          pfVar22 = pfVar13 + 0x247;
          fVar12 = 0.0;
          unaff_d8 = 0.0166666666666667;
          ppuVar16 = (undefined **)0x0;
          pfVar19 = pfVar13 + 0x242;
          unaff_d14 = 0x3f8666663e19999a;
          pfStack_16c = pfVar13 + 0x243;
          pfStack_15c = pfStack_158;
          goto code_r0x00067964;
        }
      }
code_r0x000674dc:
      pfVar9 = pfVar8;
      if (fVar30 == fVar12) goto code_r0x00067b24;
      pfVar9 = (float *)pfVar13[0x1e0];
      pfVar8 = pfVar8 + 5;
      pfVar22 = pfStack_158;
      goto code_r0x000674ec;
    }
    ppuVar4 = ppuVar3;
    if (puVar20 < (undefined *)0x14) {
      pfStack_15c = &fStack_128;
      unaff_d8 = 4.656612875245797e-10;
      unaff_d14 = 0x431f27aa40c90fdb;
      iVar17 = 2;
      func_0x00061ff8(pfStack_15c,pfVar13 + 0x1e2);
      do {
        uVar6 = func_0x00027f38();
        dVar36 = (double)VectorSignedToFloat(uVar6,(byte)(in_fpscr >> 0x16) & 3);
        fVar35 = (float)(dVar36 * 4.656612875245797e-10);
        uVar6 = func_0x00027f38();
        dVar36 = (double)VectorSignedToFloat(uVar6,(byte)(in_fpscr >> 0x16) & 3);
        iVar24 = (int)(fVar35 * 6.2831855 * 159.15494);
        fVar30 = (float)VectorSignedToFloat(iVar24,(byte)(in_fpscr >> 0x16) & 3);
        fVar30 = fVar35 * 6.2831855 - fVar30 * 0.0062831854;
        if (iVar24 == 999) {
          iVar7 = 0;
        }
        else {
          iVar7 = iVar24 + 1;
        }
        pdVar23 = (double *)(&UNK_00072a78 + iVar7 * 8);
        dVar37 = *pdVar23;
        if (iVar24 != 999) {
          pdVar23 = (double *)(iVar24 + 1);
        }
        if (iVar24 == 999) {
          pdVar23 = (double *)0x0;
        }
        dVar38 = (double)fVar30;
        uStack_80 = 0;
        dVar39 = (double)(1.0 - fVar30);
        fVar30 = SQRT((float)(double)(((ulonglong)(dVar36 * 4.656612875245797e-10) &
                                      0xffffffff00000000) +
                                     (ulonglong)
                                     ((SUB84(dVar36 * 4.656612875245797e-10,0) >> 1) + 0x1fc00000)))
                 * pfVar13[0x240] * 0.1;
        fStack_100 = pfVar13[0x4b1] +
                     (float)(dVar39 * *(double *)(&UNK_00072a78 + iVar24 * 8) + dVar38 * dVar37) *
                     fVar30;
        fStack_fc = pfVar13[0x4b2] +
                    (float)(dVar39 * *(double *)(&UNK_00070b38 + iVar24 * 8) +
                           dVar38 * *(double *)(&UNK_00070b38 + (int)pdVar23 * 8)) * fVar30;
        fStack_138 = (float)func_0x00029828(pfVar13 + 0xf8,&fStack_128);
        if (fStack_138 != 0.0) {
          func_0x0003d728(pfStack_168,pfStack_148);
        }
        iVar17 = iVar17 + -1;
      } while (iVar17 != 0);
      puVar11 = _ZTVN8SPhysics20SPSmoothedParticle2DIfEE;
      fStack_128 = 6.77915e-40;
      func_0x00033b9c(auStack_7c);
      fStack_128 = 6.77365e-40;
      func_0x0003d214(auStack_124);
      fVar30 = pfVar8[2];
      fVar12 = pfVar8[3];
      puVar20 = (undefined *)((int)fVar12 - (int)fVar30 >> 2);
      ppuVar14 = (undefined **)0x0;
      ppuVar4 = &__DT_PLTGOT;
    }
    if (puVar20 == (undefined *)0x0) {
      ppuVar16 = (undefined **)0x0;
      unaff_d8 = (double)CONCAT44(pfVar8[1],SUB84(unaff_d8,0));
      in_fpscr = in_fpscr & 0xfffffff | (uint)(pfVar8[1] < 1.0) << 0x1f;
      if (SUB41(in_fpscr >> 0x1f,0)) {
        pfVar8[1] = 1.0;
        ppuVar16 = ppuVar4;
        goto code_r0x000674dc;
      }
code_r0x00067568:
      fVar30 = (float)((ulonglong)unaff_d8 >> 0x20) + 0.02;
      unaff_d8 = (double)CONCAT44(fVar30,SUB84(unaff_d8,0));
      pfVar8[1] = fVar30;
    }
    else {
      uVar18 = 0;
      puVar11 = (undefined1 *)(pfVar13 + 0x4b2);
      ppuVar14 = (undefined **)0x1;
      do {
        fVar12 = pfVar8[1];
        fVar35 = 1.0;
        iVar17 = *(int *)((int)fVar30 + uVar18 * 4);
        fVar30 = 1.0;
        uVar26 = in_fpscr & 0xfffffff | (uint)(fVar12 < 1.0) << 0x1f;
        fVar33 = *(float *)(iVar17 + 0x5c);
        unaff_d14 = CONCAT44(fVar12,fVar33);
        if (SUB41(uVar26 >> 0x1f,0)) {
          in_fpscr = in_fpscr & 0xfffffff | (uint)(fVar33 < 0.5) << 0x1f;
          if (SUB41(in_fpscr >> 0x1f,0)) {
            ppuVar14 = (undefined **)0x0;
            *(float *)(iVar17 + 0x5c) = fVar33 + 0.021666666;
            fVar12 = (float)func_0x00029480(fVar33 + 0.021666666);
            fVar33 = *(float *)(iVar17 + 0x28);
            fVar30 = *(float *)(iVar17 + 0x2c);
            fVar29 = pfVar13[0x228] - *(float *)(iVar17 + 0x60);
            fVar28 = *(float *)(iVar17 + 0x60) + fVar12 * fVar29;
            *(float *)(iVar17 + 0x60) = fVar28;
            fVar35 = *(float *)(iVar17 + 0x70) +
                     fVar12 * (pfVar13[0x22c] - *(float *)(iVar17 + 0x70));
            *(float *)(iVar17 + 0x70) = fVar35;
            fVar32 = pfVar13[0x22f];
            unaff_d14 = CONCAT44(fVar29,fVar32);
            fVar29 = pfVar13[0x22e];
            *(undefined4 *)(iVar17 + 0x40) = *(undefined4 *)(iVar17 + 0x5c);
            *(float *)(iVar17 + 0x44) = fVar28;
            *(float *)(iVar17 + 0x7c) =
                 *(float *)(iVar17 + 0x7c) + fVar12 * (fVar32 - *(float *)(iVar17 + 0x7c));
            *(float *)(iVar17 + 0x78) =
                 *(float *)(iVar17 + 0x78) + fVar12 * (fVar29 - *(float *)(iVar17 + 0x78));
          }
          else {
            fVar33 = *(float *)(iVar17 + 0x28);
            fVar30 = *(float *)(iVar17 + 0x2c);
          }
        }
        else {
          fVar28 = *(float *)(iVar17 + 0x68);
          fVar12 = 2.0 - fVar12;
          fVar33 = (float)func_0x00029480(fVar12);
          fVar32 = *(float *)(iVar17 + 0x28);
          fVar29 = *(float *)(iVar17 + 0x2c);
          unaff_d14 = CONCAT44(fVar32,fVar29);
          uVar26 = uVar26 & 0xfffffff;
          uVar27 = uVar26 | (uint)(fVar12 == 0.2) << 0x1e | (uint)(0.2 <= fVar12) << 0x1d;
          bVar2 = (byte)(uVar27 >> 0x18);
          *(float *)(iVar17 + 0x5c) = fVar33 * fVar28;
          if (!(bool)(bVar2 >> 5 & 1) || (bool)(bVar2 >> 6)) {
code_r0x0006743c:
            fVar30 = 0.0;
          }
          else {
            uVar1 = uVar26 | (uint)(fVar12 < 1.0) << 0x1f;
            uVar27 = uVar1 | (uint)NAN(fVar12) << 0x1c;
            if ((byte)(uVar1 >> 0x1f) != ((byte)(uVar27 >> 0x1c) & 1)) {
              fVar33 = (fVar12 + -0.2) * 1.25;
              uVar27 = uVar26 | (uint)(fVar33 == 0.0) << 0x1e | (uint)(0.0 <= fVar33) << 0x1d;
              bVar2 = (byte)(uVar27 >> 0x18);
              if (!(bool)(bVar2 >> 5 & 1) || (bool)(bVar2 >> 6)) goto code_r0x0006743c;
              uVar26 = uVar26 | (uint)(fVar33 < 1.0) << 0x1f;
              uVar27 = uVar26 | (uint)NAN(fVar33) << 0x1c;
              if ((byte)(uVar26 >> 0x1f) != ((byte)(uVar27 >> 0x1c) & 1)) {
                dVar36 = (double)func_0x0002981c(SUB84((double)fVar33 * 3.141592653589793,0),
                                                 (int)((ulonglong)
                                                       ((double)fVar33 * 3.141592653589793) >> 0x20)
                                                );
                fVar30 = (float)((1.0 - dVar36) * 0.5);
              }
            }
          }
          fVar30 = (float)func_0x00029810(fVar30);
          fVar33 = pfVar13[0x4b1];
          in_fpscr = uVar27 & 0xfffffff | (uint)(fVar12 == 0.15) << 0x1e |
                     (uint)(0.15 <= fVar12) << 0x1d;
          fVar33 = fVar33 + fVar30 * (fVar32 - fVar33);
          bVar2 = (byte)(in_fpscr >> 0x18);
          fVar30 = *(float *)puVar11 + fVar30 * (fVar29 - *(float *)puVar11);
          *(float *)(iVar17 + 0x28) = fVar33;
          *(float *)(iVar17 + 0x2c) = fVar30;
          if (!(bool)(bVar2 >> 5 & 1) || (bool)(bVar2 >> 6)) {
            ppuVar16 = (undefined **)0x1;
            goto code_r0x00067554;
          }
        }
        fVar30 = *(float *)puVar11 - fVar30;
        fVar33 = pfVar13[0x4b1] - fVar33;
        fVar12 = pfVar13[0x242] * 0.16;
        fVar28 = SQRT(fVar30 * fVar30 + fVar33 * fVar33);
        uVar26 = in_fpscr & 0xfffffff | (uint)(fVar28 < fVar12) << 0x1f |
                 (uint)(fVar28 == fVar12) << 0x1e;
        in_fpscr = uVar26 | (uint)(NAN(fVar28) || NAN(fVar12)) << 0x1c;
        bVar2 = (byte)(uVar26 >> 0x18);
        if (!(bool)(bVar2 >> 6 & 1) && bVar2 >> 7 == ((byte)(in_fpscr >> 0x1c) & 1)) {
          fVar35 = *(float *)(iVar17 + 0x3c) + fVar30 * (1.75 - *pfStack_158);
          *(float *)(iVar17 + 0x38) = *(float *)(iVar17 + 0x38) + fVar33 * (1.75 - *pfStack_158);
          *(float *)(iVar17 + 0x3c) = fVar35;
          fVar30 = *pfStack_158;
          fVar12 = (1.9 - fVar30) * *(float *)(iVar17 + 0x34);
          unaff_d14 = CONCAT44(*(float *)(iVar17 + 0x30),fVar12);
          *(float *)(iVar17 + 0x34) = fVar12;
          *(float *)(iVar17 + 0x30) = *(float *)(iVar17 + 0x30) * (1.9 - fVar30);
        }
        fVar30 = pfVar8[2];
        uVar18 = uVar18 + 1;
      } while (uVar18 < (uint)((int)pfVar8[3] - (int)fVar30 >> 2));
      ppuVar16 = (undefined **)0x0;
code_r0x00067554:
      unaff_d8 = (double)CONCAT44(pfVar8[1],0x55555562);
      in_fpscr = in_fpscr & 0xfffffff | (uint)(pfVar8[1] < 1.0) << 0x1f;
      if (!SUB41(in_fpscr >> 0x1f,0)) goto code_r0x00067568;
      if (ppuVar14 != (undefined **)0x0) {
        fVar30 = pfVar8[2];
        pfVar8[1] = 1.0;
        puVar11 = (undefined1 *)pfVar8[3];
        ppuVar14 = (undefined **)((int)puVar11 - (int)fVar30);
        iVar17 = (int)ppuVar14 >> 2;
        if (iVar17 != 0) {
          iVar24 = 0;
          do {
            iVar7 = *(int *)((int)fVar30 + iVar24);
            iVar24 = iVar24 + 4;
            ppuVar14 = (undefined **)0x0;
            fStack_138 = *(float *)(iVar7 + 0x28);
            fStack_134 = *(float *)(iVar7 + 0x2c);
            *(float *)(iVar7 + 0x40) = fStack_138;
            *(float *)(iVar7 + 0x44) = fStack_134;
            puVar11 = *(undefined1 **)(iVar7 + 0x5c);
            *(undefined4 *)(iVar7 + 0x60) = 0;
            *(undefined1 **)(iVar7 + 0x68) = puVar11;
          } while (iVar24 != iVar17 * 4);
        }
      }
    }
joined_r0x00067578:
    pfVar9 = pfVar8;
    if (ppuVar16 == (undefined **)0x0) {
      fVar30 = pfVar8[2];
      if (fVar30 == pfVar8[3]) {
code_r0x00067b24:
        if (fVar30 != 0.0) {
          ppuVar16 = (undefined **)pfVar9[4];
          if (((int)ppuVar16 - (int)fVar30 & 0xfffffffcU) < 0x81) {
            func_0x00027d1c();
          }
          else {
            func_0x00027d28(fVar30);
          }
        }
        pfStack_16c = (float *)pfVar13[0x1e0];
        pfVar19 = pfVar9 + 5;
        pfVar22 = pfStack_168;
        pfVar8 = pfVar9;
        if (pfVar19 != pfStack_16c) {
          do {
            pfVar10 = pfVar19 + 5;
            pfVar19[-5] = *pfVar19;
            pfVar19[-4] = pfVar19[1];
            *(float *)((int)pfStack_168 + (int)pfVar22 + (-8 - (int)pfVar9)) = pfVar22[5];
            *(float *)((int)pfStack_168 + (int)pfVar22 + (-4 - (int)pfVar9)) = pfVar22[6];
            pfVar22[2] = pfVar19[4];
            pfVar22[5] = 0.0;
            pfVar22[7] = 0.0;
            pfVar22[6] = 0.0;
            pfVar19 = pfVar10;
            pfVar22 = pfVar22 + 5;
            pfStack_15c = pfVar13;
          } while (pfStack_16c != pfVar10);
          goto code_r0x00067624;
        }
        goto code_r0x00067650;
      }
      pfVar9 = (float *)pfVar13[0x1e0];
      pfVar8 = pfVar8 + 5;
      pfVar22 = pfStack_158;
      param_1 = pfVar13;
    }
    else {
      if ((int)*pfVar8 < 1) {
        puVar11 = (undefined1 *)0x3;
        func_0x00028520(pfVar13 + 0xf8,pfStack_168);
        pfVar13[0xc] = 4.2039e-45;
      }
      else {
        func_0x00028520(pfVar15,pfStack_168);
      }
      func_0x00033b9c(pfStack_168);
      pfStack_16c = (float *)pfVar13[0x1e0];
      pfVar19 = pfVar8 + 5;
      pfVar22 = pfStack_168;
      if (pfVar19 != pfStack_16c) {
        do {
          pfVar9 = pfVar19 + 5;
          pfVar19[-5] = *pfVar19;
          pfVar19[-4] = pfVar19[1];
          *(float *)((int)pfStack_168 + (int)pfVar22 + (-8 - (int)pfVar8)) = pfVar22[5];
          *(float *)((int)pfStack_168 + (int)pfVar22 + (-4 - (int)pfVar8)) = pfVar22[6];
          pfVar22[2] = pfVar19[4];
          pfVar22[5] = 0.0;
          pfVar22[7] = 0.0;
          pfVar22[6] = 0.0;
          pfVar19 = pfVar9;
          pfVar22 = pfVar22 + 5;
          pfStack_15c = pfVar13;
        } while (pfStack_16c != pfVar9);
code_r0x00067624:
        ppuVar14 = (undefined **)(((uint)((int)pfStack_16c - (int)(pfVar8 + 10)) >> 2) * 0xccccccd);
        ppuVar16 = (undefined **)((uint)ppuVar14 & 0x3fffffff);
        pfVar9 = pfVar8 + ((int)ppuVar16 + 1U) * 5;
        puVar11 = (undefined1 *)pfStack_150;
        pfVar13 = pfStack_15c;
      }
code_r0x00067650:
      pfVar13[0x1e0] = (float)pfVar9;
      pfVar22 = pfStack_158;
      param_1 = pfVar13;
    }
code_r0x000674ec:
    pfVar19 = (float *)((int)pfStack_164 + 1);
    pfVar13 = pfVar8;
    pfStack_158 = pfVar22;
    pfStack_164 = pfVar19;
    if (pfVar8 == pfVar9) {
code_r0x00067500:
      if (iStack_6c == ___stack_chk_guard) {
        return;
      }
      func_0x00028604();
      puVar20 = &__stack_chk_guard;
      fVar30 = extraout_s5_00;
code_r0x00067e6c:
      bVar2 = (byte)(in_fpscr >> 0x18);
      fVar12 = fVar30;
      do {
        if ((bool)(bVar2 >> 7)) goto code_r0x00067998;
code_r0x00067a2c:
        fVar30 = *pfVar22;
        fVar33 = pfVar22[1];
        *(int *)((int)puVar11 + 0x6c) = (int)*(float *)((int)puVar11 + 0x6c) + 1;
        *(float *)((int)puVar11 + 0x48) = fVar30;
        *(float *)((int)puVar11 + 0x4c) = fVar33;
        fVar28 = *pfVar19;
        fVar33 = *(float *)((int)puVar11 + 0x28);
        fVar32 = *(float *)((int)puVar11 + 0x2c);
        fVar30 = *(float *)((int)puVar11 + 0x9c);
        fVar29 = fVar28 * (float)unaff_d14;
        if (!NAN(fVar35)) {
          ppuVar16 = (undefined **)0x1;
        }
        uVar18 = in_fpscr & 0xfffffff | (uint)(fVar29 < fVar33) << 0x1f |
                 (uint)(fVar29 == fVar33) << 0x1e;
        bVar2 = (byte)(uVar18 >> 0x18);
        fVar34 = (float)((ulonglong)unaff_d14 >> 0x20);
        if ((!(bool)(bVar2 >> 6 & 1) && (bool)(bVar2 >> 7) == (NAN(fVar29) || NAN(fVar33))) ||
           (uVar18 = in_fpscr & 0xfffffff | (uint)(fVar28 - fVar29 < fVar33) << 0x1f,
           SUB41(uVar18 >> 0x1f,0))) {
          fVar31 = param_1[0x245];
          fVar28 = fVar28 * 0.5;
          uVar18 = uVar18 & 0xfffffff | (uint)(fVar28 < fVar33) << 0x1f |
                   (uint)(fVar28 == fVar33) << 0x1e;
          bVar2 = (byte)(uVar18 >> 0x18);
          if ((bool)(bVar2 >> 6 & 1) || (bool)(bVar2 >> 7) != (NAN(fVar28) || NAN(fVar33))) {
            fVar31 = -fVar31;
          }
          fVar30 = fVar30 * fVar34 + param_1[0x4bb] * 0.004166667 * fVar31;
          *(float *)((int)puVar11 + 0x9c) = fVar30;
        }
        uVar26 = uVar18 & 0xfffffff | (uint)(fVar29 < fVar32) << 0x1f |
                 (uint)(fVar29 == fVar32) << 0x1e;
        in_fpscr = uVar26 | (uint)(NAN(fVar29) || NAN(fVar32)) << 0x1c;
        bVar2 = (byte)(uVar26 >> 0x18);
        if ((bool)(bVar2 >> 6 & 1) || bVar2 >> 7 != ((byte)(in_fpscr >> 0x1c) & 1)) {
          fVar33 = *pfStack_16c;
          in_fpscr = uVar18 & 0xfffffff | (uint)(fVar33 - fVar29 < fVar32) << 0x1f;
          if (SUB41(in_fpscr >> 0x1f,0)) goto code_r0x00067904;
        }
        else {
          fVar33 = *pfStack_16c;
code_r0x00067904:
          fVar28 = param_1[0x4bb];
          fVar29 = param_1[0x246];
          fVar33 = fVar33 * 0.5;
          *(float *)((int)puVar11 + 0x9c) = fVar30;
          uVar18 = in_fpscr & 0xfffffff | (uint)(fVar33 < fVar32) << 0x1f |
                   (uint)(fVar33 == fVar32) << 0x1e;
          in_fpscr = uVar18 | (uint)(NAN(fVar33) || NAN(fVar32)) << 0x1c;
          bVar2 = (byte)(uVar18 >> 0x18);
          if (!(bool)(bVar2 >> 6 & 1) && bVar2 >> 7 == ((byte)(in_fpscr >> 0x1c) & 1)) {
            fVar29 = -fVar29;
          }
          *(float *)((int)puVar11 + 0xa0) =
               *(float *)((int)puVar11 + 0xa0) * fVar34 + fVar28 * 0.004166667 * fVar29;
        }
        pfVar8 = pfVar13;
        if ((undefined *)((int)ppuVar14 + 1U) == puVar20) {
          ppuVar14 = (undefined **)((int)ppuVar14 + 1);
          pfStack_158 = pfStack_15c;
          pfVar13 = param_1;
          goto joined_r0x00067578;
        }
        ppuVar14 = (undefined **)((int)ppuVar14 + 1);
        fVar35 = pfVar13[1];
        fVar30 = pfVar13[2];
code_r0x00067964:
        pfVar13 = pfVar8;
        uVar18 = in_fpscr & 0xfffffff;
        uVar26 = uVar18 | (uint)(fVar35 < 0.3) << 0x1f;
        puVar11 = *(undefined1 **)((int)fVar30 + (int)ppuVar14 * 4);
        fVar30 = *(float *)((int)puVar11 + 0x5c);
        if (!SUB41(uVar26 >> 0x1f,0)) {
          fVar30 = *(float *)((int)puVar11 + 0x40);
          fVar28 = (0.3 - fVar35) * 1.4285715 + 1.0;
          fVar35 = (float)func_0x00029588(fVar28);
          fVar33 = *(float *)((int)puVar11 + 0x44);
          *(float *)((int)puVar11 + 0x5c) = fVar35 * fVar30;
          fVar35 = (float)func_0x00029588(fVar28 * fVar28);
          in_fpscr = uVar26 & 0xfffffff | (uint)(fVar28 == 0.0) << 0x1e |
                     (uint)(0.0 <= fVar28) << 0x1d;
          bVar2 = (byte)(in_fpscr >> 0x18);
          *(float *)((int)puVar11 + 0x60) = fVar35 * fVar33;
          if (!(bool)(bVar2 >> 5 & 1) || (bool)(bVar2 >> 6)) {
            ppuVar16 = (undefined **)0x1;
          }
          fVar35 = pfVar13[1];
          goto code_r0x00067a2c;
        }
        in_fpscr = uVar18 | (uint)(fVar30 < 0.35) << 0x1f;
        if (ppuVar14 == (undefined **)0x0) goto code_r0x00067e6c;
        if (SUB41(in_fpscr >> 0x1f,0)) {
code_r0x00067998:
          fVar35 = (float)((double)fVar30 + unaff_d8);
          in_fpscr = in_fpscr & 0xfffffff | (uint)(fVar35 == 1.0) << 0x1e |
                     (uint)(1.0 <= fVar35) << 0x1d;
          bVar2 = (byte)(in_fpscr >> 0x18);
          if ((bool)(bVar2 >> 5 & 1) && !(bool)(bVar2 >> 6)) {
            fVar35 = 1.0;
          }
          *(float *)((int)puVar11 + 0x5c) = fVar35;
          fVar35 = (float)func_0x00029480(fVar35);
          fVar30 = *(float *)((int)puVar11 + 0x60) +
                   fVar35 * (param_1[0x228] - *(float *)((int)puVar11 + 0x60));
          *(float *)((int)puVar11 + 0x60) = fVar30;
          *(float *)((int)puVar11 + 0x70) =
               *(float *)((int)puVar11 + 0x70) +
               fVar35 * (param_1[0x22c] - *(float *)((int)puVar11 + 0x70));
          fVar28 = param_1[0x22f];
          fVar33 = param_1[0x22e];
          *(float *)((int)puVar11 + 0x40) = *(float *)((int)puVar11 + 0x5c);
          *(float *)((int)puVar11 + 0x44) = fVar30;
          *(float *)((int)puVar11 + 0x7c) =
               *(float *)((int)puVar11 + 0x7c) + fVar35 * (fVar28 - *(float *)((int)puVar11 + 0x7c))
          ;
          *(float *)((int)puVar11 + 0x78) =
               *(float *)((int)puVar11 + 0x78) + fVar35 * (fVar33 - *(float *)((int)puVar11 + 0x78))
          ;
          fVar35 = pfVar13[1];
          goto code_r0x00067a2c;
        }
        in_fpscr = uVar18 | (uint)(fVar30 < fVar12) << 0x1f;
        bVar2 = (byte)(in_fpscr >> 0x18);
      } while( true );
    }
  } while( true );
}

