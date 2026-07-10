
void _ZN8SPhysics18SPColourDropletApp21onEventSurfaceChangedEii(int param_1,int param_2,int param_3)

{
  uint uVar1;
  byte bVar2;
  int iVar3;
  undefined4 uVar4;
  int iVar5;
  undefined4 *puVar6;
  undefined4 *puVar7;
  int iVar8;
  uint in_fpscr;
  uint uVar9;
  undefined4 uVar10;
  float fVar11;
  int extraout_s11;
  int extraout_s13;
  int iVar12;
  float fVar13;
  float fVar14;
  
  func_0x00028370(4,&UNK_000698bc,&UNK_0007071c,param_2,param_3);
  iVar3 = *(int *)(param_1 + 0x10);
  iVar5 = *(int *)(param_1 + 0x14);
  fVar13 = (float)VectorSignedToFloat(param_3,(byte)(in_fpscr >> 0x16) & 3);
  fVar11 = (float)VectorSignedToFloat(param_2,(byte)(in_fpscr >> 0x16) & 3);
  iVar12 = extraout_s13;
  iVar8 = iVar3;
  if (iVar5 <= iVar3) {
    iVar12 = iVar5;
    iVar8 = extraout_s11;
  }
  fVar11 = fVar13 / fVar11;
  if (iVar3 < iVar5) {
    fVar14 = (float)VectorSignedToFloat(iVar8,(byte)(in_fpscr >> 0x16) & 3);
  }
  else {
    fVar14 = (float)VectorSignedToFloat(iVar12,(byte)(in_fpscr >> 0x16) & 3);
  }
  uVar1 = in_fpscr & 0xfffffff | (uint)(fVar11 < 1.0) << 0x1f | (uint)(fVar11 == 1.0) << 0x1e;
  uVar9 = uVar1 | (uint)NAN(fVar11) << 0x1c;
  bVar2 = (byte)(uVar1 >> 0x18);
  *(float *)(param_1 + 0x12dc) = 7.2 / fVar14;
  *(float *)(param_1 + 0x12cc) = fVar11;
  *(int *)(param_1 + 0x10) = param_2;
  *(int *)(param_1 + 0x14) = param_3;
  *(undefined4 *)(param_1 + 0x12e4) = 0x3fc00000;
  if ((bool)(bVar2 >> 6 & 1) || bVar2 >> 7 != ((byte)(uVar9 >> 0x1c) & 1)) {
    uVar4 = 0x43b28000;
    fVar14 = fVar11 * 178.5;
    *(undefined4 *)(param_1 + 0x34) = 0x43328000;
    *(float *)(param_1 + 0x38) = fVar14;
    *(undefined4 *)(param_1 + 0x908) = 0x3f19999a;
    *(float *)(param_1 + 0x90c) = fVar11 * 0.6;
    *(float *)(param_1 + 0x12d0) = fVar11 * 0.6 * 0.16;
  }
  else {
    uVar4 = 0x43870000;
    fVar14 = fVar11 * 135.0;
    *(undefined4 *)(param_1 + 0x34) = 0x43070000;
    *(float *)(param_1 + 0x38) = fVar14;
    *(undefined4 *)(param_1 + 0x908) = 0x3ee66667;
    *(float *)(param_1 + 0x90c) = fVar11 * 0.45000002;
    *(undefined4 *)(param_1 + 0x12d0) = 0x3d9374bd;
  }
  puVar7 = (undefined4 *)(param_1 + 0x908);
  puVar6 = (undefined4 *)(param_1 + 0x90c);
  *(undefined4 *)(param_1 + 0x3c) = uVar4;
  *(bool *)(param_1 + 0x21) = param_2 <= param_3;
  *(undefined1 *)(param_1 + 0x22) = 1;
  iVar8 = param_1 + 0xa90;
  if (*(char *)(param_1 + 0x23) == '\0') {
    fVar11 = 0.00078125;
  }
  else {
    fVar11 = 0.0009765625;
  }
  *(float *)(param_1 + 0x40) = fVar14 + fVar14;
  *(float *)(param_1 + 0x12c0) = fVar13 * fVar11;
  func_0x000296cc(param_1);
  func_0x000296f0(param_1);
  func_0x00029618(param_1);
  fVar13 = *(float *)(param_1 + 0x34);
  fVar11 = *(float *)(param_1 + 0x38);
  func_0x00028898(0xde1,*(undefined4 *)(param_1 + 0x958));
  func_0x000288a4(0xde1,0x2802,0x812f);
  func_0x000288a4(0xde1,0x2803,0x812f);
  func_0x000288a4(0xde1,0x2801,0x2601);
  func_0x000288a4(0xde1,0x2800,0x2601);
  func_0x000288b0(0xde1,0,0x1908,(uint)(0.0 < fVar13) * (int)fVar13,
                  (uint)(0.0 < fVar11) * (int)fVar11,0,0x1908,0x1401,0);
  func_0x00028838(0x8d40,*(undefined4 *)(param_1 + 0x954));
  func_0x00028850(0,0,0,0);
  func_0x0002885c(0x4100);
  func_0x00028838(0x8d40,0);
  fVar11 = *(float *)(param_1 + 0x3c);
  fVar13 = *(float *)(param_1 + 0x40);
  func_0x00028898(0xde1,*(undefined4 *)(param_1 + 0x968));
  func_0x000288a4(0xde1,0x2802,0x812f);
  func_0x000288a4(0xde1,0x2803,0x812f);
  func_0x000288a4(0xde1,0x2801,0x2601);
  func_0x000288a4(0xde1,0x2800,0x2601);
  func_0x000288b0(0xde1,0,0x1908,(uint)(0.0 < fVar11) * (int)fVar11,
                  (uint)(0.0 < fVar13) * (int)fVar13,0,0x1908,0x1401,0);
  func_0x00028838(0x8d40,*(undefined4 *)(param_1 + 0x964));
  func_0x00028850(0,0,0,0);
  func_0x0002885c(0x4100);
  func_0x00028838(0x8d40,0);
  fVar13 = (float)VectorSignedToFloat(*(undefined4 *)(param_1 + 0x10),(byte)(uVar9 >> 0x16) & 3);
  fVar11 = *(float *)(param_1 + 0x12dc);
  fVar14 = (float)VectorSignedToFloat(*(undefined4 *)(param_1 + 0x14),(byte)(uVar9 >> 0x16) & 3);
  fVar13 = fVar13 * fVar11;
  fVar14 = fVar14 * fVar11;
  func_0x00028898(0xde1,*(undefined4 *)(param_1 + 0x974));
  func_0x000288a4(0xde1,0x2802,0x812f);
  func_0x000288a4(0xde1,0x2803,0x812f);
  func_0x000288a4(0xde1,0x2801,0x2601);
  func_0x000288a4(0xde1,0x2800,0x2601);
  func_0x000288b0(0xde1,0,0x1908,(uint)(0.0 < fVar13) * (int)fVar13,
                  (uint)(0.0 < fVar14) * (int)fVar14,0,0x1908,0x1401,0);
  func_0x00028838(0x8d40,*(undefined4 *)(param_1 + 0x970));
  func_0x00028850(0,0,0,0);
  func_0x0002885c(0x4100);
  func_0x00028838(0x8d40,0);
  func_0x000294e0(iVar8,(float *)(param_1 + 0x12c0));
  func_0x00029534(param_1 + 0xc2c,*puVar7,*puVar6);
  uVar4 = VectorSignedToFloat(*(undefined4 *)(param_1 + 0x14),(byte)(uVar9 >> 0x16) & 3);
  uVar10 = VectorSignedToFloat(*(undefined4 *)(param_1 + 0x10),(byte)(uVar9 >> 0x16) & 3);
  func_0x00029534(iVar8,uVar10,uVar4);
  func_0x000294f8(iVar8,param_1 + 0x34);
  func_0x00029534(param_1 + 0xd98,*puVar7,*puVar6);
  func_0x00029510(param_1 + 0xd98,param_1 + 0x974);
  func_0x00029534(param_1 + 0xe54,*puVar7,*puVar6);
  func_0x00029528(param_1 + 0xe54,param_1 + 0x3c);
  uVar4 = VectorSignedToFloat(*(undefined4 *)(param_1 + 0x10),(byte)(uVar9 >> 0x16) & 3);
  uVar10 = VectorSignedToFloat(*(undefined4 *)(param_1 + 0x14),(byte)(uVar9 >> 0x16) & 3);
  func_0x00029534(param_1 + 0xf30,uVar4,uVar10);
  func_0x00029534(param_1 + 0x10a4,*puVar7,*puVar6);
  *(undefined1 *)(param_1 + 0x25) = 1;
  return;
}

