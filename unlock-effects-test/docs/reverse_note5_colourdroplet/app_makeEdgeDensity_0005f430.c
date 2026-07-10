
void _ZN8SPhysics18SPColourDropletApp15makeEdgeDensityEv
               (int param_1,undefined4 param_2,undefined4 param_3,undefined4 param_4)

{
  byte bVar1;
  int iVar2;
  float *pfVar3;
  int iVar4;
  int iVar5;
  int iVar6;
  uint in_fpscr;
  uint uVar7;
  int iVar8;
  float fVar9;
  float fVar10;
  float fVar11;
  float fVar12;
  float fVar13;
  float fVar14;
  
  iVar6 = *(int *)(param_1 + 0x44);
  iVar8 = *(int *)(param_1 + 0x48) - iVar6 >> 2;
  if (0 < iVar8) {
    func_0x00027f44(iVar6,0,iVar8 << 2,param_4,param_4);
  }
  fVar14 = *(float *)(param_1 + 0x34);
  fVar13 = *(float *)(param_1 + 0x38);
  uVar7 = in_fpscr & 0xfffffff | (uint)(fVar13 < 0.0) << 0x1f | (uint)(fVar13 == 0.0) << 0x1e;
  iVar8 = (uint)(0.0 < fVar14 * 0.175) * (int)(fVar14 * 0.175);
  bVar1 = (byte)(uVar7 >> 0x18);
  if (!(bool)(bVar1 >> 6 & 1) && (bool)(bVar1 >> 7) == NAN(fVar13)) {
    fVar10 = (float)VectorUnsignedToFloat(iVar8,(byte)(uVar7 >> 0x16) & 3);
    fVar11 = 0.0;
    iVar2 = 0;
    do {
      if (iVar8 != 0) {
        iVar4 = 0;
        while( true ) {
          fVar13 = (float)VectorSignedToFloat(iVar4,(byte)(uVar7 >> 0x16) & 3);
          fVar9 = 1.0 - fVar13 * (1.0 / fVar10);
          fVar14 = fVar13 + fVar14 * fVar11;
          fVar9 = fVar9 * fVar9 * 0.7;
          *(float *)(iVar6 + (uint)(0.0 < fVar14) * (int)fVar14 * 4) = fVar9;
          fVar13 = (*(float *)(param_1 + 0x34) + -1.0 + fVar11 * *(float *)(param_1 + 0x34)) -
                   fVar13;
          *(float *)(iVar6 + (uint)(0.0 < fVar13) * (int)fVar13 * 4) = fVar9;
          if (iVar4 + 1 == iVar8) break;
          fVar14 = *(float *)(param_1 + 0x34);
          iVar4 = iVar4 + 1;
        }
        fVar13 = *(float *)(param_1 + 0x38);
        fVar14 = *(float *)(param_1 + 0x34);
      }
      iVar2 = iVar2 + 1;
      fVar11 = (float)VectorSignedToFloat(iVar2,(byte)(uVar7 >> 0x16) & 3);
      uVar7 = uVar7 & 0xfffffff | (uint)(fVar11 < fVar13) << 0x1f;
    } while (SUB41(uVar7 >> 0x1f,0));
  }
  uVar7 = uVar7 & 0xfffffff | (uint)(fVar14 < 0.0) << 0x1f | (uint)(fVar14 == 0.0) << 0x1e;
  bVar1 = (byte)(uVar7 >> 0x18);
  if (!(bool)(bVar1 >> 6 & 1) && (bool)(bVar1 >> 7) == NAN(fVar14)) {
    fVar10 = 0.0;
    iVar2 = 0;
    fVar13 = (float)VectorUnsignedToFloat(iVar8,(byte)(uVar7 >> 0x16) & 3);
    do {
      if (iVar8 != 0) {
        iVar4 = 0;
        do {
          iVar5 = iVar4 + 1;
          fVar12 = (float)VectorSignedToFloat(iVar4,(byte)(uVar7 >> 0x16) & 3);
          fVar9 = fVar10 + fVar12 * fVar14;
          fVar11 = 1.0 - fVar12 * (1.0 / fVar13);
          fVar11 = fVar11 * fVar11 * 0.7;
          pfVar3 = (float *)(iVar6 + (uint)(0.0 < fVar9) * (int)fVar9 * 4);
          fVar9 = *pfVar3;
          if (fVar9 < fVar11) {
            *pfVar3 = fVar11;
          }
          if (fVar9 < fVar11) {
            fVar14 = *(float *)(param_1 + 0x34);
          }
          fVar9 = fVar10 + fVar14 * ((*(float *)(param_1 + 0x38) + -1.0) - fVar12);
          pfVar3 = (float *)(iVar6 + (uint)(0.0 < fVar9) * (int)fVar9 * 4);
          fVar9 = *pfVar3;
          uVar7 = uVar7 & 0xfffffff | (uint)(fVar11 < fVar9) << 0x1f |
                  (uint)(fVar11 == fVar9) << 0x1e;
          bVar1 = (byte)(uVar7 >> 0x18);
          if (!(bool)(bVar1 >> 6 & 1) && (bool)(bVar1 >> 7) == (NAN(fVar11) || NAN(fVar9))) {
            *pfVar3 = fVar11;
            fVar14 = *(float *)(param_1 + 0x34);
          }
          iVar4 = iVar5;
        } while (iVar5 != iVar8);
      }
      iVar2 = iVar2 + 1;
      fVar10 = (float)VectorSignedToFloat(iVar2,(byte)(uVar7 >> 0x16) & 3);
      uVar7 = uVar7 & 0xfffffff | (uint)(fVar10 < fVar14) << 0x1f;
    } while (SUB41(uVar7 >> 0x1f,0));
    return;
  }
  return;
}

