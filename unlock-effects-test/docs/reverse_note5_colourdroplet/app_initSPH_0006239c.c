
void _ZN8SPhysics18SPColourDropletApp7initSPHEv(int param_1)

{
  uint uVar1;
  byte bVar2;
  undefined4 uVar3;
  float *pfVar4;
  undefined4 *puVar5;
  uint in_fpscr;
  float fVar6;
  float fVar7;
  float fVar8;
  
  fVar8 = *(float *)(param_1 + 0x12cc);
  puVar5 = (undefined4 *)(param_1 + 0x910);
  uVar1 = in_fpscr & 0xfffffff | (uint)(fVar8 < 1.0) << 0x1f | (uint)(fVar8 == 1.0) << 0x1e;
  *puVar5 = 0x3c888889;
  pfVar4 = (float *)(param_1 + 0x908);
  bVar2 = (byte)(uVar1 >> 0x18);
  if ((bool)(bVar2 >> 6 & 1) || (bool)(bVar2 >> 7) != NAN(fVar8)) {
    fVar7 = 24.0;
    uVar3 = 0x18;
  }
  else {
    fVar7 = 18.0;
    uVar3 = 0x12;
  }
  *(undefined4 *)(param_1 + 0x8f8) = uVar3;
  fVar6 = (float)VectorSignedToFloat((int)(fVar8 * fVar7),(byte)(uVar1 >> 0x16) & 3);
  *(int *)(param_1 + 0x8fc) = (int)(fVar8 * fVar7);
  *(float *)(param_1 + 0x904) = *(float *)(param_1 + 0x90c) / fVar6;
  *(float *)(param_1 + 0x900) = *pfVar4 / fVar7;
  func_0x000285c8(param_1 + 0x50);
  func_0x0002960c(param_1 + 0x50,param_1 + 0x8f8,pfVar4);
  *(undefined4 *)(param_1 + 0xc0) = 2;
  *(undefined4 *)(param_1 + 0xac) = *puVar5;
  func_0x000285c8(param_1 + 0x3e0);
  func_0x0002960c(param_1 + 0x3e0,param_1 + 0x8f8,pfVar4);
  *(undefined4 *)(param_1 + 0x43c) = *puVar5;
  *(undefined4 *)(param_1 + 0x450) = 2;
  *(undefined4 *)(param_1 + 0x7d8) = 0;
  *(float *)(param_1 + 0x7e8) = *(float *)(param_1 + 0x1c0) * 0.1;
  *(undefined4 *)(param_1 + 2000) = 0;
  *(undefined4 *)(param_1 + 0x7d4) = 0;
  *(undefined4 *)(param_1 + 0x7b8) = 0;
  *(undefined4 *)(param_1 + 0x7bc) = 0;
  *(undefined4 *)(param_1 + 0x7fc) = 0x3f800000;
  *(undefined4 *)(param_1 + 0x7f8) = 0x41400000;
  *(undefined4 *)(param_1 + 0x808) = 0x40400000;
  *(undefined4 *)(param_1 + 0x80c) = 0x3f000000;
  *(undefined4 *)(param_1 + 0x800) = 0;
  *(undefined4 *)(param_1 + 0x804) = 0;
  *(undefined4 *)(param_1 + 0x810) = 0x42c80000;
  *(undefined4 *)(param_1 + 0x814) = 0x3e19999a;
  *(undefined4 *)(param_1 + 0x7e4) = 0x3dcccccd;
  *(undefined4 *)(param_1 + 0x818) = 0x3f000000;
  *(undefined4 *)(param_1 + 0x81c) = 0x3f000000;
  *(undefined4 *)(param_1 + 0x820) = 0;
  *(undefined4 *)(param_1 + 0x824) = 0;
  *(undefined4 *)(param_1 + 0x828) = 0;
  *(undefined4 *)(param_1 + 0x7f4) = 1;
  *(undefined4 *)(param_1 + 0x85c) = *(undefined4 *)(param_1 + 0x7a4);
  *(undefined4 *)(param_1 + 0x868) = *(undefined4 *)(param_1 + 0x7b0);
  *(undefined4 *)(param_1 + 0x86c) = *(undefined4 *)(param_1 + 0x7b4);
  *(undefined4 *)(param_1 + 0x870) = *(undefined4 *)(param_1 + 0x7b8);
  *(undefined4 *)(param_1 + 0x874) = *(undefined4 *)(param_1 + 0x7bc);
  func_0x000285ec(param_1 + 0x8ec,param_1 + 0x834);
  uVar3 = 0x40c00000;
  *(undefined2 *)(param_1 + 0x898) = *(undefined2 *)(param_1 + 0x7e0);
  *(undefined4 *)(param_1 + 0x894) = *(undefined4 *)(param_1 + 0x7dc);
  *(undefined4 *)(param_1 + 0x880) = *(undefined4 *)(param_1 + 0x7c8);
  *(undefined4 *)(param_1 + 0x884) = *(undefined4 *)(param_1 + 0x7cc);
  *(undefined4 *)(param_1 + 0x878) = *(undefined4 *)(param_1 + 0x7c0);
  *(undefined4 *)(param_1 + 0x87c) = *(undefined4 *)(param_1 + 0x7c4);
  *(undefined4 *)(param_1 + 0x888) = *(undefined4 *)(param_1 + 2000);
  *(undefined4 *)(param_1 + 0x88c) = *(undefined4 *)(param_1 + 0x7d4);
  *(undefined4 *)(param_1 + 0x8d0) = *(undefined4 *)(param_1 + 0x818);
  *(undefined4 *)(param_1 + 0x8d4) = *(undefined4 *)(param_1 + 0x81c);
  *(undefined4 *)(param_1 + 0x8d8) = *(undefined4 *)(param_1 + 0x820);
  *(undefined4 *)(param_1 + 0x89c) = *(undefined4 *)(param_1 + 0x7e4);
  *(undefined4 *)(param_1 + 0x8a4) = *(undefined4 *)(param_1 + 0x7ec);
  *(undefined4 *)(param_1 + 0x8a8) = *(undefined4 *)(param_1 + 0x7f0);
  *(undefined4 *)(param_1 + 0x8ac) = *(undefined4 *)(param_1 + 0x7f4);
  *(undefined4 *)(param_1 + 0x8c0) = *(undefined4 *)(param_1 + 0x808);
  *(undefined4 *)(param_1 + 0x8c4) = *(undefined4 *)(param_1 + 0x80c);
  *(undefined4 *)(param_1 + 0x8c8) = *(undefined4 *)(param_1 + 0x810);
  *(undefined4 *)(param_1 + 0x8b4) = *(undefined4 *)(param_1 + 0x7fc);
  *(undefined4 *)(param_1 + 0x8b8) = *(undefined4 *)(param_1 + 0x800);
  *(undefined4 *)(param_1 + 0x8bc) = *(undefined4 *)(param_1 + 0x804);
  *(undefined4 *)(param_1 + 0x8cc) = *(undefined4 *)(param_1 + 0x814);
  *(undefined4 *)(param_1 + 0x890) = *(undefined4 *)(param_1 + 0x7d8);
  if (*(char *)(param_1 + 0x23) == '\0') {
    uVar3 = 0x40800000;
  }
  *(undefined4 *)(param_1 + 0x860) = *(undefined4 *)(param_1 + 0x7a8);
  *(undefined4 *)(param_1 + 0x864) = *(undefined4 *)(param_1 + 0x7ac);
  *(undefined4 *)(param_1 + 0x8dc) = *(undefined4 *)(param_1 + 0x824);
  *(undefined4 *)(param_1 + 0x8e0) = *(undefined4 *)(param_1 + 0x828);
  *(undefined4 *)(param_1 + 0x8e8) = *(undefined4 *)(param_1 + 0x830);
  *(undefined4 *)(param_1 + 0x8e4) = *(undefined4 *)(param_1 + 0x82c);
  *(float *)(param_1 + 0x8a0) = *(float *)(param_1 + 0x1c0) * 3.7;
  *(undefined4 *)(param_1 + 0x8b0) = uVar3;
  *(undefined4 *)(param_1 + 0x8b8) = 0x3ecccccd;
  *(undefined4 *)(param_1 + 0x8bc) = 0x3ecccccd;
  *(undefined4 *)(param_1 + 0x914) = 0xbeb33333;
  *(undefined4 *)(param_1 + 0x918) = 0xbeb33333;
  return;
}

